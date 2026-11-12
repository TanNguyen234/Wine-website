package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.PaymentCallbackResult;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Controller handling VNPay return and IPN webhook endpoints
 */
@Controller
@RequestMapping("/api/payments/vnpay")
public class VNPayApiController {

    private static final Logger log = LoggerFactory.getLogger(VNPayApiController.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/return")
    public String handleReturn(@RequestParam Map<String, String> callbackParams,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        Long userId = getUserId(authentication);
        PaymentCallbackResult result = processReturn(callbackParams, userId);

        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("error", result.getMessage());
            return "redirect:/cart";
        }

        if (userId == null) {
            redirectAttributes.addFlashAttribute("success", result.getMessage());
            return "redirect:/login";
        }

        Order order = orderService.getOrderById(result.getOrderId()).orElse(null);
        if (order == null || order.getUser() == null || !userId.equals(order.getUser().getId())) {
            redirectAttributes.addFlashAttribute("error", "Không thể truy cập đơn hàng thanh toán");
            return "redirect:/orders";
        }

        redirectAttributes.addFlashAttribute("orderId", result.getOrderId());
        redirectAttributes.addFlashAttribute("fullName", order.getShippingFullName());
        redirectAttributes.addFlashAttribute("phone", order.getShippingPhone());
        redirectAttributes.addFlashAttribute("address", order.getShippingAddress());
        redirectAttributes.addFlashAttribute("success", result.getMessage());
        return "redirect:/order-confirmation";
    }

    @GetMapping("/ipn")
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleIpn(@RequestParam Map<String, String> callbackParams) {
        try {
            return ResponseEntity.ok(paymentService.handleVnPayIpn(callbackParams));
        } catch (RuntimeException ex) {
            // The service transaction was rolled back, so VNPay retries this notification until it gets 00 or 02.
            log.error("VNPay IPN processing failed", ex);
            return ResponseEntity.ok(Map.of("RspCode", "99", "Message", "Unknown error"));
        }
    }

    private PaymentCallbackResult processReturn(Map<String, String> callbackParams, Long userId) {
        try {
            return paymentService.handleVnPayReturn(callbackParams, userId);
        } catch (RuntimeException ex) {
            log.error("VNPay return processing failed", ex);
            String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return new PaymentCallbackResult(false, "Xác thực VNPay thất bại: " + detail, null);
        }
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        if (!(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            return null;
        }
        User user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        return user != null ? user.getId() : null;
    }
}
