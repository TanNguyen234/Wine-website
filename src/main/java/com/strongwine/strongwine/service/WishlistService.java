package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.entity.WishlistItem;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WineRepository;
import com.strongwine.strongwine.repository.WishlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application service managing customer wine wishlists.
 */
@Service
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final WineRepository wineRepository;
    private final UserRepository userRepository;

    public WishlistService(WishlistItemRepository wishlistItemRepository,
                           WineRepository wineRepository,
                           UserRepository userRepository) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.wineRepository = wineRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public boolean toggleWishlist(Long userId, Long wineId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId không được để trống.");
        }
        if (wineId == null) {
            throw new IllegalArgumentException("wineId không được để trống.");
        }

        Wine wine = wineRepository.findById(wineId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm rượu #" + wineId));
        if (wine.isDeleted()) {
            throw new IllegalStateException("Sản phẩm rượu này đã ngừng kinh doanh.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản người dùng #" + userId));

        Optional<WishlistItem> existing = wishlistItemRepository.findByUserIdAndWineId(userId, wineId);
        if (existing.isPresent()) {
            wishlistItemRepository.delete(existing.get());
            return false;
        }

        WishlistItem item = new WishlistItem();
        item.setUser(user);
        item.setWine(wine);
        wishlistItemRepository.save(item);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Wine> getWishlistWines(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return wishlistItemRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(WishlistItem::getWine)
                .filter(wine -> wine != null && !wine.isDeleted())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> getWishlistWineIds(Long userId) {
        return getWishlistWines(userId).stream()
                .map(Wine::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countWishlist(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return wishlistItemRepository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public boolean isInWishlist(Long userId, Long wineId) {
        if (userId == null || wineId == null) {
            return false;
        }
        return wishlistItemRepository.existsByUserIdAndWineId(userId, wineId);
    }
}
