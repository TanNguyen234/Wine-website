(function (window, document) {
    "use strict";

    var orderStatuses = {
        PENDING: "Chờ xử lý",
        PAID: "Đã thanh toán",
        RETURN_REQUESTED: "Yêu cầu hoàn trả",
        RETURN_APPROVED: "Đã duyệt trả hàng",
        RETURNED: "Đã hoàn trả",
        REFUNDED: "Đã hoàn tiền",
        CANCELLED: "Đã hủy"
    };

    var shipmentStatuses = {
        PENDING_ASSIGNMENT: "Chờ phân công",
        ASSIGNED: "Đã phân công",
        PICKED_UP: "Đã lấy hàng",
        DELIVERING: "Đang giao",
        COMPLETED: "Hoàn tất",
        FAILED: "Thất bại"
    };

    var shipmentSteps = {
        PENDING_ASSIGNMENT: 1,
        ASSIGNED: 2,
        PICKED_UP: 3,
        DELIVERING: 4,
        COMPLETED: 5,
        FAILED: 4
    };

    function notify(title, message, tone) {
        if (window.SwToast && typeof window.SwToast.show === "function") {
            window.SwToast.show({
                title: title,
                message: message,
                tone: tone || "info",
                duration: 6000
            });
        }
    }

    function updateStepper(status) {
        var activeStep = shipmentSteps[status];
        if (!activeStep) {
            return;
        }
        for (var index = 1; index <= 5; index += 1) {
            var step = document.getElementById("step-" + index);
            if (!step) {
                continue;
            }
            step.classList.remove("active", "done", "current", "completed");
            if (index < activeStep || status === "COMPLETED") {
                step.classList.add("done");
            } else if (index === activeStep) {
                step.classList.add("active");
            }
        }
    }

    function wireCopyOtp() {
        var copyBtn = document.getElementById("btnCopyOtp");
        var otpDisplay = document.getElementById("otpCodeDisplay");
        var copyText = document.getElementById("copyOtpText");
        if (!copyBtn || !otpDisplay) {
            return;
        }

        copyBtn.addEventListener("click", function () {
            var otp = (otpDisplay.textContent || "").trim();
            if (!otp || otp.indexOf("-") !== -1) {
                return;
            }
            if (navigator.clipboard && typeof navigator.clipboard.writeText === "function") {
                navigator.clipboard.writeText(otp).then(function () {
                    if (copyText) copyText.textContent = "Đã sao chép mã OTP";
                    notify("OTP giao nhận", "Đã sao chép mã OTP " + otp + ".", "ok");
                    window.setTimeout(function () {
                        if (copyText) copyText.textContent = "Sao chép mã OTP";
                    }, 2500);
                }).catch(function () {
                    notify("OTP giao nhận", "Mã OTP của bạn là: " + otp, "info");
                });
            } else {
                notify("OTP giao nhận", "Mã OTP của bạn là: " + otp, "info");
            }
        });
    }

    function wireReturnForm() {
        var form = document.getElementById("returnRequestForm");
        var reasonInput = document.getElementById("returnReasonInput");
        if (!form || !reasonInput) {
            return;
        }
        form.addEventListener("submit", function (event) {
            var val = (reasonInput.value || "").trim();
            if (!val) {
                event.preventDefault();
                notify("Trả hàng / Hoàn tiền", "Vui lòng nhập lý do trả hàng cụ thể.", "warn");
                reasonInput.focus();
            }
        });
    }

    function initializeOrderTracking() {
        var container = document.getElementById("orderDetailContainer");
        if (!container) {
            return;
        }

        wireCopyOtp();
        wireReturnForm();

        if (!window.StrongWineTracking) {
            return;
        }

        var orderId = container.getAttribute("data-order-id");
        if (!orderId) {
            return;
        }

        var statusText = document.getElementById("sseStatusText");
        var orderStatusBadge = document.getElementById("orderStatusBadge");
        var orderPaymentBadge = document.getElementById("orderPaymentBadge");
        var shipmentStatusBadge = document.getElementById("shipmentStatusBadge");
        var shipperWrap = document.getElementById("shipperInfoWrap");
        var shipperPending = document.getElementById("shipperPending");
        var shipperName = document.getElementById("shipperName");
        var shipperPhone = document.getElementById("shipperPhone");
        var otpVaultCard = document.getElementById("otpVaultCard");

        var tracking = new window.StrongWineTracking(orderId, {
            onConnect: function () {
                if (statusText) {
                    statusText.textContent = "Đang cập nhật trực tiếp";
                }
            },
            onDisconnect: function () {
                if (statusText) {
                    statusText.textContent = "Mất kết nối, đang thử lại…";
                }
            },
            onStatusChange: function (data) {
                var code = String(data.status || "").toUpperCase();
                var label = orderStatuses[code] || data.status || "Đang cập nhật";
                if (orderStatusBadge) {
                    orderStatusBadge.textContent = label;
                }
                notify("Cập nhật đơn hàng", data.message || ("Trạng thái mới: " + label), code === "CANCELLED" ? "warn" : "info");
            },
            onOrderPaid: function () {
                if (orderPaymentBadge) {
                    orderPaymentBadge.textContent = "Đã thanh toán";
                }
                if (orderStatusBadge) {
                    orderStatusBadge.textContent = "Đã thanh toán";
                }
                notify("Thanh toán thành công", "Đơn hàng #" + orderId + " đã được xác nhận thanh toán.", "ok");
            },
            onShipmentChange: function (data) {
                var code = String(data.status || "").toUpperCase();
                var label = shipmentStatuses[code] || data.status || "Đang cập nhật";
                if (shipmentStatusBadge) {
                    shipmentStatusBadge.textContent = label;
                }
                updateStepper(code);

                if (data.shipperName && shipperName && shipperWrap) {
                    shipperName.textContent = data.shipperName;
                    shipperWrap.classList.remove("d-none");
                    if (shipperPending) shipperPending.classList.add("d-none");
                }
                if (data.shipperPhone && shipperPhone) {
                    shipperPhone.textContent = "SĐT: " + data.shipperPhone;
                }
                if (code === "COMPLETED" && otpVaultCard) {
                    otpVaultCard.classList.add("d-none");
                }

                notify("Cập nhật vận đơn", "Trạng thái giao hàng: " + label, code === "FAILED" ? "warn" : "ok");
            }
        });

        window.addEventListener("pagehide", function () {
            tracking.disconnect();
        }, { once: true });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initializeOrderTracking, { once: true });
    } else {
        initializeOrderTracking();
    }
})(window, document);
