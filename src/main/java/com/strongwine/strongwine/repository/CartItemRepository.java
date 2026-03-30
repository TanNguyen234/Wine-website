package com.strongwine.strongwine.repository;

import com.strongwine.strongwine.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndWineId(Long cartId, Long wineId);
}
