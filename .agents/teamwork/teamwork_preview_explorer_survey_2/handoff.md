# Handoff Report — Frontend UI, Templates & UX Survey (Explorer Survey 2)

## 1. Observation

### A. Template Inventory & Structure
- **Public & Auth Templates** (`src/main/resources/templates`):
  - `home.html` (Lines 1-209): Luxury landing hero ("Maison de Vins"), featured wines, offcanvas cart drawer (`#cartDrawer`), links to `/wines`, `/cart`, `/login`, `/register`.
  - `wine-list.html` (Lines 1-375): Comprehensive multi-filter panel (`#filterForm`), type selects (Red, White, Rose, Sparkling), country, vintage, price slider, instant pill tags, pagination fragment (`fragments/pagination.html`), offcanvas cart drawer.
  - `wine-details.html` (Lines 1-280): Hero product view, tasting notes, grape varieties, stock badges (`stock-badge stock-ok/low/out`), "Add to Cart" button, quantity incrementer.
  - `cart.html` (Lines 1-220): Cart table, item quantity modifiers, subtotal, shipping fee calculation, proceed to `/cart/checkout` button.
  - `login.html`, `register.html`, `forgot-password.html`, `reset-password.html`: Dark luxury glassmorphism auth cards, CSRF tokens (`th:action="@{/login}"`), validation feedback banners.
- **Customer Order & Live Stepper Templates**:
  - `orders.html` (Lines 1-180): Order history cards, status badges, link to `/orders/{id}`.
  - `order-detail.html` (Lines 1-443):
    - **Glowing 5-Step Stepper** (`#orderTimelineStepper`, lines 100-159): 5 distinct stages (1: Đã Đặt Hàng, 2: Đã Thanh Toán, 3: Đang Chuẩn Bị, 4: Đang Giao Hàng, 5: Giao Thành Công) styled with `.timeline-step`, `.step-node`, `.completed`, `.active`, and pulsating CSS glow animations (`@keyframes activeNodeGlow`).
    - **OTP Vault Card** (`#otpVaultCard`, lines 162-186): Displays 6-digit delivery verification code (`#otpCodeDisplay`, e.g., `888999`), copy button (`#btnCopyOtp` calling `copyOtpToClipboard()`), auto-hidden when order status is `COMPLETED`.
    - **Real-Time Live SSE Indicator** (`#sseIndicator`, lines 63-75): Live pill badge with pulsing green indicator (`.sse-pulse-dot` with `@keyframes ssePulse`) showing connection state.
    - **SSE Connection Script** (Lines 295-440): Loads `/js/order-tracking-realtime.js`, initializes `new StrongWineTracking(currentOrderId, { onConnect, onDisconnect, onStatusChange, onShipmentChange })`.
- **Admin Management & Analytics Templates**:
  - `admin-dashboard.html` (Lines 1-320): KPI stat cards (Total Revenue, Total Orders, Active Customers, Low Stock Alerts), Chart.js canvas elements (`#revenueChart`, `#categoryPieChart`), Live Cockpit status pill (`#adminLiveBadge`), live toast notifications (`#adminLiveToast`).
  - `admin-wine-import.html` (Lines 1-240):
    - Excel Drag-and-Drop dropzone (`#excelDropZone`, lines 111-125) with upload icon and dashed border.
    - Binary download button for standard template: `<a href="/api/admin/excel/template" class="btn btn-gold btn-sm">` (line 87).
    - Binary download button for live inventory: `<a href="/api/admin/excel/export/inventory" class="btn btn-outline-secondary btn-sm">` (line 90).
    - Dry-run validation summary table (`#previewContainer`, `#previewTableBody`, `#errorSection`, `#cleanSection`).
    - Final execution button (`#btnExecuteImport`).
- **Shipper Mobile Cockpit Template**:
  - `shipper-dashboard.html` (Lines 1-210):
    - Mobile-first viewport: `<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">` (line 5).
    - Ergonomic shipment cards (`.shipper-card` with status-based border ribbons).
    - One-tap customer calling button (Line 119): `<a th:href="${s.shippingPhone != null and !#strings.isEmpty(s.shippingPhone) ? 'tel:' + s.shippingPhone : '#'}" class="btn btn-outline-success btn-sm flex-fill">`.
    - One-tap Google Maps turn-by-turn routing button (Line 127): `<a th:href="${s.shippingAddress != null and !#strings.isEmpty(s.shippingAddress) ? 'https://www.google.com/maps/search/?api=1&query=' + #strings.replace(s.shippingAddress, ' ', '+') : '#'}" target="_blank" class="btn btn-outline-primary btn-sm flex-fill">`.
    - 6-Digit Auto-Advancing OTP Keypad (Lines 142-166): `.otp-container[data-shipment-id]` containing 6 `.otp-box` inputs, hidden full OTP field (`#hiddenOtp-{id}`), and submit action (`submitOtpForm('{id}')`).

