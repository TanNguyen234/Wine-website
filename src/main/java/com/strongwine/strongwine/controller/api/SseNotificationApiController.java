package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/live")
public class SseNotificationApiController {

    private final SseNotificationService sseService;

    public SseNotificationApiController(SseNotificationService sseService) {
        this.sseService = sseService;
    }

    @GetMapping(value = "/orders/{orderId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderEvents(@PathVariable Long orderId) {
        return sseService.subscribeOrder(orderId);
    }

    @GetMapping(value = "/admin/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAdminNotifications() {
        return sseService.subscribeAdmin();
    }
}
