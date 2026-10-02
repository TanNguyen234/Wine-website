# Kế Hoạch Tổng Thể Nâng Cấp Hệ Thống StrongWine (Master Super Plan)

> **Mã tài liệu**: `SW-SUPERPLAN-2026-V2`  
> **Dự án**: StrongWine — Luxury Wine E-Commerce & Last-Mile Delivery Platform  
> **Nguyên tắc bất biến**:
> 1. **Bảo toàn 100% Cơ sở dữ liệu SQL Server hiện tại**: Tuyệt đối không xóa, không sửa cấu trúc cột, luôn vượt qua `spring.jpa.hibernate.ddl-auto=validate`.
> 2. **Cơ chế Real-Time chuẩn Shopee/ShopeeFood (Không Mock)**: Sử dụng Server-Sent Events (SSE) chuẩn công nghiệp, tự động chuyển bước timeline, chuông âm thanh, hiển thị shipper và OTP trực tiếp.
> 3. **OOP Design Patterns thực tế**: Strategy Pattern cho Import/Export, Specification Pattern cho bộ lọc động, Template Method cho báo cáo, Exception phân cấp rõ ràng.
> 4. **Trải nghiệm đẳng cấp (Maison de Vins) & Mobile-First cho Shipper**: Loại bỏ hoàn toàn AI slop, icon SVG thủ công tinh xảo, giao diện Shipper tối ưu cảm ứng di động (1-chạm gọi điện, 1-chạm Google Maps, bàn phím OTP 6 số).
> 5. **Hợp đồng kiểm thử thực tế (Per-Task Real Test)**: Mọi task đều có JUnit 5 test tự động và kịch bản Live cURL/PowerShell xác thực ngay trên runtime.

---

## 🗺️ Bản Đồ Điều Phối 6 Phase Kỹ Thuật (Master Phase Index)

Mỗi giai đoạn được quy hoạch thành một tài liệu thiết kế thi công chi tiết độc lập nằm trong thư mục [`docs/plans/`](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/):

| Phase | Tên Giai Đoạn & Tài Liệu Chi Tiết | Mục Tiêu Kỹ Thuật Cốt Lõi | Số Lượng Tasks | Tiêu Chí Nghiệm Thu Thực Tế (Real Gate) |
| :---: | :--- | :--- | :---: | :--- |
| **Phase 1** | [**`phase-1-core-foundation.md`**](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-1-core-foundation.md) | Tích hợp Apache POI 5.2.5, Hợp đồng Strategy Pattern, Phân cấp Domain Exceptions (`@RestControllerAdvice`), và Khung SSE Broker với Heartbeat 15s. | 4 tasks | `./mvnw test -Dtest=PoiDependencyTest,StrategyInterfaceTest,GlobalExceptionHandlerTest,SseNotificationServiceTest` kết quả `BUILD SUCCESS`. |
| **Phase 2** | [**`phase-2-excel-oop-engine.md`**](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-2-excel-oop-engine.md) | Động cơ Upsert Excel theo cặp `(Tên + Năm)`, Tự tạo Category, Quản lý kho (Cộng dồn/Ghi đè), Sinh file mẫu `.xlsx`, Xuất báo cáo qua Template Method, và Lọc động bằng JPA Specification. | 5 tasks | `./mvnw test -Dtest=ExcelWineImportStrategyTest,ExcelTemplateServiceTest,InventoryStockReportGeneratorTest,WineSpecificationTest,AdminExcelApiControllerTest` kết quả `BUILD SUCCESS`. |
| **Phase 3** | [**`phase-3-realtime-sse-shopee.md`**](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-3-realtime-sse-shopee.md) | Quản lý Emitter đa kênh (`order:{id}`, `shipper:{id}`, `admin`), Bắt sự kiện Spring Domain Events, Client Engine `sse-client.js` tự kết nối lại và phát chuông âm thanh Web Audio API. | 3 tasks | `./mvnw test -Dtest=SseMultiChannelTest,RealtimeEventListenerTest` pass 100%; Stream cURL nhận frame `order_status_updated`. |
| **Phase 4** | [**`phase-4-user-ui-ux-maison.md`**](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-4-user-ui-ux-maison.md) | Nâng cấp toàn diện Storefront phong cách hầm rượu Maison de Vins: Icon SVG thủ công, Filter Drawer thanh trượt giá, Modal Quick View xem nhanh, Cart Drawer trượt mượt mà qua AJAX, và Timeline 5 bước phát sáng. | 4 tasks | Kiểm tra giao diện hiển thị xuất sắc trên Mobile (390px) và Desktop (1440px); Cart drawer phản hồi < 100ms qua AJAX. |
| **Phase 5** | [**`phase-5-admin-shipper-cockpit.md`**](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-5-admin-shipper-cockpit.md) | Bảng điều khiển Quản trị tích hợp biểu đồ Chart.js (Doanh thu & Cơ cấu kho), Bàn làm việc kéo thả Excel có Bảng Xem Trước Lỗi từng dòng; **Nâng cấp Shipper thành Mobile-First Delivery Cockpit (1-chạm gọi điện, 1-chạm Google Maps, bàn phím OTP 6 số)**. | 3 tasks | Drag & drop file Excel hiển thị preview trong 1.5s; Giao diện Shipper thao tác một ngón tay cái mượt mà trên smartphone. |
| **Phase 6** | [**`phase-6-e2e-verification-rollback.md`**](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-6-e2e-verification-rollback.md) | Kiểm tra toàn vẹn CSDL SQL Server 100% (`ddl-auto=validate`), Kiểm thử luồng nghiệp vụ xuyên suốt End-to-End, Kiểm tra độ bền SSE dưới tải, và Sổ tay khôi phục khẩn cấp (`ROLLBACK_RUNBOOK.md`). | 3 tasks | Chạy `./mvnw clean test` pass 100% toàn bộ suite; CSDL giữ nguyên vẹn không sai lệch 1 cột; Rollback runbook sẵn sàng. |

