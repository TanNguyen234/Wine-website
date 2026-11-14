package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.dto.UserDto;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.service.AdminAuditLogService;
import com.strongwine.strongwine.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST API Controller for User operations (Admin or authenticated resource owner)
 */
@RestController
@RequestMapping("/api/users")
public class UserApiController {
    
    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private AdminAuditLogService adminAuditLogService;
    
    /**
     * Get all users (Admin only)
     */
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers(Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(userService.getAllUsers().stream().map(UserDto::from).toList());
    }
    
    /**
     * Get user by ID (Admin or resource owner)
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id, Authentication authentication) {
        Optional<User> userOpt = userService.getUserById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User target = userOpt.get();
        if (!isAdminOrOwner(authentication, target)) {
            throw new AccessDeniedException("Không có quyền truy cập thông tin người dùng này");
        }
        return ResponseEntity.ok(UserDto.from(target));
    }
    
    /**
     * Create a new user (Admin only)
     */
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody User user,
                                              Authentication authentication,
                                              HttpServletRequest request) {
        requireAdmin(authentication);
        User createdUser = userService.createUser(user);
        UserDto dto = UserDto.from(createdUser);
        if (adminAuditLogService != null) {
            adminAuditLogService.log(resolveActor(authentication), "USER_CREATED_API", "User",
                    createdUser.getId(), null, dto, request != null ? request.getRemoteAddr() : null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
    
    /**
     * Update a user (Admin only)
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id,
                                              @RequestBody User user,
                                              Authentication authentication,
                                              HttpServletRequest request) {
        requireAdmin(authentication);
        User existing = userService.getUserById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng ID: " + id));
        UserDto before = UserDto.from(existing);
        User updatedUser = userService.updateUser(id, user);
        UserDto after = UserDto.from(updatedUser);
        if (adminAuditLogService != null) {
            adminAuditLogService.log(resolveActor(authentication), "USER_UPDATED_API", "User",
                    id, before, after, request != null ? request.getRemoteAddr() : null);
        }
        return ResponseEntity.ok(after);
    }
    
    /**
     * Delete a user (Admin only)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           Authentication authentication,
                                           HttpServletRequest request) {
        requireAdmin(authentication);
        User existing = userService.getUserById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng ID: " + id));
        UserDto before = UserDto.from(existing);
        userService.deleteUser(id);
        if (adminAuditLogService != null) {
            adminAuditLogService.log(resolveActor(authentication), "USER_DELETED_API", "User",
                    id, before, null, request != null ? request.getRemoteAddr() : null);
        }
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null) {
            return;
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            throw new AccessDeniedException("Chỉ Quản trị viên mới được phép thực hiện thao tác này");
        }
    }

    private boolean isAdminOrOwner(Authentication authentication, User target) {
        if (authentication == null) {
            return true;
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return isAdmin || (target != null && target.getUsername() != null
                && target.getUsername().equalsIgnoreCase(authentication.getName()));
    }

    private String resolveActor(Authentication authentication) {
        return authentication != null && authentication.getName() != null ? authentication.getName() : "ADMIN";
    }
}





