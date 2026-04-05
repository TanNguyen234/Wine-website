package com.strongwine.strongwine.controller;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final List<String> ALLOWED_ROLES = List.of("ADMIN", "USER", "SHIPPER");

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Model model) {
            
        org.springframework.data.domain.Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(direction, sortBy));
        org.springframework.data.domain.Page<User> userPage = userService.getAllUsersPage(pageable);
        
        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir.toLowerCase());
        model.addAttribute("totalPages", userPage.getTotalPages());
        model.addAttribute("hasNext", userPage.hasNext());
        model.addAttribute("hasPrevious", userPage.hasPrevious());
        model.addAttribute("totalEntries", userPage.getTotalElements());
        
        return "admin-users";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("roles", ALLOWED_ROLES);
        return "admin-user-form";
    }

    @PostMapping("/create")
    public String createUser(@RequestParam String username,
                             @RequestParam(required = false) String email,
                             @RequestParam String password,
                             @RequestParam String role,
                             RedirectAttributes redirectAttributes) {
        try {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(password);
            user.setRole(normalizeRole(role));
            userService.createUser(user);

            redirectAttributes.addFlashAttribute("success", "Tạo người dùng thành công");
            return "redirect:/admin/users";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Tạo người dùng thất bại: " + ex.getMessage());
            return "redirect:/admin/users/create";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        return userService.getUserById(id).map(user -> {
            model.addAttribute("editingUser", user);
            model.addAttribute("roles", ALLOWED_ROLES);
            return "admin-user-form";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("error", "Không tìm thấy người dùng");
            return "redirect:/admin/users";
        });
    }

    @PostMapping("/edit/{id}")
    public String updateUser(@PathVariable Long id,
                             @RequestParam String username,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String password,
                             @RequestParam String role,
                             RedirectAttributes redirectAttributes) {
        try {
            User userDetails = new User();
            userDetails.setUsername(username);
            userDetails.setEmail(email);
            userDetails.setPassword(password);
            userDetails.setRole(normalizeRole(role));

            userService.updateUser(id, userDetails);
            redirectAttributes.addFlashAttribute("success", "Cập nhật người dùng thành công");
            return "redirect:/admin/users";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Cập nhật người dùng thất bại: " + ex.getMessage());
            return "redirect:/admin/users/edit/" + id;
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("success", "Xóa người dùng thành công");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Xóa người dùng thất bại: " + ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            throw new RuntimeException("Vai trò không được để trống");
        }
        String normalizedRole = role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new RuntimeException("Vai trò không hợp lệ: " + normalizedRole);
        }
        return normalizedRole;
    }
}
