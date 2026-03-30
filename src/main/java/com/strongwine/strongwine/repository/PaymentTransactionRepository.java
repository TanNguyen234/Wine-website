package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findTop200ByOrderByCreatedAtDesc();
    boolean existsByTransactionTypeAndPayload(String transactionType, String payload);
}
