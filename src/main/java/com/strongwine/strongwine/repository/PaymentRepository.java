package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentReference(String paymentReference);
    boolean existsByPaymentReference(String paymentReference);
    Optional<Payment> findByGatewaySessionId(String gatewaySessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);
    Optional<Payment> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);
    List<Payment> findTop100ByOrderByCreatedAtDesc();
    List<Payment> findByStatus(PaymentStatus status);
    long countByStatus(PaymentStatus status);

    @Query("select p.id from Payment p where p.method = :method and p.status = :status and p.createdAt < :createdBefore order by p.createdAt asc")
    List<Long> findExpirableIds(@Param("method") PaymentMethod method,
                                @Param("status") PaymentStatus status,
                                @Param("createdBefore") LocalDateTime createdBefore,
                                Pageable pageable);
}
