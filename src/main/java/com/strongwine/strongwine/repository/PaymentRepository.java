package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Payment;
import com.strongwine.strongwine.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentReference(String paymentReference);
    Optional<Payment> findByGatewaySessionId(String gatewaySessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);
    Optional<Payment> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);
    List<Payment> findTop100ByOrderByCreatedAtDesc();
    List<Payment> findByStatus(PaymentStatus status);
    long countByStatus(PaymentStatus status);
}
