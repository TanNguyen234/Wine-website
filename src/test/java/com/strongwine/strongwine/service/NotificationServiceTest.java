package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.NotificationLog;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.NotificationLogRepository;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private SseNotificationService sseNotificationService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationLogRepository, sseNotificationService);
    }

    @Test
    void createAdminNotificationPersistsTruncatedLogAndBroadcastsToAdminSse() {
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog log = inv.getArgument(0);
            log.setId(10L);
            return log;
        });

        String longMessage = "x".repeat(1200);
        NotificationLog saved = notificationService.createAdminNotification(
                "Low Stock", longMessage, "/admin/inventory", "warning");

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getTargetRole()).isEqualTo("ADMIN");
        assertThat(saved.getUser()).isNull();
        assertThat(saved.getSeverity()).isEqualTo("WARNING");
        assertThat(saved.getMessage()).hasSize(1000);
        assertThat(saved.getIsRead()).isFalse();

        verify(sseNotificationService, times(1)).broadcastToAdmin(eq("notification_created"), any());
    }

    @Test
    void createUserNotificationPersistsUserScopedLogAndBroadcastsToUserSse() {
        User buyer = new User();
        buyer.setId(42L);
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> {
            NotificationLog log = inv.getArgument(0);
            log.setId(21L);
            return log;
        });

        NotificationLog saved = notificationService.createUserNotification(
                buyer, "Order Paid", "Your payment is confirmed.", "/orders/5", "INFO");

        assertThat(saved.getTargetRole()).isEqualTo("USER");
        assertThat(saved.getUser()).isSameAs(buyer);
        assertThat(saved.getIsRead()).isFalse();

        verify(sseNotificationService, times(1)).broadcastToUser(eq(42L), eq("notification_created"), any());
    }

    @Test
    void markAsReadEnforcesOwnershipIsolation() {
        User owner = new User();
        owner.setId(42L);

        NotificationLog userLog = new NotificationLog();
        userLog.setId(5L);
        userLog.setUser(owner);
        userLog.setTargetRole("USER");
        userLog.setIsRead(false);

        when(notificationLogRepository.findById(5L)).thenReturn(Optional.of(userLog));
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> notificationService.markAsRead(5L, 99L, false))
                .isInstanceOf(AccessDeniedException.class);
        verify(notificationLogRepository, never()).save(any());

        NotificationLog marked = notificationService.markAsRead(5L, 42L, false);
        assertThat(marked.getIsRead()).isTrue();
        verify(notificationLogRepository, times(1)).save(userLog);
    }

    @Test
    void markAsReadAdminNotificationRequiresAdminRoleAndMissingThrowsNotFound() {
        NotificationLog adminLog = new NotificationLog();
        adminLog.setId(8L);
        adminLog.setUser(null);
        adminLog.setTargetRole("ADMIN");
        adminLog.setIsRead(false);

        when(notificationLogRepository.findById(8L)).thenReturn(Optional.of(adminLog));
        when(notificationLogRepository.findById(999L)).thenReturn(Optional.empty());
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> notificationService.markAsRead(8L, 42L, false))
                .isInstanceOf(AccessDeniedException.class);

        NotificationLog marked = notificationService.markAsRead(8L, 1L, true);
        assertThat(marked.getIsRead()).isTrue();

        assertThatThrownBy(() -> notificationService.markAsRead(999L, 1L, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAllAsReadUpdatesAllUnreadEntriesForScope() {
        NotificationLog u1 = new NotificationLog();
        u1.setId(1L);
        u1.setIsRead(false);
        NotificationLog u2 = new NotificationLog();
        u2.setId(2L);
        u2.setIsRead(false);

        when(notificationLogRepository.findByUserIdAndIsReadFalse(42L)).thenReturn(List.of(u1, u2));
        when(notificationLogRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        int count = notificationService.markAllAsReadForUser(42L);
        assertThat(count).isEqualTo(2);
        assertThat(u1.getIsRead()).isTrue();
        assertThat(u2.getIsRead()).isTrue();

        NotificationLog a1 = new NotificationLog();
        a1.setId(3L);
        a1.setTargetRole("ADMIN");
        a1.setIsRead(false);
        when(notificationLogRepository.findByTargetRoleAndIsReadFalse("ADMIN")).thenReturn(List.of(a1));

        int adminCount = notificationService.markAllAsReadForAdmin();
        assertThat(adminCount).isEqualTo(1);
        assertThat(a1.getIsRead()).isTrue();
    }
}