### B. Client Scripts & Third-Party Dependencies
- **Styling & Fonts**:
  - `css/app.css` (2011 lines): Noir & Champagne Gold tokens (`--noir: #0c0a08`, `--gold: #c9a84c`, `--crimson: #8b1a1a`, `--text: #f0e8da`), typography (`Playfair Display`, `Lato`), `.timeline-stepper`, `.otp-vault-card`, `.otp-box-mobile`, `.shipper-card`, `.sse-live-pill`, `.sse-pulse-dot`.
  - Google Fonts CDN: `Playfair Display` (700, 800) and `Lato` (300, 400, 700).
  - Bootstrap 5.3.0 CDN: `bootstrap.min.css` and `bootstrap.bundle.min.js`.
  - FontAwesome 6.5.2 CDN: `all.min.css`.
  - Chart.js CDN: `https://cdn.jsdelivr.net/npm/chart.js` (loaded in `admin-dashboard.html`).
- **Client JavaScript Modules** (`src/main/resources/static/js/`):
  - `order-tracking-realtime.js`: Implements `OrderTrackingRealtime` class (aliased to `window.StrongWineTracking`), connects to `/api/live/orders/${orderId}/stream`, handles `init`, `ping`, `order_status_updated`, `shipment_step_updated`, native Web Audio API chime (sine wave D5 -> A5), exponential backoff reconnection (1s -> 16s).
  - `admin-charts.js`: Renders Chart.js smooth line gradient revenue chart (`#revenueChart`) and doughnut category composition chart (`#categoryPieChart`). Connects SSE to `/api/live/admin/stream`.
  - `admin-excel-import.js`: Drag-and-drop file listener (`dragenter`, `dragover`, `dragleave`, `drop`), client-side extension validation (`.xlsx`, `.xls`), calls dry-run endpoint `POST /api/admin/excel/preview`, displays error row breakdowns, executes final import `POST /api/admin/excel/import`.
  - `shipper-mobile-actions.js`: Implements 6-digit auto-advance input, backspace retreat, 6-digit clipboard paste splitting, Web Audio API chime (triangle wave 440Hz -> 880Hz), connects SSE to `/api/live/shipper/${shipperId}/stream`.
  - `cart-client.js` & `cart-drawer.js`: AJAX cart addition, offcanvas drawer update, badge counter synchronization (`.js-cart-count`).
  - `wine-filters.js` & `wine-filter-drawer.js`: Dynamic query filtering, auto-submit on dropdown change, debounced text search.

### C. Mismatch & Latent Console Error Observations
1. **Admin SSE Endpoint Mismatch**:
   - In `admin-charts.js` line 112: `const adminSse = new EventSource('/api/live/admin/stream');`
   - In `SseNotificationApiController.java` line 26: `@GetMapping(value = "/admin/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)`
   - Impact: Browser console on `/admin` will log an EventSource 404 error when trying to reach `/api/live/admin/stream`.
2. **Shipper SSE Endpoint Mismatch**:
   - In `shipper-mobile-actions.js` line 67: `const sse = new EventSource(`/api/live/shipper/${shipperIdMeta}/stream`);`
   - In `SseNotificationApiController.java`: No `@GetMapping` exists for `/shipper/{id}/stream` (only `streamOrderEvents` and `streamAdminNotifications` are exposed, despite `SseNotificationService` having `subscribeShipper(Long)`).
   - Impact: If a shipper logs in and `meta[name="shipper-id"]` is present, the browser will log an EventSource 404 error.
3. **Customer Order SSE Endpoint Match**:
   - `order-tracking-realtime.js`: `/api/live/orders/${this.orderId}/stream` matches `SseNotificationApiController.streamOrderEvents` perfectly.

---

## 2. Logic Chain

1. **Requirement R1 (Anonymous User Surface)**:
   - Observation: `home.html`, `wine-list.html`, `wine-details.html`, `cart.html`, `login.html`, `register.html`, and `forgot-password.html` all exist, link valid CSS (`/css/app.css`), standard CDNs (Bootstrap 5.3.0, FontAwesome 6.5.2, Google Fonts), and standard viewport tags (`width=device-width, initial-scale=1.0`).
   - Deduction: Anonymous navigation across public pages has complete template coverage with zero missing stylesheet references.

2. **Requirement R2 (Customer Real-Time Stepper & OTP Vault)**:
   - Observation: `order-detail.html` features the 5-step stepper `#orderTimelineStepper` (`#step-1` through `#step-5`), CSS glows, and dynamic state-binding based on `order.status` and `shipment.status`.
   - Observation: `#otpVaultCard` renders `#otpCodeDisplay` (`shipment.otpCode`) with a clipboard copy helper and hides automatically upon completion.
   - Observation: `OrderTrackingRealtime` connects to `/api/live/orders/${orderId}/stream`, which matches `@GetMapping("/orders/{orderId}/stream")` in `SseNotificationApiController.java`.
   - Deduction: Customer order tracking, visual stepper progression, and OTP vault rendering are fully wired.

