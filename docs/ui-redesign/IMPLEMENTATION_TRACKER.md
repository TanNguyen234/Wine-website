# StrongWine UI/UX Redesign — Implementation Tracker (26/26 Templates)

**Branch:** `feat/strongwine-modernize-superplan`  
**Design System:** `Modern Wine Maison` (Storefront) · `Control Center` (Admin) · `Delivery Mobile` (Shipper)  
**Source Handoff:** `D:\Download\StrongWine_UI_UX_Antigravity_Handoff\StrongWine-UI-Handoff`

| # | Khu vực | Template Thymeleaf | Route thực tế | Mockup Key | Controller | Trạng thái |
|---|---|---|---|---|---|---|
| 1 | Storefront | `home.html` | `/home` | `home` | `HomeController.home` | `VERIFIED` |
| 2 | Storefront | `wine-list.html` | `/wines` | `catalog` | `WineController.wineList` | `VERIFIED` |
| 3 | Storefront | `wine-details.html` | `/wines/{id}` | `details` | `WineController.wineDetails` | `VERIFIED` |
| 4 | Storefront | `cart.html` | `/cart` | `cart` | `CartController.viewCart` | `VERIFIED` |
| 5 | Storefront | `checkout.html` | `/cart/checkout` | `checkout` | `CartController.showCheckout` | `VERIFIED` |
| 6 | Storefront | `orders.html` | `/orders` | `orders` | `OrderController.myOrders` | `VERIFIED` |
| 7 | Storefront | `order-detail.html` | `/orders/{id}` | `tracking` | `OrderController.orderDetail` | `VERIFIED` |
| 8 | Storefront | `order-confirmation.html` | `/order-confirmation` | `confirmation` | `OrderController.orderConfirmation` | `VERIFIED` |
| 9 | Storefront | `payment-session.html` | Auxiliary / Sandbox view (no active GET mapping; Stripe redirects directly via `PaymentService`) | `payment` | `PaymentController` (redirect-based) | `VERIFIED` |
| 10 | Storefront | `login.html` | `/login` | `login` | `AuthController.login` | `VERIFIED` |
| 11 | Storefront | `register.html` | `/register` | `register` | `AuthController.register` | `VERIFIED` |
| 12 | Storefront | `forgot-password.html` | `/forgot-password` | `forgot` | `AuthController.forgotPassword` | `VERIFIED` |
| 13 | Storefront | `reset-password.html` | `/reset-password` | `reset` | `AuthController.resetPassword` | `VERIFIED` |
| 14 | Admin | `admin-dashboard.html` | `/admin` | `dashboard` | `AdminController.dashboard` | `VERIFIED` |
| 15 | Admin | `admin-orders.html` | `/admin/orders` | `adminorders` | `AdminOrderController.listOrders` | `VERIFIED` |
| 16 | Admin | `admin-inventory.html` | `/admin/inventory` | `inventory` | `AdminInventoryController.listInventory` | `VERIFIED` |
| 17 | Admin | `admin-payments.html` | `/admin/payments` | `payments` | `AdminPaymentController.listPayments` | `VERIFIED` |
| 18 | Admin | `admin-shipments.html` | `/admin/shipments` | `shipments` | `AdminShipmentController.listShipments` | `VERIFIED` |
| 19 | Admin | `admin-shipment-form.html` | `/admin/shipments/edit/{id}` | `shipmentform` | `AdminShipmentController.showEditForm` | `VERIFIED` |
| 20 | Admin | `admin-shippers.html` | `/admin/shippers` | `shippers` | `AdminShipperController.listShippers` | `VERIFIED` |
| 21 | Admin | `admin-shipper-form.html` | `/admin/shippers/create`, `/admin/shippers/edit/{id}` | `shipperform` | `AdminShipperController` | `VERIFIED` |
| 22 | Admin | `admin-users.html` | `/admin/users` | `users` | `AdminUserController.listUsers` | `VERIFIED` |
| 23 | Admin | `admin-user-form.html` | `/admin/users/create`, `/admin/users/edit/{id}` | `userform` | `AdminUserController` | `VERIFIED` |
| 24 | Admin | `admin-wine-import.html` | `/admin/wines/import` | `import` | `AdminController.wineImportPage` | `VERIFIED` |
| 25 | Admin | `wine-form.html` | `/wines/admin/create`, `/wines/admin/edit/{id}` | `wineform` | `WineController.showCreateForm/showEditForm` | `VERIFIED` |
| 26 | Shipper | `shipper-dashboard.html` | `/shipper/dashboard` | `shipper` | `ShipperController.dashboard` | `VERIFIED` |
