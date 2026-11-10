package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.ShipperService;
import com.strongwine.strongwine.service.UserService;
import com.strongwine.strongwine.service.WineService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/admin/profile")
public class AdminProfileController {

    private final UserService userService;
    private final WineService wineService;
    private final OrderService orderService;
    private final ShipperService shipperService;

    public AdminProfileController(UserService userService,
                                  WineService wineService,
                                  OrderService orderService,
                                  ShipperService shipperService) {
        this.userService = userService;
        this.wineService = wineService;
        this.orderService = orderService;
        this.shipperService = shipperService;
    }

    @GetMapping
    public String showAdminProfile(Authentication authentication, Model model) {
        User currentAdmin = requireCurrentAdmin(authentication);
        if (currentAdmin == null) {
            return "redirect:/login";
        }

        long totalWines = wineService.countWines();
        long totalOrders = orderService.countOrders();
        long totalUsers = userService.countUsers();
        Map<String, Long> shipperStats = shipperService.getShipperOverviewStats();
        long activeShippers = shipperStats.getOrDefault("active", 0L);

        model.addAttribute("admin", currentAdmin);
        model.addAttribute("totalWines", totalWines);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("activeShippers", activeShippers);

        return "admin-profile";
    }

    @PostMapping
    public String updateAdminProfile(
            Authentication authentication,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String currentPassword,
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) String confirmPassword,
            RedirectAttributes redirectAttributes) {

        User currentAdmin = requireCurrentAdmin(authentication);
        if (currentAdmin == null) {
            return "redirect:/login";
        }

        try {
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                if (!newPassword.equals(confirmPassword)) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu xác nhận không khớp");
                    return "redirect:/admin/profile";
                }
            }

            userService.updateProfile(currentAdmin.getId(), email, currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật hồ sơ quản trị thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/profile";
    }

    private User requireCurrentAdmin(Authentication authentication) {
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
