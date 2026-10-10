document.addEventListener('DOMContentLoaded', () => {
    const priceSlider = document.getElementById('priceRangeSlider');
    const priceOutput = document.getElementById('priceRangeOutput');
    if (priceSlider && priceOutput) {
        priceSlider.addEventListener('input', (e) => {
            const val = parseInt(e.target.value, 10);
            priceOutput.textContent = new Intl.NumberFormat('vi-VN').format(val) + ' ₫';
        });
    }

    document.querySelectorAll('.btn-quick-view').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            const id = btn.getAttribute('data-wine-id');
            const name = btn.getAttribute('data-wine-name');
            const price = btn.getAttribute('data-wine-price');
            const rawPrice = btn.getAttribute('data-wine-raw-price') || price;
            const type = btn.getAttribute('data-wine-type');
            const year = btn.getAttribute('data-wine-year');
            const country = btn.getAttribute('data-wine-country');
            const desc = btn.getAttribute('data-wine-desc');
            const img = btn.getAttribute('data-wine-img');
            const stock = btn.getAttribute('data-wine-stock') || '10';

            const modal = document.getElementById('quickViewModal');
            if (!modal) return;

            const setTxt = (sel, val) => { const el = modal.querySelector(sel); if (el) el.textContent = val; };
            setTxt('.modal-wine-name', name || '');
            setTxt('.modal-wine-price', price || '');
            setTxt('.modal-wine-type', type || '');
            setTxt('.modal-wine-year', year || '');
            setTxt('.modal-wine-country', country || 'Pháp');
            setTxt('.modal-wine-desc', desc || 'Hương vị cân bằng, hậu vị thanh lịch từ nhà làm vang tuyển chọn.');

            const imgEl = modal.querySelector('.modal-wine-img');
            if (imgEl && img) {
                imgEl.src = img;
                imgEl.alt = name || 'Rượu vang';
            }

            const detailLink = modal.querySelector('.modal-wine-detail-link');
            if (detailLink && id) {
                detailLink.setAttribute('href', '/wines/' + id);
            }

            const addBtn = modal.querySelector('.modal-add-to-cart-btn');
            if (addBtn) {
                addBtn.setAttribute('data-cart-add', 'true');
                addBtn.setAttribute('data-cart-product-id', id || '');
                addBtn.setAttribute('data-cart-name', name || '');
                addBtn.setAttribute('data-cart-price', String(rawPrice).replace(/[^0-9.]/g, ''));
                addBtn.setAttribute('data-cart-image', img || '');
                addBtn.setAttribute('data-cart-stock', stock);
                addBtn.disabled = parseInt(stock, 10) <= 0;
            }

            if (window.bootstrap && window.bootstrap.Modal) {
                const bsModal = window.bootstrap.Modal.getOrCreateInstance(modal);
                bsModal.show();
            } else {
                modal.classList.add('open');
            }
        });
    });
});
