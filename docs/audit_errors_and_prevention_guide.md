# BÁO CÁO PHÂN TÍCH TOÀN DIỆN CÁC LỖI TRONG CONVERSATION VÀ QUY TẮC PHÒNG NGỪA (POST-MORTEM & PREVENTION GUIDE)

**Dự án:** Tiểu luận Phát triển phần mềm hướng đối tượng - Hệ thống StrongWine  
**Tài liệu mục tiêu:** `D:\Projects\strongwine_2\Wine-website\docs\Tieu-luan-Phat-trien-phan-mem-huong-doi-tuong-StrongWine-ban-hoan-chinh.docx`  
**Ngày lập:** 18/10/2026  
**Mục đích:** Ghi nhận chi tiết nguyên nhân gốc rễ (root cause) của tất cả 8 lỗi xuất hiện trong quá trình xử lý tài liệu, thiết lập quy chuẩn kỹ thuật bắt buộc để cập nhật vào Skill Workflow và ngăn chặn triệt để mọi lỗi tái diễn.

---

## I. TỔNG HỢP VÀ PHÂN TÍCH NGUYÊN NHÂN GỐC RỄ (ROOT CAUSE ANALYSIS)

### 1. Lỗi 1: Dấu gạch dưới giả (Pseudo-Underline) ở tiêu đề trang bìa
- **Hiện tượng:** Dưới dòng chữ "TIỂU LUẬN HỌC PHẦN" trên trang bìa xuất hiện một đường kẻ ngang màu xanh/accent, nhìn giống như gạch chân văn bản nhưng khi chọn khối văn bản thì không thấy bật chế độ Underline.
- **Nguyên nhân kỹ thuật (Root Cause):** 
  - Đoạn văn P5 ("TIỂU LUẬN HỌC PHẦN") được gán kiểu định dạng `<w:pStyle w:val="Title"/>`.
  - Trong tệp `word/styles.xml`, định nghĩa kiểu `Title` mặc định chứa thẻ viền đoạn văn phía dưới:
    ```xml
    <w:pBdr>
      <w:bottom w:val="single" w:sz="8" w:space="4" w:color="4F81BD" w:themeColor="accent1"/>
    </w:pBdr>
    ```
  - Khi Microsoft Word render đoạn văn này, nó tự động kẻ một đường viền dưới màu xanh accent 1 bên dưới chữ, tạo thành vệt gạch ngang gây mất thẩm mỹ bìa luận văn.
- **Biện pháp khắc phục triệt để:**
  - Can thiệp loại bỏ hoàn toàn thẻ `<w:pBdr>` khỏi định nghĩa style `Title` trong `styles.xml`.
  - Hoặc trên đoạn văn trang bìa, thiết lập tường minh `<w:pBdr><w:bottom w:val="none"/></w:pBdr>` hoặc chuyển style sang `Normal` kèm định dạng font trực tiếp (Times New Roman, Bold, kích thước chuẩn, căn giữa, không viền).

---

### 2. Lỗi 2: Mất hoặc thiếu Header và Footer chuẩn học thuật
- **Hiện tượng:** Văn bản bị mất hoặc không có tiêu đề đầu trang (running header), chân trang chỉ có số trang trơn trọi hoặc bị đứt gãy giữa các section.
- **Nguyên nhân kỹ thuật (Root Cause):**
  - Tệp gốc vốn dĩ có `s.header` rỗng và chân trang chỉ chứa trường `{PAGE}` thô.
  - Khi script tự động hóa can thiệp định dạng section hoặc chia tách văn bản, việc không khởi tạo đúng các quan hệ header/footer (`w:headerReference`, `w:footerReference`) dẫn đến việc văn bản thiếu hẳn tiêu đề đầu trang chuyên nghiệp.
  - Section 0 (Trang bìa) không được tách biệt hoàn toàn hoặc thiếu cấu hình `differentFirstPage` / unlinked header-footer, khiến người dùng thấy thiếu vắng hệ thống phân trang học thuật chuẩn mực.
