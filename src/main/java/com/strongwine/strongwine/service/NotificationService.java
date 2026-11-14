package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.NotificationLog;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.NotificationLogRepository;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Persists user-scoped and admin-scoped notifications and pushes real-time SSE alerts.
 */
@Service
@Transactional
public class NotificationService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_LINK_LENGTH = 500;
    private static final int MAX_SEVERITY_LENGTH = 24;

    private final NotificationLogRepository notificationLogRepository;
    private final SseNotificationService sseNotificationService;

    @Autowired
    public NotificationService(NotificationLogRepository notificationLogRepository,
                               SseNotificationService sseNotificationService) {
        this.notificationLogRepository = notificationLogRepository;
        this.sseNotificationService = sseNotificationService;
    }

    public NotificationLog createAdminNotification(String title, String message, String linkUrl, String severity) {
        NotificationLog log = new NotificationLog();
        log.setUser(null);
        log.setTargetRole("ADMIN");
        log.setTitle(truncate(defaultIfBlank(title, "Admin Notification"), MAX_TITLE_LENGTH));
        log.setMessage(truncate(defaultIfBlank(message, ""), MAX_MESSAGE_LENGTH));
        log.setLinkUrl(truncateNullable(linkUrl, MAX_LINK_LENGTH));
        log.setSeverity(normalizeSeverity(severity));
        log.setIsRead(false);

        NotificationLog saved = notificationLogRepository.save(log);
        if (sseNotificationService != null) {
            sseNotificationService.broadcastToAdmin("notification_created", toSsePayload(saved));
        }
        return saved;
    }

    public NotificationLog createUserNotification(User user, String title, String message, String linkUrl, String severity) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Recipient user with valid id is required");
        }
        NotificationLog log = new NotificationLog();
        log.setUser(user);
        log.setTargetRole("USER");
        log.setTitle(truncate(defaultIfBlank(title, "Notification"), MAX_TITLE_LENGTH));
        log.setMessage(truncate(defaultIfBlank(message, ""), MAX_MESSAGE_LENGTH));
        log.setLinkUrl(truncateNullable(linkUrl, MAX_LINK_LENGTH));
        log.setSeverity(normalizeSeverity(severity));
        log.setIsRead(false);

        NotificationLog saved = notificationLogRepository.save(log);
        if (sseNotificationService != null) {
            sseNotificationService.broadcastToUser(user.getId(), "notification_created", toSsePayload(saved));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> getAdminNotifications() {
        return notificationLogRepository.findTop50ByTargetRoleOrderByCreatedAtDesc("ADMIN");
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> getUserNotifications(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return notificationLogRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long countUnreadForAdmin() {
        return notificationLogRepository.countByTargetRoleAndIsReadFalse("ADMIN");
    }

    @Transactional(readOnly = true)
    public long countUnreadForUser(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return notificationLogRepository.countByUserIdAndIsReadFalse(userId);
    }

    public NotificationLog markAsRead(Long notificationId, Long currentUserId, boolean isAdmin) {
        NotificationLog entry = notificationLogRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (entry.getUser() != null) {
            Long ownerId = entry.getUser().getId();
            if (currentUserId == null || !Objects.equals(ownerId, currentUserId)) {
                throw new AccessDeniedException("You do not have permission to mark this notification as read");
            }
        } else if (!isAdmin) {
            throw new AccessDeniedException("Admin privileges are required to mark this notification as read");
        }

        if (!Boolean.TRUE.equals(entry.getIsRead())) {
            entry.setIsRead(true);
            return notificationLogRepository.save(entry);
        }
        return entry;
    }

    public int markAllAsReadForAdmin() {
        List<NotificationLog> unread = notificationLogRepository.findByTargetRoleAndIsReadFalse("ADMIN");
        if (unread.isEmpty()) {
            return 0;
        }
        unread.forEach(item -> item.setIsRead(true));
        notificationLogRepository.saveAll(unread);
        return unread.size();
    }

    public int markAllAsReadForUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        List<NotificationLog> unread = notificationLogRepository.findByUserIdAndIsReadFalse(userId);
        if (unread.isEmpty()) {
            return 0;
        }
        unread.forEach(item -> item.setIsRead(true));
        notificationLogRepository.saveAll(unread);
        return unread.size();
    }

    private Map<String, Object> toSsePayload(NotificationLog saved) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", saved.getId());
        payload.put("targetRole", saved.getTargetRole());
        payload.put("title", saved.getTitle());
        payload.put("message", saved.getMessage());
        payload.put("linkUrl", saved.getLinkUrl() != null ? saved.getLinkUrl() : "");
        payload.put("severity", saved.getSeverity());
        payload.put("isRead", Boolean.TRUE.equals(saved.getIsRead()));
        payload.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : "");
        return payload;
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "INFO";
        }
        return truncate(severity.trim().toUpperCase(Locale.ROOT), MAX_SEVERITY_LENGTH);
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String truncateNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
