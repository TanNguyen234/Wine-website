1. Bảng USERS (Người dùng hệ thống)
Lưu thông tin tài khoản đăng nhập và phân quyền người dùng.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính người dùng
username | NVARCHAR(255) | Not Null, Unique | Tên đăng nhập
email | NVARCHAR(255) | Unique, Null | Email người dùng
password | NVARCHAR(255) | Not Null | Mật khẩu đã mã hóa
role | NVARCHAR(255) | Not Null | Vai trò tài khoản (ADMIN/USER/SHIPPER)

*Chỉ mục: PK_USERS(id), UQ_USERS_USERNAME(username), UQ_USERS_EMAIL(email)  
| Quan hệ: USERS có quan hệ 1-N với ORDERS; 1-N với REVIEWS; 1-1 với CARTS; 1-1 với SHIPPERS; 1-N với PASSWORD_RESET_TOKENS

2. Bảng CATEGORIES (Danh mục sản phẩm)
Lưu danh mục phân loại sản phẩm rượu.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính danh mục
name | NVARCHAR(255) | Not Null, Unique | Tên danh mục
description | NVARCHAR(500) | Null | Mô tả danh mục
created_at | DATETIME | Null | Thời điểm tạo (audit)
updated_at | DATETIME | Null | Thời điểm cập nhật (audit)
created_by | NVARCHAR(255) | Null | Người tạo (audit)
updated_by | NVARCHAR(255) | Null | Người cập nhật (audit)
deleted | BIT | Not Null, Default 0 | Cờ xóa mềm

*Chỉ mục: PK_CATEGORIES(id), UQ_CATEGORIES_NAME(name)  
| Quan hệ: CATEGORIES có quan hệ 1-N với WINES

3. Bảng WINES (Sản phẩm rượu)
Lưu thông tin sản phẩm rượu trong hệ thống bán hàng.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính sản phẩm
name | NVARCHAR(255) | Not Null | Tên rượu
type | NVARCHAR(255) | Not Null | Loại rượu
year | INT | Not Null | Năm sản xuất
price | DECIMAL(10,2) | Not Null | Giá bán
description | NVARCHAR(1000) | Null | Mô tả sản phẩm
country | NVARCHAR(100) | Null | Quốc gia xuất xứ
image_url | NVARCHAR(500) | Null | URL/đường dẫn ảnh
category_id | BIGINT | FK -> CATEGORIES(id), Null | Danh mục sản phẩm
created_at | DATETIME | Null | Thời điểm tạo (audit)
updated_at | DATETIME | Null | Thời điểm cập nhật (audit)
created_by | NVARCHAR(255) | Null | Người tạo (audit)
updated_by | NVARCHAR(255) | Null | Người cập nhật (audit)
deleted | BIT | Not Null, Default 0 | Cờ xóa mềm

*Chỉ mục: PK_WINES(id)  
| Quan hệ: WINES có quan hệ N-1 với CATEGORIES; 1-N với ORDER_ITEMS; 1-N với REVIEWS; 1-N với CART_ITEMS; 1-N với INVENTORY

4. Bảng WAREHOUSE (Kho hàng)
Lưu thông tin kho dùng cho quản lý tồn kho.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính kho
name | NVARCHAR(255) | Not Null, Unique | Tên kho
location | NVARCHAR(500) | Null | Địa chỉ/vị trí kho
active | BIT | Not Null, Default 1 | Trạng thái hoạt động
created_at | DATETIME | Not Null, Default thời điểm tạo | Thời điểm tạo bản ghi

*Chỉ mục: PK_WAREHOUSE(id), UQ_WAREHOUSE_NAME(name)  
| Quan hệ: WAREHOUSE có quan hệ 1-N với INVENTORY; 1-N với INVENTORY_TRANSACTIONS

5. Bảng INVENTORY (Tồn kho)
Lưu số lượng tồn và giữ chỗ theo từng sản phẩm tại từng kho.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính tồn kho
wine_id | BIGINT | FK -> WINES(id), Not Null | Sản phẩm rượu
warehouse_id | BIGINT | FK -> WAREHOUSE(id), Not Null | Kho hàng
current_quantity | INT | Not Null, Default 0 | Số lượng hiện có
reserved_quantity | INT | Not Null, Default 0 | Số lượng đã giữ chỗ
reorder_level | INT | Not Null, Default 10 | Ngưỡng nhập lại
updated_at | DATETIME | Not Null, Default thời điểm tạo/cập nhật | Thời điểm cập nhật
version | BIGINT | Null | Phiên bản optimistic locking

