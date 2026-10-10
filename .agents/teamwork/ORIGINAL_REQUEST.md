# Original User Request

## 2026-10-10T05:44:41Z

Automate end-to-end browser verification of the StrongWine multi-role wine e-commerce and logistics platform using Chrome DevTools Protocol across Anonymous, Customer, Administrator, and Shipper roles.

Working directory: D:\Projects\strongwine_2\Wine-website  
Integrity mode: development

## Requirements

### R1. Anonymous User Public Surface & Boundary Verification
Verify that unauthenticated visitors can access `/home`, `/wines`, `/wines/{id}`, `/cart`, `/login`, `/register`, and `/forgot-password` without console errors or layout degradation. Verify that navigating to protected routes (`/orders`, `/admin`, `/shipper/dashboard`) redirects strictly to `/login`.

### R2. Customer Journey & Privacy Boundary Verification
Verify customer authentication (`demo`), order history rendering, real-time 5-step glowing shipment stepper on `/orders/{id}`, OTP vault visibility, and Server-Sent Events (SSE) stream stability. Verify that attempting to view another user's order redirects safely to `/orders` and accessing `/admin` or `/shipper/dashboard` returns HTTP 403 Forbidden.

### R3. Admin Dashboard, Analytics & Excel Logistics Verification
Verify administrator authentication (`admin`), Chart.js canvas rendering, KPI metrics display, Excel import drag-and-drop dropzone on `/admin/wines/import`, standard template `.xlsx` binary download, and inventory export `.xlsx` generation.

### R4. Shipper Mobile-First Cockpit Verification
Verify delivery driver authentication (`shipper.ops.hcm`) under mobile emulation (390x844), one-tap calling (`tel:`), Google Maps routing link, and 6-digit auto-advancing OTP keypad on `/shipper/dashboard`. Verify strict HTTP 403 prevention on `/admin`.

## Acceptance Criteria

### Browser Runtime & Console Health
- [x] Zero JavaScript runtime exceptions on public, user, admin, and shipper pages.
- [x] All 403 Forbidden security boundary checks pass with proper access rejection.
- [x] Mobile emulation rendering accurately positions ergonomic action buttons for drivers.
- [x] Captured visual screenshots confirm modern styling and responsive layout.
