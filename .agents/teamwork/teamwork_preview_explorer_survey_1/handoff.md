# Handoff Report — Explorer Survey 1: Backend & Runtime Architecture

## 1. Observation

1. **Active Server & Port**:
   - Running process: Java processes (PIDs `11972`, `14624`) active on Windows.
   - Port binding: `Get-NetTCPConnection -LocalPort 8080` confirmed TCP `::` port `8080` in `Listen` state.
   - HTTP probe: `curl.exe -s -i http://localhost:8080/login` returned `HTTP/1.1 200` with `Set-Cookie: JSESSIONID=...` and Thymeleaf template rendering.

2. **Spring Boot Configuration**:
   - Location: `src/main/resources/application.properties` (lines 1-96).
   - Port: Default port `8080` (no explicit `server.port` override; `.env` line 38 confirms `VNP_RETURN_URL=http://localhost:8080/api/payments/vnpay/return`).
   - Database: SQL Server at `localhost:1433`, database `winestore_2`, user `sa`, password from `.env` (`123456`).
   - Schema management: `spring.jpa.hibernate.ddl-auto=validate`, Flyway enabled (`classpath:db/migration`).
   - Active features: SSE streaming, Stripe & VNPay integrations, Google Gemini (`gemini-1.5-flash`), Gmail SMTP for OTP emails, auto-dispatch scheduler.

3. **Spring Security & RBAC Configuration**:
   - Location: `src/main/java/com/strongwine/strongwine/security/SecurityConfig.java` (lines 54-93).
   - Filter chain rules:
     - Admin only: `/admin/**`, `/api/admin/**`, `/wines/admin/**` required role `ROLE_ADMIN` (line 58).
     - Shipper & Admin: `/shipper/**` required roles `ROLE_SHIPPER` or `ROLE_ADMIN` (line 60).
     - Public routes: `/`, `/home`, `/wines`, `/wines/**`, `/cart/**`, `/register`, `/login`, `/forgot-password`, `/reset-password`, `/css/**`, `/js/**`, `/images/**`, `/uploads/**`, `/payment/webhook`, `/api/auth/**`, `/api/assistant/**`, and `GET /api/wines/**` permit all (lines 62-65, 69).
     - Customer authenticated routes: `/order-confirmation`, `/orders/**`, `/reviews/create`, `/api/orders/**`, `/api/reviews/**` (lines 67, 73).
     - REST mutations: POST/PUT/DELETE `/api/wines/**` and `/api/users/**` require `ROLE_ADMIN` (lines 70-74).
   - Form Login & Handlers:
     - Login page: `/login`, failure url `/login?error=true` (lines 78-80).
     - Post-login routing via `LoginSuccessHandler.java` (lines 20-32):
       - `ROLE_ADMIN` -> 302 Redirect to `/admin`.
       - `ROLE_SHIPPER` -> 302 Redirect to `/shipper/dashboard`.
       - `ROLE_USER` / default -> 302 Redirect to `/home`.
     - CSRF: Disabled (`csrf.disable()`, line 88).
     - Unauthenticated access to protected routes: Spring Security default redirects (302) to `/login`.
     - Authenticated unauthorized access (e.g. `USER` hitting `/admin` or `/shipper/dashboard`): Throws `AccessDeniedException` returning `HTTP 403 Forbidden`.

4. **Seed Users & Verified Credentials**:
   - Verified via live HTTP login requests (`curl.exe -s -i -d "username=...&password=..." http://localhost:8080/login`):
     - **Administrator**: `admin` / `admin123` -> 302 Redirects to `http://localhost:8080/admin` (Role: `ROLE_ADMIN`).
     - **Customer**: `demo` / `demo123` -> 302 Redirects to `http://localhost:8080/home` (Role: `ROLE_USER`).
     - **Delivery Driver**: `shipper.ops.hcm` / `admin123` -> 302 Redirects to `http://localhost:8080/shipper/dashboard` (Role: `ROLE_SHIPPER`).
       *(Note: `V6__seed_sample_data_for_core_admin_shipper.sql` copied the admin BCrypt password for the seed shipper, normalized to `shipper.ops.hcm` in V12).*
     - Alternative shipper from `DataInitializer.java`: `shipper1` / `shipper123` (Role: `ROLE_SHIPPER`).