- **Biện pháp khắc phục triệt để:**
  - Cấu hình Section 0 (Trang bìa và Trang phụ bìa) KHÔNG có Header và KHÔNG có Footer.
  - Từ Section 1 (hoặc phần nội dung chính), thiết lập:
    - **Header:** Căn lề 2 bên hoặc căn phải thanh nhã: `"Tiểu luận: Phát triển phần mềm hướng đối tượng - Hệ thống StrongWine"` (kèm đường kẻ chỉ mỏng cách điệu chuẩn báo cáo tốt nghiệp/tiểu luận đại học).
    - **Footer:** Căn giữa hoặc căn phải: `"Trang {PAGE}"` (sử dụng trường Word động `<w:fldChar>` với `w:instrText PAGE`).

---

### 3. Lỗi 3: Mục lục (TOC) mất liên kết Hyperlink / Bookmark với các Heading nhỏ
- **Hiện tượng:** Người dùng ấn Ctrl + Click chuột vào các mục con trong mục lục (như 1.1, 1.2, 1.5.1...) nhưng Word không tự động nhảy đến vị trí đề mục trong thân bài; tính năng điều hướng bị vô hiệu hóa hoàn toàn.
- **Nguyên nhân kỹ thuật (Root Cause):**
  - Script xử lý trước đó (`master_upgrade_v2.py`) khi chuẩn hóa khoảng trắng và căn dòng đã quét qua các đoạn thuộc Mục lục (`toc 1`, `toc 2`, `toc 3`) và gán lại nội dung bằng các Run văn bản thuần túy (`<w:r><w:t>...</w:t></w:r>`).
  - Thao tác này đã vô tình xóa sạch thẻ `<w:hyperlink w:anchor="_Toc...">` và thẻ trường tính trang tự động `<w:instrText> PAGEREF _Toc... \h </w:instrText>` vốn có của Word OOXML.
- **Biện pháp khắc phục triệt để:**
  - Tuyệt đối KHÔNG thay thế cấu trúc XML của đoạn Mục lục bằng text tĩnh.
  - Phải bảo toàn nguyên vẹn thẻ `<w:hyperlink>` và `<w:instrText> PAGEREF </w:instrText>` kèm bookmark tương ứng trên từng Heading trong thân bài.
  - Sử dụng trường mã gốc Word TOC (`{ TOC \o "1-3" \h \z \u }`) để đảm bảo khả năng Update Field tự động 100% trong Microsoft Word.

---

### 4. Lỗi 4: Sơ đồ màn hình (Wireframe) sai thực tế, vẽ theo kiểu WinForms Desktop
- **Hiện tượng:** Các sơ đồ giao diện được vẽ với thanh tiêu đề cửa sổ Desktop OS `[ _ ][ □ ][ X ]`, nút bấm khối xám cổ điển, giao diện hộp thoại đặc trưng của Windows Forms, hoàn toàn không tương thích với một trang web hiện đại.
- **Nguyên nhân kỹ thuật (Root Cause):**
  - Script sinh wireframe sử dụng mẫu canvas mô phỏng Desktop App cũ kỹ, không đọc và phân tích cấu trúc template Thymeleaf/HTML của dự án StrongWine (`src/main/resources/templates/`).
  - Bỏ qua các thành phần cốt lõi của Web: Thanh URL trình duyệt, Top Bar / Navbar (Logo, Danh mục, Giỏ hàng, Tài khoản), Breadcrumb điều hướng, Hero Banner, Product Grid Card, Form checkout web responsive...
- **Biện pháp khắc phục triệt để:**
  - Bám sát cấu trúc các tệp template HTML trong dự án StrongWine:
    - Khách hàng: `home.html`, `wines.html`, `wine-detail.html`, `cart.html`, `checkout.html`, `order-detail.html`, `login.html`, `register.html`...
    - Quản trị: `admin/dashboard.html`, `admin/wines.html`, `admin/wine-form.html`, `admin/users.html`, `admin/orders.html`, `admin/inventory.html`...
    - Shipper: `shipper/dashboard.html`, `shipper/shipments.html`, `shipper/delivery-detail.html`...
  - Vẽ sơ đồ wireframe dạng **Browser Mockup hiện đại**: Có thanh địa chỉ trình duyệt web (`https://strongwine.vn/...`), Navbar web chuẩn, responsive grid layout, icon và typography hiện đại.

