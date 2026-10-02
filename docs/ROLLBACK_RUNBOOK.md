# Sổ Tay Khôi Phục Thảm Họa & Vận Hành Khẩn Cấp (StrongWine Rollback & Disaster Recovery Runbook)

## 1. Nguyên Tắc An Toàn Cốt Lõi (Core Safety Principles)

1. **Bảo toàn CSDL tuyệt đối (Zero Database Drift):**
   - Toàn bộ 6 Phase của dự án StrongWine hiện đại hóa đều tuân thủ nghiêm ngặt nguyên tắc **KHÔNG thay đổi, KHÔNG xóa bất kỳ bảng hoặc cột nào** trên Microsoft SQL Server.
   - Cấu hình `spring.jpa.hibernate.ddl-auto=validate` và `spring.sql.init.mode=never` đảm bảo Hibernate không bao giờ tự ý sửa đổi schema khi khởi động.
   - Do đó, khi cần khôi phục khẩn cấp (Rollback), **KHÔNG CẦN và TUYỆT ĐỐI KHÔNG CHẠY** các lệnh `DROP TABLE` hay khôi phục backup database, vì schema CSDL hoàn toàn tương thích ngược 100%.

2. **Khôi phục hoàn toàn ở Tầng Ứng Dụng (Application / Git Layer):**
   - Toàn bộ các tính năng mới (Excel Engine, Shopee SSE Real-time, Cart Drawer, Shipper Mobile Cockpit, Quick View Modal) đều được xây dựng theo kiến trúc mô-đun phân lớp tách biệt.

---

## 2. Quy Trình Khôi Phục Khẩn Cấp (Emergency Rollback Procedures)

Nếu phát hiện sự cố nghiêm trọng trên môi trường production/staging sau khi triển khai phiên bản mới:

### Bước 1: Dừng tiến trình ứng dụng đang chạy
Trên PowerShell (Windows):
```powershell
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
```

### Bước 2: Khôi phục mã nguồn về điểm chốt an toàn (Safe Baseline)
Xác định commit ổn định trước đó (ví dụ commit gốc trước khi triển khai hoặc commit của Phase trước đó):
```powershell
# Chuyển về nhánh an toàn hoặc checkout commit gốc
git fetch origin
git checkout feat/strongwine-modernize-superplan

# Trong trường hợp cần quay lui toàn bộ về commit gốc:
# git reset --hard <commit-hash>
```

### Bước 3: Dọn dẹp thư mục build và bộ nhớ đệm
```powershell
./mvnw clean
```

### Bước 4: Khởi động lại ứng dụng
```powershell
./mvnw spring-boot:run
```

---

## 3. Hướng Dẫn Vận Hành & Khắc Phục Sự Cố Phổ Biến (Troubleshooting Guide)

### 3.1. Sự cố Excel Bulk Import
- **Triệu chứng:** Người dùng tải lên file Excel nhưng hệ thống báo lỗi không đọc được hoặc lỗi định dạng.
- **Nguyên nhân:** File không đúng định dạng `.xlsx` (OpenXML) hoặc các tiêu đề cột bị thay đổi so với file mẫu chuẩn.
- **Xử lý:**
  1. Yêu cầu quản trị viên bấm nút **"Tải file mẫu chuẩn (.xlsx)"** tại `/admin/wines/import` (hoặc gọi API `/api/admin/excel/template`).
  2. Bảng xem trước lỗi (Dry-Run Preview) sẽ chỉ rõ số dòng lỗi, tên cột lỗi và giá trị sai (ví dụ: Năm sản xuất không phải số nguyên, giá tiền âm).
  3. Sửa lại các ô lỗi theo đúng gợi ý hiển thị trên bảng màu đỏ và tải lại file.

### 3.2. Sự cố Mất Kết Nối Real-Time Server-Sent Events (SSE)
- **Triệu chứng:** Đèn trạng thái hiển thị "Đang kết nối lại...", khách hàng không nhận được chuông hoặc cập nhật trạng thái đơn hàng tức thời.
- **Cơ chế tự phục hồi tích hợp:**
  - Client JS (`order-tracking-realtime.js` & `shipper-mobile-actions.js`) đã được trang bị cơ chế **Exponential Backoff** tự động thử kết nối lại sau 1s, 2s, 4s, 8s, tối đa 16s.
  - Server `SseNotificationService` tự động phát khung dữ liệu nhịp tim `ping` mỗi 15 giây để ngăn các proxy/load-balancer ngắt kết nối rảnh (idle timeout).
- **Xử lý thủ công nếu proxy chặn:**
  - Kiểm tra cấu hình Nginx/Reverse Proxy có bật tính năng streaming:
    ```nginx
    proxy_set_header Connection '';
    proxy_http_version 1.1;
    chunked_transfer_encoding off;
    proxy_buffering off;
    proxy_cache off;
    ```

### 3.3. Sự cố Dịch Vụ SQL Server Bị Dừng
- **Triệu chứng:** Lỗi `Connection refused: getsockopt` tới `localhost:1433`.
- **Xử lý:**
  1. Mở `services.msc` trên Windows với quyền Quản trị viên (Administrator).
  2. Tìm dịch vụ `SQL Server (MSSQLSERVER)` hoặc `SQL Server (SQLEXPRESS)`.
  3. Bấm chuột phải chọn **Start** (Khởi động).
  4. Xác nhận cổng TCP/IP 1433 đã được kích hoạt trong `SQL Server Configuration Manager`.

---

## 4. Bảng Kiểm Tra Sau Khôi Phục (Post-Rollback Verification Checklist)

- [ ] Ứng dụng Spring Boot khởi động thành công với HTTP Status 200 tại `/home`.
- [ ] Xác nhận CSDL SQL Server kết nối bình thường, không có lỗi Hibernate schema validation.
- [ ] Đăng nhập tài khoản Quản trị viên truy cập `/admin` thành công.
- [ ] Khách hàng xem danh mục rượu tại `/wines` bình thường.
- [ ] Các tính năng giỏ hàng và đặt hàng hoạt động ổn định.