---

## 📋 Tóm Tắt Chi Tiết Từng Giai Đoạn (Detailed Phase Breakdown)

### Phase 1: Nền Tảng Kỹ Thuật, Thư Viện POI & Các Trừu Tượng OOP Cốt Lõi
- **Chi tiết tại**: [`docs/plans/phase-1-core-foundation.md`](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-1-core-foundation.md)
- **Nội dung thực thi**:
  1. Thêm `org.apache.poi:poi:5.2.5` và `poi-ooxml:5.2.5` vào `pom.xml`.
  2. Xây dựng các interface Strategy: `DataImportStrategy<T>`, `DataExportStrategy<T>`, `ImportResult<T>`, `ImportErrorItem`.
  3. Xây dựng cây Exception: `StrongWineException`, `ExcelImportValidationException`, `ResourceNotFoundException`, `InsufficientStockException`, và `GlobalExceptionHandler` (`@RestControllerAdvice`).
  4. Khởi tạo `SseNotificationService` quản lý kết nối đa luồng `SseEmitter` và scheduler gửi heartbeat ping 15s.

### Phase 2: Triển Khai OOP Nâng Cao & Động Cơ Nhập/Xuất Excel Hàng Loạt
- **Chi tiết tại**: [`docs/plans/phase-2-excel-oop-engine.md`](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-2-excel-oop-engine.md)
- **Nội dung thực thi**:
  1. Triển khai `ExcelWineImportStrategy`: Đọc `XSSFWorkbook`, định danh tự nhiên `(Tên + Năm)`, cơ chế Upsert với tùy chọn kho `ADD` hoặc `REPLACE`, tự động tạo `Category` nếu chưa có.
  2. Triển khai `ExcelTemplateService`: Tạo file mẫu `strongwine_product_import_template.xlsx` chuẩn format và endpoint `GET /api/admin/excel/template`.
  3. Triển khai Template Method cho Báo Cáo: `AbstractExcelReportGenerator` và `InventoryStockReportGenerator`.
  4. Triển khai JPA Specification: `WineSearchCriteria` và `WineSpecification` hỗ trợ lọc động kết hợp (từ khóa, loại vang, khoảng giá, năm vintage, xuất xứ, còn hàng).
  5. Xây dựng REST API: `POST /api/admin/excel/preview` (Dry-run mode phân loại dòng xanh/đỏ) và `POST /api/admin/excel/import`.

### Phase 3: Hệ Thống Real-Time Sự Kiện Chuẩn Shopee (SSE Emitter Broker)
- **Chi tiết tại**: [`docs/plans/phase-3-realtime-sse-shopee.md`](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-3-realtime-sse-shopee.md)
- **Nội dung thực thi**:
  1. Mở rộng `SseNotificationService` hỗ trợ các kênh riêng biệt: Kênh đơn hàng `order:{id}`, kênh Shipper `shipper:{id}`, và kênh Quản trị `admin:dashboard`.
  2. Bắt các sự kiện Spring Application Events (`OrderStatusUpdatedEvent`, `ShipmentLiveStatusEvent`) bằng `@EventListener` và broadcast tức thời ra SSE.
  3. Xây dựng client engine `order-tracking-realtime.js`: Tự động kết nối `EventSource`, tự động reconnect bằng exponential backoff (1s -> 2s -> 4s -> 8s -> 16s), phát chuông âm thanh thông báo nhẹ qua Web Audio API khi trạng thái thay đổi.

