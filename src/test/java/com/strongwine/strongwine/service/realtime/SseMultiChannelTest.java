package com.strongwine.strongwine.service.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.junit.jupiter.api.Assertions.*;

class SseMultiChannelTest {

    private SseNotificationService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseNotificationService();
    }

    @Test
    void testShipperChannelSubscriptionAndBroadcast() {
        Long shipperId = 55L;
        SseEmitter emitter = sseService.subscribeShipper(shipperId);
        assertNotNull(emitter);

        assertDoesNotThrow(() -> {
            sseService.broadcastToShipper(shipperId, "new_shipment_assigned", "{\"shipmentId\":1001}");
        });
    }

    @Test
    void testCleanupOnEmitterError() {
        Long shipperId = 56L;
        SseEmitter emitter = sseService.subscribeShipper(shipperId);
        sseService.removeShipperEmitter(shipperId, emitter);
        assertEquals(0, sseService.getShipperSubscriberCount(shipperId));
    }
}
