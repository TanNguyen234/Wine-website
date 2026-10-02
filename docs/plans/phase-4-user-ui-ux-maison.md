# Phase 4: User Portal Complete UI/UX Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thiết kế lại toàn bộ giao diện và trải nghiệm người dùng (Khách Hàng) theo phong cách **Maison de Vins** (Hầm rượu quý tộc Pháp): Bảng màu nhung đen than, đỏ vang Bordeaux và vàng champagne hoàng gia; biểu tượng SVG thủ công (loại bỏ hoàn toàn AI slop); bộ lọc đa tiêu chí dạng drawer trượt; modal xem nhanh rượu (Quick View); giỏ hàng trượt (Cart Drawer) cập nhật số lượng tức thời qua AJAX; và trang theo dõi đơn hàng thời gian thực chuẩn Shopee.

**Architecture:** Sử dụng Thymeleaf SSR kết hợp Modern CSS Design Tokens, Semantic HTML5, Vanilla JavaScript (ES6+), tích hợp các micro-interactions mượt mà, và kết nối trực tiếp với Server-Sent Events (SSE).

**Tech Stack:** Thymeleaf, HTML5, CSS3 Variables, JavaScript ES6, Bootstrap 5 (tùy biến tối đa), FontAwesome 6, Google Fonts (`Playfair Display`, `Plus Jakarta Sans`).

## Global Constraints
- Zero AI Slop: Không dùng icon màu mè rẻ tiền, không dùng gradient tím generic, không dùng layout cẩu thả.
- Database Preservation: Giữ nguyên 100% database; không sửa đổi bảng/cột.
- Ergonomic UX: Mọi thao tác thêm giỏ hàng, đổi số lượng, lọc giá phải phản hồi dưới 100ms mà không reload trang.
- Per-Task Real Test: Mỗi task phải có kiểm tra cú pháp HTML/JS và kiểm thử giao diện thực tế trên trình duyệt (cả Desktop và Mobile).

---

### Task 4.1: Nâng Cấp Hệ Thống Design Tokens & Bộ Biểu Tượng SVG Thủ Công Trong `app.css`

**Files:**
- Modify: `src/main/resources/static/css/app.css:1-120`
- Test: Kiểm tra hiển thị style guide bằng trình duyệt.

**Interfaces:**
- Consumes: CSS Custom Properties.
- Produces: Hệ thống biến màu `--noir`, `--crimson`, `--gold`, typographic hierarchy, và các utility class cho Maison de Vins.

- [ ] **Step 1: Cập nhật Design Tokens và Biểu tượng SVG trong `app.css`**

