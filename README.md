# StrongWine — Nền Tảng Thương Mại Điện Tử & Điều Phối Giao Nhận Rượu Vang Cao Cấp

Ứng dụng thương mại điện tử rượu vang cao cấp (**Dark Wine Luxury**) phát triển trên nền tảng **Java 21 + Spring Boot 4**, hỗ trợ đầy đủ quy trình mua sắm trực tuyến, thanh toán đa kênh (COD, Stripe, VNPay, MoMo), quản trị tồn kho đa kho (`Warehouse` & `Inventory`), nhập/xuất dữ liệu hàng loạt qua Excel (`.xlsx`), điều phối giao vận thời gian thực qua **Server-Sent Events (SSE)** và xác thực giao hàng bảo mật bằng **OTP 6 chữ số**.

---

## Điểm Nổi Bật Trong Phiên Bản Hiện Tại

1. **Giao diện Dark Wine Luxury đồng bộ toàn hệ thống (26 trang Thymeleaf)**
   - Thiết kế chuẩn thẩm mỹ hầm rượu thượng hạng (`#0C0A09` Obsidian Cellar, `#881337` Burgundy Primary, `#D4AF37` Champagne Gold) kết hợp typography **Cormorant Garamond** & **Plus Jakarta Sans**.
   - Bao phủ toàn bộ luồng **Khách hàng** (`/home`, `/wines`, `/wines/{id}`, `/cart` + slide-over cart drawer, `/checkout`, `/orders`, `/orders/{id}`), **Quản trị viên** (`/admin`, `/admin/orders`, `/admin/inventory`, `/admin/payments`, `/admin/shipments`, `/admin/shippers`, `/admin/wines/import`, `/admin/users`) và **Buồng lái Shipper Mobile-First** (`/shipper/dashboard`).
2. **Cơ sở dữ liệu SQL Server hợp nhất (1 File SQL duy nhất + Flyway V2..V13)**
   - Toàn bộ các script SQL rời rạc trước đây đã được **gộp thành 1 file SQL duy nhất, tự chứa đầy đủ cấu trúc 18 bảng và dữ liệu thực tế**:
     - [`src/main/resources/sql/strongwine-full-database.sql`](src/main/resources/sql/strongwine-full-database.sql)
   - Hỗ trợ đồng thời **Flyway Migration tự động** (`src/main/resources/db/migration/V2..V13`) khi chạy ứng dụng bằng lệnh `mvnw spring-boot:run`.
   - **24 sản phẩm rượu vang mặc định** trong cơ sở dữ liệu đi kèm **24 URL hình ảnh chai rượu riêng biệt (100% không trùng lặp)**, phân loại chuẩn theo 4 danh mục (`Vang đỏ`, `Vang trắng`, `Vang hồng`, `Vang sủi`), kèm lịch sử đơn hàng 6 tháng, giao dịch thanh toán, vận đơn có tọa độ GPS TP.HCM/Hà Nội + mã OTP và đánh giá sommelier thực tế.
3. **Bộ dữ liệu thật (`.xlsx` & `.csv`) sẵn sàng để test chức năng Upload Excel nhanh tại Admin (`/admin/wines/import`)**
   - Đi kèm file dữ liệu thực tế gồm **20 dòng rượu vang danh tiếng thế giới** (không trùng với 24 chai mặc định trong DB) kèm đầy đủ tồn kho và 20 URL hình ảnh riêng biệt:
     - File Excel dùng để upload ngay: [`sample-data/strongwine-admin-bulk-import-20-wines.xlsx`](sample-data/strongwine-admin-bulk-import-20-wines.xlsx) (và bản tĩnh tại [`src/main/resources/static/samples/strongwine-admin-bulk-import-20-wines.xlsx`](src/main/resources/static/samples/strongwine-admin-bulk-import-20-wines.xlsx))
     - File CSV đối chiếu nhanh: [`sample-data/strongwine-admin-bulk-import-20-wines.csv`](sample-data/strongwine-admin-bulk-import-20-wines.csv)

---

## Kiến Trúc Cơ Sở Dữ Liệu (18 Bảng)

| Phân hệ | Các bảng chính | Mô tả |
|---|---|---|
| **Danh mục & Sản phẩm** | `categories`, `wines` | Danh mục rượu vang, thông tin niên vụ, xuất xứ, giá bán, URL ảnh (`image_url`) và khóa lạc quan (`version`). |
| **Tài khoản & Bảo mật** | `users`, `user_sessions`, `password_reset_tokens`, `audit_logs`, `notification_logs` | Quản lý người dùng theo vai trò (`ADMIN`, `USER`, `SHIPPER`), phiên đăng nhập, khôi phục mật khẩu và nhật ký hệ thống. |
| **Kho vận & Tồn kho** | `warehouses`, `inventory`, `inventory_transactions`, `stock_alerts` | Quản lý tồn kho thực tế (`current_quantity`), tồn kho tạm giữ (`reserved_quantity`), ngưỡng cảnh báo (`reorder_level`) và lịch sử nhập/xuất. |
| **Đơn hàng & Thanh toán** | `orders`, `order_items`, `payments`, `payment_transactions` | Đơn hàng đa trạng thái, chi tiết sản phẩm, đối soát thanh toán (`COD`, `STRIPE`, `VNPAY`, `MOMO`) và lịch sử giao dịch. |
| **Giao vận & OTP** | `shippers`, `shipments`, `shipment_status_history` | Đội ngũ tài xế giao rượu bảo ôn, trạng thái vận đơn 5 bước, tọa độ GPS và mã xác thực giao hàng OTP 6 chữ số. |
| **Đánh giá** | `reviews` | Nhận xét và chấm điểm (1–5 sao) từ khách hàng đã trải nghiệm. |

