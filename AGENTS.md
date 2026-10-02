# AGENTS.md — StrongWine Engineering & Architecture Contract

These rules apply to every coding agent, subagent, and engineer operating on the **StrongWine** codebase (`Wine-website`). Current code, tests, and explicit runtime verification override past historical documents.

---

## 1. Core Mission & Strict Database Integrity Contract

1. **System Identity**: StrongWine is a Java 21 / Spring Boot web application for luxury wine commerce, multi-warehouse inventory management, and real-time delivery logistics.
2. **STRICT DATABASE INTEGRITY (CRITICAL RULE)**:
   - The existing Microsoft SQL Server database schema and data must be **strictly preserved intact**.
   - **ABSOLUTELY NO** destructive DDL operations: no `DROP TABLE`, `DROP COLUMN`, `ALTER COLUMN` that breaks existing data, or removing constraints.
   - `spring.jpa.hibernate.ddl-auto=validate` is strictly enforced. Any entity mapping change must match the existing database schema without causing Hibernate validation failures.
   - Any new feature or optimization must be **purely additive** and 100% backward-compatible.
3. **Real Implementations Only**:
   - Production runtime code must always use real implementations. Mocks are strictly prohibited in runtime code and are allowed only in isolated unit tests (`src/test/java`).
   - Real-time systems, payments, database transactions, and background tasks must never be mocked or faked in production.
   - Real operations must never catch exceptions silently to claim false success. Failures must fail explicitly with typed diagnostics.

---

## 2. Real-Time Transport Contract (Shopee / ShopeeFood Style)

1. **Real-time Event Streaming via Server-Sent Events (SSE)**:
   - As per architectural alignment, real-time live tracking (Order status, Shipper progress, Admin dashboard alerts) uses **Server-Sent Events (`SseEmitter`)** over HTTP.
   - Client connections listen on dedicated endpoints (e.g., `/api/live/orders/{orderId}`, `/api/live/admin/notifications`).
   - Connection lifecycle must handle:
     * Heartbeat / keep-alive pings (every 15–25 seconds) to prevent reverse proxy/firewall timeouts.
     * Graceful client disconnects (`onCompletion`, `onTimeout`, `onError`) with proper memory cleanup in the emitter registry.
     * Automatic client-side reconnection with exponential backoff and `Last-Event-ID` re-synchronization.
2. **Event Publishing**:
   - Status transitions (Order placed, paid, packed, shipping, delivered; low stock warnings) trigger typed Spring Application Events (`OrderEvent`, `ShipmentEvent`, `InventoryEvent`) dispatched asynchronously via the SSE broker.

---

## 3. Object-Oriented Programming (OOP) & Clean Architecture Standards

1. **Design Patterns**:
   - **Strategy Pattern**: Used for file import/export (`DataImportStrategy<T>`, `DataExportStrategy<T>`), separating Excel (`Apache POI`), CSV, and JSON parsing logic from controllers.
   - **Specification / Criteria Pattern**: Used for wine searching and filtering. Encapsulate dynamic compound criteria (`Specification<Wine>`) to eliminate nested if-else spaghetti in repositories and controllers.
   - **Template Method & Factory Pattern**: Used for generating formatted executive reports and Excel workbooks (`ExcelReportTemplate`, `MonthlyRevenueReport`, `InventoryReport`).
   - **Facade / Service Layer**: Maintain clean boundaries between Web Controllers, Application Services, Domain Entities, and JPA Repositories.
2. **Error Handling**:
   - Centralized exception management using `@RestControllerAdvice` and `@ControllerAdvice`.
   - Typed domain exceptions (e.g., `StrongWineException`, `ResourceNotFoundException`, `ExcelImportValidationException`, `InsufficientStockException`).
   - Meaningful, structured JSON error responses for APIs and localized alert messages for SSR pages.

---

## 4. Frontend & UI/UX Aesthetic Standards (Maison de Vins)

1. **Design Philosophy**:
   - Aesthetic direction: **Luxury Dark Wine Boutique ("Maison de Vins")**.
   - Palette: Deep charcoal/noir background (`#0c0a08`), rich burgundy/crimson accents (`#8b1a1a`, `#a83030`), warm metallic champagne gold (`#c9a84c`, `#e2be72`), and warm ivory typography (`#f0e8da`).
2. **Zero "AI Slop" Rule**:
   - **NO** generic AI slop: no garish neon purple gradients on white backgrounds, no misaligned generic icons, no cluttered cards without clear hierarchy.
   - Use crisp, bespoke SVG icons or refined vector glyphs tailored to wine commerce (grape cluster, oak barrel, tasting glass, decanter, vintage cork, seal).
   - High typographic hierarchy: Editorial serif display font (`Playfair Display` / `Cormorant Garamond`) paired with modern, legible body font (`Plus Jakarta Sans` / `Lato`).
3. **User Experience (UX) Ergonomics**:
   - Instant visual feedback: Skeleton loading states, optimistic UI updates for cart adjustments, clear toast notifications.
   - Multi-criteria filter drawer with price range sliders, instant pill tags, and quick-view modals for wine details without page navigation.
   - Visual Order Tracking: A step-by-step progress timeline displaying real-time updates and secure delivery OTP status.

---

## 5. Security & Data Integrity

1. **Spring Security 6 & RBAC**:
   - Role boundaries: `ROLE_ADMIN`, `ROLE_USER`, `ROLE_SHIPPER`.
   - CSRF protection must be retained on all state-altering requests (POST, PUT, DELETE).
   - Password encryption with BCrypt; sensitive keys strictly injected from environment variables (`.env`).
2. **Transactional Rigor**:
   - All state mutations (orders, inventory reservations, shipments) must be properly annotated with `@Transactional`.
   - Optimistic locking (`@Version`) on inventory to prevent race conditions during high-concurrency order placement.

---

## 6. Verification & Quality Gates

1. Every code change must be verified by running:
   ```bash
   ./mvnw compile -DskipTests
   ```
2. Any entity or database-related changes must be validated against Hibernate:
   - Ensure `spring.jpa.hibernate.ddl-auto=validate` passes without exception on startup.
3. Review git diff before claiming completion:
   ```bash
   git status
   git diff
   ```
4. Never assume success without evidence from compilation logs and runtime tests.
