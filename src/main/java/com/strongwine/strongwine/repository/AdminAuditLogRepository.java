package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AdminAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId, Pageable pageable);

    Page<AdminAuditLog> findByActorUsernameContainingIgnoreCaseOrderByCreatedAtDesc(String actorUsername, Pageable pageable);

    List<AdminAuditLog> findTop100ByOrderByCreatedAtDesc();
}
