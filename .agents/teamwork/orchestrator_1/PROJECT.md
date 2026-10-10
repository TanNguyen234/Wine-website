# Project: StrongWine E2E Browser Verification

## Architecture
- **Application Core**: Spring Boot 3 / Java 21 web application on port 8080.
- **Persistence**: Microsoft SQL Server (`winestore_2`), Hibernate validate mode (`spring.jpa.hibernate.ddl-auto=validate`).
- **Security & RBAC**: Spring Security 6 with Form Login at `/login`.
  - Roles: `ROLE_USER`, `ROLE_ADMIN`, `ROLE_SHIPPER`.
  - Public routes: `/home`, `/wines`, `/wines/{id}`, `/cart`, `/login`, `/register`, `/forgot-password`.
  - Protected routes redirect unauthenticated visitors to `/login`.
  - Access violations yield HTTP 403 Forbidden.
- **Real-Time Communication**: Server-Sent Events (SSE) via `SseEmitter` (`/api/live/orders/{orderId}/stream`, `/api/live/admin/notifications`, `/api/live/admin/stream`, `/api/live/shipper/{shipperId}/stream`).
- **Test Automation Harness**: Python Playwright 1.61.0 + Pytest + Chrome DevTools Protocol (CDP) raw session binding with headless Google Chrome (v154).
  - Traps zero JS console errors via CDP `Console.enable` and `Runtime.exceptionThrown`.
  - Captures full visual page screenshots into `test-results/screenshots/`.
  - Emulates mobile viewports (390x844) for shipper cockpit testing.
  - Verifies binary `.xlsx` downloads using `openpyxl`.

## Feature Inventory
| # | Feature | Description | Milestone | Status |
|---|---------|-------------|-----------|--------|
| 1 | Public Anonymous Surface | Unauthenticated access to `/home`, `/wines`, `/wines/{id}`, `/cart`, `/login`, `/register`, `/forgot-password` with 200 OK and 0 console errors | M1 | DONE |
| 2 | Unauthenticated Boundary Redirect | Accessing `/orders`, `/admin`, `/shipper/dashboard` without session redirects 302 to `/login` | M1 | DONE |
| 3 | Customer Authentication & History | Customer login (`demo`/`demo123`), session establishment, redirect to `/home`, order history display on `/orders` | M2 | DONE |
| 4 | Glowing 5-Step Stepper & OTP Vault | Visual rendering of `#orderTimelineStepper` (5 steps) and `#otpVaultCard` on `/orders/{id}` with CSS glow animations | M2 | DONE |
| 5 | Real-Time SSE Stream Stability | Order tracking SSE connection on `/api/live/orders/{id}/stream` with pulsing live indicator and zero connection errors | M2 | DONE |
| 6 | Customer Privacy Order Boundary | Attempting to view another user's order ID on `/orders/{id}` safely redirects 302 to `/orders` | M2 | DONE |
| 7 | Customer RBAC Security Barrier | Authenticated customer session accessing `/admin` or `/shipper/dashboard` returns HTTP 403 Forbidden | M2 | DONE |
| 8 | Admin Authentication & Dashboard KPIs | Administrator login (`admin`/`admin123`), redirect to `/admin`, KPI cards (Revenue, Orders, Customers, Low Stock) | M3 | DONE |
| 9 | Chart.js Analytics Visualization | Chart.js canvas elements (`#revenueChart`, `#categoryPieChart`) rendering without JS errors | M3 | DONE |
| 10 | Excel Drag-and-Drop Dropzone | Interactive `#excelDropZone` on `/admin/wines/import` with file validation | M3 | DONE |
| 11 | Excel Binary Logistics Files | Binary `.xlsx` downloads for standard template (`/api/admin/excel/template`) and inventory export (`/api/admin/excel/export/inventory`) validated with openpyxl | M3 | DONE |
| 12 | Shipper Mobile Emulation & Login | Driver login (`shipper.ops.hcm`/`admin123`) redirect to `/shipper/dashboard` under mobile viewport (390x844) | M4 | DONE |
| 13 | Shipper 1-Tap Calling & Maps Routing | Driver ergonomic action links: `tel:` phone call and Google Maps directions link | M4 | DONE |
| 14 | 6-Digit Auto-Advancing OTP Keypad | Interactive 6-box OTP entry (`.otp-box-mobile`) on `/shipper/dashboard` with auto-focus advance and backspace retreat | M4 | DONE |
| 15 | Shipper RBAC Security Barrier | Authenticated shipper session accessing `/admin` returns HTTP 403 Forbidden | M4 | DONE |
| 16 | Full E2E & Visual Hardening Gate | Automated execution of 100% of CDP test cases, screenshot verification, zero console error gate, forensic audit clean | M5 | DONE |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Anonymous Public Surface & Boundaries | Verify public pages (0 errors) & unauthenticated redirects to `/login` | none | DONE |
| M2 | Customer Journey & Privacy Boundaries | Verify demo login, order history, 5-step stepper, OTP vault, SSE stream, privacy redirect, 403 on admin/shipper | M1 | DONE |
| M3 | Admin Analytics & Excel Logistics | Verify admin login, KPI cards, Chart.js canvas, Excel dropzone, template & inventory .xlsx exports | M1 | DONE |
| M4 | Shipper Mobile-First Cockpit | Verify mobile emulation (390x844), tel/maps actions, 6-digit OTP keypad, 403 on admin | M1 | DONE |
| M5 | Full Suite E2E & Visual Verification Gate | Full test run across all roles, visual screenshot artifacts, zero console exceptions gate, Forensic Audit | M2, M3, M4 | DONE |

