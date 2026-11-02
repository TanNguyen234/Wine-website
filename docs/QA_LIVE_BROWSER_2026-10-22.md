# Live browser QA — 2026-10-22

## Scope and evidence

This is a running record of real browser and database-backed checks for the StrongWine application. The browser sessions used the local application on ports `8089`–`8095`; all connected to SQL Server database `winestore_2`. The app loaded `.env` without printing its values. Test runtimes disabled mail, Flyway, and startup data/dispatch jobs; Hibernate started with schema validation. No database schema migration or destructive DDL was run.

Evidence labels: **Live** means a real Chrome page or request against the running app; **Integration** means the running application used its real SQL Server/provider boundary; **Unit** means a local automated test. No mock was used in the runtime flows below.

## Role and CRUD checks

| Role / area | Live evidence | Result |
|---|---|---|
| Customer — catalog and cart | Loaded real catalog/detail pages; searched, filtered, sorted, opened quick view; added, changed quantity, and removed cart items. | Passed; no app console errors observed. |
| Customer — checkout | Used the user-provided delivery coordinates and completed one Stripe test payment, creating a real test order and shipment. | Passed. The payment was not repeated. |
| Customer — payment cancel route | In an authenticated customer Chrome context, opened `/payment/cancel`; the app redirected to `/cart` and rendered “Bạn đã hủy phiên thanh toán Stripe”. The handler only returns a flash result; it does not contact Stripe or mutate an order/payment. | Route redirect passed. A real Stripe Checkout session cancellation was not exercised. |
| Customer — review CRUD | Submitted a temporary review through the rendered form; updated it through the authenticated browser API (`200`); deleted it (`204`); confirmed lookup returned `404`; reloaded the page and confirmed the review was absent. | Passed; test review removed. |
| RBAC — customer/admin/shipper | Customer could read an owned order (`200`) but received `403` for admin inventory and all-orders endpoints. Shipper received `403` for admin inventory and an order outside the shipper's role scope. Admin's inventory endpoint returned `200`. | Passed for the checked boundaries. |
| RBAC — live GET API sweep | In three authenticated Chrome contexts on `8091`, public `/api/wines`, `/api/wines/search`, and `/api/reviews` returned `200` for every role. Admin-only `/api/orders`, `/api/users`, `/api/admin/products`, `/api/admin/categories`, inventory summary/low-stock, Excel template, and inventory export returned `200` for admin and `403` for customer and shipper. The customer could read its own order list and order `#28` (`200`); an admin-verified order owned by another account (`#1`) returned `403` to both customer and shipper. Admin could read both (`200`). The browser recorded only status/content type and ownership equality; no personal fields or response bodies were reported, and no state changed. | Passed for this read-only route subset; this is not an exhaustive REST-route audit. |
| RBAC — SSE streams on patched runtime | Customer: admin stream `403`, owned order stream `200`, foreign order stream `404`, shipper stream `403`. Shipper: admin stream `403`, assigned shipper stream `200`, another shipper stream `404`, unrelated order stream `404`. Admin could open the admin, order, and shipper streams (`200`). Each allowed stream delivered its real `init` event and was then aborted; no business data was changed. | Passed after the access-control fix. |
| SSE — role interfaces | On fresh runtime `8091`, Chrome loaded the admin, customer order `#28`, and shipper dashboard in separate authenticated contexts. The admin page loaded `admin-live-notifications.js` and opened `/api/live/admin/notifications` (`200`, badge `Trực tuyến`); the customer page opened `/api/live/orders/28/stream` (`200`, label `Đang cập nhật trực tiếp`); the shipper page opened `/api/live/shipper/7/stream` (`200`, badge `Trực tuyến`). Admin and shipper scripts loaded with `200`; no order/customer data was read from the page. | Passed; no business mutation. |
| Admin — wine CRUD | Created, edited, and soft-deleted a temporary wine through the real admin UI. | Passed; removed from the public catalog. |
| Admin — wine REST CRUD | In authenticated Chrome on patched runtime `8092`, created a wine through `POST /api/wines` with an active database category, read it (`200`), changed its name/price through `PUT` (`200`), and confirmed the same category remained set. Deleted it (`204`); follow-up GET returned `404`. An invalid category ID returned `400` and created no row. | Passed; the temporary wine was soft-deleted. |
| Admin — user REST CRUD | In authenticated Chrome on patched runtime `8092`, created a temporary account through `/api/users` (`201`), read (`200`), edited (`200`), and deleted it (`204`); follow-up GET returned `404`. The response DTO omitted password fields on create, read, and update. | Passed; temporary account removed, no email sent. |
| Admin — user CRUD | Created, edited, and deleted a temporary user through the real admin UI. | Passed; baseline user count restored. |
| Customer — registration/login/logout | On runtime `8092`, registered a temporary account in Chrome, confirmed it existed through the admin API, logged in through the real form, and logged out; then deleted it from the admin UI and confirmed its API lookup returned `404`. The first pass exposed a missing registration-success flash. After the fix, a fresh runtime `8093` displayed the exact success message on `/login`; that temporary account was also deleted through the admin UI and follow-up API lookup returned `404`. Both test registrations used `.invalid` email addresses; no email was sent. | Passed on the current build; temporary accounts removed. |
| Admin — shipper CRUD | Created, edited, and deleted a temporary shipper through the real admin UI. Also toggled availability for an idle real shipper, verified the changed value, then restored and verified its original value. | Passed; baseline shipper/profile counts and original availability restored. |
| Admin — category CRUD | Created a temporary category (`201`), updated it (`200`), soft-deleted it (`204`), then confirmed lookup returned `404` and it was absent from the active list. | Passed. The schema retains the soft-deleted row, as designed. |
| Admin — Excel import | Uploaded the real 20-row workbook `sample-data/strongwine-admin-bulk-import-20-wines.xlsx` through the browser file input. Preview returned `200`: 20 valid, 0 errors, 20 inserts. Confirmed the real import in the UI; the import endpoint returned `200` and reported 20 inserted wines. SQL-backed reads showed 20 wines, 4 new categories, and 40 inventory rows. Restored all imported warehouse quantities to their pre-import value of zero, then soft-deleted wine IDs `20`–`39` and category IDs `6`–`9`; follow-up lookups returned `404`, active totals returned to 12 wines/3 categories, and inventory overview returned to 24 rows. | Passed; active catalog/category/inventory counts restored. Soft-delete rows and audit history remain. |
| Admin — screen smoke | In the authenticated Chrome admin context on `8091`, opened the catalog, wine create form, Excel import, inventory, orders, payments, shipments, shippers, users, then dashboard. Every document request returned `200` and rendered the expected page title. No records were changed during this pass. | Passed for page availability. |
| Customer — page smoke | In the authenticated customer Chrome context on `8091`, live GET checks returned `200` for home, catalog, cart, checkout, orders, registration, and forgot-password pages. Reset-password without a token redirected to forgot-password (`200`). These checks did not submit forms or read page payloads. | Passed for page availability; reset-password correctly requires its token path. |
| Admin — order CRUD | Created one temporary unpaid order through the authenticated customer API (`201`), updated it through the admin API without changing its total (`200`), and deleted that same order through the admin API (`204`). A follow-up GET returned `404`. The related wine inventory reservation counts matched the pre-test values afterward. No payment or shipment was created. | Passed; temporary order removed and reservation released. |
| Admin — inventory/payments/shipments | On the patched runtime, imported one unit and exported it back through the admin inventory API (`200` each): quantity changed from 58 to 59 and back to 58. Also exercised the `/api/inventory/import` and `/api/inventory/export` aliases using a real available stock row: `50` → `51` → `50`, with final reserved quantity still `0`. Response bodies contained only flat DTO fields, no password/hash or nested `orderItems`. The resulting audit entries remain as expected; live history also contains an earlier matching QA import/export pair. Opened and canceled an inventory adjustment dialog without changing stock. Filtered shipments by order keyword and got one matching row. Saved the current shipment edit form with unchanged recipient/status values and confirmed a successful redirect. Inventory overview and low-stock GET APIs returned `200`; overview returned 24 safe summary rows. | Passed for the actions listed. |
| Shipper — shipment lifecycle | On the assigned shipment, used the real pickup, start-delivery, resend-email, and OTP-completion controls. The user confirmed the new message arrived, then supplied its OTP; the real shipper form accepted it and showed `Giao hàng thành công`. After reload, admin showed shipment `16` / order `28` as `Hoàn tất`, and the customer order detail showed successful OTP verification and completed handoff. | Passed for pickup, delivery, email resend/receipt, OTP verification, and completion. The OTP value is not recorded. |