---

### 5. Lỗi 5: Đặt sai vị trí sơ đồ màn hình (Bị đẩy xuống cuối file thay vì nằm sau UML)
- **Hiện tượng:** Tất cả 26 sơ đồ màn hình bị gom hết vào "Phụ lục 2" ở cuối tài liệu (trang 173+), tách rời hoàn toàn khỏi nội dung phân tích chức năng ở các chương.
- **Nguyên nhân kỹ thuật (Root Cause):**
  - Sự sai lệch trong định hướng cấu trúc: Thay vì đặt giao diện minh họa trực tiếp sau các mô hình phân tích thiết kế hướng đối tượng (Use Case, Activity, Sequence Diagram) để người đọc theo dõi mạch lạc, script lại gom thành một phụ lục riêng ở cuối bài.
- **Biện pháp khắc phục triệt để:**
  - Đặt các sơ đồ màn hình **NGAY SAU CÁC SƠ ĐỒ UML TƯƠNG ỨNG** trong từng chương nghiệp vụ:
    - **Chương 4 (Phân hệ Khách hàng):** Sau sơ đồ Activity Checkout và Sequence Đặt hàng, đặt ngay các sơ đồ màn hình Trang chủ, Danh mục rượu, Chi tiết sản phẩm, Giỏ hàng, Thanh toán, Lịch sử đơn.
    - **Chương 5 (Phân hệ Quản trị & Kho):** Sau sơ đồ Activity Thêm rượu/Upload ảnh và Sequence Admin, đặt ngay các sơ đồ Dashboard quản trị, Quản lý rượu, Thêm/Sửa rượu, Quản lý tài khoản, Quản lý đơn hàng, Quản lý kho.
    - **Chương 6 (Phân hệ Shipper):** Sau sơ đồ nghiệp vụ giao hàng và xác thực OTP, đặt ngay sơ đồ Dashboard Shipper, Danh sách vận đơn nhận giao, Chi tiết giao hàng & Nhập mã OTP.

---

### 6. Lỗi 6: Mục 7.6 chứa bảng thống kê kỳ lạ và dán ảnh báo lỗi HTTP 500
- **Hiện tượng:** Tại mục 7.6 ("Ảnh giao diện và giới hạn của sơ đồ lịch sử"), xuất hiện một bảng danh mục 30 tệp ảnh và một bức ảnh chụp lỗi server `Hình 7.1: Lỗi máy chủ tại bước xác nhận đơn hàng`.
- **Nguyên nhân kỹ thuật (Root Cause):**
  - Tệp gốc mang tàn dư của giai đoạn nghiệm thu kiểm thử sơ bộ, trong đó ghi lại bằng chứng lỗi kỹ thuật (HTTP 500 Whitelabel Error).
  - Quá trình nâng cấp tài liệu không biên tập/thay thế phần tàn dư này, mà lại sao chép nguyên trạng một bảng kê khai ảnh lỗi và ảnh chụp lỗi máy chủ vào báo cáo chính thức.
- **Biện pháp khắc phục triệt để:**
  - Xóa bỏ bức ảnh lỗi máy chủ HTTP 500 (`Hình 7.1`) và xóa bỏ Bảng 21 (bảng kê khai trạng thái chụp ảnh).
  - Biên tập lại toàn bộ Mục 7.6 thành nội dung học thuật chuẩn mực: **"Đánh giá tính tiện dụng giao diện (UI/UX Review) và sự tương thích với mô hình phân tích"**: Phân tích khả năng đáp ứng responsive, trải nghiệm đặt hàng, tiêu chuẩn tương phản màu sắc và kiến trúc component giao diện.