## Interface Contracts
### Client ↔ Server Security Contract
- `POST /login` with `username` and `password` parameters.
- Post-login 302 redirects:
  - `ROLE_ADMIN` -> `/admin`
  - `ROLE_SHIPPER` -> `/shipper/dashboard`
  - `ROLE_USER` / default -> `/home`
- Unauthenticated access to `/orders/**`, `/admin/**`, `/shipper/**` returns 302 redirect to `/login`.
- Unauthorized role access returns HTTP 403 Forbidden.

### Customer Privacy Contract
- `GET /orders/{id}`:
  - If `order.user.id == currentUser.id` -> 200 OK rendering `order-detail.html`.
  - If `order.user.id != currentUser.id` -> 302 Redirect to `/orders`.

### Excel Binary Contract
- `GET /api/admin/excel/template` -> Content-Type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, binary header `PK\x03\x04`.
- `GET /api/admin/excel/export/inventory` -> Content-Type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, binary header `PK\x03\x04`.

### Shipper Mobile Ergonomics Contract
- Viewport: `390x844`, `device_scale_factor=3`, `is_mobile=True`.
- Element `a[href^="tel:"]` must be visible, tap target ≥ 44px height (measured 61.22px).
- Element `a[href*="google.com/maps"]` must be visible, tap target ≥ 44px height (measured 61.22px).
- 6 inputs with `.otp-box-mobile` or `.otp-box` handling single-digit entry.

## Code Layout
- Test Automation Suite: `e2e_cdp/`
  - `conftest.py`: Playwright & CDP fixtures, raw CDP console listener, screenshot capturer.
  - `test_r1_anonymous.py`: Public routes and unauthenticated redirect tests.
  - `test_r2_customer.py`: Customer authentication, glowing stepper, OTP vault, SSE stream, privacy redirect, 403 checks.
  - `test_r3_admin.py`: Admin dashboard, Chart.js canvas, dropzone, Excel export validation.
  - `test_r4_shipper.py`: Mobile viewport (390x844), tel/maps links, OTP auto-advance keypad, 403 check.
  - `utils/excel_validator.py`: Binary Excel validation using openpyxl.
- Artifacts:
  - Screenshots: `test-results/screenshots/` (23 verified PNG screenshots)
  - Reports: `test-results/`