## Bug fixed during QA

The admin shipment update POST returned a success flash named `success`, while `admin-shipments.html` only rendered `message`. A real edit redirect showed no success notice. Added a regression assertion, confirmed it failed before the fix, added rendering for `success`, then confirmed the test passed. A fresh runtime and Chrome session rendered `Cập nhật đơn giao hàng thành công` after saving unchanged shipment values.

Changed files for this fix:

- `src/main/resources/templates/admin-shipments.html`
- `src/test/java/com/strongwine/strongwine/ui/ThymeleafTemplateRedesignVerificationTest.java`

## Registration success flash fixed during QA

A real Chrome registration on `8092` created a SQL-backed account and redirected to `/login`, but the page did not render the `success` flash attribute already set by `AuthController`. Added a focused template regression assertion, confirmed it failed before the fix, and rendered the flash in `login.html`. On a fresh runtime `8093`, a new real Chrome registration redirected to `/login` and rendered `Đăng ký thành công. Vui lòng đăng nhập.` The temporary account was deleted from the admin UI and its follow-up API lookup returned `404`. The test used an `.invalid` email address; no email was sent.

Changed files for this fix:

- `src/main/resources/templates/login.html`
- `src/test/java/com/strongwine/strongwine/ui/ThymeleafTemplateRedesignVerificationTest.java`

