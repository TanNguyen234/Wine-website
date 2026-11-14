package com.strongwine.strongwine.service.realtime;

import com.strongwine.strongwine.entity.SseEventJournal;
import com.strongwine.strongwine.repository.SseEventJournalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    @Test
    void broadcastToOrderPersistsJournalEvenWithZeroSubscribersAndAttachesEventId() {
        SseEventJournalRepository journalRepository = mock(SseEventJournalRepository.class);
        AtomicLong seq = new AtomicLong(41L);
        when(journalRepository.save(any(SseEventJournal.class))).thenAnswer(inv -> {
            SseEventJournal entry = inv.getArgument(0);
            entry.setId(seq.incrementAndGet());
            return entry;
        });

        List<RecordingSseEmitter> created = new ArrayList<>();
        SseNotificationService journaledService = new SseNotificationService(journalRepository) {
            @Override
            protected SseEmitter createEmitter() {
                RecordingSseEmitter emitter = new RecordingSseEmitter();
                created.add(emitter);
                return emitter;
            }
        };

        // No subscribers connected yet: event must still be persisted for Last-Event-ID replay
        journaledService.broadcastToOrder(101L, "order_status_updated", Map.of("status", "PAID"));

        ArgumentCaptor<SseEventJournal> captor = ArgumentCaptor.forClass(SseEventJournal.class);
        verify(journalRepository, times(1)).save(captor.capture());
        assertEquals("ORDER", captor.getValue().getChannel());
        assertEquals(101L, captor.getValue().getTargetId());
        assertEquals("order_status_updated", captor.getValue().getEventName());
        assertTrue(captor.getValue().getPayloadJson().contains("PAID"));

        // Now connect subscriber and emit second event: SSE frame carries monotonic id:43
        journaledService.subscribeOrder(101L);
        journaledService.broadcastToOrder(101L, "order_status_updated", Map.of("status", "PACKED"));

        RecordingSseEmitter emitter = created.get(0);
        assertEquals(2, emitter.events.size()); // init + PACKED
        assertFalse(emitter.events.get(0).contains("id:"));
        assertTrue(emitter.events.get(1).contains("id:43"));
        assertTrue(emitter.events.get(1).contains("event:order_status_updated"));
    }

    @Test
    void subscribeOrderWithLastEventIdReplaysMissedEventsSequentially() {
        SseEventJournalRepository journalRepository = mock(SseEventJournalRepository.class);
        SseEventJournal e1 = journalEntry(11L, "ORDER", 101L, "order_status_updated", "{\"status\":\"PAID\"}");
        SseEventJournal e2 = journalEntry(12L, "ORDER", 101L, "shipment_step_updated", "{\"status\":\"DELIVERING\"}");
        when(journalRepository.findByChannelAndTargetIdAndIdGreaterThanOrderByIdAsc(
                eq("ORDER"), eq(101L), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(e1, e2));

        List<RecordingSseEmitter> created = new ArrayList<>();
        SseNotificationService journaledService = new SseNotificationService(journalRepository) {
            @Override
            protected SseEmitter createEmitter() {
                RecordingSseEmitter emitter = new RecordingSseEmitter();
                created.add(emitter);
                return emitter;
            }
        };

        journaledService.subscribeOrder(101L, 10L);

        RecordingSseEmitter emitter = created.get(0);
        assertEquals(3, emitter.events.size());
        assertTrue(emitter.events.get(0).contains("event:init"));
        assertFalse(emitter.events.get(0).contains("id:"));
        assertTrue(emitter.events.get(1).contains("id:11"));
        assertTrue(emitter.events.get(1).contains("event:order_status_updated"));
        assertTrue(emitter.events.get(1).contains("{\"status\":\"PAID\"}"));
        assertTrue(emitter.events.get(2).contains("id:12"));
        assertTrue(emitter.events.get(2).contains("event:shipment_step_updated"));
        assertTrue(emitter.events.get(2).contains("{\"status\":\"DELIVERING\"}"));
    }

    @Test
    void broadcastAndHeartbeatPruneDeadEmitters() {
        AtomicBoolean failOnNextSend = new AtomicBoolean(false);
        SseNotificationService resilientService = new SseNotificationService() {
            @Override
            protected SseEmitter createEmitter() {
                return new RecordingSseEmitter(failOnNextSend);
            }
        };

        resilientService.subscribeOrder(105L);
        assertEquals(1, resilientService.getOrderSubscriberCount(105L));

        failOnNextSend.set(true);
        assertDoesNotThrow(() -> resilientService.sendHeartbeat());
        assertEquals(0, resilientService.getOrderSubscriberCount(105L));

        failOnNextSend.set(false);
        resilientService.subscribeOrder(106L);
        assertEquals(1, resilientService.getOrderSubscriberCount(106L));

        failOnNextSend.set(true);
        assertDoesNotThrow(() -> resilientService.broadcastToOrder(106L, "order_status_updated", "{}"));
        assertEquals(0, resilientService.getOrderSubscriberCount(106L));
    }

    @Test
    void sendHeartbeatIsScheduledEveryTwentySeconds() throws NoSuchMethodException {
        Method method = SseNotificationService.class.getMethod("sendHeartbeat");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled);
        assertEquals(20000L, scheduled.fixedRate());
    }

    private static SseEventJournal journalEntry(Long id, String channel, Long targetId, String eventName, String payloadJson) {
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
                throw new IOException("Broken pipe");
            }
            String serialized = builder.build().stream()
                    .map(ResponseBodyEmitter.DataWithMediaType::getData)
                    .map(String::valueOf)
                    .collect(Collectors.joining());
            events.add(serialized);
        }
    }
}
