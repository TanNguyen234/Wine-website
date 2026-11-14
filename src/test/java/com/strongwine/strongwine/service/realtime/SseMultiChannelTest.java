package com.strongwine.strongwine.service.realtime;

import com.strongwine.strongwine.entity.SseEventJournal;
import com.strongwine.strongwine.repository.SseEventJournalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    @Test
    void adminAndShipperReplayUseChannelJournalLookups() {
        SseEventJournalRepository journalRepository = mock(SseEventJournalRepository.class);
        SseEventJournal adminEvent = journal(101L, "ADMIN", null, "low_stock_alert", "{\"wineId\":7}");
        SseEventJournal shipperEvent = journal(102L, "SHIPPER", 55L, "new_shipment_assigned", "{\"shipmentId\":10}");
        when(journalRepository.findByChannelAndTargetIdIsNullAndIdGreaterThanOrderByIdAsc(
                eq("ADMIN"), eq(99L), any(Pageable.class)))
                .thenReturn(List.of(adminEvent));
        when(journalRepository.findByChannelAndTargetIdAndIdGreaterThanOrderByIdAsc(
                eq("SHIPPER"), eq(55L), eq(100L), any(Pageable.class)))
                .thenReturn(List.of(shipperEvent));

        List<RecordingSseEmitter> created = new ArrayList<>();
        SseNotificationService journaledService = new SseNotificationService(journalRepository) {
            @Override
            protected SseEmitter createEmitter() {
                RecordingSseEmitter emitter = new RecordingSseEmitter();
                created.add(emitter);
                return emitter;
            }
        };

        journaledService.subscribeAdmin(99L);
        journaledService.subscribeShipper(55L, 100L);

        assertEquals(1, journaledService.getAdminSubscriberCount());
        assertEquals(1, journaledService.getShipperSubscriberCount(55L));

        assertEquals(2, created.get(0).events.size());
        assertTrue(created.get(0).events.get(1).contains("id:101"));
        assertTrue(created.get(0).events.get(1).contains("event:low_stock_alert"));

        assertEquals(2, created.get(1).events.size());
        assertTrue(created.get(1).events.get(1).contains("id:102"));
        assertTrue(created.get(1).events.get(1).contains("event:new_shipment_assigned"));
    }

    @Test
    void userChannelPersistsReplaysAndPrunesDeadEmitters() {
        SseEventJournalRepository journalRepository = mock(SseEventJournalRepository.class);
        AtomicLong seq = new AtomicLong(200L);
        when(journalRepository.save(any(SseEventJournal.class))).thenAnswer(inv -> {
            SseEventJournal entry = inv.getArgument(0);
            entry.setId(seq.incrementAndGet());
            return entry;
        });
        SseEventJournal missed = journal(199L, "USER", 77L, "notification_created", "{\"title\":\"Order Paid\"}");
        when(journalRepository.findByChannelAndTargetIdAndIdGreaterThanOrderByIdAsc(
                eq("USER"), eq(77L), eq(190L), any(Pageable.class)))
                .thenReturn(List.of(missed));

        AtomicBoolean failOnSend = new AtomicBoolean(false);
        List<RecordingSseEmitter> created = new ArrayList<>();
        SseNotificationService userService = new SseNotificationService(journalRepository) {
            @Override
            protected SseEmitter createEmitter() {
                RecordingSseEmitter emitter = new RecordingSseEmitter(failOnSend);
                created.add(emitter);
                return emitter;
            }
        };

        userService.subscribeUser(77L, 190L);
        assertEquals(1, userService.getUserSubscriberCount(77L));
        userService.broadcastToUser(77L, "notification_created", Map.of("title", "Shipped"));

        RecordingSseEmitter emitter = created.get(0);
        assertEquals(3, emitter.events.size()); // init + replay(199) + live(201)
        assertTrue(emitter.events.get(1).contains("id:199"));
        assertTrue(emitter.events.get(2).contains("id:201"));

        failOnSend.set(true);
        userService.sendHeartbeat();
        assertEquals(0, userService.getUserSubscriberCount(77L));
    }

    private static SseEventJournal journal(Long id, String channel, Long targetId, String eventName, String payloadJson) {
        SseEventJournal entry = new SseEventJournal();
        entry.setId(id);
        entry.setChannel(channel);
        entry.setTargetId(targetId);
        entry.setEventName(eventName);
        entry.setPayloadJson(payloadJson);
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }

    private static final class RecordingSseEmitter extends SseEmitter {
        private final List<String> events = new ArrayList<>();
        private final AtomicBoolean failOnSend;

        private RecordingSseEmitter() {
            this(new AtomicBoolean(false));
        }

        private RecordingSseEmitter(AtomicBoolean failOnSend) {
            super(30_000L);
            this.failOnSend = failOnSend;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failOnSend.get()) {
                throw new IOException("Connection reset");
            }
            String serialized = builder.build().stream()
                    .map(ResponseBodyEmitter.DataWithMediaType::getData)
                    .map(String::valueOf)
                    .collect(Collectors.joining());
            events.add(serialized);
        }
    }
}
