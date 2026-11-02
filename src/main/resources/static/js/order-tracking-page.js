(function () {
    "use strict";

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
    const shipmentSteps = {
        ASSIGNED: 2,
        PICKED_UP: 3,
        DELIVERING: 4,
        COMPLETED: 5,
        FAILED: 4
    };

    function initializeOrderTracking() {
        const container = document.getElementById("orderDetailContainer");
        if (!container || !window.StrongWineTracking) return;

        const orderId = container.dataset.orderId;
        if (!orderId) return;

        const statusText = document.getElementById("sseStatusText");
        const orderStatusBadge = document.getElementById("orderStatusBadge");
        const shipmentStatusBadge = document.getElementById("shipmentStatusBadge");
        const toast = document.getElementById("liveTrackingToast");
        const toastTitle = document.getElementById("liveToastTitle");
        const toastMessage = document.getElementById("liveToastMessage");
        let fallbackToastTimer = null;

        function showToast(title, message) {
            if (!toast || !toastTitle || !toastMessage) return;
            toastTitle.textContent = title;
            toastMessage.textContent = message;

            if (window.bootstrap && window.bootstrap.Toast) {
                window.bootstrap.Toast.getOrCreateInstance(toast, { delay: 6000 }).show();
                return;
            }

            toast.classList.add("show");
            window.clearTimeout(fallbackToastTimer);
            fallbackToastTimer = window.setTimeout(() => toast.classList.remove("show"), 6000);
        }

        function updateStepper(status) {
            const activeStep = shipmentSteps[status];
            if (!activeStep) return;

            for (let index = 1; index <= 5; index += 1) {
                const step = document.getElementById(`step-${index}`);
                if (!step) continue;
                step.classList.remove("active", "done");
                if (index < activeStep || status === "COMPLETED") step.classList.add("done");
                else if (index === activeStep) step.classList.add("active");
            }
        }

        const tracking = new window.StrongWineTracking(orderId, {
            onConnect: () => {
                if (statusText) statusText.textContent = "Đang cập nhật trực tiếp";
            },
            onDisconnect: () => {
                if (statusText) statusText.textContent = "Mất kết nối, đang thử lại...";
            },
            onStatusChange: (data) => {
                const label = orderStatuses[data.status] || data.status || "Đang cập nhật";
                if (orderStatusBadge) orderStatusBadge.textContent = label;
                showToast("Cập nhật đơn hàng", data.message || `Trạng thái mới: ${label}`);
            },
            onShipmentChange: (data) => {
                const label = shipmentStatuses[data.status] || data.status || "Đang cập nhật";
                if (shipmentStatusBadge) shipmentStatusBadge.textContent = label;
                updateStepper(data.status);
                showToast("Cập nhật vận đơn", `Trạng thái giao hàng: ${label}`);
            }
        });

        window.addEventListener("pagehide", () => {
            tracking.disconnect();
            window.clearTimeout(fallbackToastTimer);
        }, { once: true });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initializeOrderTracking, { once: true });
    } else {
        initializeOrderTracking();
    }
})();
