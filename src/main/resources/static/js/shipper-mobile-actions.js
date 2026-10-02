/**
 * StrongWine — Shipper Mobile Ergonomics Controller
 * Xử lý: Tự động nhảy ô OTP 6 số, Dán chuỗi OTP, và SSE nhận đơn giao mới.
 */
document.addEventListener('DOMContentLoaded', () => {
    // 1. Tự động nhảy ô OTP 6 số & paste chuỗi
    document.querySelectorAll('.otp-container').forEach(container => {
        const inputs = container.querySelectorAll('.otp-box');

        inputs.forEach((input, index) => {
            input.addEventListener('input', (e) => {
                const val = input.value;
                if (/[^0-9]/.test(val)) {
                    input.value = '';
                    return;
                }
                if (val && index < inputs.length - 1) {
                    inputs[index + 1].focus();
                }
                updateHiddenOtp(container);
            });

            input.addEventListener('keydown', (e) => {
                if (e.key === 'Backspace' && !input.value && index > 0) {
                    inputs[index - 1].focus();
                    inputs[index - 1].value = '';
                    updateHiddenOtp(container);
                }
            });

            input.addEventListener('paste', (e) => {
                e.preventDefault();
                const pasteData = (e.clipboardData || window.clipboardData).getData('text').trim();
                if (/^\d{6}$/.test(pasteData)) {
                    pasteData.split('').forEach((char, i) => {
                        if (inputs[i]) inputs[i].value = char;
                    });
                    inputs[inputs.length - 1].focus();
                    updateHiddenOtp(container);
                }
            });
        });
    });

    function updateHiddenOtp(container) {
        const inputs = container.querySelectorAll('.otp-box');
        let fullOtp = '';
        inputs.forEach(inp => fullOtp += inp.value);
        const shipmentId = container.getAttribute('data-shipment-id');
        const hiddenInput = document.getElementById('hiddenOtp-' + shipmentId);
        if (hiddenInput) hiddenInput.value = fullOtp;
    }

    // 2. Chime thông báo đơn mới bằng Web Audio API
    function playShipperChime() {
        try {
            const AudioCtx = window.AudioContext || window.webkitAudioContext;
            if (!AudioCtx) return;
            const ctx = new AudioCtx();
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.type = 'triangle';
            osc.frequency.setValueAtTime(440, ctx.currentTime);
            osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.2);
            gain.gain.setValueAtTime(0.15, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.4);
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start();
            osc.stop(ctx.currentTime + 0.4);
        } catch (e) {}
    }

    // 3. Kết nối kênh thông báo SSE cho Shipper (nếu có shipperId)
    const shipperIdMeta = document.querySelector('meta[name="shipper-id"]')?.getAttribute('content');
    if (shipperIdMeta) {
        try {
            const sse = new EventSource(`/api/live/shipper/${shipperIdMeta}/stream`);
            sse.addEventListener('init', () => {
                const badge = document.getElementById('shipperOnlineBadge');
                if (badge) {
                    badge.innerHTML = '<span class="sse-pulse-dot"></span> Đang trực tuyến';
                    badge.className = 'sse-live-pill';
                }
            });

            sse.addEventListener('new_shipment_assigned', (e) => {
                playShipperChime();
                const alertEl = document.getElementById('shipperLiveAlert');
                if (alertEl) {
                    alertEl.innerHTML = '<i class="fa-solid fa-bell fa-shake me-2 text-warning"></i>Bạn có đơn giao hàng mới được phân công! Đang làm mới...';
                    alertEl.classList.remove('d-none');
                }
                setTimeout(() => window.location.reload(), 2000);
            });
        } catch (e) {
            console.debug('SSE shipper stream error or unavailable');
        }
    }
});

// Helper submit form OTP
function submitOtpForm(shipmentId) {
    const container = document.querySelector(`.otp-container[data-shipment-id="${shipmentId}"]`);
    if (!container) return;

    const boxes = container.querySelectorAll('.otp-box');
    let otpVal = '';
    boxes.forEach(b => otpVal += b.value);

    if (otpVal.length !== 6) {
        alert('Vui lòng nhập đủ 6 chữ số mã OTP nhận hàng.');
        return;
    }

    const hiddenInp = document.getElementById('hiddenOtp-' + shipmentId);
    if (hiddenInp) hiddenInp.value = otpVal;

    const form = document.getElementById('otpForm-' + shipmentId);
    if (form) form.submit();
}
