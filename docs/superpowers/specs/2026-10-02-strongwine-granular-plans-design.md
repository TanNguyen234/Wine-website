# Thiết Kế Chi Tiết: Mở Rộng Kế Hoạch Triển Khai StrongWine & Khung Kiểm Thử Thực Tế (Design Spec)

> **Mã thiết kế**: `SPEC-SW-2026-10-02`  
> **Dự án**: StrongWine (Spring Boot 4.0.0, Java 21, SQL Server, Thymeleaf)  
> **Trạng thái**: Đã phê duyệt qua phỏng vấn thiết kế (/grill-me & /brainstorming)  
> **Ràng buộc cốt lõi**: Bảo toàn tuyệt đối 100% CSDL hiện hữu (`spring.jpa.hibernate.ddl-auto=validate`), Áp dụng OOP Design Patterns thực tế, Hệ thống Real-Time SSE chuẩn Shopee (không mock), và Nâng cấp UI/UX toàn diện cho 3 phân hệ: **Khách Hàng (User)**, **Quản Trị Viên (Admin)**, và **Người Giao Hàng (Shipper)**.

---

## 1. Mục Tiêu & Phạm Vi (Goals & Scope)

### 1.1. Mục Tiêu Nghiệp Vụ
1. **Động cơ Nhập/Xuất Dữ liệu Excel Hàng Loạt**:
   - Cho phép Quản trị viên tải file Excel (`.xlsx`) chứa hàng chục đến hàng trăm sản phẩm cùng lúc.
   - Cơ chế xem trước (Dry-Run Preview) trước khi lưu: Hiển thị bảng kết quả phân tích dòng hợp lệ / dòng lỗi, nguyên nhân chi tiết.
   - Định danh sản phẩm tự nhiên bằng `(Tên Rượu + Năm Vintage)`:
     - Nếu trùng: Thực hiện Upsert cập nhật thông tin giá, xuất xứ, mô tả và cho phép chọn cộng dồn (`ADD`) hoặc ghi đè (`REPLACE`) số lượng tồn kho.
     - Nếu chưa có: Thêm sản phẩm mới và tự động tạo mới `Category` nếu chưa tồn tại.
   - Cung cấp tính năng tải file Excel mẫu chuẩn thương hiệu (`strongwine_product_import_template.xlsx`).
   - Xuất danh mục sản phẩm và báo cáo tồn kho/doanh thu ra Excel bằng mẫu thiết kế `Template Method`.

2. **Kiến Trúc Hướng Đối Tượng (OOP) Tinh Gọn & Độc Lập**:
   - Tách biệt hoàn toàn các tầng Controller, Strategy, Specification, Service, Repository và DTO.
   - Sử dụng **Strategy Pattern** cho việc xử lý các định dạng dữ liệu (Excel, CSV).
   - Sử dụng **Specification / Criteria Pattern** cho bộ lọc tìm kiếm sản phẩm đa tiêu chí động.
   - Xử lý lỗi tập trung bằng `@RestControllerAdvice` và `@ControllerAdvice` với các typed exceptions rõ ràng.

3. **Cơ Chế Real-Time Sự Kiện Chuẩn Shopee / ShopeeFood (Không Mock)**:
   - Ứng dụng **Server-Sent Events (SSE)** chuẩn HTML5 kết hợp Spring `SseEmitter` để đẩy cập nhật trực tiếp từ Server xuống Client.
   - Cập nhật tiến trình đơn hàng (Đã đặt -> Đã thanh toán -> Đang chuẩn bị -> Đang giao -> Hoàn tất) tự động trên trình duyệt khách hàng với độ trễ < 100ms.
   - Tự động hiển thị thông tin Shipper (tên, số điện thoại, biển số xe) và kích hoạt khung nhận mã OTP xác thực khi đơn bắt đầu giao.
   - Cơ chế giữ kết nối (Heartbeat 15s) và tự động kết nối lại (Auto-reconnect with exponential backoff).

4. **Nâng Cấp UI/UX Toàn Diện Cho 3 Cổng Giao Tiếp (Tri-Portal Modernization)**:
   - **User Portal (Maison de Vins)**: Phong cách hầm rượu quý tộc Pháp, biểu tượng SVG thủ công (không dùng AI slop), ngăn kéo giỏ hàng trượt (Cart Drawer) cập nhật số lượng tức thì qua AJAX, modal xem nhanh (Quick View Modal) và thanh theo dõi đơn hàng thời gian thực.
   - **Admin Portal (Executive Cellar Cockpit)**: Bảng điều khiển giám sát trực quan với biểu đồ Chart.js (doanh thu theo thời gian, top sản phẩm, cơ cấu danh mục), bàn làm việc kéo thả Excel có bảng xem trước lỗi, và điều chỉnh tồn kho tức thì.
   - **Shipper Portal (Mobile-First Logistics Cockpit)**: Giao diện tối ưu hóa cho màn hình điện thoại di động (375px - 430px), thẻ đơn hàng lớn dễ nhìn khi đang di chuyển, nút bấm 1 chạm gọi điện (`tel:`) và 1 chạm mở Google Maps dẫn đường, bàn phím nhập OTP 6 số tự động nhảy ô và dán chuỗi mượt mà, cùng chuông thông báo đơn hàng mới real-time.

