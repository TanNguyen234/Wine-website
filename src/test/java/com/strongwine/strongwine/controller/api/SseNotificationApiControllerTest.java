package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.Shipper;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseNotificationApiControllerTest {

    @Mock
    private SseNotificationService sseService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShipperRepository shipperRepository;

    private SseNotificationApiController controller;

    @BeforeEach
    void setUp() {
        controller = new SseNotificationApiController(
                sseService, orderRepository, userRepository, shipperRepository);
    }

    @Test
    void adminStreamRejectsCustomerAndDoesNotSubscribe() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.streamAdminNotifications(authentication("customer", "ROLE_USER")));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(sseService);
    }

    @Test
    void adminCanSubscribeToAdminStream() {
        SseEmitter emitter = new SseEmitter();
        when(sseService.subscribeAdmin()).thenReturn(emitter);

        assertSame(emitter, controller.streamAdminNotifications(authentication("admin", "ROLE_ADMIN")));

        verify(sseService).subscribeAdmin();
    }

    @Test
    void orderStreamAllowsOwnerAndRejectsOtherCustomers() {
        User owner = user(12L);
        when(userRepository.findByUsername("customer")).thenReturn(Optional.of(owner));
        when(orderRepository.findByIdAndUserId(42L, 12L)).thenReturn(Optional.of(new Order()));
        SseEmitter emitter = new SseEmitter();
        when(sseService.subscribeOrder(42L)).thenReturn(emitter);

        assertSame(emitter, controller.streamOrderEvents(42L, authentication("customer", "ROLE_USER")));

        when(userRepository.findByUsername("other-customer")).thenReturn(Optional.of(user(13L)));
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.streamOrderEvents(42L, authentication("other-customer", "ROLE_USER")));

        assertEquals(404, exception.getStatusCode().value());
        verify(orderRepository).findByIdAndUserId(42L, 12L);
        verify(orderRepository).findByIdAndUserId(42L, 13L);
        verify(sseService, times(1)).subscribeOrder(42L);
    }

    @Test
    void adminCanSubscribeToAnyOrderStream() {
        SseEmitter emitter = new SseEmitter();
        when(sseService.subscribeOrder(42L)).thenReturn(emitter);

        assertSame(emitter, controller.streamOrderEvents(42L, authentication("admin", "ROLE_ADMIN")));

        verifyNoInteractions(orderRepository, userRepository);
        verify(sseService).subscribeOrder(42L);
    }

    @Test
    void shipperCanSubscribeOnlyToOwnStream() {
        when(userRepository.findByUsername("shipper-user")).thenReturn(Optional.of(user(21L)));
        Shipper shipper = new Shipper();
        shipper.setId(7L);
        when(shipperRepository.findByUserId(21L)).thenReturn(Optional.of(shipper));
        SseEmitter emitter = new SseEmitter();
        when(sseService.subscribeShipper(7L)).thenReturn(emitter);

        assertSame(emitter, controller.streamShipperEvents(7L, authentication("shipper-user", "ROLE_SHIPPER")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.streamShipperEvents(8L, authentication("shipper-user", "ROLE_SHIPPER")));

        assertEquals(404, exception.getStatusCode().value());
        verify(sseService).subscribeShipper(7L);
        verify(sseService, never()).subscribeShipper(8L);
    }

    @Test
    void adminCanSubscribeToAnyShipperStream() {
        SseEmitter emitter = new SseEmitter();
        when(sseService.subscribeShipper(7L)).thenReturn(emitter);

        assertSame(emitter, controller.streamShipperEvents(7L, authentication("admin", "ROLE_ADMIN")));

        verifyNoInteractions(userRepository, shipperRepository);
        verify(sseService).subscribeShipper(7L);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Authentication authentication(String username, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                username, "test", List.of(new SimpleGrantedAuthority(role)));
    }
}
