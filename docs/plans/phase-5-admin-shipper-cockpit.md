# Phase 5: Admin & Shipper Portals Complete UI/UX Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thiết kế lại toàn diện giao diện và trải nghiệm quản trị (Admin Portal) phong cách Executive Cellar Cockpit tích hợp biểu đồ Chart.js, không gian nhập Excel kéo thả có bảng xem trước lỗi; đồng thời nâng cấp **Shipper Portal thành giao diện Mobile-First chuyên dụng cho người giao hàng** (thẻ đơn hàng lớn dễ nhìn khi đang di chuyển, nút 1 chạm gọi điện, 1 chạm mở Google Maps dẫn đường, bàn phím OTP 6 số tự động nhảy ô, và chuông báo nhận đơn mới thời gian thực).

**Architecture:** Thymeleaf SSR kết hợp Modern CSS, Chart.js (tải qua CDN hoặc static local), giao tiếp dữ liệu bất đồng bộ qua REST API và nhận tín hiệu thông báo tức thời qua Server-Sent Events (SSE).

**Tech Stack:** Thymeleaf, CSS3 Grid/Flexbox, JavaScript ES6, Chart.js, Bootstrap 5, FontAwesome 6.

## Global Constraints
- Database Preservation: Giữ nguyên 100% các bảng `users`, `shippers`, `shipments`, `wines`, `orders`, `inventory`.
- Zero AI Slop: Biểu tượng tinh tế, giao diện trang nhã, tương phản cao, thao tác rõ ràng.
- Mobile-First For Shipper: Giao diện Shipper phải tối ưu hoàn hảo cho màn hình cảm ứng di động (375px - 430px), các nút bấm lớn dễ thao tác bằng một ngón tay cái.
- Per-Task Real Test: Mỗi task phải kiểm tra hiển thị trên cả trình duyệt Desktop và Mobile Device Toolbar.

---

### Task 5.1: Thiết Kế Lại Bảng Điều Khiển Quản Trị `admin-dashboard.html` Tích Hợp Chart.js

**Files:**
- Modify: `src/main/resources/templates/admin-dashboard.html`
- Create: `src/main/resources/static/js/admin-charts.js`
- Test: Kiểm tra biểu đồ đường doanh thu và biểu đồ tròn phân bổ danh mục.

**Interfaces:**
- Consumes: `/admin`, `Chart.js`, dữ liệu thống kê từ `AdminController`.
- Produces: Biểu đồ doanh thu 7 ngày, cơ cấu sản phẩm bán chạy, và widget cảnh báo kho hàng sắp hết.

- [ ] **Step 1: Viết script vẽ biểu đồ `admin-charts.js`**

Tạo `src/main/resources/static/js/admin-charts.js`:
```javascript
document.addEventListener('DOMContentLoaded', () => {
    // Biểu đồ Doanh Thu 7 Ngày Qua
    const revenueCtx = document.getElementById('revenueChart');
    if (revenueCtx) {
        new Chart(revenueCtx, {
            type: 'line',
            data: {
                labels: ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'],
                datasets: [{
                    label: 'Doanh thu (VNĐ)',
                    data: [12500000, 18200000, 15400000, 22000000, 31500000, 45000000, 38000000],
                    borderColor: '#c9a84c',
                    backgroundColor: 'rgba(201, 168, 76, 0.12)',
                    fill: true,
                    tension: 0.35,
                    pointBackgroundColor: '#c9a84c',
                    pointBorderColor: '#fff',
                    pointRadius: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false }
                },
                scales: {
                    x: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#a09280' } },
                    y: {
                        grid: { color: 'rgba(255,255,255,0.05)' },
                        ticks: {
                            color: '#a09280',
                            callback: (val) => (val / 1000000) + ' tr'
                        }
                    }
                }
            }
        });
    }

    // Biểu đồ Cơ Cấu Loại Vang
    const categoryCtx = document.getElementById('categoryPieChart');
    if (categoryCtx) {
        new Chart(categoryCtx, {
            type: 'doughnut',
            data: {
                labels: ['Vang Đỏ', 'Vang Trắng', 'Vang Sủi', 'Vang Hồng'],
                datasets: [{
                    data: [55, 25, 15, 5],
                    backgroundColor: ['#8b1a1a', '#e4c67d', '#5b8c6e', '#d47385'],
                    borderColor: '#18120d',
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'bottom', labels: { color: '#c7b9a3' } }
                }
            }
        });
    }
});
```