5. **Quy Trình Kiểm Thử Thực Tế Sau Từng Task (Per-Task Real Test Contract)**:
   - Mỗi task kỹ thuật trong từng Phase bắt buộc phải có tiêu chuẩn nghiệm thu và lệnh kiểm thử thực tế độc lập (JUnit test tự động kết hợp cURL/PowerShell script thực tế), kiểm tra trực tiếp trên SQL Server runtime, nghiêm cấm nghiệm thu chay.

---

## 2. Kiến Trúc Dữ Liệu & Hợp Đồng Bảo Toàn CSDL (Database Preservation Contract)

Hệ thống hoạt động trên 12+ bảng cơ sở dữ liệu hiện có trong Microsoft SQL Server. Không có bất kỳ câu lệnh `ALTER`, `DROP` hoặc đổi tên cột nào được phép thực hiện:

```
[wines] <─────── [inventory] ───────> [warehouses]
   │                    │
   │ (category_id)      │ (orders.id)
   ▼                    ▼
[categories]         [orders] <─────── [shipments] ───────> [shippers]
                        │                    │
                        ▼                    ▼
                   [order_items]    [shipment_status_history]
```

Mọi thuộc tính phụ trợ mới cho giao diện (như trạng thái preview, điểm đánh giá tạm thời) được định nghĩa hoàn toàn trong tầng DTO hoặc gắn nhãn `@Transient` trên Java Entity để bảo đảm lệnh khởi động Spring Boot luôn vượt qua `spring.jpa.hibernate.ddl-auto=validate`.

---

## 3. Phân Rã Kế Hoạch Theo 6 Phase & Ma Trận Kiểm Thử Từng Task

Hệ thống được module hóa thành 6 tài liệu Phase độc lập nằm trong thư mục `docs/plans/`:

### Phase 1: `docs/plans/phase-1-core-foundation.md`
* **Task 1.1**: Tích hợp Apache POI (`poi`, `poi-ooxml` v5.2.5+) vào `pom.xml`.
  * *Real Test*: `./mvnw clean compile -DskipTests` -> Xác nhận `BUILD SUCCESS`.
* **Task 1.2**: Xây dựng Tầng Strategy Pattern (`DataImportStrategy<T>`, `DataExportStrategy<T>`, `ImportResult<T>`, `ImportErrorItem`).
  * *Real Test*: JUnit test `DataImportStrategyTest.java` kiểm tra hợp đồng phân tích.
* **Task 1.3**: Xây dựng Hệ Thống Exception Phân Cấp & Centralized Exception Handler (`StrongWineException`, `ExcelImportValidationException`, `GlobalExceptionHandler`).
  * *Real Test*: JUnit test `GlobalExceptionHandlerTest.java` xác nhận trả về JSON RFC 7807 với status HTTP 400/404.
* **Task 1.4**: Xây dựng Hạ Tầng SSE Cơ Bản (`SseNotificationService`, `SseNotificationController`).
  * *Real Test*: Gọi lệnh `curl -N http://localhost:8080/api/live/ping` -> Nhận được frame `event: ping\ndata: keep-alive`.

### Phase 2: `docs/plans/phase-2-excel-oop-engine.md`
* **Task 2.1**: Triển khai `ExcelWineImportStrategy` với cơ chế Upsert thông minh (Tên + Năm), Category auto-mapping, Tồn kho (Cộng dồn / Ghi đè).
  * *Real Test*: Nạp file Excel thử nghiệm 10 dòng (gồm 6 dòng mới, 2 dòng trùng để test Upsert, 2 dòng sai giá để test bắt lỗi) -> Kiểm tra kết quả trong DB và bảng lỗi.
* **Task 2.2**: Xây dựng `ExcelTemplateService` sinh file mẫu `.xlsx` chuẩn thương hiệu và endpoint tải về.
  * *Real Test*: `curl -o template.xlsx http://localhost:8080/admin/wines/import/template` -> Xác thực file mở được trên Microsoft Excel, có đầy đủ header và chú thích.
* **Task 2.3**: Triển khai Template Method cho Báo Cáo Excel (`AbstractExcelReportGenerator`, `RevenueReportGenerator`, `InventoryStockReportGenerator`).
  * *Real Test*: Gọi endpoint xuất báo cáo `/admin/reports/inventory/excel` -> Kiểm tra file tải về chứa dữ liệu tồn kho thực tế.
