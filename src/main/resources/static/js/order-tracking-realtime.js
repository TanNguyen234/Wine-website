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
            const AudioContextClass = typeof window !== 'undefined' ? (window.AudioContext || window.webkitAudioContext) : null;
            if (!AudioContextClass) return;
            const audioCtx = new AudioContextClass();
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
if (typeof window !== 'undefined') {
    window.StrongWineTracking = OrderTrackingRealtime;
}
