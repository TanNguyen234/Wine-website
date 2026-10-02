document.addEventListener('DOMContentLoaded', () => {
    // Xử lý thanh trượt khoảng giá
    const priceSlider = document.getElementById('priceRangeSlider');
    const priceOutput = document.getElementById('priceRangeOutput');
    if (priceSlider && priceOutput) {
        priceSlider.addEventListener('input', (e) => {
            const val = parseInt(e.target.value, 10);
            priceOutput.textContent = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val);
        });
    }

    // Xử lý Quick View Modal
    document.querySelectorAll('.btn-quick-view').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            const id = btn.getAttribute('data-wine-id');
            const name = btn.getAttribute('data-wine-name');
            const price = btn.getAttribute('data-wine-price');
            const type = btn.getAttribute('data-wine-type');
            const year = btn.getAttribute('data-wine-year');
            const country = btn.getAttribute('data-wine-country');
            const desc = btn.getAttribute('data-wine-desc');
            const img = btn.getAttribute('data-wine-img');

            const modal = document.getElementById('quickViewModal');
            if (modal) {
                const nameEl = modal.querySelector('.modal-wine-name');
                const priceEl = modal.querySelector('.modal-wine-price');
                const typeEl = modal.querySelector('.modal-wine-type');
                const yearEl = modal.querySelector('.modal-wine-year');
                const countryEl = modal.querySelector('.modal-wine-country');
                const descEl = modal.querySelector('.modal-wine-desc');
                const imgEl = modal.querySelector('.modal-wine-img');
                const addBtn = modal.querySelector('.modal-add-to-cart-btn');

                if (nameEl) nameEl.textContent = name;
                if (priceEl) priceEl.textContent = price;
                if (typeEl) typeEl.textContent = type;
                if (yearEl) yearEl.textContent = year;
                if (countryEl) countryEl.textContent = country || 'Đang cập nhật';
                if (descEl) descEl.textContent = desc || 'Chưa có mô tả nếm thử chi tiết.';
                if (imgEl && img) imgEl.src = img;
                if (addBtn) addBtn.setAttribute('data-wine-id', id);

                if (window.bootstrap && window.bootstrap.Modal) {
                    const bsModal = new bootstrap.Modal(modal);
                    bsModal.show();
                }
            }
        });
    });
});
