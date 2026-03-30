package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.dto.CartDto;
import com.strongwine.strongwine.dto.CheckoutForm;
import com.strongwine.strongwine.dto.CartItemDto;
import com.strongwine.strongwine.dto.CheckoutRequest;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.CartService;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.VietnamGeoValidationService;
import com.strongwine.strongwine.entity.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Controller for shopping cart functionality
 */
@Controller
@RequestMapping("/cart")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private static final String CHECKOUT_TOKEN_SESSION_KEY = "checkout:token";
    private static final List<String> SUPPORTED_PAYMENT_METHODS = List.of("STRIPE");
    private static final Set<String> ALLOWED_PAYMENT_METHODS = Set.copyOf(SUPPORTED_PAYMENT_METHODS);
    
    @Autowired
    private CartService cartService;
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private VietnamGeoValidationService vietnamGeoValidationService;

    private Long getUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        if (!(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            return null;
        }
        String username = userDetails.getUsername();
        User user = userRepository.findByUsername(username).orElse(null);
        return user != null ? user.getId() : null;
    }

    /**
     * View cart
     */
    @GetMapping
    public String viewCart() {
        return "cart";
    }
    
    /**
     * Show checkout form
     */
    @GetMapping("/checkout")
    public String showCheckout(HttpSession session, Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để thanh toán");
            return "redirect:/login";
        }
        
        model.addAttribute("checkoutForm", new CheckoutForm());
        model.addAttribute("paymentMethods", SUPPORTED_PAYMENT_METHODS);

        String checkoutToken = UUID.randomUUID().toString();
        session.setAttribute(CHECKOUT_TOKEN_SESSION_KEY, checkoutToken);
        model.addAttribute("checkoutToken", checkoutToken);
        
        return "checkout";
    }
    
    /**
     * Process checkout and create order
     */
    @PostMapping(value = "/checkout/process", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> processCheckout(
            @RequestBody CheckoutRequest checkoutRequest,
            HttpSession session,
            Authentication authentication,
            HttpServletRequest request) {
            
        Long userId = getUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(UNAUTHORIZED).body(Map.of("error", "Vui lòng đăng nhập để thanh toán"));
        }

        if (checkoutRequest == null) {
            return badRequest("Yêu cầu thanh toán không hợp lệ");
        }
        
        String sessionCheckoutToken = (String) session.getAttribute(CHECKOUT_TOKEN_SESSION_KEY);
        if (sessionCheckoutToken == null || !sessionCheckoutToken.equals(checkoutRequest.getCheckoutToken())) {
            return badRequest("Yêu cầu thanh toán không hợp lệ hoặc đã được xử lý.");
        }
        
        // Validate form fields
        if (checkoutRequest.getFullName() == null || checkoutRequest.getFullName().trim().isEmpty()) {
            return badRequest("Vui lòng nhập họ tên");
        }
        
        if (checkoutRequest.getPhone() == null || checkoutRequest.getPhone().trim().isEmpty()) {
            return badRequest("Vui lòng nhập số điện thoại");
        }

        if (!checkoutRequest.getPhone().matches("^(0|\\+84)[0-9]{9,10}$")) {
            return badRequest("Số điện thoại không hợp lệ");
        }
        
        if (checkoutRequest.getAddress() == null || checkoutRequest.getAddress().trim().isEmpty()) {
            return badRequest("Vui lòng nhập địa chỉ");
        }

        if (!hasValidGeoPair(checkoutRequest.getDeliveryLat(), checkoutRequest.getDeliveryLng())) {
            return badRequest("Vui lòng chọn vị trí giao hàng hợp lệ trên bản đồ Việt Nam");
        }

        if (!vietnamGeoValidationService.isWithinMainlandVietnam(checkoutRequest.getDeliveryLat(), checkoutRequest.getDeliveryLng())) {
            return badRequest("Vị trí giao hàng phải nằm trong phạm vi đất liền Việt Nam");
        }

        if (checkoutRequest.getPaymentMethod() == null || checkoutRequest.getPaymentMethod().trim().isEmpty()) {
            return badRequest("Vui lòng chọn phương thức thanh toán");
        }

        String normalizedPaymentMethod = checkoutRequest.getPaymentMethod().trim().toUpperCase();
        if (!ALLOWED_PAYMENT_METHODS.contains(normalizedPaymentMethod)) {
            return badRequest("Phương thức thanh toán không hợp lệ");
        }
        
        Order order = null;
        try {
            CartDto cartDto = cartService.validateCheckoutCart(checkoutRequest.getItems());
            if (cartDto.isEmpty()) {
                return badRequest("Giỏ hàng trống");
            }

            for (CartItemDto item : cartDto.getItems()) {
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    return badRequest("Số lượng sản phẩm không hợp lệ");
                }
            }

            Map<Long, Integer> itemsMap = cartService.toItemMap(cartDto);

            order = orderService.createPendingOrder(
                    userId,
                    itemsMap,
                    checkoutRequest.getFullName(),
                    checkoutRequest.getPhone(),
                    buildAddressWithGeo(checkoutRequest.getAddress(), checkoutRequest.getDeliveryLat(), checkoutRequest.getDeliveryLng()),
                    checkoutRequest.getNote(),
                    normalizedPaymentMethod);

            String baseUrl = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort());
            String paymentRedirectUrl = paymentService.createPaymentSession(order, normalizedPaymentMethod, baseUrl);

            session.removeAttribute(CHECKOUT_TOKEN_SESSION_KEY);

            return ResponseEntity.ok(Map.of("redirectUrl", paymentRedirectUrl));
        } catch (Exception e) {
            log.error("Checkout failed", e);
            if (order != null) {
                orderService.cancelPendingOrder(order.getId());
            }
            return badRequest("Thanh toán thất bại: " + rootCauseMessage(e));
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return "Lỗi hệ thống khi ghi nhận thanh toán";
        }
        return message;
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.status(BAD_REQUEST).body(Map.of("error", message));
    }

    private boolean hasValidGeoPair(Double lat, Double lng) {
        return lat != null && lng != null;
    }

    private String buildAddressWithGeo(String address, Double lat, Double lng) {
        String normalizedAddress = address == null ? "" : address.trim();
        String enriched = normalizedAddress + String.format(" [GPS: %.6f, %.6f]", lat, lng);
        if (enriched.length() <= 1000) {
            return enriched;
        }
        return enriched.substring(0, 1000);
    }
    
}

