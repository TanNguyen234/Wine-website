package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserIdOrderByIsDefaultDescUpdatedAtDesc(Long userId);

    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    Optional<UserAddress> findFirstByUserIdAndIsDefaultTrueOrderByUpdatedAtDesc(Long userId);

    long countByUserId(Long userId);
}