*Chỉ mục: PK_INVENTORY(id), UQ_INVENTORY_WINE_WAREHOUSE(wine_id, warehouse_id)  
| Quan hệ: INVENTORY có quan hệ N-1 với WINES; N-1 với WAREHOUSE; 1-N với STOCK_LOGS; 1-N với INVENTORY_TRANSACTIONS

6. Bảng STOCK_LOGS (Nhật ký tồn kho)
Lưu log biến động tồn kho khả dụng.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính nhật ký
inventory_id | BIGINT | FK -> INVENTORY(id), Not Null | Tham chiếu tồn kho
available_quantity | INT | Not Null | Số lượng khả dụng tại thời điểm log
message | NVARCHAR(500) | Null | Nội dung nhật ký
created_at | DATETIME | Not Null, Default thời điểm tạo | Thời điểm tạo log

*Chỉ mục: PK_STOCK_LOGS(id)  
| Quan hệ: STOCK_LOGS có quan hệ N-1 với INVENTORY

7. Bảng INVENTORY_TRANSACTIONS (Giao dịch kho)
Lưu lịch sử nghiệp vụ nhập/xuất/đặt giữ tồn kho.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính giao dịch
inventory_id | BIGINT | FK -> INVENTORY(id), Not Null | Tham chiếu tồn kho
product_id | BIGINT | FK -> WINES(id), Not Null | Sản phẩm liên quan
warehouse_id | BIGINT | FK -> WAREHOUSE(id), Not Null | Kho liên quan
quantity | INT | Not Null | Số lượng giao dịch
operation_type | NVARCHAR(255) | Not Null | Loại giao dịch tồn kho (Enum dạng STRING)
reference_type | NVARCHAR(100) | Null | Loại tham chiếu nghiệp vụ
reference_id | BIGINT | Null | ID tham chiếu nghiệp vụ
user_id | BIGINT | Null | ID người thao tác
note | NVARCHAR(500) | Null | Ghi chú
created_at | DATETIME | Not Null, Default thời điểm tạo | Thời điểm tạo
created_by | NVARCHAR(255) | Null | Người thao tác

*Chỉ mục: PK_INVENTORY_TRANSACTIONS(id)  
| Quan hệ: INVENTORY_TRANSACTIONS có quan hệ N-1 với INVENTORY; N-1 với WINES; N-1 với WAREHOUSE

8. Bảng CARTS (Giỏ hàng)
Lưu thông tin giỏ hàng theo người dùng.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính giỏ hàng
user_id | BIGINT | FK -> USERS(id), Unique, Null | Người sở hữu giỏ hàng

*Chỉ mục: PK_CARTS(id), UQ_CARTS_USER_ID(user_id)  
| Quan hệ: CARTS có quan hệ 1-1 với USERS; 1-N với CART_ITEMS

9. Bảng CART_ITEMS (Chi tiết giỏ hàng)
Lưu danh sách sản phẩm trong giỏ hàng.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính chi tiết giỏ
cart_id | BIGINT | FK -> CARTS(id), Not Null | Giỏ hàng
wine_id | BIGINT | FK -> WINES(id), Not Null | Sản phẩm
quantity | INT | Not Null | Số lượng

*Chỉ mục: PK_CART_ITEMS(id)  
| Quan hệ: CART_ITEMS có quan hệ N-1 với CARTS; N-1 với WINES

