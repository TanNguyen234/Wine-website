package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findTop50ByTargetRoleOrderByCreatedAtDesc(String targetRole);

    List<NotificationLog> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByTargetRoleAndIsReadFalse(String targetRole);

    long countByUserIdAndIsReadFalse(Long userId);

    List<NotificationLog> findByTargetRoleAndIsReadFalse(String targetRole);

    List<NotificationLog> findByUserIdAndIsReadFalse(Long userId);
}