---

### 7. Lỗi 7: Sơ đồ phân rã trang (Hierarchy Diagram) chỉ có 2 cấp và nhét tất cả vào 1 ô
- **Hiện tượng:** Các sơ đồ phân rã màn hình (Hình 2.1, 4.0, 5.0, 6.0) được vẽ rất sơ sài với chỉ 2 cấp, trong đó mỗi phân hệ chỉ là một ô chữ nhật to chứa một danh sách các gạch đầu dòng liệt kê các trang, không thể hiện được cấu trúc cây phân cấp website.
- **Nguyên nhân kỹ thuật (Root Cause):**
  - Sử dụng hàm vẽ dạng danh thiếp (`draw_card()`) chứa bullet points thay vì thuật toán vẽ đồ thị phân nhánh cây (Tree Graph Structure).
- **Biện pháp khắc phục triệt để:**
  - Thiết kế lại 100% sơ đồ phân rã theo dạng **Cây cấu trúc phân cấp website (Site Map Hierarchy Tree)** chuẩn mực:
    - **Cấp 1 (Gốc):** Hệ thống StrongWine (hoặc Phân hệ nghiệp vụ).
    - **Cấp 2 (Nhóm chức năng/Module):** Ví dụ Phân hệ Khách hàng chia thành: Khám phá sản phẩm, Quản lý giỏ & Đặt hàng, Tài khoản cá nhân.
    - **Cấp 3+ (Màn hình chi tiết):** **MỖI MÀN HÌNH LÀ MỘT HỘP CHỮ NHẬT RIÊNG BIỆT** (Ví dụ: `[Trang chủ]`, `[Danh mục rượu]`, `[Bộ lọc nâng cao]`, `[Chi tiết rượu]`, `[Giỏ hàng]`, `[Trang Checkout]`, `[Cổng thanh toán]`, `[Lịch sử đơn]`, `[Theo dõi vận đơn]`).
    - Nối các hộp bằng đường kẻ phân nhánh (Orthogonal Tree Connectors) rõ ràng, bố cục cân đối trên khổ ngang (Landscape hoặc A4 Portrait vừa vặn), không bị tràn lề hay cắt cụt.

---

### 8. Lỗi 8: Bảng dữ liệu mẫu thiếu cột và bị đổ nền màu xanh không chuẩn học thuật
- **Hiện tượng:** Các bảng dữ liệu mẫu ở Chương 3 chỉ có 4-6 cột (bỏ bớt nhiều trường của cơ sở dữ liệu), và toàn bộ các ô tiêu đề lẫn hàng dữ liệu đều bị tô màu xanh lam (`#E6EEF8`, `#D9E2EC`, `#F0F4F8`).
- **Nguyên nhân kỹ thuật (Root Cause):**
  - Script chuẩn bị dữ liệu mẫu tự ý cắt bớt cột để "vừa khung hình", làm sai lệch cấu trúc dữ liệu thực tế trong mã nguồn SQL (`strongwine-full-database.sql`).
  - Áp dụng màu nền UI trang trí web vào văn bản tiểu luận học thuật. Quy chuẩn trình bày tiểu luận/khóa luận đại học yêu cầu bảng biểu phải có nền trắng trang nhã, viền đen mảnh đơn giản, không được lạm dụng màu sắc sặc sỡ.
- **Biện pháp khắc phục triệt để:**
  - **Đầy đủ 100% số cột:** Mỗi bảng dữ liệu mẫu phải thể hiện trọn vẹn toàn bộ các trường theo đúng lược đồ DDL SQL (kể cả khóa chính, khóa ngoại, các cờ trạng thái, timestamp...).
  - **Quy chuẩn bảng học thuật:** 
    - Nền bảng hoàn toàn trong suốt hoặc trắng tinh khiết (`w:shd w:val="clear"`).
    - Tiêu đề cột in đậm (Bold), căn giữa, viền kẻ ngang rõ nét.
    - Căn chỉnh độ rộng từng cột hợp lý và sử dụng font chữ cỡ 8.5pt - 9pt để toàn bộ các cột hiển thị đẹp mắt, không bị tràn ra ngoài lề trang.

