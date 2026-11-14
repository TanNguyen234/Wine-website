package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.VoucherUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    boolean existsByVoucherIdAndUserId(Long voucherId, Long userId);

    boolean existsByOrderId(Long orderId);

    long countByVoucherIdAndUserId(Long voucherId, Long userId);

    List<VoucherUsage> findByOrderId(Long orderId);

    List<VoucherUsage> findByVoucherIdOrderByUsedAtDesc(Long voucherId);
}