3. **Requirement R3 (Admin Analytics & Excel Logistics)**:
   - Observation: `admin-dashboard.html` defines `#revenueChart` and `#categoryPieChart` canvas elements, initialized by `admin-charts.js` using Chart.js.
   - Observation: `admin-wine-import.html` provides `#excelDropZone` supported by `admin-excel-import.js`, with direct download links `/api/admin/excel/template` and `/api/admin/excel/export/inventory`.
   - Deduction: Admin analytics and Excel logistics interfaces satisfy R3. However, `admin-charts.js` calls `/api/live/admin/stream` instead of `/api/live/admin/notifications`, which risks a browser console 404 error.

4. **Requirement R4 (Shipper Mobile-First Cockpit)**:
   - Observation: `shipper-dashboard.html` enforces mobile viewport (`maximum-scale=1.0, user-scalable=no`), renders `tel:` dialer links, Google Maps URL generation (`https://www.google.com/maps/search/?api=1&query=...`), and 6-digit OTP input boxes.
   - Observation: `shipper-mobile-actions.js` provides auto-focus advance, backspace handling, clipboard pasting, and form submission.
   - Deduction: Shipper mobile cockpit satisfies R4 ergonomics under mobile emulation (390x844). However, `shipper-mobile-actions.js` points to `/api/live/shipper/{id}/stream`, which lacks a controller mapping in `SseNotificationApiController`.

---

## 3. Caveats

1. **Read-Only Investigation**: In compliance with Explorer Archetype constraints, no source code or templates were modified.
2. **Missing Endpoint Aliases in Backend**: As discovered, `SseNotificationApiController.java` exposes `/api/live/admin/notifications`, while frontend script `admin-charts.js` requests `/api/live/admin/stream`. Similarly, `/api/live/shipper/{id}/stream` is missing in the controller. These do not crash the page (wrapped in `try/catch`), but they will trigger console 404 warnings during DevTools protocol inspection.
3. **External CDN Availability**: Templates load Bootstrap, FontAwesome, and Chart.js from public CDNs (`cdn.jsdelivr.net`, `cdnjs.cloudflare.com`). In an isolated offline environment without Internet access, these CDNs must be reachable or cached.

---

## 4. Conclusion

The StrongWine frontend architecture complies with the visual, structural, and behavioral standards specified in `AGENTS.md` ("Maison de Vins" dark luxury aesthetic):
1. **Public/Anonymous**: Complete template coverage for Home, Catalog, Details, Cart, and Auth with responsive navigation and dark luxury design tokens.
2. **Customer Journey**: 5-step glowing stepper, OTP vault card with copy button, and stable SSE connection stream wired to `/api/live/orders/{orderId}/stream`.
3. **Admin Dashboard**: Chart.js revenue & category visualizations, drag-and-drop Excel dropzone with dry-run preview, and Excel template/export binary download buttons.
4. **Shipper Mobile Cockpit**: Optimized for 390x844 mobile viewport with 1-tap `tel:` calling, 1-tap Google Maps directions, and 6-digit auto-advancing OTP keypad.
5. **Key Remediation Recommendation for Verifier/Coder**: Align the SSE endpoints in `SseNotificationApiController.java` (add `/admin/stream` and `/shipper/{shipperId}/stream` mappings) or update the client scripts (`admin-charts.js` and `shipper-mobile-actions.js`) to guarantee 100% zero-console-error compliance under Chrome DevTools Protocol inspection.

---

## 5. Verification Method

To independently verify these findings:
1. **Inspect CSS tokens & Stepper/OTP classes**:
   ```powershell
   Select-String -Path "D:\Projects\strongwine_2\Wine-website\src\main\resources\static\css\app.css" -Pattern "timeline-stepper|otp-vault-card|otp-box-mobile|sse-live-pill"
   ```
2. **Inspect Stepper and OTP in `order-detail.html`**:
   ```powershell
   Select-String -Path "D:\Projects\strongwine_2\Wine-website\src\main\resources\templates\order-detail.html" -Pattern "orderTimelineStepper|otpVaultCard|StrongWineTracking"
   ```
3. **Inspect Admin Excel Dropzone and Download Buttons**:
   ```powershell
   Select-String -Path "D:\Projects\strongwine_2\Wine-website\src\main\resources\templates\admin-wine-import.html" -Pattern "excelDropZone|/api/admin/excel/template|/api/admin/excel/export/inventory"
   ```
4. **Inspect Shipper Mobile Actions & Dial/Map links**:
   ```powershell
   Select-String -Path "D:\Projects\strongwine_2\Wine-website\src\main\resources\templates\shipper-dashboard.html" -Pattern "tel:|google.com/maps|otp-container"
   ```
5. **Inspect SSE Controller Mappings vs Client Endpoints**:
   ```powershell
   Select-String -Path "D:\Projects\strongwine_2\Wine-website\src\main\java\com\strongwine\strongwine\controller\api\SseNotificationApiController.java" -Pattern "@GetMapping"
   Select-String -Path "D:\Projects\strongwine_2\Wine-website\src\main\resources\static\js\admin-charts.js" -Pattern "EventSource"
   Select-String -Path "D:\Projects\strongwine_2\Wine-website\src\main\resources\static\js\shipper-mobile-actions.js" -Pattern "EventSource"
   ```
