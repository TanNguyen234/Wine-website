function initializeWineList() {
    const filterDisclosure = document.getElementById('catalogFilters');
    if (filterDisclosure) {
        const mobileBreakpoint = window.matchMedia('(max-width: 768px)');
        const hasActiveFilters = () => [...document.querySelectorAll('#filterForm input[name], #filterForm select[name]')]
            .some((field) => {
                if (['page', 'size', 'sortBy', 'sortDir'].includes(field.name)) return false;
                return field.type === 'checkbox' ? field.checked : field.value.trim() !== '';
            });
        const syncFilterDisclosure = () => {
            filterDisclosure.open = !mobileBreakpoint.matches || hasActiveFilters();
        };
        syncFilterDisclosure();
        if (typeof mobileBreakpoint.addEventListener === 'function') {
            mobileBreakpoint.addEventListener('change', syncFilterDisclosure);
        } else {
            mobileBreakpoint.addListener(syncFilterDisclosure);
        }
    }

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
            const attr = (current, legacy) => btn.getAttribute(current) || btn.getAttribute(legacy) || '';
            const id = attr('data-id', 'data-wine-id');
            const name = attr('data-name', 'data-wine-name');
            const rawPrice = attr('data-price', 'data-wine-raw-price') || attr('data-wine-price', '');
            const type = attr('data-type', 'data-wine-type');
            const year = attr('data-year', 'data-wine-year');
            const country = attr('data-country', 'data-wine-country');
            const desc = attr('data-desc', 'data-wine-desc');
            const img = attr('data-img', 'data-wine-img');
            const stock = attr('data-stock', 'data-wine-stock') || '0';

            const modal = document.getElementById('quickViewModal');
            if (!modal) return;

            const setTxt = (id, value) => {
                const el = modal.querySelector(id);
                if (el) el.textContent = value;
            };
            const priceValue = Number(String(rawPrice).replace(/[^0-9.]/g, ''));
            const formattedPrice = Number.isFinite(priceValue)
                ? new Intl.NumberFormat('vi-VN').format(priceValue) + ' ₫'
                : rawPrice;
            setTxt('#qvName', name);
            setTxt('#qvPrice', formattedPrice);
            setTxt('#qvType', type);
            setTxt('#qvYear', year || '—');
            setTxt('#qvCountry', country || 'Quốc tế');
            setTxt('#qvDesc', desc || 'Hương vị cân bằng, hậu vị thanh lịch từ nhà làm vang tuyển chọn.');
            const stockValue = Math.max(0, parseInt(stock, 10) || 0);
            setTxt('#qvStockBadge', stockValue > 0 ? 'Còn ' + stockValue + ' chai' : 'Hết hàng');

            const imgEl = modal.querySelector('#qvImg');
            const imgFallback = modal.querySelector('#qvFallback');
            if (imgEl) {
                imgEl.alt = name || 'Rượu vang';
                imgEl.onerror = () => {
                    imgEl.classList.add('d-none');
                    if (imgFallback) imgFallback.classList.remove('d-none');
                };
                if (img.trim()) {
                    if (imgFallback) imgFallback.classList.add('d-none');
                    imgEl.classList.remove('d-none');
                    imgEl.src = img;
                } else {
                    imgEl.removeAttribute('src');
                    imgEl.classList.add('d-none');
                    if (imgFallback) imgFallback.classList.remove('d-none');
                }
            }

            const detailLink = modal.querySelector('#qvDetailLink');
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
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeWineList, { once: true });
} else {
    initializeWineList();
}