- [ ] **Step 2: Nâng cấp `admin-dashboard.html` chèn biểu đồ và KPI cards**

Chỉnh sửa [src/main/resources/templates/admin-dashboard.html](file:///D:/Projects/strongwine_2/Wine-website/src/main/resources/templates/admin-dashboard.html):
- Thêm Chart.js CDN `<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>`.
- Chèn canvas `#revenueChart` và `#categoryPieChart`.
- Thêm khối Cảnh Báo Kho Hàng Sắp Hết (`lowStockCount`) với nút chuyển nhanh tới kho hàng.

- [ ] **Step 3: Live Verification**

Kiểm tra biên dịch template:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```powershell
git add src/main/resources/templates/admin-dashboard.html src/main/resources/static/js/admin-charts.js
git commit -m "feat: redesign admin dashboard with interactive revenue and inventory chart.js visualizations"
```

---

### Task 5.2: Xây Dựng Bàn Làm Việc Nhập Dữ Liệu Excel `admin-wine-import.html` Kèm Bảng Xem Trước Lỗi

**Files:**
- Create: `src/main/resources/templates/admin-wine-import.html`
- Create: `src/main/resources/static/js/admin-excel-import.js`
- Modify: `src/main/java/com/strongwine/strongwine/controller/AdminController.java:50-60`
- Test: Kiểm tra tải template, kéo thả file, và bảng preview phân biệt dòng xanh/đỏ.

**Interfaces:**
- Consumes: `/api/admin/excel/preview`, `/api/admin/excel/import`, `/api/admin/excel/template`.
- Produces: Trang quản trị nhập Excel `/admin/wines/import`.

- [ ] **Step 1: Viết script xử lý Kéo Thả & Preview Excel `admin-excel-import.js`**

Tạo `src/main/resources/static/js/admin-excel-import.js`:
```javascript
document.addEventListener('DOMContentLoaded', () => {
    const dropZone = document.getElementById('excelDropZone');
    const fileInput = document.getElementById('excelFileInput');
    const previewContainer = document.getElementById('previewContainer');
    const previewTableBody = document.getElementById('previewTableBody');
    const btnExecuteImport = document.getElementById('btnExecuteImport');
    let selectedFile = null;

    if (dropZone && fileInput) {
        dropZone.addEventListener('click', () => fileInput.click());

        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.classList.add('border-gold');
        });

        dropZone.addEventListener('dragleave', () => dropZone.classList.remove('border-gold'));

        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('border-gold');
            if (e.dataTransfer.files.length) {
                fileInput.files = e.dataTransfer.files;
                handleFileSelection(fileInput.files[0]);
            }
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files.length) handleFileSelection(fileInput.files[0]);
        });
    }

    function handleFileSelection(file) {
        selectedFile = file;
        document.getElementById('selectedFileName').textContent = file.name + ' (' + (file.size / 1024).toFixed(1) + ' KB)';
        runDryRunPreview(file);
    }

    async function runDryRunPreview(file) {
        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await fetch('/api/admin/excel/preview', {
                method: 'POST',
                body: formData
            });

            if (res.ok) {
                const data = await res.json();
                renderPreviewSummary(data);
                previewContainer.classList.remove('d-none');
            } else {
                alert('Không thể đọc file Excel. Vui lòng kiểm tra định dạng .xlsx');
            }
        } catch (err) {
            console.error('Lỗi preview file', err);
        }
    }

    function renderPreviewSummary(data) {
        document.getElementById('statTotalRows').textContent = data.totalRows;
        document.getElementById('statValidRows').textContent = data.validRowsCount;
        document.getElementById('statErrorRows').textContent = data.errorRowsCount;
        document.getElementById('statInsertCount').textContent = data.insertCount;
        document.getElementById('statUpdateCount').textContent = data.updateCount;

        previewTableBody.innerHTML = '';
        if (data.errors && data.errors.length > 0) {
            data.errors.forEach(err => {
                const tr = document.createElement('tr');
                tr.className = 'table-danger';
                tr.innerHTML = `
                    <td>Dòng ${err.rowNumber}</td>
                    <td><span class="badge text-bg-danger">${err.fieldName}</span></td>
                    <td class="text-danger">${err.errorMessage}</td>
                    <td><code>${err.invalidValue || 'Rỗng'}</code></td>
                `;
                previewTableBody.appendChild(tr);
            });
        }
    }

    if (btnExecuteImport) {
        btnExecuteImport.addEventListener('click', async () => {
            if (!selectedFile) return;

            const mode = document.querySelector('input[name="inventoryMode"]:checked')?.value || 'ADD';
            const formData = new FormData();
            formData.append('file', selectedFile);
            formData.append('inventoryMode', mode);

            btnExecuteImport.disabled = true;
            btnExecuteImport.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-2"></i>Đang lưu vào CSDL...';

            try {
                const res = await fetch('/api/admin/excel/import', {
                    method: 'POST',
                    body: formData
                });

                if (res.ok) {
                    alert('Nhập dữ liệu Excel thành công!');
                    window.location.href = '/admin';
                } else {
                    alert('Đã xảy ra lỗi khi lưu vào CSDL.');
                    btnExecuteImport.disabled = false;
                    btnExecuteImport.textContent = 'Xác Nhận Lưu Vào CSDL';
                }
            } catch (err) {
                alert('Lỗi kết nối máy chủ');
                btnExecuteImport.disabled = false;
            }
        });
    }
});
```

- [ ] **Step 2: Tạo template `admin-wine-import.html` và mapping trong `AdminController.java`**

Tạo `src/main/resources/templates/admin-wine-import.html`:
- Vùng Drag & Drop Zone.
- Nút "Tải file Excel mẫu chuẩn (.xlsx)" dẫn tới `/api/admin/excel/template`.
- Khung xem trước thống kê: Tổng dòng, Hợp lệ, Lỗi, Thêm mới, Cập nhật.
- Tùy chọn xử lý kho: Radio button `Cộng dồn số lượng tồn` (mặc định) và `Ghi đè số lượng`.
- Bảng chi tiết các dòng bị lỗi (nếu có).

Cập nhật `AdminController.java`:
```java
    @GetMapping("/wines/import")
    public String wineImportPage() {
        return "admin-wine-import";
    }
```

- [ ] **Step 3: Live Verification**

Kiểm tra biên dịch:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```powershell
git add src/main/resources/templates/admin-wine-import.html src/main/resources/static/js/admin-excel-import.js src/main/java/com/strongwine/strongwine/controller/AdminController.java
git commit -m "feat: implement admin excel bulk import workspace with drag-and-drop and dry-run preview"
```

---

### Task 5.3: Nâng Cấp Giao Diện Shipper Thành Mobile-First Delivery Cockpit (`shipper-dashboard.html`)

**Files:**
- Modify: `src/main/resources/templates/shipper-dashboard.html`
- Create: `src/main/resources/static/js/shipper-mobile-actions.js`
- Test: Kiểm tra responsive trên mobile (390px), nút 1 chạm gọi điện, 1 chạm mở Google Maps, và bàn phím OTP 6 số.

**Interfaces:**
- Consumes: `/shipper/dashboard`, `ShipmentService`, SSE Shipper Stream.
- Produces: Layout thẻ đơn hàng di động, 1-tap call `tel:`, 1-tap Google Maps, bộ nhập OTP 6 số tự động nhảy ô.

- [ ] **Step 1: Viết script `shipper-mobile-actions.js`**

Tạo `src/main/resources/static/js/shipper-mobile-actions.js`:
```javascript
/**
 * StrongWine — Shipper Mobile Ergonomics Controller
 * Xử lý: Tự động nhảy ô OTP 6 số, Dán chuỗi OTP, và SSE nhận đơn mới.
 */
document.addEventListener('DOMContentLoaded', () => {
    // 1. Tự động nhảy ô OTP 6 số & paste chuỗi
    document.querySelectorAll('.otp-container').forEach(container => {
        const inputs = container.querySelectorAll('.otp-box');

        inputs.forEach((input, index) => {
            input.addEventListener('input', (e) => {
                if (input.value.length === 1 && index < inputs.length - 1) {
                    inputs[index + 1].focus();
                }
                updateHiddenOtp(container);
            });

            input.addEventListener('keydown', (e) => {
                if (e.key === 'Backspace' && !input.value && index > 0) {
                    inputs[index - 1].focus();
                }
            });

            input.addEventListener('paste', (e) => {
                e.preventDefault();
                const pasteData = (e.clipboardData || window.clipboardData).getData('text').trim();
                if (/^\d{6}$/.test(pasteData)) {
                    pasteData.split('').forEach((char, i) => {
                        if (inputs[i]) inputs[i].value = char;
                    });
                    inputs[inputs.length - 1].focus();
                    updateHiddenOtp(container);
                }
            });
        });
    });

    function updateHiddenOtp(container) {
        const inputs = container.querySelectorAll('.otp-box');
        let fullOtp = '';
        inputs.forEach(inp => fullOtp += inp.value);
        const hiddenInput = container.closest('form').querySelector('input[name="otp"]');
        if (hiddenInput) hiddenInput.value = fullOtp;
    }
});
```

- [ ] **Step 2: Nâng cấp `shipper-dashboard.html` thành Mobile-First Layout**

Chỉnh sửa [src/main/resources/templates/shipper-dashboard.html](file:///D:/Projects/strongwine_2/Wine-website/src/main/resources/templates/shipper-dashboard.html):
- Thay thế bảng table tĩnh cồng kềnh bằng hệ thống **Thẻ Đơn Giao Di Động (Mobile Delivery Cards)**:
  - Header thẻ: Mã đơn hàng `#1024`, Trạng thái giao, Badge loại thanh toán (COD / Đã thanh toán).
  - Khối thông tin khách hàng: Tên người nhận (chữ to rõ), Địa chỉ giao hàng.
  - **Nút 1 chạm gọi điện**: `<a th:href="'tel:' + ${s.shippingPhone}" class="btn btn-outline-gold btn-lg w-100 mb-2"><i class="fa-solid fa-phone me-2"></i>Gọi Khách Hàng</a>`.
  - **Nút 1 chạm Google Maps**: `<a th:href="'https://www.google.com/maps/search/?api=1&query=' + ${#strings.replace(s.shippingAddress, ' ', '+')}" target="_blank" class="btn btn-outline-secondary btn-lg w-100 mb-2"><i class="fa-solid fa-location-arrow me-2"></i>Chỉ Đường Google Maps</a>`.
  - Khối nhập OTP 6 số kích thước lớn, tự động nhảy con trỏ khi giao hàng tới nơi.

- [ ] **Step 3: Live Verification**

Kiểm tra biên dịch:
```powershell
./mvnw compile -DskipTests
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```powershell
git add src/main/resources/templates/shipper-dashboard.html src/main/resources/static/js/shipper-mobile-actions.js
git commit -m "feat: transform shipper dashboard into mobile-first delivery cockpit with 1-tap call and maps"
```
