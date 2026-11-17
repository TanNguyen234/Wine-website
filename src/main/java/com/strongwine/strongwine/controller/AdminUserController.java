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
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final List<String> ALLOWED_ROLES = List.of("ADMIN", "USER", "SHIPPER");
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "username", "email", "role", "fullName", "createdAt");

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

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String safeSortBy = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "id";
        String safeSortDir = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
        org.springframework.data.domain.Sort.Direction direction = "asc".equalsIgnoreCase(safeSortDir)
                ? org.springframework.data.domain.Sort.Direction.ASC
                : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest.of(
                safePage, safeSize, org.springframework.data.domain.Sort.by(direction, safeSortBy));
        org.springframework.data.domain.Page<User> userPage = userService.getAllUsersPage(pageable);

        model.addAttribute("users", userPage.getContent());
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("sortBy", safeSortBy);
        model.addAttribute("sortDir", safeSortDir);
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.strongwine.strongwine.service.AdminAuditLogService adminAuditLogService;

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
            User created = userService.createUser(user);
            recordAudit("USER_CREATED", created != null ? created.getId() : null, null,
                    created != null ? com.strongwine.strongwine.dto.UserDto.from(created) : username);

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

    public String updateUser(Long id,
                             String username,
                             String email,
                             String password,
                             String role,
                             RedirectAttributes redirectAttributes) {
        return updateUser(id, username, email, password, role, null, redirectAttributes);
    }

    @PostMapping("/edit/{id}")
    public String updateUser(@PathVariable Long id,
                             @RequestParam String username,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String password,
                             @RequestParam String role,
                             org.springframework.security.core.Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            String normalizedRole = normalizeRole(role);
            Optional<User> targetOpt = userService.getUserById(id);
            String currentActor = resolveAuthenticatedUsername(authentication);
            if (targetOpt.isPresent()
                    && currentActor != null
                    && currentActor.equalsIgnoreCase(targetOpt.get().getUsername())
                    && !"ADMIN".equalsIgnoreCase(normalizedRole)) {
                redirectAttributes.addFlashAttribute("error", "Không thể tự hạ quyền tài khoản quản trị viên đang đăng nhập.");
                return "redirect:/admin/users/edit/" + id;
            }

            com.strongwine.strongwine.dto.UserDto before = targetOpt
                    .map(com.strongwine.strongwine.dto.UserDto::from).orElse(null);
            User userDetails = new User();
            userDetails.setUsername(username);
            userDetails.setEmail(email);
            userDetails.setPassword(password);
            userDetails.setRole(normalizedRole);

            User updated = userService.updateUser(id, userDetails);
            recordAudit("USER_UPDATED", id, before,
                    updated != null ? com.strongwine.strongwine.dto.UserDto.from(updated) : username);
            redirectAttributes.addFlashAttribute("success", "Cập nhật người dùng thành công");
            return "redirect:/admin/users";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Cập nhật người dùng thất bại: " + ex.getMessage());
            return "redirect:/admin/users/edit/" + id;
        }
    }

    public String deleteUser(Long id, RedirectAttributes redirectAttributes) {
        return deleteUser(id, null, redirectAttributes);
    }

    @PostMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id,
                             org.springframework.security.core.Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            Optional<User> targetOpt = userService.getUserById(id);
            String currentActor = resolveAuthenticatedUsername(authentication);
            if (targetOpt.isPresent()
                    && currentActor != null
                    && currentActor.equalsIgnoreCase(targetOpt.get().getUsername())) {
                redirectAttributes.addFlashAttribute("error", "Không thể tự xóa tài khoản quản trị viên đang đăng nhập.");
                return "redirect:/admin/users";
            }

            com.strongwine.strongwine.dto.UserDto before = targetOpt
                    .map(com.strongwine.strongwine.dto.UserDto::from).orElse(null);
            userService.deleteUser(id);
            recordAudit("USER_DELETED", id, before, null);
            redirectAttributes.addFlashAttribute("success", "Xóa người dùng thành công");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", "Xóa người dùng thất bại: " + ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    private String resolveAuthenticatedUsername(org.springframework.security.core.Authentication authentication) {
        org.springframework.security.core.Authentication auth = authentication != null
                ? authentication
                : org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String name = auth.getName();
        return (name != null && !name.isBlank()) ? name.trim() : null;
    }

    private void recordAudit(String action, Long targetId, Object before, Object after) {
        if (adminAuditLogService == null) {
            return;
        }
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String actor = auth != null && auth.getName() != null ? auth.getName() : "ADMIN";
        adminAuditLogService.log(actor, action, "User", targetId, before, after, null);
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