### Phase 4: Thiết Kế Lại Toàn Bộ UI/UX Cho Khách Hàng (User Portal)
- **Chi tiết tại**: [`docs/plans/phase-4-user-ui-ux-maison.md`](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-4-user-ui-ux-maison.md)
- **Nội dung thực thi**:
  1. Bổ sung tokens Maison de Vins vào `app.css`: Gam màu đen hầm rượu `--noir`, đỏ vang Bordeaux `--crimson-prime`, vàng champagne `--gold-prime`, và các icon SVG thủ công chuyên biệt về rượu (không dùng icon AI slop).
  2. Nâng cấp `wine-list.html`: Thêm Drawer lọc đa tiêu chí (dual-range slider giá, pill tags giống nho) và Quick View Modal xem nhanh thông số chai rượu không cần chuyển trang.
  3. Xây dựng Slide-out Cart Drawer (`cart-drawer.js`): Trượt từ cạnh phải, thêm/xóa/sửa số lượng sản phẩm tức thì qua AJAX không giật màn hình.
  4. Nâng cấp trang theo dõi đơn hàng `order-detail.html`: Timeline 5 bước phát sáng thời gian thực kết nối với SSE, card hiển thị mã OTP 6 số kèm nút sao chép nhanh, và thông tin liên hệ shipper.

### Phase 5: Thiết Kế Lại Toàn Bộ UI/UX Quản Trị & Shipper Mobile (Admin & Shipper Cockpit)
- **Chi tiết tại**: [`docs/plans/phase-5-admin-shipper-cockpit.md`](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-5-admin-shipper-cockpit.md)
- **Nội dung thực thi**:
  1. Nâng cấp `admin-dashboard.html`: Tích hợp thư viện Chart.js vẽ biểu đồ đường doanh thu 7 ngày và biểu đồ tròn cơ cấu danh mục bán chạy.
  2. Xây dựng không gian nhập Excel `admin-wine-import.html`: Vùng kéo thả file, bảng Dry-Run Preview phân loại trực quan dòng hợp lệ (xanh) và dòng lỗi (đỏ kèm lý do), radio button chọn chế độ tồn kho (Cộng dồn / Ghi đè).
  3. **Nâng cấp toàn diện Shipper Portal (`shipper-dashboard.html`) thành Mobile-First Cockpit**:
     - Thay thế table tĩnh bằng hệ thống Thẻ Đơn Giao Di Động (Mobile Delivery Cards).
     - **Nút 1-chạm gọi điện trực tiếp cho khách hàng** (`tel:{shippingPhone}`).
     - **Nút 1-chạm mở ứng dụng Google Maps dẫn đường** (`https://maps.google.com/?q={address}`).
     - Bàn phím nhập OTP 6 số tự động nhảy con trỏ sang ô tiếp theo và hỗ trợ dán chuỗi 6 số mượt mà.
     - Lắng nghe sự kiện SSE để rung/chuông và hiện đơn giao mới tự động.

### Phase 6: Tích Hợp Toàn Diện, Kiểm Thử E2E & Kế Hoạch Dự Phòng (Rollback Runbook)
- **Chi tiết tại**: [`docs/plans/phase-6-e2e-verification-rollback.md`](file:///D:/Projects/strongwine_2/Wine-website/docs/plans/phase-6-e2e-verification-rollback.md)
- **Nội dung thực thi**:
  1. Kiểm thử xác thực CSDL với Hibernate `spring.jpa.hibernate.ddl-auto=validate` (đảm bảo không phát sinh bất kỳ lỗi schema mismatch nào).
  2. Kiểm thử luồng nghiệp vụ xuyên suốt (`EndToEndCommerceFlowTest`): Admin nhập file Excel -> Lưu DB -> Khách hàng tìm thấy qua Specification -> Thêm vào Cart Drawer -> Đặt hàng -> Shipper nhận đơn trên di động -> Xác thực OTP -> Hoàn tất.
  3. Đóng gói tài liệu Sổ tay khôi phục thảm họa [`docs/ROLLBACK_RUNBOOK.md`](file:///D:/Projects/strongwine_2/Wine-website/docs/ROLLBACK_RUNBOOK.md).

---

## 🔒 Cam Kết Kỹ Thuật Về Cơ Sở Dữ Liệu Hiện Hữu

Tất cả các tính năng mới đều tuân thủ triệt để nguyên tắc **Add-only / Non-destructive**:
```
- Không thêm cột mới vào bảng nếu chưa được cấu hình tương ứng trong Entity và Flyway.
- Không sửa kiểu dữ liệu hoặc xóa các ràng buộc khóa ngoại hiện có.
- Mọi dữ liệu tạm thời cho Preview và UI đều nằm ở tầng DTO bộ nhớ (In-Memory DTOs).
```
Chế độ `spring.jpa.hibernate.ddl-auto=validate` được duy trì nghiêm ngặt trong mọi giai đoạn phát triển.
