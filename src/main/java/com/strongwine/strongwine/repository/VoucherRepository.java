package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE UPPER(v.code) = UPPER(:code)")
    Optional<Voucher> findByCodeIgnoreCaseWithLock(@Param("code") String code);

    List<Voucher> findAllByOrderByCreatedAtDesc();

    List<Voucher> findByActiveTrueOrderByCreatedAtDesc();

    boolean existsByCodeIgnoreCase(String code);
}