Chỉnh sửa phần đầu của [src/main/resources/static/css/app.css](file:///D:/Projects/strongwine_2/Wine-website/src/main/resources/static/css/app.css):
```css
/* ============================================================
   STRONGWINE — Maison de Vins (Luxury Dark Wine Boutique)
   Design System & Custom SVG Tokens
   ============================================================ */

:root {
  /* ── Hầm rượu đêm quý tộc (Dark Cellar Palette) ────────────── */
  --noir:           #0a0806;
  --noir-soft:      #120e0a;
  --surface:        #18120d;
  --surface-hover:  #221912;
  --surface-border: rgba(201, 168, 76, 0.16);
  --surface-glow:   rgba(201, 168, 76, 0.22);

  /* ── Đỏ Vang Bordeaux (Crimson & Wine Accents) ────────────── */
  --crimson-dark:   #4a0d0d;
  --crimson-prime:  #8b1a1a;
  --crimson-soft:   #ad2b2b;
  --crimson-glow:   rgba(139, 26, 26, 0.35);

  /* ── Vàng Sâm Panh Hoàng Gia (Champagne Gold) ─────────────── */
  --gold-prime:     #c9a84c;
  --gold-light:     #e4c67d;
  --gold-dim:       #8a7032;
  --gold-border:    rgba(201, 168, 76, 0.28);
  --gold-shadow:    0 8px 32px rgba(201, 168, 76, 0.15);

  /* ── Typography Ivory & Sand ──────────────────────────────── */
  --text-ivory:     #f7f2e7;
  --text-soft:      #c7b9a3;
  --text-muted:     #8a7a65;
  --text-faint:     #524535;

  /* ── Radii & Transitions ──────────────────────────────────── */
  --r-sm:           6px;
  --r-md:           12px;
  --r-lg:           18px;
  --r-xl:           26px;
  --ease-out:       cubic-bezier(0.16, 1, 0.3, 1);
  --duration-fast:  0.15s;
  --duration-med:   0.3s;
}

/* ── Typography Scale ───────────────────────────────────────── */
h1, h2, h3, .font-serif {
  font-family: 'Playfair Display', Georgia, serif;
  font-weight: 700;
  letter-spacing: -0.01em;
}

body {
  font-family: 'Plus Jakarta Sans', 'Lato', -apple-system, sans-serif;
  background-color: var(--noir);
  color: var(--text-ivory);
  line-height: 1.6;
}

/* ── SVG Icons Tailored for Wine ────────────────────────────── */
.icon-wine-glass {
  display: inline-block;
  width: 1.25rem;
  height: 1.25rem;
  background-color: currentColor;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'%3E%3Cpath d='M8 22h8M12 15v7M5 3h14l-2 9a5 5 0 0 1-10 0L5 3z'/%3E%3C/svg%3E") no-repeat center / contain;
}

.icon-wine-bottle {
  display: inline-block;
  width: 1.25rem;
  height: 1.25rem;
  background-color: currentColor;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'%3E%3Cpath d='M10 2h4v4l2 3v13H8V9l2-3V2zM10 2h4'/%3E%3C/svg%3E") no-repeat center / contain;
}
```

- [ ] **Step 2: Live Verification**

Mở file `app.css` và kiểm tra không có lỗi cú pháp CSS:
```powershell
Get-Content -TotalCount 50 src/main/resources/static/css/app.css
```
Expected: Định nghĩa biến `--noir`, `--crimson-prime`, `--gold-prime` hiện diện đầy đủ.

- [ ] **Step 3: Commit**

```powershell
git add src/main/resources/static/css/app.css
git commit -m "style: establish maison de vins design tokens and bespoke wine svg icons"
```

---

### Task 4.2: Tái Thiết Kế Trang Danh Mục Rượu & Bộ Lọc Đa Tiêu Chí Động Trong `wine-list.html`

**Files:**
- Modify: `src/main/resources/templates/wine-list.html`
- Create: `src/main/resources/static/js/wine-filter-drawer.js`
- Test: Kiểm tra cú pháp Thymeleaf và kiểm thử bộ lọc.

**Interfaces:**
- Consumes: `/wines` với `WineSearchCriteria` (keyword, types, minPrice, maxPrice, countries).
- Produces: Giao diện lọc đa tiêu chí, thanh trượt giá, và nút Quick View.

- [ ] **Step 1: Viết script xử lý Filter Drawer & Quick View Modal (`wine-filter-drawer.js`)**

Tạo `src/main/resources/static/js/wine-filter-drawer.js`:
```javascript
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
                modal.querySelector('.modal-wine-name').textContent = name;
                modal.querySelector('.modal-wine-price').textContent = price;
                modal.querySelector('.modal-wine-type').textContent = type;
                modal.querySelector('.modal-wine-year').textContent = year;
                modal.querySelector('.modal-wine-country').textContent = country || 'Đang cập nhật';
                modal.querySelector('.modal-wine-desc').textContent = desc || 'Chưa có mô tả nếm thử chi tiết.';
                modal.querySelector('.modal-wine-img').src = img;
                modal.querySelector('.modal-add-to-cart-btn').setAttribute('data-wine-id', id);

                const bsModal = new bootstrap.Modal(modal);
                bsModal.show();
            }
        });
    });
});
```

- [ ] **Step 2: Nâng cấp `wine-list.html` bổ sung Bộ Lọc Đa Tiêu Chí và Quick View Modal**

Cập nhật giao diện trong [src/main/resources/templates/wine-list.html](file:///D:/Projects/strongwine_2/Wine-website/src/main/resources/templates/wine-list.html):
- Thêm sidebar bộ lọc: Lọc loại rượu (Red, White, Rose, Sparkling) bằng checkbox pill tag mạ vàng; Lọc khoảng giá; Lọc xuất xứ (Pháp, Ý, Chile, Tây Ban Nha).
- Bổ sung nút "Xem Nhanh" trên mỗi Card sản phẩm kèm các data attribute.
- Đặt markup `quickViewModal` ở cuối trang.

- [ ] **Step 3: Live Verification**

Chạy compile để xác nhận Thymeleaf template hợp lệ:
```powershell
./mvnw compile -DskipTests
```
Expected output: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```powershell
git add src/main/resources/templates/wine-list.html src/main/resources/static/js/wine-filter-drawer.js
git commit -m "feat: redesign wine catalog with dynamic multi-criteria filter and quick view modal"
```

---

### Task 4.3: Xây Dựng Ngăn Kéo Giỏ Hàng Trượt (Cart Drawer) Cập Nhật Tức Thời Bằng AJAX

**Files:**
- Create: `src/main/resources/static/js/cart-drawer.js`
- Modify: `src/main/resources/templates/home.html` (chèn Cart Drawer markup)
- Modify: `src/main/resources/templates/wine-list.html` (chèn Cart Drawer markup)
- Test: Kiểm tra AJAX Add-to-cart và tính lại tổng tiền.

**Interfaces:**
- Consumes: `/api/cart/add`, `/api/cart/items/{id}`, `/api/cart`.
- Produces: Ngăn kéo giỏ hàng trượt từ bên phải (Offcanvas), badge số lượng nhảy animation.

- [ ] **Step 1: Viết script điều khiển Cart Drawer `cart-drawer.js`**

Tạo `src/main/resources/static/js/cart-drawer.js`:
```javascript
/**
 * StrongWine — Slide-out Cart Drawer Controller
 */
class CartDrawerController {
    constructor() {
        this.drawerEl = document.getElementById('cartDrawer');
        this.bsDrawer = this.drawerEl ? new bootstrap.Offcanvas(this.drawerEl) : null;
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
        try {
            const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
            const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

            const headers = { 'Content-Type': 'application/json' };
            if (token && header) headers[header] = token;

            const res = await fetch('/api/cart/add', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ wineId: wineId, quantity: quantity })
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
```

- [ ] **Step 2: Live Verification**

Kiểm tra biên dịch dự án:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```powershell
git add src/main/resources/static/js/cart-drawer.js
git commit -m "feat: implement interactive slide-out cart drawer with instant ajax sync"
```

---

### Task 4.4: Thiết Kế Lại Trang Theo Dõi Đơn Hàng `order-detail.html` Tích Hợp SSE Real-Time

**Files:**
- Modify: `src/main/resources/templates/order-detail.html`
- Test: Kiểm tra hiển thị Timeline 5 bước và mã OTP an toàn.

**Interfaces:**
- Consumes: `/orders/{id}`, `order-tracking-realtime.js`, SSE Stream `/api/live/orders/{orderId}/stream`.
- Produces: Timeline phát sáng 5 bước, card hiển thị mã OTP với nút sao chép nhanh, và thông tin liên hệ Shipper.

- [ ] **Step 1: Nâng cấp `order-detail.html`**

Cập nhật [src/main/resources/templates/order-detail.html](file:///D:/Projects/strongwine_2/Wine-website/src/main/resources/templates/order-detail.html):
- Thêm thước đo tiến trình 5 bước: `1. Đã Đặt Hàng` -> `2. Đã Thanh Toán` -> `3. Đang Chuẩn Bị` -> `4. Đang Giao Hàng` -> `5. Giao Thành Công`.
- Khối hiển thị mã OTP giao hàng bảo mật (chỉ hiện khi đơn chuyển sang `DELIVERING`): Card viền vàng sáng bóng, mã 6 số cỡ lớn kèm nút sao chép `copyToClipboard()`.
- Nhúng script `order-tracking-realtime.js`: Tự động lắng nghe sự kiện `order_status_updated` và `shipment_step_updated` để dịch chuyển bước timeline và đổi màu trạng thái theo thời gian thực mà không cần F5.

- [ ] **Step 2: Live Verification**

Kiểm tra biên dịch:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```powershell
git add src/main/resources/templates/order-detail.html
git commit -m "feat: redesign order tracking with glowing 5-step timeline and sse live stream"
```