10. Bảng ORDERS (Đơn hàng)
Lưu thông tin đơn đặt hàng và giao nhận.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính đơn hàng
user_id | BIGINT | FK -> USERS(id), Not Null | Người đặt hàng
order_date | DATETIME | Not Null | Ngày tạo đơn
total_price | DECIMAL(10,2) | Not Null | Tổng tiền
status | NVARCHAR(50) | Not Null, Default PENDING (theo entity) | Trạng thái đơn hàng
payment_status | NVARCHAR(50) | Not Null, Default PENDING (theo entity) | Trạng thái thanh toán
payment_method | NVARCHAR(50) | Null | Phương thức thanh toán
shipping_full_name | NVARCHAR(255) | Null | Họ tên người nhận
shipping_phone | NVARCHAR(50) | Null | Số điện thoại nhận hàng
shipping_email | NVARCHAR(255) | Null | Email nhận OTP/thông báo
shipping_address | NVARCHAR(1000) | Null | Địa chỉ giao hàng
shipping_latitude | DECIMAL(10,2) | Null | Vĩ độ giao hàng
shipping_longitude | DECIMAL(10,2) | Null | Kinh độ giao hàng
order_note | NVARCHAR(1000) | Null | Ghi chú đơn hàng
payment_reference | NVARCHAR(100) | Null | Mã tham chiếu thanh toán
paid_at | DATETIME | Null | Thời điểm thanh toán thành công
updated_at | DATETIME | Not Null | Thời điểm cập nhật

*Chỉ mục: PK_ORDERS(id)  
| Quan hệ: ORDERS có quan hệ N-1 với USERS; 1-N với ORDER_ITEMS; 1-N với PAYMENTS; 1-1 với SHIPMENTS

11. Bảng ORDER_ITEMS (Chi tiết đơn hàng)
Lưu các sản phẩm thuộc đơn hàng và đơn giá tại thời điểm mua.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính chi tiết đơn
order_id | BIGINT | FK -> ORDERS(id), Not Null | Đơn hàng
wine_id | BIGINT | FK -> WINES(id), Not Null | Sản phẩm
quantity | INT | Not Null | Số lượng mua
price | DECIMAL(10,2) | Not Null | Đơn giá tại thời điểm đặt

*Chỉ mục: PK_ORDER_ITEMS(id)  
| Quan hệ: ORDER_ITEMS có quan hệ N-1 với ORDERS; N-1 với WINES

12. Bảng PAYMENTS (Thanh toán)
Lưu phiên thanh toán của đơn hàng.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính thanh toán
order_id | BIGINT | FK -> ORDERS(id), Not Null | Đơn hàng liên quan
method | NVARCHAR(50) | Not Null | Phương thức thanh toán (Enum dạng STRING)
status | NVARCHAR(50) | Not Null | Trạng thái thanh toán (Enum dạng STRING)
amount | DECIMAL(10,2) | Not Null | Số tiền
currency | NVARCHAR(10) | Not Null, Default VND | Loại tiền tệ
payment_reference | NVARCHAR(100) | Not Null, Unique | Mã tham chiếu thanh toán
gateway_session_id | NVARCHAR(150) | Null | Mã phiên từ cổng thanh toán
gateway_response | NVARCHAR(2000) | Null | Phản hồi cổng thanh toán
created_at | DATETIME | Not Null, Default thời điểm tạo | Thời điểm tạo
updated_at | DATETIME | Not Null, Default thời điểm tạo/cập nhật | Thời điểm cập nhật

*Chỉ mục: PK_PAYMENTS(id), UQ_PAYMENTS_PAYMENT_REFERENCE(payment_reference)  
| Quan hệ: PAYMENTS có quan hệ N-1 với ORDERS; 1-N với PAYMENT_TRANSACTIONS

13. Bảng PAYMENT_TRANSACTIONS (Lịch sử giao dịch thanh toán)
Lưu các sự kiện nghiệp vụ của một bản ghi thanh toán.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính giao dịch thanh toán
payment_id | BIGINT | FK -> PAYMENTS(id), Not Null | Thanh toán liên quan
transaction_type | NVARCHAR(100) | Not Null | Loại giao dịch
status | NVARCHAR(50) | Not Null | Trạng thái giao dịch
payload | NVARCHAR(2000) | Null | Dữ liệu phản hồi/chi tiết
created_at | DATETIME | Not Null, Default thời điểm tạo | Thời điểm tạo giao dịch

*Chỉ mục: PK_PAYMENT_TRANSACTIONS(id)  
| Quan hệ: PAYMENT_TRANSACTIONS có quan hệ N-1 với PAYMENTS

14. Bảng REVIEWS (Đánh giá sản phẩm)
Lưu đánh giá và bình luận của người dùng về sản phẩm.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính đánh giá
wine_id | BIGINT | FK -> WINES(id), Not Null | Sản phẩm được đánh giá
user_id | BIGINT | FK -> USERS(id), Not Null | Người đánh giá
rating | INT | Not Null | Điểm đánh giá
comment | NVARCHAR(1000) | Null | Bình luận
created_at | DATETIME | Not Null | Thời điểm tạo đánh giá

