package com.strongwine.strongwine.service;

import com.strongwine.strongwine.controller.api.WishlistApiController;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.entity.WishlistItem;
import com.strongwine.strongwine.exception.ResourceNotFoundException;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.repository.WineRepository;
import com.strongwine.strongwine.repository.WishlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private WineRepository wineRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InventoryService inventoryService;

    private WishlistService wishlistService;

    @BeforeEach
    void setUp() {
        wishlistService = new WishlistService(wishlistItemRepository, wineRepository, userRepository);
    }

    private User makeUser(Long id, String username) {
        User u = new User(username, "pw", "USER");
        u.setId(id);
        return u;
    }

    private Wine makeWine(Long id, String name, boolean deleted) {
        Wine w = new Wine(name, "Red", 2018, new BigDecimal("2850000"), "Grand Cru");
        w.setId(id);
        w.setCountry("France");
        w.setImageUrl("/images/margaux.jpg");
        w.setDeleted(deleted);
        return w;
    }

    @Test
    @DisplayName("toggleWishlist rejects null inputs, missing wine/user, and soft-deleted wine")
    void toggleWishlistRejectsInvalidInputsAndDeletedWine() {
        assertThatThrownBy(() -> wishlistService.toggleWishlist(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wishlistService.toggleWishlist(1L, null))
                .isInstanceOf(IllegalArgumentException.class);

        when(wineRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> wishlistService.toggleWishlist(1L, 999L))
                .isInstanceOf(ResourceNotFoundException.class);

        Wine deletedWine = makeWine(5L, "Old Wine", true);
        when(wineRepository.findById(5L)).thenReturn(Optional.of(deletedWine));
        assertThatThrownBy(() -> wishlistService.toggleWishlist(1L, 5L))
                .isInstanceOf(IllegalStateException.class);

        Wine activeWine = makeWine(6L, "Margaux", false);
        when(wineRepository.findById(6L)).thenReturn(Optional.of(activeWine));
        when(userRepository.findById(88L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> wishlistService.toggleWishlist(88L, 6L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("toggleWishlist adds item when absent and deletes item when present")
    void toggleWishlistAddsAndRemoves() {
        User user = makeUser(10L, "demo");
        Wine wine = makeWine(20L, "Château Margaux", false);

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(wineRepository.findById(20L)).thenReturn(Optional.of(wine));
        when(wishlistItemRepository.findByUserIdAndWineId(10L, 20L)).thenReturn(Optional.empty());
        when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(inv -> {
            WishlistItem item = inv.getArgument(0);
            item.setId(100L);
            return item;
        });

        boolean added = wishlistService.toggleWishlist(10L, 20L);
        assertThat(added).isTrue();
        ArgumentCaptor<WishlistItem> captor = ArgumentCaptor.forClass(WishlistItem.class);
        verify(wishlistItemRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getWine()).isSameAs(wine);

        WishlistItem existing = new WishlistItem();
        existing.setId(100L);
        existing.setUser(user);
        existing.setWine(wine);
        when(wishlistItemRepository.findByUserIdAndWineId(10L, 20L)).thenReturn(Optional.of(existing));

        boolean removed = wishlistService.toggleWishlist(10L, 20L);
        assertThat(removed).isFalse();
        verify(wishlistItemRepository).delete(existing);
    }

    @Test
    @DisplayName("getWishlistWines, getWishlistWineIds, and countWishlist filter deleted wines and handle null")
    void wishlistQueriesReturnActiveWinesAndCounts() {
        User user = makeUser(10L, "demo");
        Wine w1 = makeWine(1L, "Margaux", false);
        Wine deleted = makeWine(2L, "Discontinued", true);
        Wine w3 = makeWine(3L, "Opus One", false);

        WishlistItem item1 = new WishlistItem();
        item1.setUser(user);
        item1.setWine(w1);

        WishlistItem itemDeleted = new WishlistItem();
        itemDeleted.setUser(user);
        itemDeleted.setWine(deleted);

        WishlistItem item3 = new WishlistItem();
        item3.setUser(user);
        item3.setWine(w3);

        when(wishlistItemRepository.findByUserIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(item1, itemDeleted, item3));
        when(wishlistItemRepository.countByUserId(10L)).thenReturn(2L);

        assertThat(wishlistService.getWishlistWines(10L)).containsExactly(w1, w3);
        assertThat(wishlistService.getWishlistWineIds(10L)).containsExactly(1L, 3L);
        assertThat(wishlistService.countWishlist(10L)).isEqualTo(2L);

        assertThat(wishlistService.getWishlistWines(null)).isEmpty();
        assertThat(wishlistService.getWishlistWineIds(null)).isEmpty();
        assertThat(wishlistService.countWishlist(null)).isZero();
    }

    @Test
    @DisplayName("WishlistApiController enforces auth, lists DTOs & IDs, and toggles wishlist state")
    void wishlistApiControllerEndpointsWork() throws Exception {
        User user = makeUser(10L, "demo");
        Wine wine = makeWine(20L, "Château Margaux", false);

        lenient().when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(wineRepository.findById(20L)).thenReturn(Optional.of(wine));
        when(inventoryService.getAvailableStockByWineIds(List.of(20L))).thenReturn(Map.of(20L, 12));

        WishlistItem savedItem = new WishlistItem();
        savedItem.setUser(user);
        savedItem.setWine(wine);
        when(wishlistItemRepository.findByUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(savedItem));
        when(wishlistItemRepository.findByUserIdAndWineId(10L, 20L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedItem));
        when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(wishlistItemRepository.countByUserId(10L))
                .thenReturn(1L)
                .thenReturn(0L);

        WishlistApiController controller = new WishlistApiController(
                wishlistService, userRepository, inventoryService, new EnumDisplayService());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        // Unauthenticated -> 401
        mockMvc.perform(get("/api/wishlist"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/wishlist/20/toggle"))
                .andExpect(status().isUnauthorized());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "demo", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(get("/api/wishlist").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.wineIds[0]").value(20))
                .andExpect(jsonPath("$.items[0].id").value(20))
                .andExpect(jsonPath("$.items[0].name").value("Château Margaux"))
                .andExpect(jsonPath("$.items[0].availableStock").value(12))
                .andExpect(jsonPath("$.items[0].inStock").value(true));

        // Toggle ON -> added
        mockMvc.perform(post("/api/wishlist/20/toggle").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inWishlist").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.wineId").value(20));

        // Toggle OFF -> removed
        mockMvc.perform(post("/api/wishlist/20/toggle").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inWishlist").value(false))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.wineId").value(20));
    }
}
