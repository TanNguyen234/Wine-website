# Phase 3: Shopee-Style Real-Time Event Streaming System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng hệ thống cập nhật tiến trình đơn hàng và giao vận trực tiếp thời gian thực (chuẩn Shopee / ShopeeFood) bằng Server-Sent Events (SSE). Server tự động truyền phát tín hiệu trạng thái (Xác nhận, Lấy hàng, Đang giao, Xác thực OTP, Giao thành công) tới trình duyệt khách hàng và shipper với độ trễ < 100ms, không reload trang, cơ chế chống rò rỉ bộ nhớ và tự động kết nối lại khi mất mạng.

**Architecture:** Sử dụng Spring Event-Driven Architecture (`ApplicationEventPublisher` và `@EventListener`) kết hợp với `SseEmitter` Broker đa kênh (`order:{orderId}`, `shipper:{shipperId}`, `admin:dashboard`). Phía Client sử dụng động cơ JavaScript `EventSource` có cơ chế tự động kết nối lại (Exponential Backoff Reconnect).

**Tech Stack:** Java 21, Spring Boot 4.0.0, Spring Web (`SseEmitter`), Spring Events, HTML5 EventSource, Vanilla JS ES6+.

## Global Constraints
- No Polling / No Mocks: Tuyệt đối không dùng polling định kỳ ngốn tài nguyên, không mock kết nối giả lập.
- Thread-Safety & Memory Safety: Mọi bộ lưu trữ Emitter phải dùng `ConcurrentHashMap` và `CopyOnWriteArrayList`, giải phóng ngay lập tức khi client ngắt kết nối.
- Heartbeat Resilience: Gửi frame ping định kỳ 15s để chống timeout trên reverse proxy/firewall.
- Per-Task Real Test: Mỗi task có test case kiểm thử tự động và kịch bản Live cURL stream test.

---

### Task 3.1: Hoàn Thiện Tầng Quản Lý Đa Kênh & Chống Rò Rỉ Bộ Nhớ Trong `SseNotificationService`

**Files:**
- Modify: `src/main/java/com/strongwine/strongwine/service/realtime/SseNotificationService.java:30-80`
- Test: `src/test/java/com/strongwine/strongwine/service/realtime/SseMultiChannelTest.java`

**Interfaces:**
- Consumes: `SseEmitter`, `ConcurrentHashMap`.
- Produces: `subscribeOrder(orderId)`, `subscribeShipper(shipperId)`, `subscribeAdmin()`, `broadcastToShipper(shipperId, event, data)`.

- [ ] **Step 1: Viết test cho quản lý đa kênh và dọn dẹp Emitter**

```java
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
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=SseMultiChannelTest
```
Expected: `BUILD FAILURE` (Method `subscribeShipper` not found).

- [ ] **Step 3: Mở rộng `SseNotificationService` hỗ trợ kênh Shipper và Admin**

