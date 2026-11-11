package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.DeliveryAddressNotSupportedException;
import com.strongwine.strongwine.service.OrderService;
import com.strongwine.strongwine.service.UserService;
import com.strongwine.strongwine.service.UserShippingProfileService;
import com.strongwine.strongwine.service.UserShippingProfileService.ShippingProfileData;
import com.strongwine.strongwine.service.VietnamGeoValidationService;
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
    private final UserShippingProfileService userShippingProfileService;
    private final VietnamGeoValidationService geoValidationService;

    public ProfileController(UserService userService,
                             OrderService orderService,
                             UserShippingProfileService userShippingProfileService,
                             VietnamGeoValidationService geoValidationService) {
        this.userService = userService;
        this.orderService = orderService;
        this.userShippingProfileService = userShippingProfileService;
        this.geoValidationService = geoValidationService;
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

        // Extract latest shipping information from persistent shipping profile service
        ShippingProfileData shippingProfile = userShippingProfileService.getProfile(currentUser.getId());
        String latestFullName = shippingProfile.fullName() != null ? shippingProfile.fullName() : "";
        String latestPhone = shippingProfile.phone() != null ? shippingProfile.phone() : "";
        String latestAddress = shippingProfile.address() != null ? shippingProfile.address() : "";

        // Fallback to most recent order if profile fields are empty
        if ((latestAddress.isBlank() || latestFullName.isBlank()) && !orders.isEmpty()) {
            Order latest = orders.get(0);
            if (latestFullName.isBlank() && latest.getShippingFullName() != null) {
                latestFullName = latest.getShippingFullName();
            }
            if (latestPhone.isBlank() && latest.getShippingPhone() != null) {
                latestPhone = latest.getShippingPhone();
            }
            if (latestAddress.isBlank() && latest.getShippingAddress() != null) {
                latestAddress = latest.getShippingAddress();
            }
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
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
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

            if (email != null && !email.trim().isEmpty()) {
                userService.updateProfile(currentUser.getId(), email, currentPassword, newPassword);
            }

            if ((fullName != null && !fullName.trim().isEmpty()) || (phone != null && !phone.trim().isEmpty()) || (address != null && !address.trim().isEmpty())) {
                if (address != null && !address.trim().isEmpty()) {
                    geoValidationService.validateMainlandDeliveryAddress(address, null, null);
                }
                userShippingProfileService.saveProfile(currentUser.getId(), fullName, phone, address, null, null);
            }

            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật hồ sơ tài khoản thành công!");
        } catch (DeliveryAddressNotSupportedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/profile";
    }

    @PostMapping("/shipping")
    public String updateShippingAddress(
            Authentication authentication,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam String address,
            RedirectAttributes redirectAttributes) {

        User currentUser = requireCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            if (address == null || address.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập địa chỉ giao hàng chi tiết");
                return "redirect:/profile";
            }
            geoValidationService.validateMainlandDeliveryAddress(address, null, null);
            userShippingProfileService.saveProfile(currentUser.getId(), fullName, phone, address, null, null);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu địa chỉ giao hàng mặc định thành công!");
        } catch (DeliveryAddressNotSupportedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể lưu địa chỉ: " + ex.getMessage());
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