*Chỉ mục: PK_REVIEWS(id)  
| Quan hệ: REVIEWS có quan hệ N-1 với WINES; N-1 với USERS

15. Bảng PASSWORD_RESET_TOKENS (Token đặt lại mật khẩu)
Lưu token phục vụ quy trình đặt lại mật khẩu.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính token
user_id | BIGINT | FK -> USERS(id), Not Null | Người dùng sở hữu token
token | NVARCHAR(120) | Not Null, Unique | Chuỗi token reset
expires_at | DATETIME | Not Null | Hạn hết hiệu lực
used_at | DATETIME | Null | Thời điểm sử dụng token
created_at | DATETIME | Not Null | Thời điểm tạo token

*Chỉ mục: PK_PASSWORD_RESET_TOKENS(id), UQ_PASSWORD_RESET_TOKENS_TOKEN(token)  
| Quan hệ: PASSWORD_RESET_TOKENS có quan hệ N-1 với USERS

16. Bảng SHIPPERS (Nhân viên giao hàng)
Lưu thông tin shipper và trạng thái sẵn sàng giao hàng.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính shipper
user_id | BIGINT | FK -> USERS(id), Not Null, Unique | Tài khoản shipper liên kết
name | NVARCHAR(255) | Not Null | Tên shipper
phone | NVARCHAR(50) | Not Null | Số điện thoại
vehicle_type | NVARCHAR(100) | Null | Loại phương tiện
status | NVARCHAR(50) | Not Null, Default ACTIVE (theo entity) | Trạng thái shipper
is_available | BIT | Not Null, Default 0 | Cờ sẵn sàng nhận đơn
current_latitude | DECIMAL(10,2) | Null | Vĩ độ hiện tại
current_longitude | DECIMAL(10,2) | Null | Kinh độ hiện tại
location_updated_at | DATETIME | Null | Thời điểm cập nhật vị trí
max_concurrent_shipments | INT | Not Null, Default 1 | Số đơn tối đa đồng thời
active_shipment_count | INT | Not Null, Default 0 | Số đơn đang xử lý
last_assignment_at | DATETIME | Null | Thời điểm nhận đơn gần nhất
created_at | DATETIME | Not Null | Thời điểm tạo
updated_at | DATETIME | Not Null | Thời điểm cập nhật

*Chỉ mục: PK_SHIPPERS(id), UQ_SHIPPERS_USER_ID(user_id)  
| Quan hệ: SHIPPERS có quan hệ 1-1 với USERS; 1-N với SHIPMENTS

17. Bảng SHIPMENTS (Đơn giao hàng)
Lưu vòng đời giao hàng và thông tin OTP giao nhận.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính shipment
order_id | BIGINT | FK -> ORDERS(id), Not Null, Unique | Đơn hàng tương ứng
shipper_id | BIGINT | FK -> SHIPPERS(id), Null | Shipper phụ trách
status | NVARCHAR(50) | Not Null, Default PENDING_ASSIGNMENT (theo entity) | Trạng thái shipment
shipping_name | NVARCHAR(255) | Null | Tên người nhận
shipping_phone | NVARCHAR(50) | Null | SĐT người nhận
shipping_email | NVARCHAR(255) | Null | Email người nhận
shipping_address | NVARCHAR(1000) | Null | Địa chỉ giao
shipping_latitude | DECIMAL(10,2) | Null | Vĩ độ giao hàng
shipping_longitude | DECIMAL(10,2) | Null | Kinh độ giao hàng
otp_code | NVARCHAR(6) | Null | Mã OTP giao hàng
otp_created_at | DATETIME | Null | Thời điểm tạo OTP
otp_expires_at | DATETIME | Null | Thời điểm hết hạn OTP
otp_attempt_count | INT | Not Null, Default 0 | Số lần nhập OTP
otp_locked_until | DATETIME | Null | Thời điểm mở khóa OTP
otp_last_sent_at | DATETIME | Null | Lần gửi OTP gần nhất
otp_sent_at | DATETIME | Null | Lần gửi OTP hiện tại
otp_delivery_status | NVARCHAR(20) | Not Null, Default PENDING (theo entity) | Trạng thái gửi OTP
otp_user_id | BIGINT | Null | User ràng buộc OTP
otp_verified | BIT | Not Null, Default 0 | Đã xác thực OTP hay chưa
admin_override | BIT | Not Null, Default 0 | Cờ override bởi admin
admin_override_reason | NVARCHAR(500) | Null | Lý do override
failure_note | NVARCHAR(500) | Null | Ghi chú thất bại
failure_code | NVARCHAR(50) | Null | Mã lỗi thất bại
status_reason | NVARCHAR(500) | Null | Lý do trạng thái
estimated_delivery_at | DATETIME | Null | Thời điểm giao dự kiến
promised_window_start | DATETIME | Null | Bắt đầu khung giao hẹn
promised_window_end | DATETIME | Null | Kết thúc khung giao hẹn
delivery_attempt_count | INT | Not Null, Default 0 | Số lần thử giao
last_delivery_attempt_at | DATETIME | Null | Lần thử giao gần nhất
next_attempt_at | DATETIME | Null | Lần thử giao kế tiếp
assigned_at | DATETIME | Null | Thời điểm phân công
failed_at | DATETIME | Null | Thời điểm thất bại
returned_at | DATETIME | Null | Thời điểm hoàn trả
created_at | DATETIME | Not Null | Thời điểm tạo shipment
updated_at | DATETIME | Not Null | Thời điểm cập nhật shipment
picked_up_at | DATETIME | Null | Thời điểm lấy hàng
delivering_at | DATETIME | Null | Thời điểm bắt đầu giao
completed_at | DATETIME | Null | Thời điểm hoàn tất giao