Cập nhật `SseNotificationService.java`:
```java
    // Thêm Map lưu trữ cho Shipper
    private final Map<Long, List<SseEmitter>> shipperEmitters = new ConcurrentHashMap<>();

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

    public void removeShipperEmitter(Long shipperId, SseEmitter emitter) {
        List<SseEmitter> emitters = shipperEmitters.get(shipperId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                shipperEmitters.remove(shipperId);
            }
        }
    }

    public int getShipperSubscriberCount(Long shipperId) {
        List<SseEmitter> emitters = shipperEmitters.get(shipperId);
        return emitters != null ? emitters.size() : 0;
    }
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=SseMultiChannelTest
```
Expected: `BUILD SUCCESS` (Tests run: 2, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra compile:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/strongwine/strongwine/service/realtime/SseNotificationService.java src/test/java/com/strongwine/strongwine/service/realtime/SseMultiChannelTest.java
git commit -m "feat: enhance sse notification service with shipper channels and lifecycle cleanup"
```

---

### Task 3.2: Triển Khai Typed Domain Events & Spring Event Listeners

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/event/OrderStatusUpdatedEvent.java`
- Create: `src/main/java/com/strongwine/strongwine/event/ShipmentLiveStatusEvent.java`
- Create: `src/main/java/com/strongwine/strongwine/event/listener/RealtimeEventListener.java`
- Test: `src/test/java/com/strongwine/strongwine/event/listener/RealtimeEventListenerTest.java`

**Interfaces:**
- Consumes: Spring Application Events, `SseNotificationService`.
- Produces: `@EventListener` xử lý và broadcast sự kiện thời gian thực.

- [ ] **Step 1: Viết test cho `RealtimeEventListener`**

```java
package com.strongwine.strongwine.event.listener;

import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.event.ShipmentLiveStatusEvent;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealtimeEventListenerTest {

    @Mock private SseNotificationService sseService;

    @Test
    void testOnOrderStatusUpdatedEventBroadcastsToOrder() {
        RealtimeEventListener listener = new RealtimeEventListener(sseService);
        OrderStatusUpdatedEvent event = new OrderStatusUpdatedEvent(this, 101L, "PAID", "Đã thanh toán thành công");

        listener.handleOrderStatusUpdated(event);
        verify(sseService, times(1)).broadcastToOrder(eq(101L), eq("order_status_updated"), any());
        verify(sseService, times(1)).broadcastToAdmin(eq("admin_order_notification"), any());
    }

    @Test
    void testOnShipmentLiveStatusEventBroadcasts() {
        RealtimeEventListener listener = new RealtimeEventListener(sseService);
        ShipmentLiveStatusEvent event = new ShipmentLiveStatusEvent(this, 202L, 101L, "DELIVERING", "Nguyễn Văn Shipper", "0901234567", "59A-12345");

        listener.handleShipmentLiveStatus(event);
        verify(sseService, times(1)).broadcastToOrder(eq(101L), eq("shipment_step_updated"), any());
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=RealtimeEventListenerTest
```
Expected: `BUILD FAILURE`.

- [ ] **Step 3: Triển khai các Domain Events và `RealtimeEventListener`**

Tạo `OrderStatusUpdatedEvent.java`:
```java
package com.strongwine.strongwine.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderStatusUpdatedEvent extends ApplicationEvent {
    private final Long orderId;
    private final String status;
    private final String message;

    public OrderStatusUpdatedEvent(Object source, Long orderId, String status, String message) {
        super(source);
        this.orderId = orderId;
        this.status = status;
        this.message = message;
    }
}
```

Tạo `ShipmentLiveStatusEvent.java`:
```java
package com.strongwine.strongwine.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ShipmentLiveStatusEvent extends ApplicationEvent {
    private final Long shipmentId;
    private final Long orderId;
    private final String status;
    private final String shipperName;
    private final String shipperPhone;
    private final String vehiclePlate;

    public ShipmentLiveStatusEvent(Object source, Long shipmentId, Long orderId, String status,
                                   String shipperName, String shipperPhone, String vehiclePlate) {
        super(source);
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.status = status;
        this.shipperName = shipperName;
        this.shipperPhone = shipperPhone;
        this.vehiclePlate = vehiclePlate;
    }
}
```

Tạo `RealtimeEventListener.java`:
```java
package com.strongwine.strongwine.event.listener;

import com.strongwine.strongwine.event.OrderStatusUpdatedEvent;
import com.strongwine.strongwine.event.ShipmentLiveStatusEvent;
import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RealtimeEventListener {

    private final SseNotificationService sseService;

    public RealtimeEventListener(SseNotificationService sseService) {
        this.sseService = sseService;
    }

    @EventListener
    public void handleOrderStatusUpdated(OrderStatusUpdatedEvent event) {
        Map<String, Object> payload = Map.of(
                "orderId", event.getOrderId(),
                "status", event.getStatus(),
                "message", event.getMessage(),
                "timestamp", System.currentTimeMillis()
        );
        sseService.broadcastToOrder(event.getOrderId(), "order_status_updated", payload);
        sseService.broadcastToAdmin("admin_order_notification", payload);
    }

    @EventListener
    public void handleShipmentLiveStatus(ShipmentLiveStatusEvent event) {
        Map<String, Object> payload = Map.of(
                "shipmentId", event.getShipmentId(),
                "orderId", event.getOrderId(),
                "status", event.getStatus(),
                "shipperName", event.getShipperName() != null ? event.getShipperName() : "",
                "shipperPhone", event.getShipperPhone() != null ? event.getShipperPhone() : "",
                "vehiclePlate", event.getVehiclePlate() != null ? event.getVehiclePlate() : "",
                "timestamp", System.currentTimeMillis()
        );
        sseService.broadcastToOrder(event.getOrderId(), "shipment_step_updated", payload);
        sseService.broadcastToAdmin("admin_shipment_notification", payload);
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=RealtimeEventListenerTest
```
Expected: `BUILD SUCCESS` (Tests run: 2, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra biên dịch:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/strongwine/strongwine/event/ src/test/java/com/strongwine/strongwine/event/listener/
git commit -m "feat: implement typed domain events and realtime event listener"
```

---

### Task 3.3: Xây Dựng Động Cơ Client JavaScript `order-tracking-realtime.js`

**Files:**
- Create: `src/main/resources/static/js/order-tracking-realtime.js`
- Test: Unit/Syntax verification script `order-tracking-realtime.js`

**Interfaces:**
- Consumes: HTML5 `EventSource`, `/api/live/orders/{orderId}/stream`.
- Produces: Hàm khởi tạo `initOrderTracking(orderId, callbacks)` hỗ trợ tự động kết nối lại.

- [ ] **Step 1: Viết mã nguồn `order-tracking-realtime.js`**

Tạo `src/main/resources/static/js/order-tracking-realtime.js`:
```javascript
/**
 * StrongWine — Real-Time Order Tracking Engine (Shopee-Style)
 * Kết nối Server-Sent Events (SSE) với cơ chế tự động kết nối lại (Exponential Backoff).
 */
class OrderTrackingRealtime {
    constructor(orderId, options = {}) {
        this.orderId = orderId;
        this.options = Object.assign({
            onStatusChange: null,
            onShipmentChange: null,
            onConnect: null,
            onDisconnect: null
        }, options);

        this.eventSource = null;
        this.retryDelay = 1000;
        this.maxRetryDelay = 16000;
        this.isClosedManually = false;

        this.connect();
    }

    connect() {
        if (this.isClosedManually) return;

        const streamUrl = `/api/live/orders/${this.orderId}/stream`;
        console.log(`[SSE] Kết nối tới order stream: ${streamUrl}`);

        this.eventSource = new EventSource(streamUrl);

        this.eventSource.addEventListener('init', (e) => {
            console.log('[SSE] Khởi tạo stream thành công:', e.data);
            this.retryDelay = 1000; // Reset delay khi thành công
            if (this.options.onConnect) this.options.onConnect();
        });

        this.eventSource.addEventListener('ping', (e) => {
            // Heartbeat frame từ server để giữ kết nối
            console.debug('[SSE] Heartbeat received');
        });

        this.eventSource.addEventListener('order_status_updated', (e) => {
            try {
                const data = JSON.parse(e.data);
                console.log('[SSE] Nhận cập nhật trạng thái đơn hàng:', data);
                if (this.options.onStatusChange) this.options.onStatusChange(data);
                this.playChime();
            } catch (err) {
                console.error('[SSE] Lỗi parse dữ liệu order_status_updated', err);
            }
        });

        this.eventSource.addEventListener('shipment_step_updated', (e) => {
            try {
                const data = JSON.parse(e.data);
                console.log('[SSE] Nhận cập nhật giao hàng:', data);
                if (this.options.onShipmentChange) this.options.onShipmentChange(data);
                this.playChime();
            } catch (err) {
                console.error('[SSE] Lỗi parse dữ liệu shipment_step_updated', err);
            }
        });

        this.eventSource.onerror = (err) => {
            console.warn('[SSE] Mất kết nối tới server. Đang thử kết nối lại...', err);
            this.eventSource.close();
            if (this.options.onDisconnect) this.options.onDisconnect();

            // Thử kết nối lại với exponential backoff
            setTimeout(() => {
                this.retryDelay = Math.min(this.retryDelay * 2, this.maxRetryDelay);
                this.connect();
            }, this.retryDelay);
        };
    }

    playChime() {
        try {
            // Tạo âm thanh thông báo nhẹ nhàng bằng Web Audio API (không cần tải file audio ngoài)
            const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            const osc = audioCtx.createOscillator();
            const gain = audioCtx.createGain();
            osc.type = 'sine';
            osc.frequency.setValueAtTime(587.33, audioCtx.currentTime); // D5
            osc.frequency.exponentialRampToValueAtTime(880, audioCtx.currentTime + 0.15); // A5
            gain.gain.setValueAtTime(0.1, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.3);
            osc.connect(gain);
            gain.connect(audioCtx.destination);
            osc.start();
            osc.stop(audioCtx.currentTime + 0.3);
        } catch (e) {
            // Trình duyệt chặn audio hoặc không hỗ trợ
        }
    }

    disconnect() {
        this.isClosedManually = true;
        if (this.eventSource) {
            this.eventSource.close();
            console.log('[SSE] Đã đóng kết nối stream chủ động.');
        }
    }
}

// Global helper
window.StrongWineTracking = OrderTrackingRealtime;
```

- [ ] **Step 2: Kiểm tra cú pháp JavaScript**

Run:
```powershell
node -c src/main/resources/static/js/order-tracking-realtime.js
```
Expected: Lệnh thoát với mã 0 (Syntax OK).

- [ ] **Step 3: Live Verification**

Kiểm tra toàn bộ test suite của Phase 3:
```powershell
./mvnw test -Dtest=SseMultiChannelTest,RealtimeEventListenerTest
```
Expected: `BUILD SUCCESS` (Tất cả test pass 100%).

- [ ] **Step 4: Commit**

```powershell
git add src/main/resources/static/js/order-tracking-realtime.js
git commit -m "feat: implement client-side real-time order tracking engine with exponential backoff and audio chime"
```
