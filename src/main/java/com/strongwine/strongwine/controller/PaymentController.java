package com.strongwine.strongwine.controller;

import com.stripe.model.Event;
import com.strongwine.strongwine.dto.PaymentCallbackResult;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.PaymentService;
import com.strongwine.strongwine.service.StripeService;
import com.strongwine.strongwine.service.StripeService.StripeNotConfiguredException;
import com.strongwine.strongwine.service.StripeService.StripeWebhookVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    private StripeService stripeService;

    @GetMapping("/success")
    public String paymentSuccess(@RequestParam("session_id") String sessionId,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        Long userId = getUserId(authentication);
        if (userId == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để xác thực thanh toán");
            return "redirect:/login";
        }

        PaymentCallbackResult result = paymentService.validateStripeSuccessRedirect(sessionId, userId);
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("error", result.getMessage());
            return "redirect:/cart";
        }

        Order order = orderService.getOrderById(result.getOrderId()).orElse(null);
        if (order == null || order.getUser() == null || !userId.equals(order.getUser().getId())) {
            redirectAttributes.addFlashAttribute("error", "Không thể truy cập đơn hàng thanh toán");
            return "redirect:/cart";
        }

        redirectAttributes.addFlashAttribute("orderId", result.getOrderId());
        redirectAttributes.addFlashAttribute("fullName", order.getShippingFullName());
        redirectAttributes.addFlashAttribute("phone", order.getShippingPhone());
        redirectAttributes.addFlashAttribute("address", order.getShippingAddress());
        redirectAttributes.addFlashAttribute("success", result.getMessage());
        return "redirect:/order-confirmation";
    }

    @GetMapping("/cancel")
    public String paymentCancel(RedirectAttributes redirectAttributes) {
        PaymentCallbackResult result = paymentService.handleStripeCancel();
        redirectAttributes.addFlashAttribute("error", result.getMessage());
        return "redirect:/cart";
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody(required = false) String payload,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (payload == null || payload.isBlank() || signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest().body("Missing Stripe-Signature or empty payload");
        }
        if (stripeService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Stripe service unavailable");
        }
        try {
            Event event = stripeService.constructWebhookEvent(payload, signature);
            paymentService.handleStripeWebhookEvent(event);
            return ResponseEntity.ok("ok");
        } catch (StripeWebhookVerificationException ex) {
            log.warn("Rejected invalid Stripe webhook request: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (StripeNotConfiguredException ex) {
            log.warn("Rejected Stripe webhook because Stripe is not configured: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ex.getMessage());
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
