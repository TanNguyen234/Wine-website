package com.strongwine.strongwine.service.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);
    private static final Long DEFAULT_TIMEOUT = 30 * 60 * 1000L; // 30 phút

    private final Map<Long, List<SseEmitter>> orderEmitters = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> shipperEmitters = new ConcurrentHashMap<>();
    private final List<SseEmitter> adminEmitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribeOrder(Long orderId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        orderEmitters.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeOrderEmitter(orderId, emitter));
        emitter.onTimeout(() -> removeOrderEmitter(orderId, emitter));
        emitter.onError(e -> removeOrderEmitter(orderId, emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("connected"));
        } catch (IOException e) {
            removeOrderEmitter(orderId, emitter);
        }
        return emitter;
    }

    public SseEmitter subscribeShipper(Long shipperId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        shipperEmitters.computeIfAbsent(shipperId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeShipperEmitter(shipperId, emitter));
        emitter.onTimeout(() -> removeShipperEmitter(shipperId, emitter));
        emitter.onError(e -> removeShipperEmitter(shipperId, emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("shipper_connected"));
        } catch (IOException e) {
            removeShipperEmitter(shipperId, emitter);
        }
        return emitter;
    }

    public SseEmitter subscribeAdmin() {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        adminEmitters.add(emitter);

        emitter.onCompletion(() -> adminEmitters.remove(emitter));
        emitter.onTimeout(() -> adminEmitters.remove(emitter));
        emitter.onError(e -> adminEmitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("admin_connected"));
        } catch (IOException e) {
            adminEmitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcastToOrder(Long orderId, String eventName, Object data) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        if (emitters == null || emitters.isEmpty()) return;

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                removeOrderEmitter(orderId, emitter);
            }
        }
    }

    public void broadcastToShipper(Long shipperId, String eventName, Object data) {
        List<SseEmitter> emitters = shipperEmitters.get(shipperId);
        if (emitters == null || emitters.isEmpty()) return;

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                removeShipperEmitter(shipperId, emitter);
            }
        }
    }

    public void broadcastToAdmin(String eventName, Object data) {
        for (SseEmitter emitter : adminEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                adminEmitters.remove(emitter);
            }
        }
    }

    public void removeOrderEmitter(Long orderId, SseEmitter emitter) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                orderEmitters.remove(orderId);
            }
        }
    }

    public void removeShipperEmitter(Long shipperId, SseEmitter emitter) {
        List<SseEmitter> emitters = shipperEmitters.get(shipperId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                shipperEmitters.remove(shipperId);
            }
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

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        orderEmitters.forEach((orderId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
                } catch (IOException e) {
                    removeOrderEmitter(orderId, emitter);
                }
            }
        });

        shipperEmitters.forEach((shipperId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
                } catch (IOException e) {
                    removeShipperEmitter(shipperId, emitter);
                }
            }
        });

        for (SseEmitter emitter : adminEmitters) {
            try {
                emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
            } catch (IOException e) {
                adminEmitters.remove(emitter);
            }
        }
    }
}