## Login autocomplete warning fixed during QA

Chrome DevTools on the live `/login` page reported that username/password inputs lacked autocomplete purposes. Added `username` and `current-password` autocomplete values and corresponding template regression assertions. The focused test failed before the template change and passed afterward. On the fresh runtime `8094`, Chrome read both values from the real rendered form; the login page loaded with `200` and no console messages.

Changed files for this fix:

- `src/main/resources/templates/login.html`
- `src/test/java/com/strongwine/strongwine/ui/ThymeleafTemplateRedesignVerificationTest.java`

## Excel import and cleanup verified during QA

The real workbook in `sample-data/strongwine-admin-bulk-import-20-wines.xlsx` contained 20 product rows and 4 categories absent from the active catalog. Chrome upload preview reported 20 valid rows and no errors. After explicit confirmation, the live import returned `200` and inserted all 20 products. SQL-backed admin reads showed the corresponding 4 new active categories and 40 inventory rows (one row per wine for each of two warehouses). The imported warehouse quantities were exported back to zero; all 20 temporary wines and 4 categories were soft-deleted through the application. Follow-up reads returned `404` for those IDs, active catalog/category counts returned to 12/3, and inventory overview returned to 24 rows. Application soft-delete rows and import/export audit records remain in the database.

## Wine REST CRUD category mapping fixed during QA

Live Chrome calls to `/api/wines` exposed that create and update returned success while silently dropping a valid `categoryId`. The first temporary product showed `201`/`200` but `categoryId: null` on both writes and reads; it was soft-deleted and its follow-up GET returned `404`.

Added active-category resolution to the shared DTO-to-entity mapper. The two regression tests failed before the fix and passed afterward. On a fresh runtime using the real SQL Server database, Chrome verified create/read/update retained active category `1`; delete returned `204` and a later GET returned `404`. A candidate category confirmed absent by the live category API was rejected with `400` and no product was created.

Changed files for this fix:

- `src/main/java/com/strongwine/strongwine/controller/api/WineApiController.java`
- `src/test/java/com/strongwine/strongwine/controller/api/WineApiControllerTest.java`

## Browser favicon 404 fixed during QA

The first live admin load requested `/favicon.ico` and returned `404` because the application did not declare a favicon. Added a branded SVG wineglass mark and linked it from the admin, storefront, and shipper heads. The template regression assertion failed before the change and passed afterward. On the fresh `8091` runtime, each role page resolved `/favicon.svg` as `200 image/svg+xml`; Chrome decoded it successfully, and all three pages had no console errors.

Changed files for this fix:

- `src/main/resources/static/favicon.svg`
- `src/main/resources/templates/fragments/admin-shell.html`
- `src/main/resources/templates/fragments/store-shell.html`
- `src/main/resources/templates/shipper-dashboard.html`
- `src/test/java/com/strongwine/strongwine/ui/ThymeleafTemplateRedesignVerificationTest.java`

