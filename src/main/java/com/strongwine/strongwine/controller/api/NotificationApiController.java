package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.entity.NotificationLog;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API endpoints for the persistent Notification Center (customer + admin feeds).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationApiController(NotificationService notificationService,
                                     UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public NotificationFeedResponse listNotifications(
            Authentication authentication,
            @RequestParam(value = "scope", required = false) String scope) {
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        if (isAdmin && !"user".equalsIgnoreCase(scope)) {
            List<NotificationItemDto> items = notificationService.getAdminNotifications().stream()
                    .map(NotificationApiController::toDto)
                    .toList();
            return new NotificationFeedResponse(items, notificationService.countUnreadForAdmin());
        }

        User user = requireAuthenticatedUser(authentication);
        List<NotificationItemDto> items = notificationService.getUserNotifications(user.getId()).stream()
                .map(NotificationApiController::toDto)
                .toList();
        return new NotificationFeedResponse(items, notificationService.countUnreadForUser(user.getId()));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(
            Authentication authentication,
            @RequestParam(value = "scope", required = false) String scope) {
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        if (isAdmin && !"user".equalsIgnoreCase(scope)) {
            return Map.of("unreadCount", notificationService.countUnreadForAdmin());
        }
        User user = requireAuthenticatedUser(authentication);
        return Map.of("unreadCount", notificationService.countUnreadForUser(user.getId()));
    }

    @PostMapping("/{id}/read")
    public NotificationItemDto markNotificationRead(@PathVariable Long id, Authentication authentication) {
        User user = requireAuthenticatedUser(authentication);
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        try {
            NotificationLog updated = notificationService.markAsRead(id, user.getId(), isAdmin);
            return toDto(updated);
        } catch (ResourceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(
            Authentication authentication,
            @RequestParam(value = "scope", required = false) String scope) {
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        if (isAdmin && !"user".equalsIgnoreCase(scope)) {
            requireAuthenticatedUser(authentication);
            return Map.of("markedCount", notificationService.markAllAsReadForAdmin());
        }
        User user = requireAuthenticatedUser(authentication);
        return Map.of("markedCount", notificationService.markAllAsReadForUser(user.getId()));
    }

    private User requireAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private static NotificationItemDto toDto(NotificationLog log) {
        return new NotificationItemDto(
                log.getId(),
                log.getTargetRole(),
                log.getTitle(),
                log.getMessage(),
                log.getLinkUrl(),
                log.getSeverity(),
                Boolean.TRUE.equals(log.getIsRead()),
                log.getCreatedAt()
        );
    }

    public record NotificationItemDto(
            Long id,
            String targetRole,
            String title,
            String message,
            String linkUrl,
            String severity,
            boolean isRead,
            LocalDateTime createdAt
    ) {
    }

    public record NotificationFeedResponse(
            List<NotificationItemDto> notifications,
            long unreadCount
    ) {
    }
}