---

### 9. Lỗi 9: Lấy nội dung cũ từ tài liệu tham khảo làm quy chuẩn / khung sườn bài báo cáo
- **Hiện tượng:** Sao chép hoặc áp đặt khung sườn, cấu trúc chương mục và nội dung phân tích từ tài liệu tham khảo cũ (File 2) vào bài tiểu luận mới, làm mất đi các phân tích kiến trúc chuyên sâu, hiện đại của hệ thống StrongWine.
- **Nguyên nhân kỹ thuật (Root Cause):** 
  - Không phân định rạch ròi giữa "Tham khảo về hình thức trình bày & trích xuất hình ảnh minh họa" với "Nội dung học thuật và kiến trúc phần mềm thực tế". 
  - Tài liệu cũ đã lỗi thời, không phản ánh các công nghệ hiện đại như Spring Security RBAC, cơ chế khóa bi quan Pessimistic Locking, tích hợp cổng thanh toán kép Stripe/VNPay, quy trình phân công giao vận bằng mã OTP hay trợ lý AI Gemini.
- **Biện pháp khắc phục triệt để:**
  - **Xác lập ranh giới tài liệu rõ ràng:** Các tài liệu tham khảo cung cấp chỉ dùng để tham khảo quy cách trình bày (margins, spacing, cấu trúc các trang bìa/lời cảm ơn/lời cam đoan/bảng biểu) và trích xuất nguyên gốc các sơ đồ/ảnh giao diện chất lượng cao.
  - **Bảo toàn phân tích chuyên sâu:** Tuyệt đối giữ nguyên và đào sâu 100% nội dung phân tích kỹ thuật, 80 yêu cầu chức năng (FR01–FR80), 4 yêu cầu phi chức năng (NF01–NF04), 11 lớp miền Class Diagram và 19 bảng cơ sở dữ liệu thực tế của hệ thống StrongWine mới.

---

### 10. Lỗi 10: Văn phong dịch máy / Sáo rỗng AI (Vietnamese AI Writing Clichés)
- **Hiện tượng:** Văn bản chứa các từ ngữ phóng đại, hoa mỹ, sáo rỗng thường thấy trong văn phong AI (như "bước tiến vượt bậc", "đột phá mang tính cách mạng", "minh chứng hùng hồn", "bức tranh toàn cảnh", "không thể không nhắc đến", "đóng vai trò vô cùng quan trọng", cấu trúc câu công thức lặp lại "Không chỉ... mà còn...").
- **Nguyên nhân kỹ thuật (Root Cause):** Việc sinh văn bản tự động không được kiểm soát chặt chẽ bằng từ điển cấm sáo rỗng (forbidden clichés list), dẫn đến các câu văn dài dòng, thiếu tính kỹ thuật trực diện của một bài báo cáo khoa học/kỹ thuật phần mềm.
- **Biện pháp khắc phục triệt để:**
  - **Áp dụng triệt để bộ lọc rà soát văn phong kỹ thuật tự nhiên:** Thay thế toàn bộ các tính từ phóng đại bằng các câu khẳng định chủ động, trực diện, mô tả chính xác cơ chế hoạt động kỹ thuật (ví dụ: thay vì "đóng vai trò vô cùng quan trọng bảo vệ dữ liệu" thì viết "áp dụng cơ chế khóa bi quan Pessimistic Locking để ngăn chặn hiện tượng overselling trong các giao dịch đồng thời").
  - **Chuẩn hóa thuật ngữ kỹ thuật chuyên ngành:** Giữ đúng các thuật ngữ công nghệ quốc tế (Spring Security RBAC, Pessimistic Locking, DDL schema, HikariCP, Fat JAR, REST endpoint, Webhook/IPN) kết hợp diễn giải tiếng Việt chuẩn xác, mạch lạc.