## Security issue fixed during QA

A live authenticated admin request to `GET /api/admin/inventory` showed that serializing `Inventory` entities included nested user credential data and recursively traversed order relationships. The endpoint was admin-only, but exposing password hashes in a response was unnecessary and could enable offline cracking if the response were captured or logged (moderate, P2).

Added `AdminInventoryDto` and mapped all four inventory response paths (overview, low stock, stock import, and stock export) to the flat DTO. The focused regression test constructs a related entity graph containing a dummy marker credential and checks that none of the response JSON contains password fields, the marker, or nested `orderItems`. The test failed against entity serialization and passed after the DTO change. The patched runtime was restarted on port `8089`; live checks of both GET endpoints confirmed the safe field set and no credential/hash/relationship markers.

Changed files for this fix:

- `src/main/java/com/strongwine/strongwine/controller/api/AdminInventoryApiController.java`
- `src/main/java/com/strongwine/strongwine/dto/AdminInventoryDto.java`
- `src/test/java/com/strongwine/strongwine/controller/api/AdminInventoryApiControllerTest.java`

## SSE authorization issue fixed during QA

A live customer session received `200 text/event-stream` from `/api/live/admin/notifications`. The controller also accepted arbitrary order and shipper IDs, so any authenticated account could subscribe outside its role or ownership scope.

Restricted admin and shipper stream paths in `SecurityConfig`, then added resource checks in the controller: order streams require the order owner or admin; shipper streams require the profile's shipper account or admin. Unauthorized resource IDs return `404` to avoid confirming their existence. A focused test compile failed against the old signatures before the guard was implemented; afterward all six controller tests passed. Live Chrome checks on the patched runtime confirmed the role/ownership status matrix above.

Changed files for this fix:

- `src/main/java/com/strongwine/strongwine/controller/api/SseNotificationApiController.java`
- `src/main/java/com/strongwine/strongwine/security/SecurityConfig.java`
- `src/test/java/com/strongwine/strongwine/controller/api/SseNotificationApiControllerTest.java`

## Missing success messages fixed and verified live

Source review found that several handlers set the `success` flash attribute while their target templates rendered only `message` and `error`. Added success alerts to `admin-dashboard.html`, `wine-details.html`, and `forgot-password.html`, and extended `ThymeleafTemplateRedesignVerificationTest` to cover those templates.

On the latest-source runtime `8095` (real SQL Server, Hibernate `ddl-auto=validate`; mail and startup/background jobs disabled), Chrome confirmed:

- Admin inventory import and export each showed a success alert. Main Warehouse stock for wine 12 moved from 57 to 58 and back to 57; reserved stock stayed 0.
- A temporary customer registered through the real form, saw the registration success flash, and signed in. The customer submitted a real review for wine 12; the product page showed the success alert and review.
- In that customer's authenticated browser session, the review API returned `200` for update and `204` for delete; subsequent reads returned `404`. Reloading the product page showed no review.
- The temporary customer was removed through the admin UI; `GET /api/users/25` returned `404`. The disposable customer session was logged out. Existing admin/customer/shipper sessions on `8091` were left open.
- The forgot-password success message was verified live on `8095` using an unregistered `.invalid` address; this did not contact SMTP.
- A reset-password visit with an invalid token redirected to forgot-password and rendered the expiration/invalid-link error; no reset or email action occurred.

No test review, test account, or inventory quantity from these checks remains changed in the live database.

## Additional admin product REST CRUD verified

On the authenticated admin Chrome session on port `8091`, the separate `/api/admin/products` endpoint completed a real SQL-backed CRUD cycle: create returned `201`, read returned `200` with category `2`, update returned `200` with the category retained, delete returned `204`, and follow-up read returned `404`. A create using a nonexistent category returned `400` and created no row. The temporary product (wine ID 40) was soft-deleted.

## Review API role and ownership checks

Using the original authenticated customer and shipper Chrome sessions on 8091, review ownership was checked through the real API. Creating a review for another existing user's ID returned 403 and inserted no row. The customer could create (201), read (200), update (200), and delete (204) its own temporary review; follow-up reads returned 404. Shipper attempts to update and delete that customer review both returned 403, and the customer confirmed the review was unchanged before exercising owner update/delete. Admin could update (200) and delete (204) a temporary customer review; follow-up read returned 404. No test review remains.

