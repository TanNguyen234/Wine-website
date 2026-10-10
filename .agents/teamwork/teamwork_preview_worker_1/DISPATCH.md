# DISPATCH — Worker 1: CDP Test Suite Implementation & Verification

## Identity
- Role: Implementation & Test Automation Worker
- Type: teamwork_preview_worker
- Working directory: D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_worker_1

## Authoritative Inputs
- Read `D:\Projects\strongwine_2\Wine-website\.agents\teamwork\ORIGINAL_REQUEST.md`
- Read `D:\Projects\strongwine_2\Wine-website\AGENTS.md`
- Read `D:\Projects\strongwine_2\Wine-website\.agents\teamwork\orchestrator_1\PROJECT.md`
- Read `D:\Projects\strongwine_2\Wine-website\.agents\teamwork\orchestrator_1\TEST_INFRA.md`
- Read Explorer reports:
  - `D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_explorer_survey_1\handoff.md`
  - `D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_explorer_survey_2\handoff.md`
  - `D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_explorer_survey_3\handoff.md`

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Objectives & Work Items
1. **Endpoint Alignment for Zero Console Errors**:
   - In `SseNotificationApiController.java`:
     - Map `/api/live/admin/stream` as an alias alongside `/api/live/admin/notifications` so `admin-charts.js` connects cleanly without 404.
     - Add `@GetMapping(value = "/shipper/{shipperId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)` calling `sseNotificationService.subscribeShipper(shipperId)` so `shipper-mobile-actions.js` connects cleanly without 404.
   - Verify compilation with `.\mvnw.cmd compile -DskipTests`. (Ensure strict database integrity - NO DDL modifications).
2. **Build Chrome DevTools Protocol Test Suite in `e2e_cdp/`**:
   - `e2e_cdp/conftest.py`:
     - Fixture launching Google Chrome (`C:\Program Files\Google\Chrome\Application\chrome.exe`) headless.
     - Creates raw CDP session via `context.new_cdp_session(page)`.
     - Enables `Console.enable` and `Runtime.enable`. Attaches listeners to collect any error console messages (`Console.messageAdded` where level='error') and uncaught exceptions (`Runtime.exceptionThrown`).
     - Helper to assert zero console errors on every page navigation.
     - Helper to capture screenshots into `test-results/screenshots/<role>_<test_name>.png`.
   - `e2e_cdp/test_r1_anonymous.py` (R1):
     - Test unauthenticated visitors access `/home`, `/wines`, `/wines/1` (or valid wine ID), `/cart`, `/login`, `/register`, `/forgot-password` with HTTP 200 and zero console errors.
     - Test unauthenticated navigation to `/orders`, `/admin`, `/shipper/dashboard` redirects strictly to `/login`.
   - `e2e_cdp/test_r2_customer.py` (R2):
     - Authenticate customer `demo` / `demo123`, verify redirect to `/home`.
     - Verify order history on `/orders`.
     - Verify real-time 5-step glowing stepper on `/orders/{id}` (`#orderTimelineStepper` nodes, CSS glowing animation).
     - Verify OTP vault visibility (`#otpVaultCard` displaying 6-digit code).
     - Verify SSE stream stability on `/api/live/orders/{orderId}/stream`.
     - Verify attempting to view an order belonging to another user safely redirects to `/orders`.
     - Verify accessing `/admin` or `/shipper/dashboard` returns HTTP 403 Forbidden.
   - `e2e_cdp/test_r3_admin.py` (R3):
     - Authenticate administrator `admin` / `admin123`, verify redirect to `/admin`.
     - Verify KPI metric cards (Revenue, Orders, Customers, Low Stock).
     - Verify Chart.js canvas elements (`#revenueChart`, `#categoryPieChart`) render without console errors.
     - Verify Excel import drag-and-drop dropzone on `/admin/wines/import`.
     - Verify binary download of standard template (`/api/admin/excel/template`) and inventory export (`/api/admin/excel/export/inventory`) using `openpyxl` to assert `.xlsx` binary structure and sheets.
   - `e2e_cdp/test_r4_shipper.py` (R4):
     - Authenticate driver `shipper.ops.hcm` / `admin123` under mobile emulation (390x844 viewport, touch enabled, mobile=True).
     - Verify redirect to `/shipper/dashboard`.
     - Verify one-tap calling (`tel:`) link.
     - Verify Google Maps routing link.
     - Verify 6-digit auto-advancing OTP keypad (`.otp-box-mobile`). Test keyboard input advancing across digits.
     - Verify strict HTTP 403 Forbidden prevention on `/admin`.
3. **Execute Full Suite**:
   - Run `pytest e2e_cdp/ -v --tb=short`.
   - Ensure 100% of tests pass and all screenshots are captured.
4. **Handoff Report**:
   - Write full report with execution commands, test logs, pass/fail counts, list of screenshots, and git status to `D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_worker_1\handoff.md`.


## 2026-10-10T05:59:07Z
From: fdfe066e-c4f2-47a1-8906-3aba021ff9a7
Message:
You are Worker 1. Read D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_worker_1\DISPATCH.md, D:\Projects\strongwine_2\Wine-website\.agents\teamwork\ORIGINAL_REQUEST.md, D:\Projects\strongwine_2\Wine-website\AGENTS.md, and the Explorer reports in D:\Projects\strongwine_2\Wine-website\.agents\teamwork\.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your task is to:
1. Align SSE controller endpoints in SseNotificationApiController.java so zero console 404 errors occur on Admin and Shipper pages. Verify compilation with .\mvnw.cmd compile -DskipTests. (STRICT DATABASE INTEGRITY: No DDL changes).
2. Build the complete Chrome DevTools Protocol (CDP) automated test suite in e2e_cdp/ with conftest.py, test_r1_anonymous.py, test_r2_customer.py, test_r3_admin.py, and test_r4_shipper.py using Python Playwright, Pytest, and Chrome 154.
3. Execute the full test suite with pytest e2e_cdp/ -v --tb=short and capture screenshots in test-results/screenshots/.
4. Write your complete handoff report to D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_worker_1\handoff.md with all execution commands, pass/fail counts, and evidence. Then send a completion message back.