5. **Controllers & Endpoints**:
   - **Customer Order & Privacy Check**:
     - `src/main/java/com/strongwine/strongwine/controller/OrderController.java`:
     - `@GetMapping("/orders")` (line 47) lists current user's orders.
     - `@GetMapping("/orders/{id}")` (line 63): Uses `orderService.getOrderByIdForUser(orderId, currentUser.getId())` for non-admin users (line 74). If order belongs to another user, `orderOpt.isEmpty()` triggers line 76: `return "redirect:/orders";` (safe redirect privacy boundary).
   - **Admin Analytics & Excel Endpoints**:
     - `src/main/java/com/strongwine/strongwine/controller/AdminController.java`:
       - `/admin` (dashboard, KPIs, charts).
       - `/admin/wines/import` (Excel import view with drag-and-drop zone).
     - `src/main/java/com/strongwine/strongwine/controller/api/AdminExcelApiController.java`:
       - `GET /api/admin/excel/export/inventory` (produces `.xlsx` binary download).
       - `GET /api/admin/excel/template` (produces standard product import `.xlsx` template).
       - `POST /api/admin/excel/preview` (validates upload with dryRun preview).
       - `POST /api/admin/excel/import` (executes Excel import).
   - **Shipper Cockpit & OTP Endpoints**:
     - `src/main/java/com/strongwine/strongwine/controller/ShipperController.java`:
       - `GET /shipper/dashboard`: Active shipments view for driver.
       - `POST /shipper/shipments/{id}/pickup`: Driver marks order picked up.
       - `POST /shipper/shipments/{id}/start`: Driver starts delivery.
       - `POST /shipper/shipments/{id}/complete` (with `@RequestParam String otp`): Completes delivery with OTP verification.
       - `POST /shipper/shipments/{id}/resend-otp`: Regulates OTP re-dispatch.
       - `POST /shipper/shipments/{id}/fail`: Marks delivery failed.
   - **Real-time Server-Sent Events (SSE)**:
     - `src/main/java/com/strongwine/strongwine/controller/api/SseNotificationApiController.java`:
       - `GET /api/live/orders/{orderId}/stream` (MediaType: `text/event-stream` for customer order tracking).
       - `GET /api/live/admin/notifications` (MediaType: `text/event-stream` for live admin alerts).

---

## 2. Logic Chain

1. **Server Availability**:
   - Observation 1 confirmed PID 11972/14624 listening on port 8080 and answering HTTP GET `/login` with 200 OK.
   - Therefore, the backend application is already running and ready for end-to-end browser automation without starting another instance.

2. **Security & Boundary Enforcement**:
   - `SecurityConfig.java` defines strict antMatchers. Unauthenticated calls to `/orders`, `/admin`, `/shipper/dashboard` fail auth checks and redirect to `/login` via Spring formLogin.
   - Authenticated sessions with role `ROLE_USER` accessing `/admin` or `/shipper/**` will be rejected with HTTP 403 Forbidden because line 58 (`hasRole('ADMIN')`) and line 60 (`hasAnyRole('SHIPPER', 'ADMIN')`) do not include `ROLE_USER`.
   - Authenticated session with role `ROLE_SHIPPER` accessing `/admin` will be rejected with HTTP 403 Forbidden because line 58 requires `ROLE_ADMIN`.

3. **Privacy Logic on Orders**:
   - Observation 5 demonstrated that `OrderController.orderDetail` specifically enforces ownership check: `orderService.getOrderByIdForUser(orderId, currentUser.getId())`.
   - Accessing an order ID owned by another user yields empty result, immediately returning `redirect:/orders`.

4. **Credential Mapping**:
   - Observation 4 verified through direct HTTP authentication that:
     - `admin:admin123` -> 302 to `/admin`.
     - `demo:demo123` -> 302 to `/home`.
     - `shipper.ops.hcm:admin123` -> 302 to `/shipper/dashboard`.
   - These credentials and their post-login redirection targets directly satisfy all 4 role journeys required in `ORIGINAL_REQUEST.md`.

---

## 3. Caveats

- **Database Dependency**: The active server connects to Microsoft SQL Server `winestore_2`. All tests must observe the Strict Database Integrity Contract (no destructive DDL).
- **Email Service**: `APP_MAIL_ENABLED=true` is set with Gmail credentials in `.env`. OTP emails may attempt external delivery or use fallback mechanisms.
- **Port**: Default 8080 is verified and responsive. If the application is ever restarted, run `./mvnw spring-boot:run` from root.

---

## 4. Conclusion

The StrongWine backend architecture and runtime are fully operational, verified, and mapped:
1. Active server: `http://localhost:8080`.
2. Verified test credentials:
   - Admin: `admin` / `admin123`
   - Customer: `demo` / `demo123`
   - Shipper: `shipper.ops.hcm` / `admin123` (or `shipper1` / `shipper123`)
3. All target routes, RBAC security filters, order privacy redirect checks, Excel import/export endpoints, and SSE endpoints are located and ready for browser CDP automation across R1, R2, R3, and R4.

---

## 5. Verification Method

To independently verify these findings:
1. Check port:
   ```powershell
   Get-NetTCPConnection -LocalPort 8080
   ```
2. Check public route:
   ```bash
   curl.exe -s -o /dev/null -w "%{http_code}" http://localhost:8080/home
   # Expected: 200
   ```
3. Check unauthenticated redirect on protected route:
   ```bash
   curl.exe -s -o /dev/null -w "%{http_code} %{redirect_url}\n" http://localhost:8080/orders
   # Expected: 302 http://localhost:8080/login
   ```
4. Check authentication redirects:
   ```bash
   curl.exe -s -d "username=admin&password=admin123" http://localhost:8080/login | grep "Location"
   # Expected: Location: http://localhost:8080/admin

   curl.exe -s -d "username=demo&password=demo123" http://localhost:8080/login | grep "Location"
   # Expected: Location: http://localhost:8080/home

   curl.exe -s -d "username=shipper.ops.hcm&password=admin123" http://localhost:8080/login | grep "Location"
   # Expected: Location: http://localhost:8080/shipper/dashboard
   ```
