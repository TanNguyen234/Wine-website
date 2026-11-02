(function () {
    "use strict";

    const badge = document.getElementById("adminLiveBadge");
    if (!badge || typeof window.EventSource !== "function") return;

    const toast = document.getElementById("adminLiveToast");
    const toastTitle = document.getElementById("adminToastTitle");
    const toastBody = document.getElementById("adminToastBody");
    const maxRetryDelay = 16000;
    let retryDelay = 1000;
    let retryTimer = null;
    let eventSource = null;
    let closed = false;
    let fallbackToastTimer = null;

    const orderStatuses = {
        PENDING: "Chờ xử lý",
        PAID: "Đã thanh toán",
        CANCELLED: "Đã hủy"
    };
    const shipmentStatuses = {
        PENDING_ASSIGNMENT: "Chờ phân công",
        ASSIGNED: "Đã phân công",
        PICKED_UP: "Đã lấy hàng",
        DELIVERING: "Đang giao",
        COMPLETED: "Hoàn tất",
        FAILED: "Thất bại"
    };

    function setBadge(message, state) {
        badge.textContent = `● ${message}`;
        badge.className = `status ${state}`;
    }

    function showToast(title, message) {
        if (!toast || !toastTitle || !toastBody) return;
        toastTitle.textContent = title;
        toastBody.textContent = message;

        if (window.bootstrap && window.bootstrap.Toast) {
            window.bootstrap.Toast.getOrCreateInstance(toast, { delay: 7000 }).show();
            return;
        }

        toast.classList.remove("d-none");
        toast.classList.add("show");
        window.clearTimeout(fallbackToastTimer);
        fallbackToastTimer = window.setTimeout(() => {
            toast.classList.remove("show");
            toast.classList.add("d-none");
        }, 7000);
    }

    function parsePayload(event) {
        try {
            return JSON.parse(event.data);
        } catch (_) {
            return {};
        }
    }

    function handleOrderNotification(event) {
        const data = parsePayload(event);
        const status = orderStatuses[data.status] || data.status || "có cập nhật mới";
        const message = typeof data.message === "string" && data.message.trim()
            ? data.message
            : `Trạng thái đơn hàng: ${status}`;
        showToast(`Đơn hàng #${data.orderId || ""}`, message);
    }

    function handleShipmentNotification(event) {
        const data = parsePayload(event);
        const status = shipmentStatuses[data.status] || data.status || "có cập nhật mới";
        showToast(
            `Vận đơn #${data.shipmentId || ""}`,
            `Đơn hàng #${data.orderId || ""} · ${status}`
        );
    }

    function connect() {
        if (closed) return;

        const source = new window.EventSource("/api/live/admin/notifications");
        eventSource = source;

        source.addEventListener("init", () => {
            retryDelay = 1000;
            setBadge("Trực tuyến", "good");
        });
        source.addEventListener("admin_order_notification", handleOrderNotification);
        source.addEventListener("admin_shipment_notification", handleShipmentNotification);
        source.onerror = () => {
            source.close();
            if (eventSource === source) eventSource = null;
            if (closed) return;

            setBadge("Đang kết nối lại SSE...", "neutral");
            const delay = retryDelay;
            retryDelay = Math.min(retryDelay * 2, maxRetryDelay);
            retryTimer = window.setTimeout(connect, delay);
        };
    }

    window.addEventListener("pagehide", () => {
        closed = true;
        window.clearTimeout(retryTimer);
        window.clearTimeout(fallbackToastTimer);
        if (eventSource) eventSource.close();
    }, { once: true });

    connect();
})();
