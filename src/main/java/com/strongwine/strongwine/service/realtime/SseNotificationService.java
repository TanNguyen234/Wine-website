package com.strongwine.strongwine.service.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strongwine.strongwine.entity.SseEventJournal;
import com.strongwine.strongwine.repository.SseEventJournalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);
    private static final Long DEFAULT_TIMEOUT = 30 * 60 * 1000L; // 30 phút
    private static final int MAX_REPLAY_BATCH = 200;
    private static final int JOURNAL_RETENTION_DAYS = 7;

    private static final String CHANNEL_ORDER = "ORDER";
    private static final String CHANNEL_SHIPPER = "SHIPPER";
    private static final String CHANNEL_USER = "USER";
    private static final String CHANNEL_ADMIN = "ADMIN";

    private final SseEventJournalRepository journalRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final Map<Long, List<SseEmitter>> orderEmitters = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> shipperEmitters = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final List<SseEmitter> adminEmitters = new CopyOnWriteArrayList<>();

    public SseNotificationService() {
        this(null);
    }

    @Autowired
    public SseNotificationService(SseEventJournalRepository journalRepository) {
        this.journalRepository = journalRepository;
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(DEFAULT_TIMEOUT);
    }

    public SseEmitter subscribeOrder(Long orderId) {
        return subscribeOrder(orderId, null);
    }

    public SseEmitter subscribeOrder(Long orderId, Long lastEventId) {
        return subscribeToKeyedChannel(CHANNEL_ORDER, orderId, orderEmitters, "connected", lastEventId);
    }

    public SseEmitter subscribeShipper(Long shipperId) {
        return subscribeShipper(shipperId, null);
    }

    public SseEmitter subscribeShipper(Long shipperId, Long lastEventId) {
        return subscribeToKeyedChannel(CHANNEL_SHIPPER, shipperId, shipperEmitters, "shipper_connected", lastEventId);
    }

    public SseEmitter subscribeUser(Long userId) {
        return subscribeUser(userId, null);
    }

    public SseEmitter subscribeUser(Long userId, Long lastEventId) {
        return subscribeToKeyedChannel(CHANNEL_USER, userId, userEmitters, "user_connected", lastEventId);
    }

    public SseEmitter subscribeAdmin() {
        return subscribeAdmin(null);
    }

    public SseEmitter subscribeAdmin(Long lastEventId) {
        SseEmitter emitter = createEmitter();
        adminEmitters.add(emitter);

        emitter.onCompletion(() -> adminEmitters.remove(emitter));
        emitter.onTimeout(() -> {
            adminEmitters.remove(emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        emitter.onError(e -> adminEmitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("admin_connected"));
            replayMissedEvents(emitter, CHANNEL_ADMIN, null, lastEventId);
        } catch (IOException | IllegalStateException e) {
            adminEmitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcastToOrder(Long orderId, String eventName, Object data) {
        broadcastToKeyedChannel(CHANNEL_ORDER, orderId, orderEmitters, eventName, data);
    }

    public void broadcastToShipper(Long shipperId, String eventName, Object data) {
        broadcastToKeyedChannel(CHANNEL_SHIPPER, shipperId, shipperEmitters, eventName, data);
    }

    public void broadcastToUser(Long userId, String eventName, Object data) {
        broadcastToKeyedChannel(CHANNEL_USER, userId, userEmitters, eventName, data);
    }

    public void broadcastToAdmin(String eventName, Object data) {
        Long eventId = persistJournalEntry(CHANNEL_ADMIN, null, eventName, data);
        for (SseEmitter emitter : adminEmitters) {
            try {
                emitter.send(buildEvent(eventId, eventName, data));
            } catch (IOException | IllegalStateException e) {
                adminEmitters.remove(emitter);
            }
        }
    }

    public void removeOrderEmitter(Long orderId, SseEmitter emitter) {
        removeFromRegistry(orderEmitters, orderId, emitter);
    }

    public void removeShipperEmitter(Long shipperId, SseEmitter emitter) {
        removeFromRegistry(shipperEmitters, shipperId, emitter);
    }

    public void removeUserEmitter(Long userId, SseEmitter emitter) {
        removeFromRegistry(userEmitters, userId, emitter);
    }

    public void removeAdminEmitter(SseEmitter emitter) {
        if (emitter != null) {
            adminEmitters.remove(emitter);
        }
    }

    public int getOrderSubscriberCount(Long orderId) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        return emitters != null ? emitters.size() : 0;
    }

    public int getShipperSubscriberCount(Long shipperId) {
        List<SseEmitter> emitters = shipperEmitters.get(shipperId);
        return emitters != null ? emitters.size() : 0;
    }

    public int getUserSubscriberCount(Long userId) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        return emitters != null ? emitters.size() : 0;
    }

    public int getAdminSubscriberCount() {
        return adminEmitters.size();
    }

    @Scheduled(fixedRate = 20000)
    public void sendHeartbeat() {
        sendHeartbeatForRegistry(orderEmitters);
        sendHeartbeatForRegistry(shipperEmitters);
        sendHeartbeatForRegistry(userEmitters);

        for (SseEmitter emitter : adminEmitters) {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                adminEmitters.remove(emitter);
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredJournalEntries() {
        if (journalRepository == null) {
            return;
        }
        journalRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(JOURNAL_RETENTION_DAYS));
    }

    private SseEmitter subscribeToKeyedChannel(String channel,
                                               Long targetId,
                                               Map<Long, List<SseEmitter>> registry,
                                               String initPayload,
                                               Long lastEventId) {
        if (targetId == null) {
            throw new IllegalArgumentException("Channel targetId must not be null for " + channel);
        }
        SseEmitter emitter = createEmitter();
        registry.compute(targetId, (id, existing) -> {
            List<SseEmitter> list = existing != null ? existing : new CopyOnWriteArrayList<>();
            list.add(emitter);
            return list;
        });

        emitter.onCompletion(() -> removeFromRegistry(registry, targetId, emitter));
        emitter.onTimeout(() -> {
            removeFromRegistry(registry, targetId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        emitter.onError(e -> removeFromRegistry(registry, targetId, emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data(initPayload));
            replayMissedEvents(emitter, channel, targetId, lastEventId);
        } catch (IOException | IllegalStateException e) {
            removeFromRegistry(registry, targetId, emitter);
        }
        return emitter;
    }

    private void broadcastToKeyedChannel(String channel,
                                         Long targetId,
                                         Map<Long, List<SseEmitter>> registry,
                                         String eventName,
                                         Object data) {
        if (targetId == null) {
            return;
        }
        Long eventId = persistJournalEntry(channel, targetId, eventName, data);
        List<SseEmitter> emitters = registry.get(targetId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(buildEvent(eventId, eventName, data));
            } catch (IOException | IllegalStateException e) {
                removeFromRegistry(registry, targetId, emitter);
            }
        }
    }

    private void removeFromRegistry(Map<Long, List<SseEmitter>> registry, Long targetId, SseEmitter emitter) {
        if (targetId == null || emitter == null) {
            return;
        }
        registry.computeIfPresent(targetId, (id, existing) -> {
            existing.remove(emitter);
            return existing.isEmpty() ? null : existing;
        });
    }

    private void sendHeartbeatForRegistry(Map<Long, List<SseEmitter>> registry) {
        registry.forEach((targetId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("keep-alive"));
                } catch (IOException | IllegalStateException e) {
                    removeFromRegistry(registry, targetId, emitter);
                }
            }
        });
    }

    private SseEmitter.SseEventBuilder buildEvent(Long eventId, String eventName, Object data) {
        SseEmitter.SseEventBuilder builder = SseEmitter.event()
                .name(eventName)
                .data(data != null ? data : "");
        if (eventId != null) {
            builder.id(String.valueOf(eventId));
        }
        return builder;
    }

    private void replayMissedEvents(SseEmitter emitter,
                                    String channel,
                                    Long targetId,
                                    Long lastEventId) throws IOException {
        if (journalRepository == null || lastEventId == null) {
            return;
        }
        Pageable batch = PageRequest.of(0, MAX_REPLAY_BATCH);
        List<SseEventJournal> missed = targetId != null
                ? journalRepository.findByChannelAndTargetIdAndIdGreaterThanOrderByIdAsc(channel, targetId, lastEventId, batch)
                : journalRepository.findByChannelAndTargetIdIsNullAndIdGreaterThanOrderByIdAsc(channel, lastEventId, batch);

        for (SseEventJournal entry : missed) {
            String payload = entry.getPayloadJson() != null ? entry.getPayloadJson() : "";
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(entry.getId()))
                    .name(entry.getEventName())
                    .data((Object) payload));
        }
    }

    private Long persistJournalEntry(String channel, Long targetId, String eventName, Object data) {
        if (journalRepository == null) {
            return null;
        }
        SseEventJournal entry = new SseEventJournal();
        entry.setChannel(channel);
        entry.setTargetId(targetId);
        entry.setEventName(eventName != null ? eventName : "message");
        entry.setPayloadJson(serializePayload(data));
        SseEventJournal saved = journalRepository.save(entry);
        return saved != null ? saved.getId() : null;
    }

    private String serializePayload(Object data) {
        if (data == null) {
            return "";
        }
        if (data instanceof String raw) {
            return raw;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize SSE payload for {}: {}", data.getClass().getSimpleName(), ex.getMessage());
            return String.valueOf(data);
        }
    }
}
