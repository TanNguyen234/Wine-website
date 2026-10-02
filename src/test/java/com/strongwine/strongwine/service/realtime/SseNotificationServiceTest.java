package com.strongwine.strongwine.service.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.junit.jupiter.api.Assertions.*;

class SseNotificationServiceTest {

    private SseNotificationService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseNotificationService();
    }

    @Test
    void testSubscribeAndEmitEvent() {
        Long orderId = 101L;
        SseEmitter emitter = sseService.subscribeOrder(orderId);
        assertNotNull(emitter);
        assertEquals(1, sseService.getOrderSubscriberCount(orderId));

        assertDoesNotThrow(() -> {
            sseService.broadcastToOrder(orderId, "order_status_updated", "{\"status\":\"DELIVERING\"}");
        });
    }

    @Test
    void testUnsubscribeOnComplete() {
        Long orderId = 102L;
        SseEmitter emitter = sseService.subscribeOrder(orderId);
        emitter.complete();
        // Giả lập callback completed
        sseService.removeOrderEmitter(orderId, emitter);
        assertEquals(0, sseService.getOrderSubscriberCount(orderId));
    }
}
