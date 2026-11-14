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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    public SseEmitter streamOrderEvents(Long orderId, Authentication authentication) {
        return streamOrderEvents(orderId, authentication, null, null);
    }

    @GetMapping(value = "/orders/{orderId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderEvents(
            @PathVariable Long orderId,
            Authentication authentication,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
            @RequestParam(value = "lastEventId", required = false) Long lastEventIdParam) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            User user = authenticatedUser(authentication);
            if (orderRepository.findByIdAndUserId(orderId, user.getId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        Long lastEventId = resolveLastEventId(lastEventIdHeader, lastEventIdParam);
        return lastEventId != null
                ? sseService.subscribeOrder(orderId, lastEventId)
                : sseService.subscribeOrder(orderId);
    }

    public SseEmitter streamAdminNotifications(Authentication authentication) {
        return streamAdminNotifications(authentication, null, null);
    }

    @GetMapping(value = {"/admin/notifications", "/admin/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAdminNotifications(
            Authentication authentication,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
            @RequestParam(value = "lastEventId", required = false) Long lastEventIdParam) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Long lastEventId = resolveLastEventId(lastEventIdHeader, lastEventIdParam);
        return lastEventId != null
                ? sseService.subscribeAdmin(lastEventId)
                : sseService.subscribeAdmin();
    }

    public SseEmitter streamShipperEvents(Long shipperId, Authentication authentication) {
        return streamShipperEvents(shipperId, authentication, null, null);
    }

    @GetMapping(value = "/shipper/{shipperId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamShipperEvents(
            @PathVariable Long shipperId,
            Authentication authentication,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
            @RequestParam(value = "lastEventId", required = false) Long lastEventIdParam) {
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
        Long lastEventId = resolveLastEventId(lastEventIdHeader, lastEventIdParam);
        return lastEventId != null
                ? sseService.subscribeShipper(shipperId, lastEventId)
                : sseService.subscribeShipper(shipperId);
    }

    public SseEmitter streamUserNotifications(Authentication authentication) {
        return streamUserNotifications(authentication, null, null);
    }

    @GetMapping(value = {"/user/stream", "/notifications/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUserNotifications(
            Authentication authentication,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader,
            @RequestParam(value = "lastEventId", required = false) Long lastEventIdParam) {
        User user = authenticatedUser(authentication);
        Long lastEventId = resolveLastEventId(lastEventIdHeader, lastEventIdParam);
        return lastEventId != null
                ? sseService.subscribeUser(user.getId(), lastEventId)
                : sseService.subscribeUser(user.getId());
    }

    private Long resolveLastEventId(String headerValue, Long paramValue) {
        if (headerValue != null && !headerValue.isBlank()) {
            try {
                long parsed = Long.parseLong(headerValue.trim());
                if (parsed >= 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Fall back to explicit query parameter or fresh subscription
            }
        }
        return paramValue != null && paramValue >= 0 ? paramValue : null;
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
