# StrongWine V3 Super Plan — Implementation Plan

> **For agentic workers:** Execute via **Swarm** waves (`W0` -> `W1` -> `W2` -> `W3-A` -> `W3-B`/`W3-C` -> `W4`). Each task maps 1-to-1 to a named Swarm worker with strictly disjoint file and test locks. Workers running in parallel use `workspace: "share"` for disjoint file ownership and commit on their share branch before Coordinator merges and runs the Wave Gate (`*-G`). Follow strict TDD (RED -> GREEN -> REFACTOR) for every task.

**Goal:** Deliver the complete **StrongWine V3** upgrade across all three personas (`USER` Storefront, `ADMIN` Control Center, `SHIPPER` Mobile Cockpit PWA): (1) comprehensive UI/UX transformation using the *"Maison de Vins"* design system (`cellar` dark / `ivory` light), (2) end-to-end bugfixes and business logic hardening across inventory, orders, COD, OTP, payments, Excel, SSE, AI Sommelier, and security, and (3) seven major V3 commerce/logistics expansions.

**Architecture:** Retain **Spring Boot 4.0.0 + Java 21 + Spring Security 6 + Thymeleaf SSR** paired with progressive zero-build **ESM JavaScript** and `@layer`-scoped modern CSS. Preserve existing MS SQL Server schema integrity via purely additive `V17` migration and lockstep precision widening `V18` synced with JPA `@Column(precision = 18, scale = 2)` under strict `spring.jpa.hibernate.ddl-auto=validate`. Real-time notifications use `SseEmitter` + persisted `sse_event_journal` with `Last-Event-ID` replay and keep-alive heartbeats.

**Tech Stack:** Java 21, Spring Boot 4.0.0 (`spring-boot-starter-webmvc`, `data-jpa`, `security`, `thymeleaf`, `mail`), MS SQL Server (Flyway manual migration runner `DatabaseMigrationInitializer`), Stripe Java SDK, VNPay HMAC-SHA512, Apache POI (`SXSSFWorkbook`), **OpenPDF 3.0.5** (`org.openpdf.text.*`), Tailwind/CSS Custom Properties (`tokens.css`), Native DOM (`<dialog closedby="any">`, `[popover]`, `@container`, `@view-transition`), Leaflet, Playwright/Browser CDP verification.

**Spec Reference:** [docs/superpowers/specs/2026-11-13-strongwine-v3-super-plan-design.md](../specs/2026-11-13-strongwine-v3-super-plan-design.md)

---

## Global Constraints

1. **Strict Database Integrity (`AGENTS.md` §1):**
   - `V2..V16` migrations are immutable. Never `DROP TABLE`, `DROP COLUMN`, or break existing constraints.
   - `spring.jpa.hibernate.ddl-auto=validate` and `spring.sql.init.mode=never` remain enforced.
   - All `V17` DDL must be idempotent (`IF OBJECT_ID(..., N'U') IS NULL`, `IF COL_LENGTH('dbo.<table>', '<col>') IS NULL`).
   - `V18` alters existing `DECIMAL(10,2)` columns (`orders.total_price`, `order_items.price`, `wines.price`, `payments.amount`) to `DECIMAL(18,2) NOT NULL` in strict lockstep with the 4 JPA `@Column(precision = 18, scale = 2)` annotations in `Order`, `OrderItem`, `Wine`, and `Payment`.
2. **Real Implementations Only (`AGENTS.md` §1):**
   - Zero mocks or faked external effects in production runtime (`src/main/java`). Mocks belong strictly in `src/test/java`.
   - Never catch exceptions silently to claim false success. Missing external credentials (`STRIPE_SECRET_KEY`, SMTP, Gemini key) must log clearly at startup and fail explicitly when invoked, without crashing context startup for unrelated tests/routes.
3. **Frozen Cross-Worker Contracts:**
   - **`CodSettlementPort` (`service/port/CodSettlementPort.java`, created in W0):** `void settleCodOrder(Long orderId, String actorUsername)` — implemented by `OrderService` (`w1-order-cod`), locks `Order` (`PESSIMISTIC_WRITE`), enforces `PaymentMethod.COD` + `PaymentStatus.PENDING`, transitions payment to `SUCCESS` (`COD_COLLECTED`), order to `PAID` / `CONFIRMED`, calls `inventoryService.confirmOrderPayment`, writes `AdminAuditLogService`, publishes `OrderStatusUpdatedEvent`. Consumed by `ShipperCodBatchService` (`w1-shipment`).
   - **`ShipmentOrderLifecyclePort` (`service/port/ShipmentOrderLifecyclePort.java`, created in W0):** `void cancelShipmentForOrder(Long orderId, String actorUsername, String reason)`, `Optional<String> decryptOtpForOrderView(Shipment shipment)` — implemented by `ShipmentService` (`w1-shipment`), consumed via `@Lazy ShipmentOrderLifecyclePort` in `OrderService` & `OrderController` (`w1-order-cod`). `decryptOtpForOrderView` is bound to `decryptedOtp` **only** when `!isAdmin && owner`.
   - **`VoucherPricingPort` + `VoucherValidationResult` (`service/port/VoucherPricingPort.java`, `dto/VoucherValidationResult.java`, created in W0):** `VoucherValidationResult validateAndQuote(String code, Long userId, BigDecimal subtotal, BigDecimal shippingFee)` & `void redeemForOrder(String code, User user, Order order)` — implemented by `VoucherService` (`w2-voucher-wishlist`), consumed by `CartController` (`w2-auth-checkout`).
   - **Shared Events (`LowStockAlertEvent`, `AdminAlertEvent`, created in W0):** Published by `w1-warehouse-excel` / `w1-payment` / `w1-order-cod`, listened to by `w1-sse-ai` (`RealtimeEventListener` -> `NotificationService` & `SseNotificationService`).
   - Preserve 10-arg `OrderService.createPendingOrder(Long userId, Map<Long, Integer> cartItems, String fullName, String phone, String email, String address, Double deliveryLat, Double deliveryLng, String note, String paymentMethod)` as a delegating overload in W1 until W2 updates `CartController` cleanly (`EndToEndCommerceFlowTest` compatibility).
   - Preserve `public SseNotificationService()` zero-arg ctor + `RealtimeEventListener(SseNotificationService)` + existing event ctors for `EndToEndCommerceFlowTest`.
   - `AdminAuditLogService.log(String actor, String action, String targetType, Long targetId, Object beforeState, Object afterState, String ipAddress)` built in **Wave 0** and injected across W1/W2 services.
   - Canonical Admin Wine routes: `/admin/wines/**` (`AdminWineController`); `/wines/admin/**` issues redirects in `WineController`.
   - Invoice contract: `OrderInvoiceService.generateInvoice(Order, InvoiceFormat)` (`PDF`, `XLSX`) + `InvoiceController` (`GET /orders/{id}/invoice`, `GET /admin/orders/{id}/invoice`) in `w1-warehouse-excel`. Note OpenPDF `3.0.5` uses root package **`org.openpdf.*`** (not `com.lowagie.*`).
