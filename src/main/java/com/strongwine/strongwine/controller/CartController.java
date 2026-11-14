package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.dto.CartDto;
import com.strongwine.strongwine.dto.CheckoutForm;
import com.strongwine.strongwine.dto.CartItemDto;
import com.strongwine.strongwine.dto.CheckoutRequest;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.exception.DeliveryAddressNotSupportedException;
import com.strongwine.strongwine.service.CartService;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.UserShippingProfileService;
import com.strongwine.strongwine.service.UserShippingProfileService.ShippingProfileData;
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
import com.strongwine.strongwine.util.AddressTextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.regex.Pattern;
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
    private static final List<String> SUPPORTED_PAYMENT_METHODS = List.of("COD", "STRIPE", "VNPAY");
    private static final Set<String> ALLOWED_PAYMENT_METHODS = Set.copyOf(SUPPORTED_PAYMENT_METHODS);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    
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

    @Autowired
    private UserShippingProfileService userShippingProfileService;

    @Autowired(required = false)
    private com.strongwine.strongwine.service.port.VoucherPricingPort voucherPricingPort;

    @Autowired(required = false)
    private com.strongwine.strongwine.service.ShippingFeeService shippingFeeService;

    @Autowired(required = false)
    private com.strongwine.strongwine.repository.WarehouseRepository warehouseRepository;

    @Autowired(required = false)
    private com.strongwine.strongwine.service.UserAddressService userAddressService;

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
        
        // Load default shipping profile saved by customer
        ShippingProfileData profile = userShippingProfileService.getProfile(userId);
        CheckoutForm checkoutForm = new CheckoutForm();
        String userEmail = userRepository.findById(userId).map(User::getEmail).orElse("");
        checkoutForm.setEmail(profile.email() != null && !profile.email().isBlank() ? profile.email() : userEmail);
        checkoutForm.setFullName(profile.fullName() != null ? profile.fullName() : "");
        checkoutForm.setPhone(profile.phone() != null ? profile.phone() : "");
        checkoutForm.setAddress(profile.address() != null ? profile.address() : "");
        checkoutForm.setDeliveryLat(profile.deliveryLat());
        checkoutForm.setDeliveryLng(profile.deliveryLng());

        model.addAttribute("checkoutForm", checkoutForm);
        model.addAttribute("paymentMethods", SUPPORTED_PAYMENT_METHODS);
        if (userAddressService != null) {
            model.addAttribute("savedAddresses", userAddressService.getAddressesForUser(userId));
            model.addAttribute("defaultAddress", userAddressService.getDefaultAddress(userId).orElse(null));
        } else {
            model.addAttribute("savedAddresses", List.of());
            model.addAttribute("defaultAddress", null);
        }

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

        // Resolve savedAddressId if supplied
        if (checkoutRequest.getSavedAddressId() != null) {
            if (userAddressService == null) {
                return badRequest("Không thể truy xuất sổ địa chỉ");
            }
            try {
                com.strongwine.strongwine.entity.UserAddress savedAddress =
                        userAddressService.getOwnedAddress(userId, checkoutRequest.getSavedAddressId());
                if (checkoutRequest.getFullName() == null || checkoutRequest.getFullName().isBlank()) {
                    checkoutRequest.setFullName(savedAddress.getRecipientName());
                }
                if (checkoutRequest.getPhone() == null || checkoutRequest.getPhone().isBlank()) {
                    checkoutRequest.setPhone(savedAddress.getPhone());
                }
                if (checkoutRequest.getAddress() == null || checkoutRequest.getAddress().isBlank()) {
                    checkoutRequest.setAddress(userAddressService.formatFullAddress(savedAddress));
                }
                if (checkoutRequest.getDeliveryLat() == null && savedAddress.getLatitude() != null) {
                    checkoutRequest.setDeliveryLat(savedAddress.getLatitude());
                }
                if (checkoutRequest.getDeliveryLng() == null && savedAddress.getLongitude() != null) {
                    checkoutRequest.setDeliveryLng(savedAddress.getLongitude());
                }
            } catch (org.springframework.security.access.AccessDeniedException ex) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body(Map.of("error", ex.getMessage()));
            } catch (RuntimeException ex) {
                return badRequest(ex.getMessage());
            }
        }

        if (checkoutRequest.getEmail() == null || checkoutRequest.getEmail().trim().isEmpty()) {
            String fallbackEmail = resolveUserEmail(authentication, userId);
            if (fallbackEmail != null && !fallbackEmail.isBlank()) {
                checkoutRequest.setEmail(fallbackEmail);
            }
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

        if (checkoutRequest.getEmail() == null || checkoutRequest.getEmail().trim().isEmpty()) {
            return badRequest("Vui lòng nhập email nhận OTP giao hàng");
        }

        String normalizedEmail;
        try {
            normalizedEmail = normalizeEmail(checkoutRequest.getEmail());
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
        
        if (checkoutRequest.getAddress() == null || checkoutRequest.getAddress().trim().isEmpty()) {
            return badRequest("Vui lòng nhập địa chỉ giao hàng chi tiết");
        }

        Double deliveryLat = checkoutRequest.getDeliveryLat();
        Double deliveryLng = checkoutRequest.getDeliveryLng();

        // If coordinates weren't set on interactive map, auto-resolve default coordinates from address text
        if (deliveryLat == null || deliveryLng == null) {
            double[] resolvedCoords = vietnamGeoValidationService.resolveDefaultCoordinates(checkoutRequest.getAddress());
            deliveryLat = resolvedCoords[0];
            deliveryLng = resolvedCoords[1];
            checkoutRequest.setDeliveryLat(deliveryLat);
            checkoutRequest.setDeliveryLng(deliveryLng);
        }

        // Strictly validate: mainland Vietnam delivery only, rejecting remote islands (Hoàng Sa, Trường Sa, Côn Đảo, v.v.)
        try {
            vietnamGeoValidationService.validateMainlandDeliveryAddress(checkoutRequest.getAddress(), deliveryLat, deliveryLng);
        } catch (DeliveryAddressNotSupportedException | IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
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

            String normalizedAddress = normalizeAddressText(checkoutRequest.getAddress());
            if (normalizedAddress.isBlank()) {
                return badRequest("Vui lòng nhập địa chỉ giao hàng hợp lệ");
            }

            for (CartItemDto item : cartDto.getItems()) {
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    return badRequest("Số lượng sản phẩm không hợp lệ");
                }
            }

            Map<Long, Integer> itemsMap = cartService.toItemMap(cartDto);

            boolean useV3ServerPricing = shippingFeeService != null
                    || voucherPricingPort != null
                    || warehouseRepository != null
                    || checkoutRequest.getSavedAddressId() != null
                    || (checkoutRequest.getVoucherCode() != null && !checkoutRequest.getVoucherCode().isBlank());

            if (useV3ServerPricing) {
                java.math.BigDecimal subtotal = resolveServerSubtotal(cartDto);
                com.strongwine.strongwine.entity.Warehouse allocatedWarehouse =
                        resolveNearestWarehouse(deliveryLat, deliveryLng);
                java.math.BigDecimal baseShippingFee = calculateServerShippingFee(
                        allocatedWarehouse, deliveryLat, deliveryLng, subtotal);
                java.math.BigDecimal discountAmount = java.math.BigDecimal.ZERO;
                java.math.BigDecimal finalShippingFee = baseShippingFee;
                String appliedVoucherCode = null;

                if (checkoutRequest.getVoucherCode() != null && !checkoutRequest.getVoucherCode().trim().isEmpty()) {
                    if (voucherPricingPort == null) {
                        return badRequest("Hệ thống mã giảm giá hiện không khả dụng");
                    }
                    String candidateCode = checkoutRequest.getVoucherCode().trim();
                    com.strongwine.strongwine.dto.VoucherValidationResult voucherResult =
                            voucherPricingPort.validateAndQuote(candidateCode, userId, subtotal, baseShippingFee);
                    if (voucherResult == null || !voucherResult.valid()) {
                        return badRequest(voucherResult != null && voucherResult.message() != null
                                ? voucherResult.message()
                                : "Mã giảm giá không hợp lệ");
                    }
                    discountAmount = voucherResult.discountAmount() != null
                            ? voucherResult.discountAmount()
                            : java.math.BigDecimal.ZERO;
                    finalShippingFee = voucherResult.finalShippingFee() != null
                            ? voucherResult.finalShippingFee()
                            : baseShippingFee;
                    appliedVoucherCode = voucherResult.code() != null ? voucherResult.code() : candidateCode;
                }

                order = orderService.createPendingOrder(
                        userId,
                        itemsMap,
                        checkoutRequest.getFullName(),
                        checkoutRequest.getPhone(),
                        normalizedEmail,
                        normalizedAddress,
                        deliveryLat,
                        deliveryLng,
                        checkoutRequest.getNote(),
                        normalizedPaymentMethod,
                        subtotal,
                        discountAmount,
                        finalShippingFee,
                        appliedVoucherCode,
                        allocatedWarehouse);

                if (appliedVoucherCode != null && voucherPricingPort != null && order != null) {
                    User orderUser = order.getUser() != null
                            ? order.getUser()
                            : resolveCurrentUser(authentication, userId);
                    voucherPricingPort.redeemForOrder(appliedVoucherCode, orderUser, order);
                }
            } else {
                order = orderService.createPendingOrder(
                        userId,
                        itemsMap,
                        checkoutRequest.getFullName(),
                        checkoutRequest.getPhone(),
                        normalizedEmail,
                        normalizedAddress,
                        deliveryLat,
                        deliveryLng,
                        checkoutRequest.getNote(),
                        normalizedPaymentMethod);
            }

            // Persist the customer's verified shipping details into their profile for subsequent checkouts
            try {
                userShippingProfileService.saveProfile(
                        userId,
                        checkoutRequest.getFullName(),
                        checkoutRequest.getPhone(),
                        normalizedAddress,
                        deliveryLat,
                        deliveryLng);
            } catch (Exception ex) {
                log.warn("Could not save shipping profile for user {}: {}", userId, ex.getMessage());
            }

            String baseUrl = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort());
            String paymentRedirectUrl = paymentService.createPaymentSession(order, normalizedPaymentMethod, baseUrl, request.getRemoteAddr());

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

    private String normalizeAddressText(String address) {
        String normalizedAddress = AddressTextUtils.stripLegacyGpsSuffix(address == null ? "" : address.trim());
        if (normalizedAddress.length() <= 1000) {
            return normalizedAddress;
        }
        return normalizedAddress.substring(0, 1000);
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập email nhận OTP giao hàng");
        }
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("Email không hợp lệ");
        }
        return normalizedEmail;
    }

    @PostMapping(value = "/checkout/quote", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quoteCheckout(
            @RequestBody(required = false) CheckoutRequest checkoutRequest,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        CheckoutRequest req = checkoutRequest != null ? checkoutRequest : new CheckoutRequest();

        CartDto cartDto;
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            cartDto = cartService.validateCheckoutCart(req.getItems());
        } else {
            cartDto = cartService.getCartDetails();
        }
        java.math.BigDecimal subtotal = resolveServerSubtotal(cartDto);

        Double lat = req.getDeliveryLat();
        Double lng = req.getDeliveryLng();
        if (req.getSavedAddressId() != null && userAddressService != null && userId != null) {
            try {
                com.strongwine.strongwine.entity.UserAddress addr =
                        userAddressService.getOwnedAddress(userId, req.getSavedAddressId());
                if (lat == null) lat = addr.getLatitude();
                if (lng == null) lng = addr.getLongitude();
            } catch (Exception ignored) {
            }
        } else if ((lat == null || lng == null) && req.getAddress() != null && !req.getAddress().isBlank()) {
            double[] resolved = vietnamGeoValidationService.resolveDefaultCoordinates(req.getAddress());
            if (resolved != null && resolved.length == 2) {
                lat = resolved[0];
                lng = resolved[1];
            }
        }

        com.strongwine.strongwine.entity.Warehouse warehouse = resolveNearestWarehouse(lat, lng);
        java.math.BigDecimal baseShippingFee = calculateServerShippingFee(warehouse, lat, lng, subtotal);
        java.math.BigDecimal discountAmount = java.math.BigDecimal.ZERO;
        java.math.BigDecimal shippingDiscount = java.math.BigDecimal.ZERO;
        java.math.BigDecimal finalShippingFee = baseShippingFee;
        boolean voucherValid = true;
        String voucherMessage = "";

        if (req.getVoucherCode() != null && !req.getVoucherCode().trim().isEmpty()) {
            if (voucherPricingPort == null) {
                voucherValid = false;
                voucherMessage = "Hệ thống mã giảm giá hiện không khả dụng";
            } else {
                com.strongwine.strongwine.dto.VoucherValidationResult quote =
                        voucherPricingPort.validateAndQuote(req.getVoucherCode().trim(), userId, subtotal, baseShippingFee);
                if (quote != null && quote.valid()) {
                    voucherValid = true;
                    voucherMessage = quote.message() != null ? quote.message() : "Hợp lệ";
                    discountAmount = quote.discountAmount() != null ? quote.discountAmount() : java.math.BigDecimal.ZERO;
                    shippingDiscount = quote.shippingDiscount() != null ? quote.shippingDiscount() : java.math.BigDecimal.ZERO;
                    finalShippingFee = quote.finalShippingFee() != null ? quote.finalShippingFee() : baseShippingFee;
                } else {
                    voucherValid = false;
                    voucherMessage = quote != null && quote.message() != null ? quote.message() : "Mã giảm giá không hợp lệ";
                }
            }
        }

        java.math.BigDecimal finalTotal = subtotal.subtract(discountAmount)
                .max(java.math.BigDecimal.ZERO)
                .add(finalShippingFee);

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("subtotal", subtotal);
        body.put("originalShippingFee", baseShippingFee);
        body.put("shippingDiscount", shippingDiscount);
        body.put("shippingFee", finalShippingFee);
        body.put("discountAmount", discountAmount);
        body.put("finalTotal", finalTotal);
        body.put("voucherValid", voucherValid);
        body.put("voucherMessage", voucherMessage);
        return ResponseEntity.ok(body);
    }

    private java.math.BigDecimal resolveServerSubtotal(CartDto cartDto) {
        if (cartDto == null) {
            return java.math.BigDecimal.ZERO;
        }
        if (cartDto.getSubtotal() != null && cartDto.getSubtotal().compareTo(java.math.BigDecimal.ZERO) > 0) {
            return cartDto.getSubtotal();
        }
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        if (cartDto.getItems() != null) {
            for (CartItemDto item : cartDto.getItems()) {
                if (item != null && item.getLineTotal() != null) {
                    sum = sum.add(item.getLineTotal());
                }
            }
        }
        return sum;
    }

    private com.strongwine.strongwine.entity.Warehouse resolveNearestWarehouse(Double destLat, Double destLng) {
        if (warehouseRepository == null) {
            return null;
        }
        List<com.strongwine.strongwine.entity.Warehouse> active = warehouseRepository.findByActiveTrue();
        if (active == null || active.isEmpty()) {
            return warehouseRepository.findFirstByIsDefaultTrueAndActiveTrue().orElse(null);
        }
        if (destLat == null || destLng == null || shippingFeeService == null) {
            return active.stream()
                    .filter(w -> Boolean.TRUE.equals(w.getIsDefault()))
                    .findFirst()
                    .orElse(active.get(0));
        }
        com.strongwine.strongwine.entity.Warehouse best = null;
        double bestDist = Double.MAX_VALUE;
        for (com.strongwine.strongwine.entity.Warehouse w : active) {
            Double dist = shippingFeeService.calculateDistanceKm(w.getLatitude(), w.getLongitude(), destLat, destLng);
            if (dist != null && dist < bestDist) {
                bestDist = dist;
                best = w;
            }
        }
        return best != null ? best : active.get(0);
    }

    private java.math.BigDecimal calculateServerShippingFee(
            com.strongwine.strongwine.entity.Warehouse warehouse,
            Double destLat,
            Double destLng,
            java.math.BigDecimal subtotal) {
        if (shippingFeeService == null) {
            return java.math.BigDecimal.ZERO;
        }
        Double distanceKm = null;
        if (warehouse != null && warehouse.getLatitude() != null && warehouse.getLongitude() != null
                && destLat != null && destLng != null) {
            distanceKm = shippingFeeService.calculateDistanceKm(
                    warehouse.getLatitude(), warehouse.getLongitude(), destLat, destLng);
        }
        return shippingFeeService.calculateFee(distanceKm, subtotal);
    }

    private User resolveCurrentUser(Authentication authentication, Long userId) {
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            User byUsername = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
            if (byUsername != null) {
                return byUsername;
            }
        }
        if (userId != null) {
            return userRepository.findById(userId).orElse(null);
        }
        return null;
    }

    private String resolveUserEmail(Authentication authentication, Long userId) {
        User currentUser = resolveCurrentUser(authentication, userId);
        return currentUser != null ? currentUser.getEmail() : null;
    }
    
}