## Admin product write RBAC checks

Two temporary products were created by the admin on the real API. Customer and shipper sessions each attempted PUT and DELETE on their respective temporary product; both roles received 403 for both writes. The admin confirmed each product remained intact, then deleted them (204); follow-up reads returned 404. Product IDs 41 and 42 are soft-deleted.

## Category API CRUD and write authorization

Admin created temporary category ID 10 (201), read it (200), and updated it (200). Customer and shipper PUT and DELETE attempts returned 403; the category remained unchanged. Admin soft-deleted it (204), and lookup returned 404. The active category list returned to 3 entries with no QA category.

## User API write authorization

Admin created a temporary USER through POST /api/users (201); the response exposed only id, username, email, and role. Customer and shipper PUT attempts to promote it to ADMIN and DELETE attempts all returned 403. Admin confirmed its role remained USER, deleted it (204), and a follow-up lookup returned 404. Temporary user ID 27 is removed.

## Order API create, RBAC, update, and delete

The authenticated customer created a temporary order through POST /api/orders. It was PENDING/PENDING with one wine 12 item; no payment session or shipment is created by this code path. Shipper PUT and DELETE attempts returned 403. Admin read the order (200), updated it without changing its total (200), then deleted it (204); follow-up read returned 404. Wine 12 Main Warehouse returned to its baseline of 57 units, reserved 0, available 57. Temporary order ID 30 is removed.

## REST authentication route verified

A temporary customer created through the real registration form successfully used POST /api/auth/login (200, Bearer token returned) on runtime 8095. The token was used in the browser to access the authenticated reviews endpoint (200) and was not recorded. The test account was removed through the admin UI; GET /api/users/26 returned 404. No credential or token was saved in this report.

## Final live cleanup reconciliation

Admin API lookups returned 404 for temporary user IDs 25, 26, and 27 and soft-deleted product IDs 40, 41, and 42. The wine 12 review list contained no QA comments. Main Warehouse inventory for wine 12 was back at 57 units, reserved 0, available 57. The current admin shipment filter for PENDING_ASSIGNMENT returned 200 with zero eligible rows. Soft-delete tombstones and expected inventory audit history remain.

## Chrome DevTools console review after RBAC probes

Chrome DevTools console review after the role-page sweep on `8091`: admin, customer, and shipper showed only “Failed to load resource” entries matching deliberate invalid-category `400`, denied RBAC `403`, and post-delete `404` checks. No uncaught JavaScript exception was listed. The admin session loaded `favicon.svg` with `200`.

## Verification after the fixes

