package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WishlistItem> findByUserIdAndWineId(Long userId, Long wineId);

    boolean existsByUserIdAndWineId(Long userId, Long wineId);

    void deleteByUserIdAndWineId(Long userId, Long wineId);

    long countByUserId(Long userId);
}