4. **Modern Web Guidance Contract:**
   - CSS Cascade layers: `@layer reset, tokens, base, components, utilities;`.
   - Dual themes: dark default (`cellar`) and light (`ivory`) with `color-scheme: dark light` and WCAG AA contrast (`>= 4.5:1`).
   - Top-layer UI: native `<dialog closedby="any">` modals and `[popover]` toasts/notification menus with Escape & focus restoration.
   - Fluid cards & transitions: `@container` wine/stat grid cards, `@view-transition { navigation: auto; }` gated by `prefers-reduced-motion: no-preference`.
   - Mobile Shipper OTP UX: single `<input autocomplete="one-time-code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6">` (never 6 split inputs) + `touch-action: manipulation`.
   - Zero AI Slop: bespoke SVG bottle/grape/oak/seal iconography; Playfair Display / Cormorant Garamond + Plus Jakarta Sans typography.

---

## Review Focus

Pin these 6 high-risk failure modes with explicit test assertions before any Wave Gate passes:

1. **Hibernate `ddl-auto=validate` Precision Drift (`V18`):** SQL `DECIMAL(18,2)` vs JPA `@Column(precision = 18, scale = 2)` must match identically across `orders.total_price`, `order_items.price`, `wines.price`, and `payments.amount`. *(Pinned in Task 1 `V3FoundationSchemaAndServicesTest` & `DatabaseIntegrityValidationTest`)*
2. **Double-Release or Silent Webhook Resurrection Race:** Late Stripe/VNPay webhook arriving on an already-`CANCELLED` order must acquire `PESSIMISTIC_WRITE` on `Order`, detect `CANCELLED`, persist `PaymentStatus.REFUND_REQUIRED` + alert notification, and never re-deduct stock or create a shipment. *(Pinned in Task 5 `StripeWebhookAndRefundRequiredTest`)*
3. **Self-Invocation Swallowing OTP Lockout Counter:** Recording wrong OTP attempt inside `ShipmentService` must cross Spring AOP proxy into `ShipmentOtpStateService` (`Propagation.REQUIRES_NEW`) so `otpAttemptCount` persists even when `verifyOtp` throws an exception and rolls back the outer transaction. *(Pinned in Task 3 `ShipmentServiceTest`)*
4. **Client-Tamperable Checkout Total & Voucher Stacking:** `CartController` / `OrderService` / `VoucherService` must always recompute `subtotal - discount + shippingFee` server-side, reject expired/depleted/below-min-subtotal vouchers cleanly, and block duplicate per-user usage under pessimistic lock. *(Pinned in Tasks 8 & 9 `VoucherServiceTest`, `CartControllerAddressValidationTest`)*
5. **Excel Formula Injection & Partial Import Corruption:** Report generators (`OrderReportGenerator`, `RevenueReportGenerator`, `InventoryStockReportGenerator`) must neutralize leading `=`, `+`, `-`, `@`, `\t`, `\r` in cell strings and use `SXSSFWorkbook`; `ExcelWineImportStrategy` must roll back or isolate atomically when strict mode fails, and accept `"Traditional"` + `/images/...`. *(Pinned in Task 2 `ExcelWineImportStrategyTest`, `ExcelTemplateServiceTest`)*
6. **CSP / Thymeleaf Expression Breakage & OTP Leak in Templates:** UI overhaul across Wave 3 must keep customer OTP visible only to `!isAdmin && owner` (`OrderDetailOtpVisibilityTemplateTest`), admin OTP masked (`AdminShipmentOtpMaskingTemplateTest`), checkout payment radios deterministic (`CheckoutTemplateSelectionStateTest`), and Shipper OTP using a single `autocomplete="one-time-code"` field. *(Pinned in Tasks 11–14)*

---

## Wave 0 — Schema, Entities, Core Contracts (Blocking Foundation)