*Chỉ mục: PK_SHIPMENTS(id), UQ_SHIPMENTS_ORDER_ID(order_id)  
| Quan hệ: SHIPMENTS có quan hệ 1-1 với ORDERS; N-1 với SHIPPERS; 1-N với SHIPMENT_STATUS_HISTORY; 1-N với SHIPMENT_OTP_AUDIT_LOGS

18. Bảng SHIPMENT_STATUS_HISTORY (Lịch sử trạng thái giao hàng)
Lưu lịch sử chuyển trạng thái của shipment.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính lịch sử
shipment_id | BIGINT | FK -> SHIPMENTS(id), Not Null | Shipment liên quan
from_status | NVARCHAR(50) | Null | Trạng thái trước
to_status | NVARCHAR(50) | Not Null | Trạng thái sau
reason | NVARCHAR(500) | Null | Lý do thay đổi
metadata | NVARCHAR(2000) | Null | Dữ liệu bổ sung
actor_user_id | BIGINT | Null | ID người thao tác
actor_username | NVARCHAR(255) | Null | Username người thao tác
created_at | DATETIME | Not Null, Default thời điểm tạo | Thời điểm ghi nhận

*Chỉ mục: PK_SHIPMENT_STATUS_HISTORY(id)  
| Quan hệ: SHIPMENT_STATUS_HISTORY có quan hệ N-1 với SHIPMENTS

19. Bảng SHIPMENT_OTP_AUDIT_LOGS (Nhật ký OTP giao hàng)
Lưu toàn bộ log liên quan OTP trong quá trình giao hàng.

Tên Thuộc tính | Kiểu dữ liệu (MS SQL) | Ràng buộc | Mô tả
id | BIGINT | PK, Auto Increment, Not Null | Khóa chính audit OTP
shipment_id | BIGINT | FK -> SHIPMENTS(id), Not Null | Shipment liên quan
order_id | BIGINT | Null | ID đơn hàng liên quan
otp_user_id | BIGINT | Null | User của OTP
actor_user_id | BIGINT | Null | ID người thao tác
actor_username | NVARCHAR(255) | Null | Username người thao tác
action | NVARCHAR(100) | Not Null | Hành động OTP
status | NVARCHAR(50) | Not Null | Kết quả hành động
reason | NVARCHAR(1000) | Null | Lý do
metadata | NVARCHAR(2000) | Null | Thông tin bổ sung
created_at | DATETIME | Not Null, Default thời điểm tạo | Thời điểm ghi log

*Chỉ mục: PK_SHIPMENT_OTP_AUDIT_LOGS(id)  
| Quan hệ: SHIPMENT_OTP_AUDIT_LOGS có quan hệ N-1 với SHIPMENTS
