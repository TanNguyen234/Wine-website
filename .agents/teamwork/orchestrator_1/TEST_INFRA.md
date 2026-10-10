# E2E Test Infra: StrongWine Multi-Role Platform

## Test Philosophy
- **Opaque-box & Requirement-driven**: Tests interact via Chrome DevTools Protocol (CDP) and standard HTTP requests exactly as real users, shippers, and admins do.
- **Methodology**: Systematic 4-tier methodology (Category-Partition, Boundary Value Analysis, Cross-Feature Pairwise, and Real-World Workload Scenarios).
- **Zero Console Error Mandate**: Every page load must trap all CDP `Console.messageAdded` and `Runtime.exceptionThrown` events, failing if any uncaught JavaScript error or exception is observed.
- **Visual Artifacts**: Every critical transition captures full-page and element screenshots to `test-results/screenshots/`.

## Feature Inventory & Test Coverage
| # | Feature | Requirement | Tier 1 (Coverage) | Tier 2 (Boundary) | Tier 3 (Cross-Role) | Tier 4 (Scenario) |
|---|---------|-------------|:-----------------:|:-----------------:|:-------------------:|:-----------------:|
| 1 | Public Surface (/home, /wines, /wines/{id}, /cart, /login, /register, /forgot-password) | ORIGINAL_REQUEST §R1 | 5+ | 5+ | ✓ | ✓ |
| 2 | Unauthenticated Boundary Redirects (/orders, /admin, /shipper/dashboard) | ORIGINAL_REQUEST §R1 | 5+ | 5+ | ✓ | ✓ |
| 3 | Customer Authentication & History (`demo`) | ORIGINAL_REQUEST §R2 | 5+ | 5+ | ✓ | ✓ |
| 4 | Glowing 5-Step Stepper & OTP Vault | ORIGINAL_REQUEST §R2 | 5+ | 5+ | ✓ | ✓ |
| 5 | Real-Time SSE Stream Stability | ORIGINAL_REQUEST §R2 | 5+ | 5+ | ✓ | ✓ |
| 6 | Customer Privacy Order Isolation (redirect to /orders) | ORIGINAL_REQUEST §R2 | 5+ | 5+ | ✓ | ✓ |
| 7 | Customer RBAC Access Denial (403 on admin/shipper) | ORIGINAL_REQUEST §R2 | 5+ | 5+ | ✓ | ✓ |
| 8 | Admin Authentication & Dashboard KPIs (`admin`) | ORIGINAL_REQUEST §R3 | 5+ | 5+ | ✓ | ✓ |
| 9 | Chart.js Canvas Rendering | ORIGINAL_REQUEST §R3 | 5+ | 5+ | ✓ | ✓ |
| 10 | Excel Drag-and-Drop Dropzone | ORIGINAL_REQUEST §R3 | 5+ | 5+ | ✓ | ✓ |
| 11 | Excel Binary Template & Inventory Export | ORIGINAL_REQUEST §R3 | 5+ | 5+ | ✓ | ✓ |
| 12 | Shipper Mobile Emulation & Login (`shipper.ops.hcm`) | ORIGINAL_REQUEST §R4 | 5+ | 5+ | ✓ | ✓ |
| 13 | 1-Tap Calling & Google Maps Turn-by-Turn | ORIGINAL_REQUEST §R4 | 5+ | 5+ | ✓ | ✓ |
| 14 | 6-Digit Auto-Advancing OTP Keypad | ORIGINAL_REQUEST §R4 | 5+ | 5+ | ✓ | ✓ |
| 15 | Shipper RBAC Access Denial (403 on admin) | ORIGINAL_REQUEST §R4 | 5+ | 5+ | ✓ | ✓ |

## Test Architecture
- **Harness**: Python Playwright (`playwright.sync_api`) + `pytest`.
- **Browser**: Google Chrome `154.0.8037.98` launched with native CDP session (`context.new_cdp_session(page)`).
- **Domains Enabled**: `Console.enable`, `Runtime.enable`, `Network.enable`, `Emulation.setDeviceMetricsOverride`.
- **Target URL**: `http://localhost:8080`.
- **Execution Command**:
  ```bash
  pytest e2e_cdp/ -v --tb=short
  ```
- **Exit Code Semantics**: `0` for all tests passing; non-zero if any test fails, assertion fails, or console error is trapped.

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Expected Outcome |
|---|----------|--------------------|------------------|
| 1 | Full Guest-to-Customer Discovery Flow | F1, F2, F3 | Guest browses catalog, adds to cart, redirected to login on protected order flow |
| 2 | Customer Real-Time Order Tracking & Vault Verification | F3, F4, F5, F6 | Customer checks order detail, verifies 5-step glowing stepper, copies OTP from vault, confirms SSE live connection |
| 3 | Cross-Tenant Privacy & Boundary Penetration | F2, F6, F7, F15 | Tampering with order ID redirects safely to `/orders`; customer and shipper probing `/admin` strictly blocked with 403 |
| 4 | Logistics Executive Inventory Audit & Export | F8, F9, F10, F11 | Admin views live KPIs and charts, verifies dropzone, downloads and parses `.xlsx` template & inventory export with openpyxl |
| 5 | Delivery Driver Mobile Delivery Cockpit Flow | F12, F13, F14, F15 | Driver logs in on 390x844 mobile screen, taps call link, tests 6-digit auto-advancing OTP keypad |

## Coverage Thresholds
- Tier 1: ≥5 per feature across public, customer, admin, shipper roles.
- Tier 2: Boundary value and error rejection test cases for routes, inputs, and authentication states.
- Tier 3: Pairwise cross-role security boundaries (Anonymous, Customer, Admin, Shipper).
- Tier 4: 5 comprehensive end-to-end multi-role real-world scenarios.
