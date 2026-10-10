/**
 * StrongWine — Shipper Mobile Ergonomics Controller
 * Xử lý: OTP 6 số (.otp-box & .otp-box-mobile), lọc tab trạng thái, và SSE trực tuyến.
 */
document.addEventListener('DOMContentLoaded', () => {
    const OTP_SELECTOR = '.otp-box, .otp-box-mobile';

    document.querySelectorAll('.otp-container').forEach(container => {
        const inputs = container.querySelectorAll(OTP_SELECTOR);

        inputs.forEach((input, index) => {
            input.addEventListener('input', () => {
                const val = input.value.replace(/\D/g, '').slice(0, 1);
                input.value = val;
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
                const pasteData = (e.clipboardData || window.clipboardData).getData('text').replace(/\D/g, '').slice(0, 6);
                if (pasteData.length > 0) {
                    e.preventDefault();
                    inputs.forEach((inp, i) => {
                        inp.value = pasteData[i] || '';
                    });
                    const focusIdx = Math.min(pasteData.length, inputs.length) - 1;
                    if (focusIdx >= 0 && inputs[focusIdx]) inputs[focusIdx].focus();
                    updateHiddenOtp(container);
                }
            });
        });
    });

    function updateHiddenOtp(container) {
        const inputs = container.querySelectorAll(OTP_SELECTOR);
        let fullOtp = '';
        inputs.forEach(inp => fullOtp += inp.value);
        const shipmentId = container.getAttribute('data-shipment-id');
        const hiddenInput = document.getElementById('hiddenOtp-' + shipmentId);
        if (hiddenInput) hiddenInput.value = fullOtp;
    }

    // Tab filtering on mobile shipper cockpit
    const tabButtons = document.querySelectorAll('[data-shipper-tab]');
    const cards = document.querySelectorAll('[data-shipment-group]');
    if (tabButtons.length > 0) {
        tabButtons.forEach(btn => {
            btn.addEventListener('click', () => {
                const targetGroup = btn.getAttribute('data-shipper-tab');
                tabButtons.forEach(b => b.classList.toggle('active', b.getAttribute('data-shipper-tab') === targetGroup));
                cards.forEach(card => {
                    const group = card.getAttribute('data-shipment-group');
                    const visible = (targetGroup === 'all') || (group === targetGroup);
                    card.classList.toggle('d-none', !visible);
                });
            });
        });
    }

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

    const shipperIdMeta = document.querySelector('meta[name="shipper-id"]')?.getAttribute('content');
    const badge = document.getElementById('shipperOnlineBadge');
    if (shipperIdMeta && shipperIdMeta.trim() !== '') {
        try {
            const sse = new EventSource(`/api/live/shipper/${shipperIdMeta}/stream`);
            sse.addEventListener('init', () => {
                if (badge) {
                    badge.textContent = '● Trực tuyến';
                    badge.className = 'status good';
                }
            });
            sse.addEventListener('new_shipment_assigned', () => {
                playShipperChime();
                const alertEl = document.getElementById('shipperLiveAlert');
                if (alertEl) {
                    alertEl.textContent = 'Bạn có đơn giao hàng mới được phân công! Đang làm mới...';
                    alertEl.classList.remove('d-none');
                }
                setTimeout(() => window.location.reload(), 2000);
            });
            sse.onerror = () => {
                if (badge) {
                    badge.textContent = '○ Ngoại tuyến';
                    badge.className = 'status neutral';
                }
            };
        } catch (e) {
            if (badge) {
                badge.textContent = '○ Ngoại tuyến';
                badge.className = 'status neutral';
            }
        }
    }
});

function submitOtpForm(shipmentId) {
    const container = document.querySelector(`.otp-container[data-shipment-id="${shipmentId}"]`);
    if (!container) return;

    const boxes = container.querySelectorAll('.otp-box, .otp-box-mobile');
    let otpVal = '';
    boxes.forEach(b => otpVal += (b.value || '').trim());

    if (!/^\d{6}$/.test(otpVal)) {
        alert('Vui lòng nhập đủ 6 chữ số mã OTP nhận hàng.');
        return;
    }

    const hiddenInp = document.getElementById('hiddenOtp-' + shipmentId);
    if (hiddenInp) hiddenInp.value = otpVal;

    const form = document.getElementById('otpForm-' + shipmentId);
    if (form) {
        if (form.dataset.submitting === 'true') return;
        form.dataset.submitting = 'true';
        const btn = container.querySelector('button[data-otp-submit]');
        if (btn) btn.disabled = true;
        form.submit();
    }
}
