package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.service.PasswordResetService;
import com.strongwine.strongwine.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for authentication pages (login, register)
 */
@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordResetService passwordResetService;

    /**
     * Login page
     */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", "Sai tên đăng nhập hoặc mật khẩu");
        }
        return "login";
    }

    /**
     * Register page
     */
    @GetMapping("/register")
    public String register() {
        return "register";
    }

    /**
     * Handle registration
     */
    @PostMapping("/register")
    public String handleRegister(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes) {

        if (!userService.isValidEmailFormat(email)) {
            redirectAttributes.addFlashAttribute("error", "Email không hợp lệ");
            return "redirect:/register";
        }

        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu xác nhận không khớp");
            return "redirect:/register";
        }

        if (userService.usernameExists(username)) {
            redirectAttributes.addFlashAttribute("error", "Tên đăng nhập đã tồn tại");
            return "redirect:/register";
        }

        if (userService.emailExists(email)) {
            redirectAttributes.addFlashAttribute("error", "Email đã được sử dụng");
            return "redirect:/register";
        }

        User user = new User(username, password, "USER");
        user.setEmail(email);
        try {
            userService.createUser(user);
            redirectAttributes.addFlashAttribute("success", "Đăng ký thành công. Vui lòng đăng nhập.");
            return "redirect:/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Đăng ký thất bại: " + e.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam String email,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        passwordResetService.requestResetForUser(email, resolveBaseUrl(request));
        redirectAttributes.addFlashAttribute("success",
                "Nếu email tồn tại trong hệ thống người dùng, liên kết đặt lại mật khẩu đã được gửi.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) String token,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (!passwordResetService.isResetTokenUsableForUser(token)) {
            redirectAttributes.addFlashAttribute("error", "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
            return "redirect:/forgot-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String handleResetPassword(@RequestParam String token,
                                      @RequestParam String password,
                                      @RequestParam String confirmPassword,
                                      RedirectAttributes redirectAttributes) {
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu xác nhận không khớp");
            return "redirect:/reset-password?token=" + token;
        }

        try {
            passwordResetService.resetPasswordForUser(token, password);
            redirectAttributes.addFlashAttribute("success", "Đặt lại mật khẩu thành công. Vui lòng đăng nhập.");
            return "redirect:/login";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/reset-password?token=" + token;
        }
    }

    private String resolveBaseUrl(HttpServletRequest request) {
        if (request == null) {
            return "http://localhost:8080";
        }
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        boolean isDefaultPort = ("http".equalsIgnoreCase(scheme) && serverPort == 80)
                || ("https".equalsIgnoreCase(scheme) && serverPort == 443);
        return scheme + "://" + serverName + (isDefaultPort ? "" : ":" + serverPort);
    }
}