* **Task 2.4**: Triển khai JPA Specification Dynamic Filtering (`WineSearchCriteria`, `WineSpecification`).
  * *Real Test*: Viết test `WineSpecificationTest.java` kiểm tra lọc kết hợp đồng thời: Loại vang + Khoảng giá + Xuất xứ + Cờ còn hàng.
* **Task 2.5**: Xây dựng Controller & Service cho Dry-Run Preview (`POST /admin/wines/import/preview`).
  * *Real Test*: Gửi file Excel qua `Invoke-RestMethod` -> Nhận về DTO danh sách các dòng kèm cờ `VALID_NEW`, `VALID_UPDATE`, `ERROR`.

### Phase 3: `docs/plans/phase-3-realtime-sse-shopee.md`
* **Task 3.1**: Hoàn thiện `SseEmitterService` với thread-safe multi-client registry và bộ dọn dẹp kết nối rò rỉ.
  * *Real Test*: Mở và ngắt 50 kết nối đồng thời -> Bộ đếm Emitter trở về 0 an toàn.
* **Task 3.2**: Xây dựng Spring Event Listeners (`OrderEvent`, `ShipmentEvent`, `StockEvent`).
  * *Real Test*: Kích hoạt `applicationEventPublisher.publishEvent(...)` -> Kiểm tra subscriber nhận được sự kiện và broadcast ra SSE.
* **Task 3.3**: Xây dựng Client Engine `sse-client.js` có xử lý auto-reconnect với exponential backoff.
  * *Real Test*: Ngắt mạng giả lập trong tab DevTools và bật lại -> Kết nối tự khôi phục trong vòng 2 giây.
* **Task 3.4**: Xây dựng Endpoint Stream Trạng Thái Đơn Hàng `/api/live/orders/{orderId}/stream`.
  * *Real Test*: Dùng cURL kết nối lắng nghe order stream, ở terminal khác cập nhật shipment status -> cURL nhận ngay frame JSON `order_step_updated`.

### Phase 4: `docs/plans/phase-4-user-ui-ux-maison.md`
* **Task 4.1**: Thiết lập Tokens & Style Guide Maison de Vins trong `app.css` cùng bộ icon SVG bespoke.
  * *Real Test*: Kiểm tra hiển thị màu sắc, font chữ `Playfair Display` / `Plus Jakarta Sans` trên trình duyệt.
* **Task 4.2**: Thiết kế lại Navbar & Sticky Glassmorphism Header.
  * *Real Test*: Cuộn trang web -> Header làm mờ nền mượt mà, giỏ hàng cập nhật số lượng có animation nảy.
* **Task 4.3**: Thiết kế lại Trang Chủ `home.html` với Hero Banner hầm rượu và thẻ sản phẩm có tasting notes.
  * *Real Test*: Kiểm tra responsive trên độ phân giải 390px (iPhone) và 1440px (Desktop).
* **Task 4.4**: Thiết kế lại Danh Mục `wine-list.html` với Drawer lọc đa tiêu chí và Quick View Modal.
  * *Real Test*: Kéo thanh trượt khoảng giá -> Danh sách rượu cập nhật ngay lập tức; Bấm Quick View -> Modal mở ra xem nhanh thông số chai rượu.
* **Task 4.5**: Xây dựng Ngăn Kéo Giỏ Hàng Trượt (Cart Drawer) tương tác tức thời qua AJAX.
  * *Real Test*: Bấm "Thêm vào giỏ" ở bất kỳ trang nào -> Drawer trượt ra bên phải, đổi số lượng chai rượu -> Tổng tiền tự tính lại không reload trang.
* **Task 4.6**: Thiết kế lại Trang Theo Dõi Đơn Hàng `order-detail.html` với Timeline 5 bước phát sáng tích hợp SSE.
  * *Real Test*: Mở trang đơn hàng, cập nhật trạng thái ở phía backend -> Timeline tự đổi màu bước tiến và rung chuông nhẹ.

### Phase 5: `docs/plans/phase-5-admin-ui-ux-cockpit.md`
* **Task 5.1**: Thiết kế lại Khung Giao Diện Quản Trị `admin-dashboard.html` phong cách Executive Cellar.
  * *Real Test*: Kiểm tra hiển thị sidebar thu gọn, thẻ KPI doanh thu và cảnh báo tồn kho thấp.
* **Task 5.2**: Tích hợp Biểu Đồ Thống Kê Doanh Thu & Kho Hàng Bằng Chart.js.
  * *Real Test*: Biểu đồ 7 ngày tải dữ liệu thực từ `OrderService.getTotalRevenue()`, tooltip hiển thị định dạng VNĐ chuẩn xác.
