/**
 * StrongWine — Slide-out Cart Drawer Controller (Unified with StrongWineCart localStorage)
 */
class CartDrawerController {
    constructor() {
        this.drawerEl = document.getElementById('cartDrawer');
        this.itemsEl = document.getElementById('cartDrawerItems');
        this.subtotalEl = document.getElementById('cartDrawerSubtotal');
        this.initListeners();
        this.renderDrawer();
    }

    initListeners() {
        document.addEventListener('click', (e) => {
            if (e.target.closest('.js-open-cart-drawer')) {
                e.preventDefault();
                this.openDrawer();
                return;
            }
            if (e.target.closest('.js-close-cart-drawer') || e.target === this.drawerEl) {
                this.closeDrawer();
                return;
            }
            const removeBtn = e.target.closest('[data-drawer-remove-id]');
            if (removeBtn && window.StrongWineCart) {
                const pid = removeBtn.getAttribute('data-drawer-remove-id');
                window.StrongWineCart.removeItem(pid);
                if (typeof window.StrongWineCart.renderCartPage === 'function') {
                    window.StrongWineCart.renderCartPage();
                }
            }
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.closeDrawer();
            }
        });

        window.addEventListener('strongwine:cart-updated', () => {
            this.renderDrawer();
        });
    }

    openDrawer() {
        this.renderDrawer();
        if (this.drawerEl) {
            this.drawerEl.classList.add('open');
            this.drawerEl.setAttribute('aria-hidden', 'false');
        }
    }

    closeDrawer() {
        if (this.drawerEl) {
            this.drawerEl.classList.remove('open');
            this.drawerEl.setAttribute('aria-hidden', 'true');
        }
    }

    renderDrawer() {
        if (!this.itemsEl || !window.StrongWineCart) return;
        const items = window.StrongWineCart.getCart();
        const fmt = window.StrongWineCart.formatCurrency;

        if (!items.length) {
            this.itemsEl.innerHTML = '<p class="muted">Chưa có sản phẩm. Hãy khám phá bộ sưu tập rượu vang.</p>';
            if (this.subtotalEl) this.subtotalEl.textContent = '0 ₫';
            return;
        }

        let sum = 0;
        this.itemsEl.innerHTML = items.map(item => {
            const lineTotal = item.price * item.quantity;
            sum += lineTotal;
            return `
                <div class="order-row">
                    <div class="display-flex gap16">
                        <div class="cart-image">
                            ${item.image ? `<img src="${item.image}" alt="${item.name}">` : `<span class="serif" style="color:var(--sw-bordeaux)">S</span>`}
                        </div>
                        <div>
                            <b>${item.name}</b>
                            <p class="muted small">Số lượng: ${item.quantity}</p>
                            <span style="color:var(--sw-bordeaux);font-weight:800">${fmt(lineTotal)}</span>
                        </div>
                    </div>
                    <button type="button" class="close-btn" data-drawer-remove-id="${item.productId}" aria-label="Xóa ${item.name}">&times;</button>
                </div>
            `;
        }).join('');

        if (this.subtotalEl) {
            this.subtotalEl.textContent = fmt(sum);
        }
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.cartDrawer = new CartDrawerController();
});
