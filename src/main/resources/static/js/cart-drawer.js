/**
 * StrongWine — Slide-out Cart Drawer Controller
 */
class CartDrawerController {
    constructor() {
        this.drawerEl = document.getElementById('cartDrawer');
        this.bsDrawer = this.drawerEl && window.bootstrap ? new bootstrap.Offcanvas(this.drawerEl) : null;
        this.initListeners();
    }

    initListeners() {
        // Bắt sự kiện bấm "Thêm vào giỏ" trên toàn trang
        document.addEventListener('click', (e) => {
            const btn = e.target.closest('.js-add-to-cart');
            if (btn) {
                e.preventDefault();
                const wineId = btn.getAttribute('data-wine-id');
                const quantity = parseInt(btn.getAttribute('data-quantity') || '1', 10);
                this.addToCart(wineId, quantity);
            }
        });
    }

    async addToCart(wineId, quantity = 1) {
        if (!wineId) return;
        try {
            const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
            const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

            const headers = { 'Content-Type': 'application/json' };
            if (token && header) headers[header] = token;

            const res = await fetch('/api/cart/add', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ wineId: parseInt(wineId, 10), quantity: quantity })
            });

            if (res.ok) {
                this.openDrawer();
                this.refreshCartBadge();
            } else {
                console.warn('Lỗi thêm giỏ hàng, chuyển hướng trang login nếu chưa đăng nhập');
                window.location.href = '/login';
            }
        } catch (err) {
            console.error('Lỗi khi gọi API add to cart', err);
        }
    }

    openDrawer() {
        if (!this.bsDrawer && this.drawerEl && window.bootstrap) {
            this.bsDrawer = new bootstrap.Offcanvas(this.drawerEl);
        }
        if (this.bsDrawer) this.bsDrawer.show();
    }

    refreshCartBadge() {
        fetch('/api/cart')
            .then(res => res.json())
            .then(data => {
                const count = data.items ? data.items.reduce((acc, it) => acc + it.quantity, 0) : 0;
                document.querySelectorAll('.js-cart-count').forEach(badge => {
                    badge.textContent = count;
                    badge.classList.add('animate-bounce');
                    setTimeout(() => badge.classList.remove('animate-bounce'), 500);
                });
            })
            .catch(() => {});
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.cartDrawer = new CartDrawerController();
});