- `.\mvnw.cmd compile -DskipTests` — passed on the latest source (2026-10-22).
- Latest full suite rerun with the command and runtime flags below — 67 tests passed, 0 failures/errors after the template success-alert fixes.
- `./mvnw.cmd -Dtest=ThymeleafTemplateRedesignVerificationTest test` — 1 test passed.
- `./mvnw.cmd -Dtest=AdminInventoryApiControllerTest test` — 3 tests passed.
- `./mvnw.cmd -Dtest=SseNotificationApiControllerTest test` — 6 tests passed.
- `.\mvnw.cmd -Dtest=WineApiControllerTest test` — 2 tests passed after reproducing both failures before the fix.
- `.\mvnw.cmd compile -DskipTests` — build passed.
- `.\mvnw.cmd "-DargLine=-Xmx768m" "-Dapp.mail.enabled=false" "-Dspring.flyway.enabled=false" "-Dstrongwine.startup.initializers-enabled=false" "-Dstrongwine.shipment.backfill-on-startup=false" "-Dapp.shipment.reconcile.startup-enabled=false" "-Dapp.shipment.reconcile.web-enabled=false" "-Dapp.shipment.dispatch.enabled=false" test` — 67 tests passed, 0 failures/errors after the login autocomplete fix.
- The patched application started on port `8090` against real SQL Server with `ddl-auto=validate`; startup completed without schema validation errors. Admin, customer, and shipper sessions were logged in on isolated Chrome contexts. The previous `8089` runtime and sessions were left available.
- After the favicon change, the final source was restarted on port `8091` against the same real SQL Server database with `ddl-auto=validate`; startup completed without schema validation errors. Admin, customer, and shipper were logged in directly through Chrome on isolated contexts, and their UI EventSource connections and favicon resource were observed live. Mail, Flyway, startup initializers, backfill, reconciliation, and dispatch were disabled for this QA runtime.
- After the Wine API fix, a second runtime on port `8092` loaded the updated source and connected to the same SQL Server with `ddl-auto=validate`; startup completed without schema validation errors. The original admin/customer/shipper sessions on `8091` remained open. A real DB-backed admin session on `8092` verified the corrected category round trip and cleanup.
- After the registration flash fix, a third runtime on port `8093` loaded the updated source and connected to the same SQL Server with `ddl-auto=validate`; startup completed without schema validation errors. A real registration in Chrome rendered the success flash; the test account was removed through the authenticated admin UI.
- After the autocomplete fix, runtime `8094` loaded the updated source and connected to the same SQL Server with `ddl-auto=validate`; startup completed without schema validation errors. Chrome confirmed both autocomplete values and no console messages on `/login`.
- After the latest template success-alert fixes, runtime `8095` loaded the updated source and connected to the same SQL Server with `ddl-auto=validate`; startup completed without schema validation errors. Chrome verified live inventory import/export, customer registration/login, review CRUD, and forgot-password success feedback there.
- Chrome DevTools on `8093` showed no JavaScript errors on the resulting login page and surfaced the missing password autocomplete attribute as two related accessibility messages. That finding was fixed and rechecked on `8094` as documented above.
- Live inventory API check — `GET /api/admin/inventory` returned `200` with 24 rows and safe summary fields; `GET /api/admin/inventory/low-stock` returned `200` with 0 rows. Both responses had no password field, bcrypt hash, or `orderItems`.
- Live SSE access check — all eleven role/resource combinations returned the expected `200`, `403`, or `404` status. The three allowed channels delivered their expected init event; streams were then aborted. Fresh runtime `8091` verified that all three actual role pages initialize their EventSource clients and show a connected state. After the favicon fix, admin, customer, and shipper each loaded `/favicon.svg` (`200 image/svg+xml`) and had no console errors.
- `git diff --check` — passed after the report update; Git printed only existing LF/CRLF conversion warnings.

## Customer order write authorization recheck

On the authenticated customer Chrome session on `8091`, created a real one-item unpaid order (`201`, `PENDING/PENDING`). The same customer then attempted to update and delete their own order; both requests returned `403`. Admin confirmed the order remained pending, deleted the temporary order (`204`), and the follow-up lookup returned `404`. Wine 12 Main Warehouse returned to `57` current, `0` reserved, `57` available. No payment or shipment was created, and no CSRF token or personal order fields were recorded.

## Direct page authorization by role

From the authenticated customer and shipper sessions on `8091`, read-only GET requests to `/admin`, `/admin/wines/import`, `/admin/inventory`, `/admin/orders`, `/admin/payments`, `/admin/shipments`, `/admin/shippers`, `/admin/users`, the user/shipper create pages, and `/wines/admin/create` all returned `403`. For `/shipper/dashboard`, customer returned `403`, while shipper and admin returned `200`. Only status codes and final paths were captured; response bodies were not read.

## Shipment deletion eligibility guard

Admin attempted to delete the QA shipment `16` for order `28` while its live status was `Đang giao`. The real POST redirected to the shipment list and rendered the expected “only pending assignment shipments may be deleted” error. A follow-up filtered list still contained the shipment with status `Đang giao`. This verifies the rejection guard; it does not verify deletion of an eligible `PENDING_ASSIGNMENT` shipment, and no OTP data was read.

## User-authorized OTP resend attempt

After the user asked for another delivery check, the authenticated shipper session made one POST to `/shipper/shipments/16/resend-otp`. The app redirected back to the shipper dashboard and rendered its resend-failure message. Runtime `8091` was launched with `app.mail.enabled=false`; `ShipmentOtpEmailService` rejects delivery on this flag before resolving the recipient or calling SMTP. A follow-up admin read returned delivery status `Đang chờ gửi` and shipment status `Đang giao`, consistent with the request being rejected before mail delivery. No OTP value was read or submitted, and the request was not repeated.

## User-authorized OTP resend with mail enabled

