package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.ShipperRepository;
import com.strongwine.strongwine.repository.UserRepository;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

@RestController
@RequestMapping("/api/live")
public class SseNotificationApiController {

    private final SseNotificationService sseService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ShipperRepository shipperRepository;

    public SseNotificationApiController(SseNotificationService sseService,
                                        OrderRepository orderRepository,
                                        UserRepository userRepository,
                                        ShipperRepository shipperRepository) {
        this.sseService = sseService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.shipperRepository = shipperRepository;
    }

    @GetMapping(value = "/orders/{orderId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderEvents(@PathVariable Long orderId, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            User user = authenticatedUser(authentication);
            if (orderRepository.findByIdAndUserId(orderId, user.getId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        return sseService.subscribeOrder(orderId);
    }

    @GetMapping(value = {"/admin/notifications", "/admin/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAdminNotifications(Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return sseService.subscribeAdmin();
    }

    @GetMapping(value = "/shipper/{shipperId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamShipperEvents(@PathVariable Long shipperId, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            if (!hasRole(authentication, "ROLE_SHIPPER")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }

            User user = authenticatedUser(authentication);
            boolean ownsStream = shipperRepository.findByUserId(user.getId())
                    .map(assigned -> Objects.equals(assigned.getId(), shipperId))
                    .orElse(false);
            if (!ownsStream) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        return sseService.subscribeShipper(shipperId);
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