---

### 11. Lỗi 11: Cắt cụt ảnh chụp màn hình dài (Cropped / Cutoff Screenshots)
- **Hiện tượng:** Các giao diện web có chiều dọc lớn (như Trang Checkout gồm cả form, tóm tắt giỏ và bản đồ vị trí; hoặc Admin Dashboard gồm thẻ KPI và lưới danh mục rượu) khi chèn vào trang văn bản khổ dọc (Portrait) bị co ép tỉ lệ, tràn lề hoặc bị công cụ cắt cụt mất phần dưới khiến người đọc không thấy được nút thanh toán hay danh sách dữ liệu.
- **Nguyên nhân kỹ thuật (Root Cause):** Cố ép một ảnh chụp màn hình dài vào một ô hình duy nhất có chiều cao giới hạn trên trang văn bản A4 dọc, dẫn đến việc Word hoặc script tự động crop hoặc resize làm mờ chữ, mất thông tin nghiệp vụ quan trọng.
- **Biện pháp khắc phục triệt để:**
  - **Áp dụng giải pháp Tách đôi khối thành phần (Dual-Block Splitting):**
    - Với trang Checkout: Chia thành Khối 3.2.8.1 (Form thông tin giao hàng & Tóm tắt chi phí đơn) và Khối 3.2.8.2 (Bản đồ số định vị GPS & Lựa chọn phương thức thanh toán).
    - Với Admin Dashboard: Chia thành Khối 3.2.12.1 (Thẻ chỉ số đo lường KPI tổng quan doanh thu) và Khối 3.2.12.2 (Lưới dữ liệu quản lý danh mục sản phẩm rượu).
  - Từng ảnh được đặt ở độ rộng chuẩn 5.8 inches, giữ nguyên 100% tỉ lệ khung hình gốc (natural aspect ratio), hiển thị sắc nét từng nút bấm và nhãn giao diện mà không bị cắt cụt bất kỳ điểm ảnh nào.

---

### 12. Lỗi 12: Nhiễm bẩn tiêu đề đề mục (Heading Text Hygiene) & Số trang tĩnh
- **Hiện tượng:** Tiêu đề các mục bị chèn ký tự tab và số trang cố định vào chuỗi văn bản (ví dụ: `MỞ ĐẦU\t11`), khiến mục lục bị lỗi hiển thị số trang kép hoặc sai lệch khi văn bản thay đổi bố cục; số thứ tự hình bị trùng lặp (ví dụ 2 hình cùng mang số 3.2.19).
- **Nguyên nhân kỹ thuật (Root Cause):** Script parse từ tệp OCR hoặc tệp text cũ vô tình đọc dính phần số trang ở mép phải dòng tiêu đề và ghi thẳng vào thuộc tính `paragraph.text` của Heading; thiếu bước chuẩn hóa chuỗi và đánh số thứ tự hình độc lập.
- **Biện pháp khắc phục triệt để:**
  - **Vệ sinh tuyệt đối chuỗi tiêu đề (Heading Text Hygiene):** Chuỗi Heading chỉ chứa đúng tên đề mục (ví dụ: `LỜI MỞ ĐẦU`), cấm tuyệt đối chứa ký tự `\t` hay số trang tĩnh.
  - **Phân trang động hoàn toàn:** Để Microsoft Word tự động tính toán số trang thông qua trường mã `{ TOC \o "1-3" \h \z \u }`.
  - **Chuẩn hóa đánh số thứ tự hình ảnh và bảng biểu:** Đảm bảo mỗi hình và mỗi bảng mang một mã số duy nhất, phân cấp rõ ràng theo chương và tiểu mục (ví dụ: Hình 3.2.8.1, Hình 3.2.8.2, Hình 3.2.19, Hình 3.2.20...).

---

## II. QUY TẮC CẬP NHẬT VÀO SKILL WORKFLOW (MANDATORY RULES FOR FUTURE TASKS)

