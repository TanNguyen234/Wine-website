package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;
    private final OrderService orderService;

    public ProfileController(UserService userService, OrderService orderService) {
        this.userService = userService;
        this.orderService = orderService;
    }

    @GetMapping
    public String showProfile(Authentication authentication, Model model) {
        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        List<Order> orders = orderService.getOrdersByUserId(currentUser.getId());
        BigDecimal totalSpent = orders.stream()
                .filter(o -> o.getTotalPrice() != null)
                .map(Order::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Extract latest shipping information for prefill / display
        String latestFullName = "";
        String latestPhone = "";
        String latestAddress = "";
        if (!orders.isEmpty()) {
            Order latest = orders.get(0);
            latestFullName = latest.getShippingFullName() != null ? latest.getShippingFullName() : "";
            latestPhone = latest.getShippingPhone() != null ? latest.getShippingPhone() : "";
            latestAddress = latest.getShippingAddress() != null ? latest.getShippingAddress() : "";
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("orders", orders);
        model.addAttribute("totalOrders", orders.size());
        model.addAttribute("totalSpent", totalSpent);
        model.addAttribute("latestFullName", latestFullName);
        model.addAttribute("latestPhone", latestPhone);
        model.addAttribute("latestAddress", latestAddress);

        return "user-profile";
    }

    @PostMapping
    public String updateProfile(
            Authentication authentication,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String currentPassword,
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) String confirmPassword,
            RedirectAttributes redirectAttributes) {

        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                if (!newPassword.equals(confirmPassword)) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu xác nhận không khớp");
                    return "redirect:/profile";
                }
            }

            userService.updateProfile(currentUser.getId(), email, currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật hồ sơ tài khoản thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/profile";
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        String username;
        if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            username = userDetails.getUsername();
        } else {
            username = authentication.getName();
        }

        if (username == null || username.isBlank()) {
            return null;
        }

        return userService.getUserByUsername(username).orElse(null);
    }
}