---

## Hướng Dẫn Cài Đặt & Chạy Dự Án Cho Người Mới

### 1. Yêu cầu môi trường
- **Java**: JDK 21 trở lên
- **Maven**: Sử dụng sẵn `./mvnw` hoặc `.\mvnw.cmd` đi kèm dự án
- **Cơ sở dữ liệu**: SQL Server (SQL Server Developer/Express hoặc SQL Server LocalDB)

### 2. Khởi tạo Database (Chọn 1 trong 2 cách)

#### Cách A — Tự động hoàn toàn qua Flyway & DataInitializer (Khuyên dùng)
1. Tạo một database rỗng trên SQL Server (ví dụ: `winestore_2` hoặc `winestore`).
2. Cấu hình kết nối trong `src/main/resources/application.properties` (hoặc biến môi trường):
   ```properties
   spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=winestore_2;encrypt=true;trustServerCertificate=true
   spring.datasource.username=sa
   spring.datasource.password=123456
   ```
3. Chạy ứng dụng:
   ```bash
   .\mvnw.cmd spring-boot:run
   ```
   Flyway (`V2` -> `V13`) và `DataInitializer` sẽ tự động khởi tạo đầy đủ 18 bảng và nạp toàn bộ dữ liệu thực tế.

#### Cách B — Chạy 1 File SQL hợp nhất duy nhất (`strongwine-full-database.sql`)
Nếu bạn muốn khởi tạo toàn bộ bảng và dữ liệu mẫu trực tiếp trong **SQL Server Management Studio (SSMS)**, **Azure Data Studio** hoặc `sqlcmd` chỉ với **1 file duy nhất**:
1. Mở file [`src/main/resources/sql/strongwine-full-database.sql`](src/main/resources/sql/strongwine-full-database.sql).
2. Thực thi toàn bộ script trên database của bạn (script được thiết kế **idempotent** — có thể chạy lại nhiều lần mà không gây lỗi trùng bảng hay trùng dữ liệu).

---

## Tài Khoản Đăng Nhập Mặc Định

| Vai trò | Username | Mật khẩu | Trang đích sau đăng nhập | Ghi chú |
|---|---|---|---|---|
| **Quản trị viên (`ADMIN`)** | `admin` | `admin123` | `/admin` | Toàn quyền quản trị, thống kê, nhập/xuất Excel, điều phối vận đơn |
| **Khách hàng (`USER`)** | `demo` | `demo123` | `/home` | Tài khoản khách hàng có sẵn đơn hàng mẫu, lịch sử mua và mã OTP |
| **Khách hàng VIP (`USER`)** | `nguyen.minh.quan` | `demo123` | `/home` | Khách hàng doanh nghiệp TP.HCM (cùng mật khẩu `demo123` cho `tran.bao.ngoc`, `le.hoang.nam`, `pham.khanh.linh`, `vo.duc.thinh`, `dang.thu.ha`) |
| **Tài xế giao vận (`SHIPPER`)** | `shipper.ops.hcm` | `demo123` | `/shipper/dashboard` | Tài xế chính tại TP.HCM đang có sẵn các vận đơn `ASSIGNED`, `PICKED_UP`, `DELIVERING` và `COMPLETED` |
| **Tài xế dự phòng (`SHIPPER`)** | `shipper1` / `shipper2` | `shipper123` | `/shipper/dashboard` | Tài xế mặc định bổ sung |

---

## Hướng Dẫn Test Nhanh Chức Năng Upload File Excel Tại Admin (`/admin/wines/import`)

Dự án đã chuẩn bị sẵn file Excel dữ liệu thật gồm **20 sản phẩm rượu vang cao cấp mới** để kiểm thử tính năng **Nhập Kho Hàng Loạt Từ Excel**:

- **Đường dẫn file upload**:
  - [`sample-data/strongwine-admin-bulk-import-20-wines.xlsx`](sample-data/strongwine-admin-bulk-import-20-wines.xlsx)
  - Hoặc tải trực tiếp từ trình duyệt khi ứng dụng đang chạy tại: `http://localhost:8080/samples/strongwine-admin-bulk-import-20-wines.xlsx`

