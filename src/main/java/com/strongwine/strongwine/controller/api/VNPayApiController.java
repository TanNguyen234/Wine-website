package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.PaymentCallbackResult;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.PaymentService;
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

@Controller
@RequestMapping("/api/payments/vnpay")
public class VNPayApiController {

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
        PaymentCallbackResult result = paymentService.handleVnPayReturn(callbackParams, userId);

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
            redirectAttributes.addFlashAttribute("error", "Khong the truy cap don hang thanh toan");
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
        return ResponseEntity.ok(paymentService.handleVnPayIpn(callbackParams));
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