Nhằm đảm bảo không bao giờ lặp lại các sai sót trên, các quy tắc sau đây được tích hợp vĩnh viễn vào quy trình thực thi tài liệu Word:

1. **Quy tắc Kiểm tra Style Bìa:** Luôn kiểm tra định nghĩa của style `Title` và các đoạn văn trang bìa; cấm tuyệt đối tồn tại `w:pBdr` hoặc đường kẻ ngang trang trí dưới các tiêu đề bìa trừ khi quy chuẩn nhà trường yêu cầu.
2. **Quy tắc Header/Footer Bất biến:** Trang bìa và phụ bìa luôn luôn tách biệt không có header/footer; tất cả các trang nội dung phải duy trì running header học thuật và số trang dạng trường động (`PAGE`).
3. **Quy tắc Bảo toàn Mục lục (TOC Fidelity):** Cấm sửa đổi trực tiếp nội dung chuỗi của các đoạn Mục lục nếu làm mất các thẻ `<w:hyperlink>` và `<w:instrText> PAGEREF </w:instrText>`. Mọi cập nhật số trang phải thông qua Word Field Update.
4. **Quy tắc Wireframe Web Thực tế:** Sơ đồ giao diện cho ứng dụng Web phải thể hiện khung trình duyệt Web (Browser Frame), đường dẫn URL thực tế, và layout chuẩn của template HTML dự án; tuyệt đối không dùng giao diện WinForms/Desktop.
5. **Quy tắc Bố trí Minh họa Đồng vị (In-Situ Placement):** Sơ đồ màn hình nghiệp vụ phải được đặt ngay sát sau các sơ đồ UML tương ứng trong các chương chức năng; không được đùn đẩy ảnh chức năng chính xuống phụ lục cuối bài.
6. **Quy tắc Xử lý Tàn dư Lỗi:** Không bao giờ đưa ảnh chụp lỗi hệ thống (HTTP 500, crash) hoặc bảng kê khai lỗi tạm thời vào tài liệu chính thức; mọi vấn đề lỗi phải được biên tập lại thành đánh giá kỹ thuật chuyên nghiệp.
7. **Quy tắc Cây Phân rã Đa cấp (1 Ô/Trang):** Sơ đồ phân rã cấu trúc website phải là cây phân nhánh tối thiểu 3 cấp; mỗi màn hình/trang web con bắt buộc phải là một khối hộp chữ nhật riêng biệt có đường nối rõ ràng.
8. **Quy tắc Bảng Biểu Học thuật Thuần khiết:** Bảng dữ liệu mẫu phải có đầy đủ 100% các cột từ cơ sở dữ liệu; toàn bộ bảng sử dụng nền trắng học thuật (NO colored background), kẻ viền tiêu chuẩn.
9. **Quy tắc Ranh giới Tài liệu Tham khảo (Reference Paper Boundary Rule):** Tài liệu cũ cung cấp chỉ dùng làm mẫu đối soát quy cách trình bày học thuật và nguồn trích xuất ảnh minh họa gốc; tuyệt đối không dùng nội dung cũ làm khung sườn thay thế cho kiến trúc và phân tích nghiệp vụ của hệ thống mới.
10. **Quy tắc Khử Nhiễm Sáo rỗng AI (Vietnamese AI De-Biasing Rule):** Nghiêm cấm sử dụng các sáo rỗng AI; văn phong phải thuần túy học thuật kỹ thuật phần mềm, sử dụng câu chủ động trực diện và thuật ngữ chuyên môn chính xác.
11. **Quy tắc Tách đôi Ảnh Giao diện Dài (Dual-Block Splitting Rule):** Các ảnh chụp màn hình dài vượt quá tỷ lệ trang A4 dọc bắt buộc phải tách thành hai khối liên tiếp logic kèm chú thích phân cấp (ví dụ: .1 và .2), đảm bảo 100% không bị cắt cụt mép biên hay tràn lề.
13. **Quy tắc Bảo toàn Căn cứ Tài liệu Master (Master File Integrity Rule):** Khi người dùng yêu cầu chỉnh sửa một file báo cáo đã có sẵn (đặc biệt là các bản sao lưu master có kích thước lớn và nhiều chương chuyên sâu), cấm tuyệt đối việc tự ý khởi tạo một file mới hay tổng hợp lại từ một kịch bản tóm tắt làm mất các chương nội dung (như làm mất Chương 4, 5, 6, 7). Mọi thao tác sửa đổi bắt buộc phải là *chỉnh sửa trực tiếp tại chỗ (In-Place Mutation)* trên đúng đối tượng DOM của file Master, chỉ can thiệp vào các thành phần bị lỗi (như lề, font, giãn dòng, co tỷ lệ ảnh tràn trang, chuẩn hóa caption 1:1, thay thế mục lục tĩnh bằng trường động).
14. **Quy tắc Triệt tiêu Lỗi Chữ Giãn Cách Bất thường (Word Abnormal Spacing Elimination Rule):** Hiện tượng chữ bị giãn cách bất thường (lỗ hổng khoảng trắng lớn giữa các từ khi căn đều hai bên Justify) trong Word xuất phát từ việc thiếu khối `<w:compat><w:compatSetting w:name="compatibilityMode" w:val="15"/></w:compat>` (khiến Word dùng thuật toán ngắt dòng lạc hậu Word 2003) kết hợp với tính năng tự động giãn chữ Đông Á / Latin (`autoSpaceDE` và `autoSpaceDN`). Khắc phục triệt để bằng cách: kích hoạt compatibilityMode=15 trong `settings.xml`, tắt `autoSpaceDE="0"` và `autoSpaceDN="0"`, và chuẩn hóa khoảng cách từ trong các đoạn văn có chứa từ kỹ thuật / code dài.
15. **Quy tắc Giới hạn Chiều cao Ảnh và Triệt tiêu Nhập nhằng Chú thích (Image Height Bounding & Caption Disambiguation Rule):** Chiều cao tối đa của bất kỳ sơ đồ UML hay ảnh chụp nào trong khổ giấy A4 (với lề trên/dưới 2.0cm) không được vượt quá **14.5 – 15.0 cm** để tránh bị tràn trang làm cắt xén ảnh hoặc đẩy dòng caption sang trang sau. Giữa hai hình ảnh đặt liên tiếp nhau bắt buộc phải có một đoạn văn văn bản chuyển tiếp nghiệp vụ (transition paragraph); cấm tuyệt đối đặt hai hình ảnh chỉ cách nhau bởi một dòng caption vì WordAssistant sẽ báo lỗi `caption_ambiguous` và Word sẽ dễ bị lỗi đè ảnh.