### Cấu trúc 9 cột của file `.xlsx`
1. `Tên Sản Phẩm (*)` — Tên đầy đủ của chai rượu vang (VD: *Château Cheval Blanc Saint-Émilion Premier Grand Cru Classé A*)
2. `Loại Rượu (*)` — `Red`, `White`, `Rose`, `Sparkling` (hoặc tiếng Việt: `Vang đỏ`, `Vang trắng`, `Vang hồng`, `Vang sủi`)
3. `Năm Sản Xuất (*)` — Niên vụ từ `1900` đến năm hiện tại + 1
4. `Giá Bán (VNĐ) (*)` — Giá bán hợp lệ (`> 0`)
5. `Xuất Xứ` — Quốc gia sản xuất (`France`, `Italy`, `Spain`, `USA`, `Australia`, `Chile`, `Argentina`, `New Zealand`)
6. `Mô Tả` — Ghi chú hương vị sommelier chi tiết
7. `Tên Danh Mục` — Tự động ánh xạ hoặc khởi tạo danh mục (`Vang đỏ`, `Vang trắng`, `Vang hồng`, `Vang sủi`)
8. `Số Lượng Kho Khởi Tạo` — Số lượng chai nhập vào kho được chọn
9. `URL Hình Ảnh` — Đường dẫn ảnh chai rượu riêng biệt (nếu để trống, hệ thống tự động gán ảnh chuẩn theo từng loại vang)

### Các bước thực hiện test trên giao diện Web
1. Đăng nhập tài khoản **`admin` / `admin123`**.
2. Truy cập trang **Nhập Excel** tại `http://localhost:8080/admin/wines/import` (hoặc nhấn nút **Nhập Excel** trên thanh công cụ trang Quản lý Kho `/admin/inventory`).
3. Kéo thả hoặc chọn file [`sample-data/strongwine-admin-bulk-import-20-wines.xlsx`](sample-data/strongwine-admin-bulk-import-20-wines.xlsx).
4. **Bước 1 (Kiểm thử an toàn - Dry Run)**:
   - Giữ tùy chọn **"Chạy thử (Dry Run)"** được bật -> Nhấn **Thực Hiện Import**.
   - Hệ thống kiểm tra hợp lệ toàn bộ 20 dòng và trả về kết quả: **Tổng số dòng xử lý: 20 | Thành công: 20 | Dòng lỗi: 0** mà chưa ghi vào DB.
5. **Bước 2 (Nhập thật vào Database)**:
   - Chọn lại file `strongwine-admin-bulk-import-20-wines.xlsx`, **bỏ chọn "Chạy thử (Dry Run)"**, chọn kho đích (VD: *Main Warehouse*) và chế độ tồn kho -> Nhấn **Thực Hiện Import**.
   - Toàn bộ 20 sản phẩm rượu vang mới cùng tồn kho và 20 hình ảnh tương ứng sẽ xuất hiện ngay trên trang `/wines` và `/admin/inventory` (nâng tổng danh mục từ 24 lên **44 sản phẩm**).

---

## Kiểm Thử Tự Động (Automated Tests)

Chạy bộ kiểm thử đơn vị và kiểm thử giao diện/chiến lược import:

```bash
# Kiểm thử trình đọc & sinh dữ liệu thực tế 20 rượu vang Excel (.xlsx)
.\mvnw.cmd test "-Dtest=ExcelWineImportStrategyTest"

# Kiểm thử cấu trúc giao diện Thymeleaf toàn hệ thống (26 templates)
.\mvnw.cmd test "-Dtest=ThymeleafTemplateRedesignVerificationTest"

# Chạy toàn bộ unit tests
.\mvnw.cmd test "-Dtest=!StrongwineApplicationTests"
```

---

## Cấu Trúc Thư Mục Chính

```text
Wine-website/
├── sample-data/
│   ├── strongwine-admin-bulk-import-20-wines.xlsx   # File Excel 20 rượu vang thật để test upload Admin
│   └── strongwine-admin-bulk-import-20-wines.csv    # Bản CSV đối chiếu nội dung 20 rượu vang
├── src/main/java/com/strongwine/strongwine/
│   ├── config/                                      # DataInitializer & cấu hình hệ thống
│   ├── controller/                                  # Controllers cho Customer, Admin, Shipper & API
│   ├── entity/                                      # Các thực thể JPA ánh xạ 18 bảng
│   ├── repository/                                  # Spring Data JPA Repositories
│   ├── security/                                    # Spring Security & phân quyền RBAC
│   └── service/                                     # Nghiệp vụ đơn hàng, kho, vận đơn, Excel Import/Export
└── src/main/resources/
    ├── db/migration/                                # Các phiên bản Flyway (V2 -> V13)
    ├── sql/
    │   └── strongwine-full-database.sql             # File SQL hợp nhất duy nhất (18 bảng + full dữ liệu thật)
    ├── static/
    │   ├── css/                                     # Hệ thống CSS Dark Wine Luxury
    │   └── samples/                                 # File mẫu .xlsx tải trực tiếp từ web
    └── templates/                                   # 26 giao diện Thymeleaf đã thiết kế lại
```