### Task 1: Wave 0 Foundation (`w0-foundation`)

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/db/migration/V17__strongwine_v3_domain_foundation.sql`
- Create: `src/main/resources/db/migration/V18__widen_money_columns_precision_18.sql`
- Modify: `src/main/java/com/strongwine/strongwine/entity/Order.java`
- Modify: `src/main/java/com/strongwine/strongwine/entity/OrderItem.java`
- Modify: `src/main/java/com/strongwine/strongwine/entity/Wine.java`
- Modify: `src/main/java/com/strongwine/strongwine/entity/Payment.java`
- Modify: `src/main/java/com/strongwine/strongwine/entity/Warehouse.java`
- Modify: `src/main/java/com/strongwine/strongwine/entity/Shipment.java`
- Modify: `src/main/java/com/strongwine/strongwine/entity/User.java`
- Modify: `src/main/java/com/strongwine/strongwine/entity/OrderStatus.java`
- Modify: `src/main/java/com/strongwine/strongwine/entity/PaymentStatus.java`
- Modify: `src/main/java/com/strongwine/strongwine/entity/ShipmentStatus.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/VoucherType.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/ReturnStatus.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/CodBatchStatus.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/UserAddress.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/WishlistItem.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/Voucher.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/VoucherUsage.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/ReturnRequest.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/ShipperCodBatch.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/AdminAuditLog.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/NotificationLog.java`
- Create: `src/main/java/com/strongwine/strongwine/entity/SseEventJournal.java`
- Create: `src/main/java/com/strongwine/strongwine/repository/UserAddressRepository.java`
- Create: `src/main/java/com/strongwine/strongwine/repository/WishlistItemRepository.java`
- Create: `src/main/java/com/strongwine/strongwine/repository/VoucherRepository.java`
- Create: `src/main/java/com/strongwine/strongwine/repository/VoucherUsageRepository.java`
- Create: `src/main/java/com/strongwine/strongwine/repository/ReturnRequestRepository.java`
- Create: `src/main/java/com/strongwine/strongwine/repository/ShipperCodBatchRepository.java`
- Create: `src/main/java/com/strongwine/strongwine/repository/AdminAuditLogRepository.java`
- Create: `src/main/java/com/strongwine/strongwine/repository/NotificationLogRepository.java`
- Create: `src/main/java/com/strongwine/strongwine/repository/SseEventJournalRepository.java`
- Modify: `src/main/java/com/strongwine/strongwine/repository/WarehouseRepository.java`
- Modify: `src/main/java/com/strongwine/strongwine/repository/ShipmentRepository.java`
- Modify: `src/main/java/com/strongwine/strongwine/repository/OrderRepository.java`
- Modify: `src/main/java/com/strongwine/strongwine/repository/PaymentRepository.java`
- Modify: `src/main/java/com/strongwine/strongwine/repository/InventoryRepository.java`
- Create: `src/main/java/com/strongwine/strongwine/event/LowStockAlertEvent.java`
- Create: `src/main/java/com/strongwine/strongwine/event/AdminAlertEvent.java`
- Create: `src/main/java/com/strongwine/strongwine/dto/VoucherValidationResult.java`
- Create: `src/main/java/com/strongwine/strongwine/service/port/CodSettlementPort.java`
- Create: `src/main/java/com/strongwine/strongwine/service/port/ShipmentOrderLifecyclePort.java`
- Create: `src/main/java/com/strongwine/strongwine/service/port/VoucherPricingPort.java`
- Create: `src/main/java/com/strongwine/strongwine/service/ShippingFeeService.java`
- Create: `src/main/java/com/strongwine/strongwine/service/ShipmentOtpCryptoService.java`
- Create: `src/main/java/com/strongwine/strongwine/service/AdminAuditLogService.java`
- Create: `src/test/java/com/strongwine/strongwine/service/V3FoundationSchemaAndServicesTest.java`

**Interfaces:**
- Produces: OpenPDF 3.0.5 Maven dependency (`com.github.librepdf:openpdf:3.0.5`), `V17` + `V18` SQL schema & lockstep entities/repositories (including `OrderRepository.findByIdWithPessimisticLock` & `InventoryRepository` active-stock queries), cross-worker ports (`CodSettlementPort`, `ShipmentOrderLifecyclePort`, `VoucherPricingPort`, `VoucherValidationResult`), shared events (`LowStockAlertEvent`, `AdminAlertEvent`), `ShippingFeeService.calculateFee(Double distanceKm, BigDecimal subtotal)`, `ShipmentOtpCryptoService.encrypt(String plainOtp)` / `decrypt(String cipherText)`, `AdminAuditLogService.log(String actor, String action, String targetType, Long targetId, Object beforeState, Object afterState, String ipAddress)`.

- [ ] **Step 1 (RED):** Write `V3FoundationSchemaAndServicesTest.java` asserting:
  1. `Order.totalPrice`, `OrderItem.price`, `Wine.price`, `Payment.amount` JPA `@Column` metadata declare `precision = 18, scale = 2`.
  2. `ShippingFeeService` tiers: free shipping `>= 3_000_000 VND`, inner-city tier `<= 5km` (`25_000 VND`), medium `<= 15km` (`45_000 VND`), inter-zone `> 15km` (`75_000 VND`), null distance fallback (`35_000 VND`).
  3. `ShipmentOtpCryptoService` encrypts 6-digit OTP into non-plaintext AES-GCM token and decrypts back identically; invalid ciphertext throws explicit exception or returns empty cleanly per contract.
  4. `AdminAuditLogService.log(...)` serializes non-null `beforeState`/`afterState` to JSON safely and persists `AdminAuditLog`.
- [ ] **Step 2:** Run `./mvnw test -Dtest=V3FoundationSchemaAndServicesTest` — confirm compile/RED failure because V3 classes/annotations do not exist yet.
- [ ] **Step 3:** Add `com.github.librepdf:openpdf:3.0.5` to `pom.xml`. Author `V17__strongwine_v3_domain_foundation.sql` (idempotent `warehouse`, `orders`, `shipments`, `users` column additions + seed coordinates for `Main Warehouse` / HCM / HN + 9 new tables + indexes) and `V18__widen_money_columns_precision_18.sql` (`DECIMAL(18,2) NOT NULL` on the 4 money columns).
- [ ] **Step 4:** Update existing entities (`@Column(precision = 18, scale = 2)` + V3 fields) and enums (`OrderStatus`, `PaymentStatus`, `ShipmentStatus`). Create the 9 V3 entities, 3 enums, 9 repositories, repository lock/stock queries (`OrderRepository`, `PaymentRepository`, `InventoryRepository`, `WarehouseRepository`, `ShipmentRepository`), cross-worker ports (`CodSettlementPort`, `ShipmentOrderLifecyclePort`, `VoucherPricingPort`, `VoucherValidationResult`), shared events (`LowStockAlertEvent`, `AdminAlertEvent`), `ShippingFeeService`, `ShipmentOtpCryptoService`, `AdminAuditLogService`.
- [ ] **Step 5 (GREEN — Gate W0-G):** Run `./mvnw test -Dtest=V3FoundationSchemaAndServicesTest,DatabaseIntegrityValidationTest,StrongwineApplicationTests` and verify `Exit code: 0` with zero skipped assertions.

---

## Wave 1 — Parallel Domain & Infrastructure Workers (`W1`)

*Run Tasks 2, 3, 4, 5, 6, 7 concurrently after Gate W0-G passes. Zero shared files.*

### Task 2: Warehouse, Inventory, Excel & Commercial Invoice (`w1-warehouse-excel`)

**Files:**
- Modify: `src/main/java/com/strongwine/strongwine/service/InventoryService.java`
- Modify: `src/main/java/com/strongwine/strongwine/config/DataInitializer.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/WineService.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/WineController.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/AdminWineController.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/strategy/ExcelWineImportStrategy.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/strategy/ImportOptions.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/report/AbstractExcelReportGenerator.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/report/InventoryStockReportGenerator.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/report/OrderReportGenerator.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/report/RevenueReportGenerator.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/report/ExcelTemplateService.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/AdminExcelApiController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/AdminInventoryApiController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/AdminInventoryController.java`
- Create: `src/main/java/com/strongwine/strongwine/service/invoice/OrderInvoiceService.java`
- Create: `src/main/java/com/strongwine/strongwine/service/invoice/InvoiceFormat.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/InvoiceController.java`
- Create: `src/main/java/com/strongwine/strongwine/config/LowStockAlertScheduler.java`
- Create/Update Tests: `InventoryServiceMultiWarehouseTest.java`, `ExcelWineImportStrategyTest.java`, `ExcelTemplateServiceTest.java`, `InventoryStockReportGeneratorTest.java`, `AdminExcelApiControllerTest.java`, `AdminInventoryApiControllerTest.java`, `OrderInvoiceServiceTest.java`

**Interfaces:**
- Consumes: `WarehouseRepository`, `InventoryRepository`, `AdminAuditLogService`, `ApplicationEventPublisher`, OpenPDF `3.0.5` (`org.openpdf.text.*`), `SXSSFWorkbook`.
- Produces:
  - `InventoryService.reserveForOrder(Order)` selecting closest active warehouse with sufficient stock via Haversine (`allocatedWarehouse` stored on `Order`), `getAvailableStockByWineIds` summing across active warehouses.
  - `OrderInvoiceService.generateInvoice(Order order, InvoiceFormat format)` + `InvoiceController` (`GET /orders/{id}/invoice?format=pdf|xlsx`, `GET /admin/orders/{id}/invoice?format=pdf|xlsx`).
  - `AdminWineController` (`/admin/wines/**`) + `WineController` 302 redirect from `/wines/admin/**`.

- [ ] **Step 1 (RED):** Add unit tests in `InventoryServiceMultiWarehouseTest` (reserve allocates nearest stocked warehouse via Haversine instead of failing when `"Main Warehouse"` has 0 units; multi-warehouse sum), `OrderInvoiceServiceTest` (PDF starts with `%PDF` header via `org.openpdf.text.Document` + includes `order.id`, items, voucher/shipping, total; XLSX opens in Apache POI), and extend `ExcelWineImportStrategyTest` / `ExcelTemplateServiceTest` (`"Traditional"` accepted, `/images/...` accepted, atomic rollback on strict failure, `=CMD()` neutralized to `'=CMD()`).
- [ ] **Step 2:** Run `./mvnw test -Dtest=InventoryServiceMultiWarehouseTest,OrderInvoiceServiceTest,ExcelWineImportStrategyTest,ExcelTemplateServiceTest` — confirm RED failures.
- [ ] **Step 3:** Remove startup stock reset block in `DataInitializer`. Update `WineService` & `ExcelWineImportStrategy` to include `"Traditional"`, local `/images/` paths, and `@Transactional` rollback semantics on strict mode.
- [ ] **Step 4:** Upgrade `InventoryService` multi-warehouse allocation (`Haversine`) + low-stock event checks (`LowStockAlertScheduler`). Upgrade `AbstractExcelReportGenerator` + report subclasses to `SXSSFWorkbook` (`finally { dispose(); }`) and `sanitizeExcelText(...)` formula guard.
- [ ] **Step 5:** Implement `OrderInvoiceService` (using `org.openpdf.text.*`), `InvoiceController` (owner-or-admin security check), `AdminWineController`, and `/wines/admin/**` redirect in `WineController`.
- [ ] **Step 6 (GREEN):** Run `./mvnw test -Dtest=InventoryServiceMultiWarehouseTest,ExcelWineImportStrategyTest,ExcelTemplateServiceTest,InventoryStockReportGeneratorTest,AdminExcelApiControllerTest,AdminInventoryApiControllerTest,OrderInvoiceServiceTest` — confirm `Exit code: 0`.

---

### Task 3: Shipment, OTP `REQUIRES_NEW`, Dual Cipher & Shipper COD Batches (`w1-shipment`)

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/service/ShipmentOtpStateService.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/ShipmentService.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/ShipmentOtpEmailService.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/ShipmentOtpAuditService.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/ShipmentStatusHistoryService.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/ShipperService.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/AdminShipmentController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/AdminShipperController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/ShipperController.java`
- Modify: `src/main/java/com/strongwine/strongwine/config/ShipmentReconciliationCoordinator.java`
- Modify: `src/main/java/com/strongwine/strongwine/config/ShipmentAutoDispatchScheduler.java`
- Create: `src/main/java/com/strongwine/strongwine/service/ShipperCodBatchService.java`
- Create/Update Tests: `ShipmentServiceTest.java`, `ShipmentOtpEmailServiceTest.java`, `UserServiceShipperLockdownTest.java`, `ShipperCodBatchServiceTest.java`

**Interfaces:**
- Consumes: `ShipmentOtpCryptoService`, `CodSettlementPort` (via `@Lazy` injection to avoid circular dependency!), `AdminAuditLogService`, `FileStorageService` (`isValidImageFile` / `saveFile`).
- Produces (`ShipmentService implements ShipmentOrderLifecyclePort`):
  - `ShipmentOtpStateService.recordFailedOtpAttempt(Long shipmentId)` (`@Transactional(propagation = Propagation.REQUIRES_NEW)`).
  - `ShipmentService.cancelShipmentForOrder(Long orderId, String actorUsername, String reason)`.
  - `Optional<String> ShipmentService.decryptOtpForOrderView(Shipment shipment)`.
  - `ShipperCodBatchService`: Shipper submits delivered COD orders into a batch (`PENDING_SETTLEMENT`); Admin verifies (`VERIFIED` -> calls `CodSettlementPort.settleCodOrder` for each order in batch) or flags (`DISPUTED`) with audit log.

- [ ] **Step 1 (RED):** Extend `ShipmentServiceTest` with tests for: (a) failed OTP attempt count persisting across thrown invalid-OTP exception via `ShipmentOtpStateService`, (b) `cancelShipmentForOrder` transitioning `ASSIGNED` -> `ShipmentStatus.CANCELLED`, decrementing shipper `activeShipmentCount`, clearing OTP, (c) `decryptOtpForOrderView` recovering exact 6-digit OTP from `otpEncrypted`. Add `ShipperCodBatchServiceTest` asserting batch creation, idempotent duplicate protection, Admin `VERIFIED` settling COD orders via `CodSettlementPort.settleCodOrder`, and `DISPUTED` audit trail.
- [ ] **Step 2:** Run `./mvnw test -Dtest=ShipmentServiceTest,ShipperCodBatchServiceTest` — confirm RED failure.
- [ ] **Step 3:** Implement `ShipmentOtpStateService` (`REQUIRES_NEW`), wire `ShipmentOtpCryptoService` into `ShipmentService` OTP generation/verification/decryption, implement `cancelShipmentForOrder`, fix shipper assignment Haversine/null-GPS fallback, and mask raw OTP from Admin/Shipper responses & logs.
- [ ] **Step 4:** Implement Shipper POD photo upload endpoint (`ShipperController`) and `ShipperCodBatchService` + Admin COD batch verification/dispute endpoints (`AdminShipmentController` / `AdminShipperController`).
- [ ] **Step 5 (GREEN):** Run `./mvnw test -Dtest=ShipmentServiceTest,ShipmentOtpEmailServiceTest,UserServiceShipperLockdownTest,ShipperCodBatchServiceTest` — confirm `Exit code: 0`.

---

### Task 4: Order COD Settlement, Cancel Cascade, Re-order & Returns (`w1-order-cod`)

**Files:**
- Modify: `src/main/java/com/strongwine/strongwine/service/OrderService.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/OrderItemService.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/OrderController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/AdminOrderController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/OrderApiController.java`
- Create: `src/main/java/com/strongwine/strongwine/service/ReturnRequestService.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/ReturnController.java`
- Create/Update Tests: `OrderServiceCancellationTest.java`, `OrderCodSettlementTest.java`, `ReturnRequestServiceTest.java`

**Interfaces:**
- Consumes: `@Lazy ShipmentOrderLifecyclePort` (`cancelShipmentForOrder`, `decryptOtpForOrderView`), `OrderRepository.findByIdWithPessimisticLock`, `InventoryService`, `PaymentRepository`, `AdminAuditLogService`, `ApplicationEventPublisher` (`OrderStatusUpdatedEvent`, `AdminAlertEvent`).
- Produces (`OrderService implements CodSettlementPort`):
  - Preserved 10-arg delegating `OrderService.createPendingOrder(...)` + V3 overload accepting `subtotalAmount`, `discountAmount`, `shippingFee`, `voucherCode`, `allocatedWarehouse`.
  - `OrderService.settleCodOrder(Long orderId, String actorUsername)` (implements `CodSettlementPort`).
  - `ReturnRequestService` (`requestReturn`, `approveReturn`, `rejectReturn`, `completeRefund`, publishing `AdminAlertEvent`) + `ReturnController` (`/orders/{id}/returns`, `/admin/returns/**`).
  - Customer `POST /orders/{id}/reorder` validation & item extraction.

- [ ] **Step 1 (RED):** Write `OrderCodSettlementTest` (verifies `settleCodOrder` locks order, rejects non-COD or non-PENDING payment, sets `PaymentStatus.SUCCESS` + `COD_COLLECTED`, deducts inventory, audits, emits `OrderStatusUpdatedEvent`) and `ReturnRequestServiceTest` (owner requests return on `DELIVERED` order -> `OrderStatus.RETURN_REQUESTED`; non-owner forbidden; Admin approve + refund -> restocks inventory, marks `PaymentStatus.REFUNDED`, `OrderStatus.RETURNED`, audits). Extend `OrderServiceCancellationTest` to assert `cancelPendingOrder` invokes `ShipmentOrderLifecyclePort.cancelShipmentForOrder`.
- [ ] **Step 2:** Run `./mvnw test -Dtest=OrderServiceCancellationTest,OrderCodSettlementTest,ReturnRequestServiceTest` — confirm RED failure.
- [ ] **Step 3:** Implement `CodSettlementPort.settleCodOrder`, `@Lazy ShipmentOrderLifecyclePort` cancellation hook, V3 `createPendingOrder` overload (keeping 10-arg delegator), `OrderController.orderDetail` `decryptedOtp` owner-only model binding (`!isAdmin && owner`), and `POST /orders/{id}/reorder`.
- [ ] **Step 4:** Implement `ReturnRequestService` & `ReturnController` + Admin `AdminOrderController` settle/return bindings.
- [ ] **Step 5 (GREEN):** Run `./mvnw test -Dtest=OrderServiceCancellationTest,OrderCodSettlementTest,ReturnRequestServiceTest` — confirm `Exit code: 0`.

---

### Task 5: Payment Pessimistic Locking, Webhook 400 & Late-Payment `REFUND_REQUIRED` (`w1-payment`)

**Files:**
- Modify: `src/main/java/com/strongwine/strongwine/service/PaymentService.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/PaymentController.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/StripeService.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/VNPayService.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/VNPayApiController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/AdminPaymentController.java`
- Modify: `src/main/java/com/strongwine/strongwine/config/VnPayPendingPaymentExpiryScheduler.java`
- Modify/Create Tests: `PaymentServiceVnPayCallbackTest.java`, `VNPayServiceTest.java`, `StripeWebhookAndRefundRequiredTest.java`

**Interfaces:**
- Consumes: `OrderRepository.findByIdWithPessimisticLock`, `PaymentRepository`, `PaymentTransactionRepository`, `OrderService`, `AdminAuditLogService`, `ApplicationEventPublisher` (`OrderPaidEvent`, `AdminAlertEvent`).
- Produces: Safe `StripeService.init()` (`isConfigured()` guard), `400 Bad Request` on invalid Stripe webhook signature in `PaymentController`, atomic `REFUND_REQUIRED` handling + `AdminAlertEvent` broadcast when late payment succeeds on an already-`CANCELLED` order.

- [ ] **Step 1 (RED):** Write `StripeWebhookAndRefundRequiredTest` asserting:
  1. Empty `stripe.secretKey` does NOT crash `StripeService.init()`, while calling `createCheckoutSession` throws explicit typed exception.
  2. Invalid `Stripe-Signature` on `POST /payment/webhook` returns HTTP `400`.
  3. Valid Stripe/VNPay success callback on an order already in `OrderStatus.CANCELLED` acquires pessimistic lock, marks payment `PaymentStatus.REFUND_REQUIRED`, records `PaymentTransaction` with reason, publishes admin notification event, and leaves `OrderStatus.CANCELLED` intact without stock deduction.
- [ ] **Step 2:** Run `./mvnw test -Dtest=StripeWebhookAndRefundRequiredTest,PaymentServiceVnPayCallbackTest,VNPayServiceTest` — confirm RED failure.
- [ ] **Step 3:** Refactor `StripeService.init()` to set `configured` state safely. Update `PaymentController.webhook` to validate signature and return `ResponseEntity.badRequest()` on signature/JSON errors.
- [ ] **Step 4:** Harden `PaymentService` Stripe & VNPay confirmation paths with `PESSIMISTIC_WRITE` lock, idempotent replay checks, `DECIMAL(18,2)` amount comparison, and `OrderStatus.CANCELLED` -> `PaymentStatus.REFUND_REQUIRED` branch.
- [ ] **Step 5 (GREEN):** Run `./mvnw test -Dtest=StripeWebhookAndRefundRequiredTest,PaymentServiceVnPayCallbackTest,VNPayServiceTest` — confirm `Exit code: 0`.

---

### Task 6: SSE Event Journal + `Last-Event-ID`, Notification Center & Grounded AI Sommelier (`w1-sse-ai`)

**Files:**
- Modify: `src/main/java/com/strongwine/strongwine/service/realtime/SseNotificationService.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/SseNotificationApiController.java`
- Modify: `src/main/java/com/strongwine/strongwine/event/listener/RealtimeEventListener.java`
- Create: `src/main/java/com/strongwine/strongwine/service/NotificationService.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/api/NotificationApiController.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/GeminiAssistantService.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/AiAssistantApiController.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/ReviewService.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/ReviewController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/ReviewApiController.java`
- Modify/Create Tests: `SseNotificationServiceTest.java`, `SseMultiChannelTest.java`, `SseNotificationApiControllerTest.java`, `RealtimeEventListenerTest.java`, `GeminiAssistantServiceTest.java`, `NotificationServiceTest.java`

**Interfaces:**
- Consumes: `SseEventJournalRepository`, `NotificationLogRepository`, `WineRepository`, `InventoryService`, existing events (`OrderPaidEvent`, `OrderStatusUpdatedEvent`, `ShipmentLiveStatusEvent`), W0 shared events (`LowStockAlertEvent`, `AdminAlertEvent`).
- Produces:
  - Preserved `public SseNotificationService()` zero-arg ctor + `RealtimeEventListener(SseNotificationService)` + existing event ctors.
  - `Last-Event-ID` SSE replay + 20s heartbeat `@Scheduled` keep-alive + `NotificationService` (`/api/notifications/**`) subscribing to `LowStockAlertEvent` & `AdminAlertEvent`.
  - `GeminiAssistantService.askStructured(...)` returning grounded prose + verified wine card payloads (`id`, `name`, `type`, `price`, `imageUrl`, `inStock`) using strict catalog grounding.

- [ ] **Step 1 (RED):** Add `NotificationServiceTest` (persisting user/admin notifications, marking read, enforcing user isolation) and extend `SseNotificationServiceTest` (replaying missed `SseEventJournal` events after `Last-Event-ID`, pruning dead emitters cleanly) and `GeminiAssistantServiceTest` (rejecting invented wines outside `WineRepository` context, returning grounded cards, neutralizing injection tags).
- [ ] **Step 2:** Run `./mvnw test -Dtest=SseNotificationServiceTest,SseMultiChannelTest,SseNotificationApiControllerTest,RealtimeEventListenerTest,GeminiAssistantServiceTest,NotificationServiceTest` — confirm RED failures.
- [ ] **Step 3:** Upgrade `SseNotificationService`, `SseNotificationApiController`, and `RealtimeEventListener` (`@Scheduled` 20s heartbeat, `SseEventJournal` persistence when wired, `Last-Event-ID` header replay). Add `NotificationService`, `NotificationApiController`, and `LowStockAlertEvent` listener.
- [ ] **Step 4:** Upgrade `GeminiAssistantService` & `AiAssistantApiController` with live catalog + stock grounding and structured card DTO alongside existing text compatibility; harden `ReviewService` / controllers for HTML sanitization and verified-purchase indicator.
- [ ] **Step 5 (GREEN):** Run `./mvnw test -Dtest=SseNotificationServiceTest,SseMultiChannelTest,SseNotificationApiControllerTest,RealtimeEventListenerTest,GeminiAssistantServiceTest,NotificationServiceTest` — confirm `Exit code: 0`.

---

### Task 7: Enum Labels, Wine Specifications (`Traditional`), Geo & DTOs (`w1-enum-misc`)

**Files:**
- Modify: `src/main/java/com/strongwine/strongwine/service/EnumDisplayService.java`
- Modify: `src/main/java/com/strongwine/strongwine/repository/specification/WineSpecification.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/VietnamGeoValidationService.java`
- Modify: `src/main/java/com/strongwine/strongwine/util/AddressTextUtils.java`
- Modify: `src/main/java/com/strongwine/strongwine/dto/ProductDto.java`
- Modify: `src/main/java/com/strongwine/strongwine/dto/OrderDto.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/WineApiController.java`
- Modify/Create Tests: `EnumDisplayServiceTest.java`, `WineSpecificationTest.java`, `VietnamGeoValidationServiceTest.java`, `WineApiControllerTest.java`

**Interfaces:**
- Consumes: V3 Enums (`ShipmentStatus.CANCELLED`, `OrderStatus.RETURN_REQUESTED`/`RETURNED`, `PaymentStatus.REFUND_REQUIRED`/`REFUNDED`, `VoucherType`, `ReturnStatus`, `CodBatchStatus`), `"Traditional"` wine type.
- Produces: Complete Vietnamese/EN labels & CSS tone tokens in `EnumDisplayService`; V3 `WineSpecification` filters; expanded `ProductDto`/`OrderDto`.

- [ ] **Step 1 (RED):** Extend `EnumDisplayServiceTest` to assert non-blank labels & tones for every new V3 enum constant (`CANCELLED`, `RETURN_REQUESTED`, `RETURNED`, `REFUND_REQUIRED`, `REFUNDED`, `Traditional`, etc.), and extend `WineSpecificationTest` for `"Traditional"` type filter, price range, country, and active-only filters.
- [ ] **Step 2:** Run `./mvnw test -Dtest=EnumDisplayServiceTest,WineSpecificationTest,VietnamGeoValidationServiceTest,WineApiControllerTest` — confirm RED failures.
- [ ] **Step 3:** Update `EnumDisplayService`, `WineSpecification`, `VietnamGeoValidationService`, `AddressTextUtils`, `ProductDto`, `OrderDto`, `WineApiController`.
- [ ] **Step 4 (GREEN — Gate W1-G):** Run `./mvnw test -Dtest=EnumDisplayServiceTest,WineSpecificationTest,VietnamGeoValidationServiceTest,WineApiControllerTest` then merge all **Wave 1** branches cleanly and run full W0+W1 backend test suite. Confirm `Exit code: 0`.

---

## Wave 2 — Security, Checkout Pipeline, Vouchers, Address Book & Wishlist (`W2`)

*Depends on Wave 1 (`W1-G`). Tasks 8 and 9 run concurrently with strictly disjoint file ownership.*

### Task 8: Security, CSRF, Auth, File Storage, Address Book & Checkout Cutover (`w2-auth-checkout`)

**Files:**
- Modify: `src/main/java/com/strongwine/strongwine/config/SecurityConfig.java`
- Modify: `src/main/java/com/strongwine/strongwine/config/WebConfig.java`
- Modify: `src/main/java/com/strongwine/strongwine/exception/GlobalExceptionHandler.java`
- Modify: `src/main/java/com/strongwine/strongwine/exception/ApiExceptionHandler.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/AuthController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/AuthApiController.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/UserService.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/PasswordResetService.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/FileStorageService.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/GlobalModelAttributes.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/HomeController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/ProfileController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/AdminController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/AdminUserController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/AdminProfileController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/AdminCategoryApiController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/AdminProductApiController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/api/UserApiController.java`
- Modify: `src/main/java/com/strongwine/strongwine/controller/CartController.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/CartService.java`
- Modify: `src/main/java/com/strongwine/strongwine/service/UserShippingProfileService.java`
- Create: `src/main/java/com/strongwine/strongwine/service/UserAddressService.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/api/AddressBookApiController.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/AdminAuditController.java`
- Modify/Create Tests: `AuthControllerTest.java`, `ProfileControllerTest.java`, `CartControllerAddressValidationTest.java`, `GlobalExceptionHandlerTest.java`, `PasswordResetServiceTest.java`, `UserShippingProfileServiceTest.java`, `UserAddressServiceTest.java`

**Interfaces:**
- Consumes: `ShippingFeeService`, `@Autowired(required = false) VoucherPricingPort` (created in W0, implemented in `w2-voucher-wishlist`), `OrderService.createPendingOrder(...)` V3 overload, `AdminAuditLogService`, `NotificationService`.
- Produces: CSRF-protected `/api/**` + `/orders/**` (webhook signature path `/payment/webhook` exempted cleanly), role-restricted PII APIs, safe `FileStorageService` magic-byte/size/traversal guard, `UserAddressService` + `/api/profile/addresses`, `AdminAuditController` (`/admin/audit-logs`), V3 server-side checkout pricing cutover in `CartController` (recomputing `subtotal - discount + shippingFee`).

- [ ] **Step 1 (RED):** Add `UserAddressServiceTest` (default address exclusivity toggle per user, ownership enforcement, max cap), extend `CartControllerAddressValidationTest` (server-side shipping fee + voucher discount recomputation rejecting tampered total; saved address auto-fill; CSRF required), and extend `AuthControllerTest` / `PasswordResetServiceTest` / `GlobalExceptionHandlerTest` (CSRF enforced, anonymous denied `/api/live/**` & PII endpoints, reset tokens excluded from responses, `FileStorageService` rejects `.jsp`/`.exe`/path-traversal `..`).
- [ ] **Step 2:** Run `./mvnw test -Dtest=AuthControllerTest,ProfileControllerTest,CartControllerAddressValidationTest,GlobalExceptionHandlerTest,PasswordResetServiceTest,UserShippingProfileServiceTest,UserAddressServiceTest` — confirm RED failures.
- [ ] **Step 3:** Harden `SecurityConfig`, `WebConfig` (clean caching headers for `/css/**`, `/js/**`, `/manifest.webmanifest`), `PasswordResetService`, `UserService`, `FileStorageService`, and exception handlers.
- [ ] **Step 4:** Implement `UserAddressService`, `AddressBookApiController`, `AdminAuditController`, and wire `CartController` + `CartService` V3 checkout total + `VoucherPricingPort` redemption + `ShippingFeeService` calculation + `GlobalModelAttributes` (`wishlistCount`, `unreadNotificationCount`).
- [ ] **Step 5 (GREEN):** Run `./mvnw test -Dtest=AuthControllerTest,ProfileControllerTest,CartControllerAddressValidationTest,GlobalExceptionHandlerTest,PasswordResetServiceTest,UserShippingProfileServiceTest,UserAddressServiceTest` — confirm `Exit code: 0`.

---

### Task 9: Voucher Engine & Wishlist API (`w2-voucher-wishlist`)

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/service/VoucherService.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/VoucherAdminController.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/api/VoucherApiController.java`
- Create: `src/main/java/com/strongwine/strongwine/service/WishlistService.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/api/WishlistApiController.java`
- Create Tests: `VoucherServiceTest.java`, `WishlistServiceTest.java`

**Interfaces:**
- Consumes: `VoucherPricingPort`, `VoucherValidationResult`, `VoucherRepository`, `VoucherUsageRepository`, `WishlistItemRepository`, `WineRepository`, `AdminAuditLogService`.
- Produces (`VoucherService implements VoucherPricingPort`):
  - `VoucherService.validateAndQuote(String code, Long userId, BigDecimal subtotal, BigDecimal shippingFee)` & `redeemForOrder(String code, User user, Order order)` (`PERCENT` capped by `maxDiscount`, `FIXED`, `FREESHIP`).
  - `VoucherAdminController` (`/admin/vouchers/**`), `VoucherApiController` (`POST /api/vouchers/validate`).
  - `WishlistService` + `WishlistApiController` (`GET /api/wishlist`, `POST /api/wishlist/{wineId}/toggle`).

- [ ] **Step 1 (RED):** Write `VoucherServiceTest` (`PERCENT` cap, `FIXED` floor at `0`, `FREESHIP` zeroing shipping fee, expired/inactive/min-order/exhausted-limit/already-used-by-user rejections, audit log on admin create/toggle) and `WishlistServiceTest` (toggle add/remove idempotency, deleted wine rejection, count query).
- [ ] **Step 2:** Run `./mvnw test -Dtest=VoucherServiceTest,WishlistServiceTest` — confirm RED failures.
- [ ] **Step 3:** Implement `VoucherService` (`implements VoucherPricingPort`), `VoucherAdminController`, `VoucherApiController`, `WishlistService`, `WishlistApiController`.
- [ ] **Step 4 (GREEN — Gate W2-G):** Run `./mvnw test -Dtest=VoucherServiceTest,WishlistServiceTest`, merge W2 branches cleanly, and run FULL backend suite (`./mvnw test`). Verify `Exit code: 0`.

---

## Wave 3 — UI/UX Complete Overhaul ("Maison de Vins") (`W3-A` -> `W3-B` + `W3-C`)

### Task 10: Design System Tokens, CSS Architecture, Shells & Shipper PWA (`w3-design-system`)

*Runs FIRST in Wave 3. Must pass `W3-A` gate before Tasks 11, 12, 13 begin.*

**Files:**
- Create: `src/main/resources/static/css/tokens.css`
- Create: `src/main/resources/static/css/storefront.css`
- Create: `src/main/resources/static/css/admin.css`
- Create: `src/main/resources/static/css/shipper.css`
- Create: `src/main/resources/static/manifest.webmanifest`
- Create: `src/main/resources/static/sw-shipper.js`
- Create: `src/main/resources/static/js/theme-toggle.js`
- Create: `src/main/resources/static/js/dialog-kit.js`
- Modify: `src/main/resources/templates/fragments/store-shell.html`
- Modify: `src/main/resources/templates/fragments/admin-shell.html`
- Create: `src/main/resources/templates/fragments/shipper-shell.html`
- Modify: `src/main/resources/templates/fragments/pagination.html`

**Interfaces:**
- Produces: `@layer reset, tokens, base, components, utilities;` CSS design tokens (`cellar` dark `#0c0a08`/`#f0e8da`/`#8b1a1a`/`#c9a84c` + `ivory` light `#f8f4ed`/`#1a1512`/`#7c1818`/`#9a7728`), Top-layer `[popover]` toast & notification slots, native `<dialog closedby="any">` utilities, `@container` grid rules, `@view-transition`, bespoke SVG symbols macro/sprite, `store-shell.html`, `admin-shell.html`, `shipper-shell.html`, PWA shell (`manifest.webmanifest`, `sw-shipper.js`).

- [ ] **Step 1:** Author `tokens.css` establishing `@layer` ordering, `cellar` & `ivory` theme tokens (`WCAG AA >= 4.5:1`), fluid `clamp()` serif/sans scales, hairline borders, elevation shadows, focus-visible rings, and reduced-motion rules.
- [ ] **Step 2:** Author `storefront.css`, `admin.css`, and `shipper.css` (`@container` card queries, timeline track styles, Admin sidebar + sticky table + KPI grid, Shipper bottom-thumb cockpit + `env(safe-area-inset-bottom)` + `touch-action: manipulation`).
- [ ] **Step 3:** Author `theme-toggle.js` (`localStorage` + `data-theme="cellar|ivory"` toggle synced without FOUC) and `dialog-kit.js` (`<dialog closedby="any">`, focus restore, `[popover="manual"]` toast notifications, Escape key cleanup).
- [ ] **Step 4:** Author `manifest.webmanifest` + `sw-shipper.js` (caches static `/css/**`, `/js/**`, `/favicon.svg`, `/images/bottles/**` shell assets; NEVER caches `/api/**` authenticated PII responses).
- [ ] **Step 5:** Overhaul `store-shell.html`, `admin-shell.html`, `pagination.html`, and create `shipper-shell.html` with CSRF meta tags, bespoke wine SVG sprite, theme toggle, notification bell `[popover]`, toast container `[popover="manual"]`, and `@view-transition` link hooks.
- [ ] **Step 6 (Gate W3-A):** Run `./mvnw test-compile` and `./mvnw test -Dtest=ThymeleafTemplateRedesignVerificationTest` (note template assertions; adjust only if shell fragment IDs intentionally improved and preserve backward selectors expected by downstream views). Confirm clean compile & valid CSS/Thymeleaf syntax.

---

### Task 11: Storefront Overhaul — 14 Templates & 11 ESM Scripts (`w3-ui-storefront`)

*Runs in parallel with Tasks 12 and 13 after W3-A completes.*

**Files:**
- Modify Templates: `home.html`, `wine-list.html`, `wine-details.html`, `cart.html`, `checkout.html`, `orders.html`, `order-detail.html`, `order-confirmation.html`, `payment-session.html`, `user-profile.html`, `login.html`, `register.html`, `forgot-password.html`, `reset-password.html`
- Modify/Create JS: `store-nav.js`, `wine-filters.js`, `wine-filter-drawer.js`, `cart-client.js`, `cart-drawer.js`, `checkout-map.js`, `order-tracking-page.js`, `order-tracking-realtime.js`, `ai-assistant.js`, `wishlist.js` (new), `notifications-client.js` (new)

**Interfaces:**
- Consumes: `store-shell.html`, `tokens.css`, `storefront.css`, `theme-toggle.js`, `dialog-kit.js`, `/api/vouchers/validate`, `/api/wishlist/**`, `/api/profile/addresses`, `/api/notifications/**`, `/api/live/orders/{id}/stream`, `/orders/{id}/invoice`, `/orders/{id}/returns`, `/orders/{id}/reorder`.

- [ ] **Step 1:** Refactor `home.html` (editorial Maison de Vins hero + Bento collection strip + 5 wine styles including `"Traditional"` + provenance story), `wine-list.html` (`@container` wine grid + instant `<dialog>`/`popover` filter drawer + Quick View modal + Wishlist heart toggle), and `wine-details.html` (tasting notes pyramid, stock across cellar warehouses, wishlist toggle, verified buyer reviews, re-order/add-to-cart).
- [ ] **Step 2:** Refactor `cart.html`, `cart-drawer.js`, `cart-client.js` (optimistic stepper + native `<dialog closedby="any">` cart drawer) and `checkout.html`, `checkout-map.js` (Saved Address selector + Voucher code apply preview + Haversine shipping fee breakdown + Leaflet pin picker + deterministic COD/VNPay/Stripe cards preserving `CheckoutTemplateSelectionStateTest`).
- [ ] **Step 3:** Refactor `orders.html`, `order-detail.html`, `order-confirmation.html`, `payment-session.html`, `order-tracking-page.js`, `order-tracking-realtime.js` (Shopee-style progress timeline via SSE `Last-Event-ID` + exponential backoff, customer-only unmasked `decryptedOtp` card preserving `OrderDetailOtpVisibilityTemplateTest`, Commercial Invoice PDF/XLSX buttons, Re-order button, Return Request `<dialog>`).
- [ ] **Step 4:** Refactor `user-profile.html` (Saved Address Book CRUD + Wishlist grid + profile prefs), `login.html`, `register.html`, `forgot-password.html`, `reset-password.html`, and upgrade `ai-assistant.js` (live grounded Sommelier wine card renderer with quick Add-to-Cart) + `wishlist.js` + `notifications-client.js` + `store-nav.js`.
- [ ] **Step 5:** Run `./mvnw test -Dtest=CheckoutTemplateSelectionStateTest,OrderDetailOtpVisibilityTemplateTest` — confirm `Exit code: 0`.

---

### Task 12: Admin Control Center Overhaul — 17 Templates & 4 ESM Scripts (`w3-ui-admin`)

*Runs in parallel with Tasks 11 and 13 after W3-A completes.*

**Files:**
- Modify Templates: `admin-dashboard.html`, `admin-inventory.html`, `admin-orders.html`, `admin-payments.html`, `admin-shipment-form.html`, `admin-shipments.html`, `admin-shipper-form.html`, `admin-shippers.html`, `admin-user-form.html`, `admin-users.html`, `admin-wine-import.html`, `wine-form.html`, `admin-profile.html`
- Create Templates: `admin-vouchers.html`, `admin-returns.html`, `admin-cod-batches.html`, `admin-audit-logs.html`
- Modify/Create JS: `admin-charts.js`, `admin-excel-import.js`, `admin-live-notifications.js`, `admin-ops.js` (new)

**Interfaces:**
- Consumes: `admin-shell.html`, `tokens.css`, `admin.css`, `theme-toggle.js`, `dialog-kit.js`, `/admin/wines/**`, `/admin/vouchers/**`, `/admin/returns/**`, `/admin/audit-logs`, `/api/admin/excel/**`, `/api/live/admin/notifications`, `/api/notifications/**`.

- [ ] **Step 1:** Overhaul `admin-dashboard.html`, `admin-charts.js`, `admin-live-notifications.js` (KPI executive grid, live SSE alerts for paid orders / late `REFUND_REQUIRED` / low-stock warnings, clean revenue & order status visualizations).
- [ ] **Step 2:** Overhaul catalog & inventory control screens (`wine-form.html` posting to canonical `/admin/wines/**` with `"Traditional"` wine type option & local `/images/` picker, `admin-wine-import.html`, `admin-excel-import.js`, `admin-inventory.html`, `admin-orders.html` with settle COD + PDF/XLSX invoice actions, `admin-payments.html` highlighting `REFUND_REQUIRED`).
- [ ] **Step 3:** Overhaul logistics & user screens (`admin-shipments.html` keeping raw OTP masked per `AdminShipmentOtpMaskingTemplateTest`, `admin-shipment-form.html`, `admin-shippers.html`, `admin-shipper-form.html`, `admin-users.html`, `admin-user-form.html`, `admin-profile.html`).
- [ ] **Step 4:** Build new V3 management consoles (`admin-vouchers.html`, `admin-returns.html`, `admin-cod-batches.html`, `admin-audit-logs.html`, `admin-ops.js`) with native confirmation `<dialog>` and diff badges.
- [ ] **Step 5:** Run `./mvnw test -Dtest=AdminShipmentOtpMaskingTemplateTest` — confirm `Exit code: 0`.

---

### Task 13: Shipper Mobile Cockpit PWA (`w3-ui-shipper`)

*Runs in parallel with Tasks 11 and 12 after W3-A completes.*

**Files:**
- Modify Template: `src/main/resources/templates/shipper-dashboard.html`
- Modify JS: `src/main/resources/static/js/shipper-mobile-actions.js`

**Interfaces:**
- Consumes: `shipper-shell.html`, `tokens.css`, `shipper.css`, `manifest.webmanifest`, `sw-shipper.js`, `dialog-kit.js`, `/shipper/**`, `/api/live/shipper/{id}/stream`.

- [ ] **Step 1:** Rebuild `shipper-dashboard.html` on `shipper-shell.html` with mobile-first thumb dock (Active Route / POD & OTP / COD Batch Wallet), live GPS telemetry banner, and customer call/map links without leaking PII unnecessarily.
- [ ] **Step 2:** Upgrade OTP modal to use a single accessible `<input type="text" autocomplete="one-time-code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6">` inside native `<dialog closedby="any">`, paired with POD camera/gallery file upload preview and COD batch submission drawer in `shipper-mobile-actions.js`.
- [ ] **Step 3 (Gate W3-G):** Merge W3-B & W3-C cleanly after completion and run full `./mvnw test` including `ThymeleafTemplateRedesignVerificationTest` (updating only obsolete old CSS class assertions in that test if needed in W4; prefer compatibility or coordinate cleanly). Confirm `Exit code: 0`.

---

## Wave 4 — Full Verification, Live Browser CDP & Code Review (`W4`)

### Task 14: End-to-End Test Suite & Live Browser Verification (`w4-verify-polish`)

**Files:**
- Modify/Create: `src/test/java/com/strongwine/strongwine/ui/ThymeleafTemplateRedesignVerificationTest.java` (sync only verified V3 design tokens/classes if modified)
- Create/Extend: `src/test/java/com/strongwine/strongwine/e2e/EndToEndCommerceFlowTest.java` (or `StrongWineV3E2ETest.java`)

- [ ] **Step 1:** Extend E2E integration test verifying the V3 flow end-to-end: Voucher quote -> Address Book -> Checkout (`subtotal - discount + shippingFee` stored in `DECIMAL(18,2)`) -> Multi-warehouse Haversine allocation -> Invoice PDF/XLSX -> Return request & restock -> Audit log entries.
- [ ] **Step 2:** Run the complete Maven test suite (`./mvnw test`) — verify 0 failures, 0 errors, 0 skipped tests.
- [ ] **Step 3:** Boot StrongWine locally against live MS SQL Server (`spring.jpa.hibernate.ddl-auto=validate`, verify `V17` + `V18` applied cleanly and Hibernate validates all entities with zero error).
- [ ] **Step 4:** Exercise live app via Browser/CDP subagent across `USER`, `ADMIN`, `SHIPPER` personas in both `cellar` (dark) & `ivory` (light) themes at desktop (`1440x900`) and mobile (`390x844`):
  - Capture zero console errors, zero uncaught page errors, zero 5xx network errors, zero horizontal overflow.
  - Verify wine catalog (`Traditional`), wishlist, voucher checkout, live SSE tracking + PDF invoice download, return modal, admin audit/voucher/COD/OTP-masking screens, shipper single `autocomplete="one-time-code"` + POD + COD batch UI.

---

### Task 15: Two-Axis Code Review & Final Audit (`w4-verify-polish`)

- [ ] **Step 1:** Run `git status` and `git diff` across all modified and untracked files.
- [ ] **Step 2:** Execute two-axis code review:
  1. **Standards Review:** No duplicated logic, shallow nesting, early returns, explicit typed exceptions, zero `com.lowagie` leftovers, zero plaintext secrets, real runtime implementations only.
  2. **Spec Fidelity Review:** Verify every item in Parts 1–3 and Frozen Contracts matches implementation + test evidence.
- [ ] **Step 3:** Run final `./mvnw test` after any review cleanups and record exact command outputs and remaining risks (`NOT RUN` items if any) before reporting completion.

---

## Self-Review Results (writing-plans Checklist)

1. **Spec Coverage:** Every item from `docs/superpowers/specs/2026-11-13-strongwine-v3-super-plan-design.md` (Parts 1–3 UI/UX overhaul, all bugfixes/hardening, all 7 major V3 features, `V17`/`V18`, Frozen Contracts, Wave W0..W4 parallel table, Modern Web Guidance) maps directly to Tasks 1–15.
2. **File/Step Executability:** All file paths verified against current repository layout; Maven OpenPDF `3.0.5` (`org.openpdf.*`) verified; SQL Server constraints verified (`V2..V16` have zero blocking indexes/checks on the 4 `V18` money columns). Disjoint Wave file locks verified (zero overlapping paths across concurrent workers within W1, W2, or W3-B/C).
3. **Contract Consistency:** `OrderService.createPendingOrder` 10-arg signature, `CodSettlementPort` (`settleCodOrder`), `ShipmentOrderLifecyclePort` (`cancelShipmentForOrder`, `decryptOtpForOrderView`), `VoucherPricingPort` + `VoucherValidationResult`, shared events (`LowStockAlertEvent`, `AdminAlertEvent`), `SseNotificationService()` zero-arg ctor, `AdminAuditLogService.log`, `/admin/wines/**`, and `OrderInvoiceService` align identically across Producer and Consumer tasks.
4. **Review Focus Coverage:** All 6 top failure modes have explicit pinning test assertions in their owning tasks.
5. **Proportionality:** Single source of truth per wave; zero speculative abstractions or unnecessary files.