## 2026-10-20 verification correction

This dated addendum records a separate review of strongwine_course_essay_final_checked_v2.docx against the current StrongWine source snapshot (fc63d312998b40380ce6d9fed61a14d6924836a8). Do not merge its results into older run claims without checking their evidence.

- Latest checked DOCX: D:\Download\WordAssistant\artifacts\strongwine_course_essay_final_checked_v2.docx, SHA-256 8198bdc4f27e4b286a3564e0f5c73b2532b8d56c50dd160fbf57759f9895f07a; Microsoft Word render: 174 pages.
- Audit: 63 inline images, 13 diagrams, 22 project screenshot assets, 19 route checks, 25 dynamic sequence fields. No crop rectangles or aspect mismatch. Fifteen contact sheets covered all pages; selected pages were inspected at high resolution.
- H5.10 sequence field was moved inside the visible caption number. An empty-cart image labeled checkout and a sitemap mislabeled as shipper profile editing were removed.
- No fresh runtime capture was performed. No course rubric/profile was supplied, so course compliance remains NOT RUN. These findings do not support old universal-compliance or runtime-freshness claims.
- Workflow and prevention notes: D:\Download\WordAssistant\docs\strongwine-course-essay-workflow.md and D:\Download\WordAssistant\artifacts\STRONGWINE_COURSE_ESSAY_LESSONS.md.