A separate local runtime on port `8096` loaded `.env`, connected to the real SQL Server with Hibernate schema validation, and ran with `app.mail.enabled=true`; migrations, initializers, backfill, reconciliation, and dispatch remained disabled. The admin used the real shipment form to submit one resend for shipment `16` / order `28`. The app reached SMTP but returned `Authentication failed`. A read-only SQL check confirmed `otp_delivery_status=FAILED`, `otp_sent_at=NULL`, and an `OTP_RESENT/FAILED` audit record for the saved shipping recipient after three SMTP attempts. No delivery is confirmed. The recipient address and OTP value are not stored in this report.

The live failure showed that authentication errors were retried three times even though the credentials cannot recover by retrying. ShipmentOtpEmailService now treats MailAuthenticationException as non-retryable while retaining configured retries for other mail failures. A focused no-mock policy test and the full unit suite pass; no further email was attempted with the expired credential.

Verification of the retry-policy change: `.\mvnw.cmd "-Dtest=ShipmentOtpEmailServiceTest,ShipmentServiceTest" test` passed 18 tests; `.\mvnw.cmd compile -DskipTests` passed; the full suite with startup and mail side effects disabled passed 68 tests with 0 failures/errors. `git diff --check` passed for the changed source and test files.

## Remaining scope / not verified

- The initial mail-enabled resend on 8096 used the expired credential and failed at Gmail authentication. After the user supplied replacement settings, application.properties was updated to prefer ACCOUNT_EMAIL_ENABLED, EMAIL_FROM, SMTP_HOST, SMTP_PORT, EMAIL_USER, and EMAIL_PASS, with the prior names retained as fallbacks. .env was updated with those keys; the new App Password was stored without display-group spaces, and the old password aliases were removed. The updated runtime loaded 35 .env variables, passed Hibernate schema validation, and did not override the new mail-enabled flag on the command line. In Chrome, the admin submitted one resend for shipment 16 / order 28; the page showed the success message and the shipment row showed OTP status sent. The application mail call completed without error; actual inbox receipt awaits user confirmation. No OTP value or recipient contact detail is recorded. Fresh .\mvnw.cmd compile -DskipTests passed. The subsequent full suite attempt ran 61 tests with 0 assertion failures/errors, then the forked JVM terminated after a native memory allocation failure while starting StrongwineApplicationTests; this latest suite run is incomplete.
- The user confirmed receipt by replying with the new email, then the authenticated shipper submitted that value through the real six-field OTP form on runtime `8096`. Chrome showed “Giao hàng thành công”, shipment `16` / order `28` changed to `Hoàn tất`, and the shipper dashboard showed zero active deliveries. After reload, the admin shipment list also showed `Hoàn tất`. The customer was re-authenticated on `8096` and its own order detail showed successful OTP verification and completed handoff. Customer and shipper EventSource requests returned `200`; DevTools reported no console errors on the customer, shipper, and admin pages. The earlier `8091` customer tab had expired its session on reload. No OTP value is recorded here.
- Admin shipment deletion is limited to shipments in `PENDING_ASSIGNMENT`; a fresh `8091` admin filter request for that status returned `200` with zero shipment rows. The ineligible-delete rejection passed live, but deletion of an eligible real record remains unverified. No second paid order was created and no active or paid shipment was deleted to manufacture a test case.
- Full exhaustive verification of every REST route and every payment/order mutation is still **not complete**. Temporary order create/update/delete and the app's no-session `/payment/cancel` redirect were verified, but cancellation of a real Stripe Checkout session and other payment mutations were not exhaustively repeated. OTP-gated delivery completion is now verified live as documented above.
- Integration preflight read `.env` without printing values and classified `STRIPE_SECRET_KEY` as `test_secret` and `STRIPE_PUBLIC_KEY` as `test_publishable`. This config check is not provider-call evidence; no additional Checkout session was created or canceled.
- The earlier user approval covered one Stripe test payment, used for order `28`. A new real Checkout cancellation session or another paid disposable order would be an additional external/provider and database side effect. A failure-delivery or eligible-shipment-deletion test also has no disposable live shipment; mutating order `28` or another active customer's shipment risks corrupting real state. These branches remain NOT RUN pending a safe disposable target and/or explicit authorization.
- CRG graph tools were unavailable; controller routes were inventoried from source with `rg` and validated against live pages where safe.

No credentials, OTP values, or customer contact details are recorded here.
