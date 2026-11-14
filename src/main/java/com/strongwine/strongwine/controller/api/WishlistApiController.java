package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.EnumDisplayService;
import com.strongwine.strongwine.service.InventoryService;
import com.strongwine.strongwine.service.WishlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for inspecting and toggling the authenticated customer's wine wishlist.
 */
@RestController
@RequestMapping("/api/wishlist")
public class WishlistApiController {

    private final WishlistService wishlistService;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final EnumDisplayService enumDisplayService;

    public WishlistApiController(WishlistService wishlistService,
                                 UserRepository userRepository) {
        this(wishlistService, userRepository, null, null);
    }

    @Autowired
    public WishlistApiController(WishlistService wishlistService,
                                 UserRepository userRepository,
                                 @Autowired(required = false) InventoryService inventoryService,
                                 @Autowired(required = false) EnumDisplayService enumDisplayService) {
        this.wishlistService = wishlistService;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.enumDisplayService = enumDisplayService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getWishlist(Authentication authentication) {
        User currentUser = resolveAuthenticatedUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        List<Wine> wines = wishlistService.getWishlistWines(currentUser.getId());
        List<Long> wineIds = wines.stream()
                .map(Wine::getId)
                .toList();

        Map<Long, Integer> stockMap = (inventoryService != null && !wineIds.isEmpty())
                ? inventoryService.getAvailableStockByWineIds(wineIds)
                : Map.of();

        List<Map<String, Object>> items = wines.stream()
                .map(wine -> toItemDto(wine, stockMap.getOrDefault(wine.getId(), 0)))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wineIds", wineIds);
        body.put("items", items);
        body.put("count", items.size());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{wineId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleWishlist(@PathVariable Long wineId,
                                                              Authentication authentication) {
        User currentUser = resolveAuthenticatedUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        try {
            boolean inWishlist = wishlistService.toggleWishlist(currentUser.getId(), wineId);
            long count = wishlistService.countWishlist(currentUser.getId());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("wineId", wineId);
            body.put("inWishlist", inWishlist);
            body.put("count", count);
            return ResponseEntity.ok(body);
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    private Map<String, Object> toItemDto(Wine wine, int availableStock) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", wine.getId());
        dto.put("name", wine.getName());
        dto.put("type", wine.getType());
        dto.put("typeLabel", enumDisplayService != null ? enumDisplayService.wineType(wine.getType()) : wine.getType());
        dto.put("typeTone", enumDisplayService != null ? enumDisplayService.wineTypeTone(wine.getType()) : "default");
        dto.put("year", wine.getYear());
        dto.put("price", wine.getPrice());
        dto.put("country", wine.getCountry());
        dto.put("imageUrl", wine.getImageUrl());
        dto.put("description", wine.getDescription());
        dto.put("availableStock", availableStock);
        dto.put("inStock", availableStock > 0);
        if (enumDisplayService != null) {
            dto.put("stockStatusLabel", enumDisplayService.stockStatusLabel(availableStock));
            dto.put("stockStatusTone", enumDisplayService.stockStatusTone(availableStock));
            dto.put("stockStatusBadgeClass", enumDisplayService.stockStatusBadgeClass(availableStock));
        }
        return dto;
    }

    private User resolveAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            username = userDetails.getUsername();
        } else {
            username = authentication.getName();
        }
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            return null;
        }
        return userRepository.findByUsername(username).orElse(null);
    }
}