* **Task 5.3**: Xây dựng Không Gian Nhập Excel Kéo Thả `admin-wine-import.html` kèm Bảng Preview Trực Quan.
  * *Real Test*: Kéo thả file Excel -> Bảng preview hiện ra với các dòng màu xanh (hợp lệ) và màu đỏ (lỗi) trước khi lưu.
* **Task 5.4**: Nâng cấp Giao diện Điều Chỉnh Tồn Kho Nhanh `admin-inventory.html`.
  * *Real Test*: Bấm sửa số lượng tồn kho trực tiếp từ bảng -> Dữ liệu cập nhật ngay qua AJAX.
* **Task 5.5**: **Thiết Kế Lại Toàn Diện Giao Diện Shipper Mobile-First (`shipper-dashboard.html`)**.
  * *Giao diện & Tiện ích*:
    - Layout thẻ đơn hàng tối ưu màn hình cảm ứng di động.
    - Nút 1 chạm gọi điện trực tiếp `tel:{shippingPhone}`.
    - Nút 1 chạm mở bản đồ dẫn đường Google Maps `https://maps.google.com/?q={shippingAddress}`.
    - Bộ nhập OTP 6 số tự động nhảy con trỏ sang ô tiếp theo và hỗ trợ dán cả chuỗi 6 ký tự.
    - Tabs phân loại trạng thái: `Cần lấy hàng`, `Đang giao`, `Hoàn tất`, `Thất bại`.
    - Chuông báo nhận đơn mới theo thời gian thực qua kết nối SSE.
  * *Real Test*: Mở trang giao diện Shipper trên thiết bị di động (hoặc chế độ Device Toolbar kích thước 390x844px), bấm gọi điện, nhập thử mã OTP 6 số và kiểm tra độ nhạy của thao tác xác nhận đơn.

### Phase 6: `docs/plans/phase-6-e2e-verification-rollback.md`
* **Task 6.1**: Chạy Kiểm Thử Biên Dịch & Xác Thực CSDL Toàn Diện (`ddl-auto=validate`).
  * *Real Test*: Khởi động ứng dụng kết nối tới Microsoft SQL Server -> Không phát sinh bất kỳ lỗi schema mismatch nào.
* **Task 6.2**: Kiểm thử Luồng Vận Hành Xuyên Suốt (End-to-End Test Flows).
  * *Flow 1*: Admin nhập 20 chai rượu qua Excel -> Preview hợp lệ -> Lưu DB -> Hiển thị trên Storefront.
  * *Flow 2*: Khách hàng lọc rượu -> Xem Quick View -> Thêm vào Cart Drawer -> Đặt hàng -> Chuyển sang Order Tracking.
  * *Flow 3*: Shipper nhận đơn trên điện thoại -> Bấm dẫn đường Google Maps -> Giao hàng -> Khách đọc OTP -> Shipper nhập OTP xác nhận -> Đơn hoàn tất tức thì trên màn hình khách hàng.
* **Task 6.3**: Kiểm tra Hiệu Năng, Chống Rò Rỉ Bộ Nhớ SSE & Đóng Gói Kế Hoạch Dự Phòng (Rollback Runbook).
  * *Real Test*: Chạy stress-test 100 kết nối SSE đồng thời, xác nhận CPU/RAM ổn định và có tài liệu hướng dẫn khôi phục nhanh qua Git khi gặp sự cố.

---

## 4. Hợp Đồng Nghiệm Thu Kỹ Thuật (Acceptance Criteria)

1. **Khả Năng Xử Lý Excel**: Nhập thành công tối thiểu 100 sản phẩm/lần qua Excel; phát hiện và báo lỗi chính xác từng ô dữ liệu sai định dạng; tự động ánh xạ Category và quản lý tồn kho không lỗi.
2. **Tính Năng Real-Time**: Tín hiệu cập nhật trạng thái đơn hàng truyền từ Server tới Client qua SSE trong thời gian dưới 200ms; không cần reload trang; cơ chế reconnect hoạt động ổn định khi rớt mạng tạm thời.
3. **Trải Nghiệm Đỉnh Cao (UI/UX)**: Không có bất kỳ icon hay giao diện AI slop nào; giao diện đồng nhất phong cách sang trọng Maison de Vins; Shipper thao tác dễ dàng trên điện thoại chỉ bằng một ngón tay cái.
4. **Bảo Toàn Dữ Liệu Tuyệt Đối**: CSDL SQL Server giữ nguyên vẹn 100% cấu trúc và dữ liệu hiện có; chế độ `spring.jpa.hibernate.ddl-auto=validate` luôn vượt qua kiểm tra thành công.
