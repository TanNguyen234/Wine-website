package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.ReturnRequest;
import com.strongwine.strongwine.entity.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    List<ReturnRequest> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<ReturnRequest> findFirstByOrderIdAndStatusIn(Long orderId, List<ReturnStatus> statuses);

    boolean existsByOrderIdAndStatusIn(Long orderId, List<ReturnStatus> statuses);

    List<ReturnRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ReturnRequest> findByStatusOrderByCreatedAtDesc(ReturnStatus status);

    List<ReturnRequest> findAllByOrderByCreatedAtDesc();

    long countByStatus(ReturnStatus status);
}
