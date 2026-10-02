/**
 * StrongWine — Admin Excel Bulk Import & Dry-Run Preview Controller
 */
document.addEventListener('DOMContentLoaded', () => {
    const dropZone = document.getElementById('excelDropZone');
    const fileInput = document.getElementById('excelFileInput');
    const previewContainer = document.getElementById('previewContainer');
    const previewTableBody = document.getElementById('previewTableBody');
    const btnExecuteImport = document.getElementById('btnExecuteImport');
    const importSuccessAlert = document.getElementById('importSuccessAlert');
    const importErrorAlert = document.getElementById('importErrorAlert');
    const dryRunSpinner = document.getElementById('dryRunSpinner');
    const filePromptText = document.getElementById('filePromptText');
    const selectedFileBadge = document.getElementById('selectedFileBadge');

    let currentFile = null;

    if (dropZone && fileInput) {
        // Click to open file dialog
        dropZone.addEventListener('click', (e) => {
            if (e.target.tagName !== 'BUTTON' && !e.target.closest('button')) {
                fileInput.click();
            }
        });

        // Drag & drop visual feedback
        ['dragenter', 'dragover'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.add('border-gold');
                dropZone.style.background = 'rgba(201, 168, 76, 0.08)';
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropZone.addEventListener(eventName, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.remove('border-gold');
                dropZone.style.background = '';
            }, false);
        });

        // Drop event
        dropZone.addEventListener('drop', (e) => {
            const dt = e.dataTransfer;
            if (dt && dt.files && dt.files.length > 0) {
                const file = dt.files[0];
                validateAndProcessFile(file);
            }
        });

        // Input change
        fileInput.addEventListener('change', () => {
            if (fileInput.files && fileInput.files.length > 0) {
                validateAndProcessFile(fileInput.files[0]);
            }
        });
    }

    function validateAndProcessFile(file) {
        if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
            alert('Vui lòng chọn tệp bảng tính Excel có định dạng .xlsx hoặc .xls');
            return;
        }

        currentFile = file;
        if (selectedFileBadge) {
            selectedFileBadge.textContent = `${file.name} (${(file.size / 1024).toFixed(1)} KB)`;
            selectedFileBadge.classList.remove('d-none');
        }
        if (filePromptText) {
            filePromptText.textContent = 'Đã chọn tệp: ' + file.name;
        }

        // Run dry-run preview automatically
        runDryRunPreview(file);
    }

    async function runDryRunPreview(file) {
        if (dryRunSpinner) dryRunSpinner.classList.remove('d-none');
        if (previewContainer) previewContainer.classList.add('d-none');
        if (importSuccessAlert) importSuccessAlert.classList.add('d-none');
        if (importErrorAlert) importErrorAlert.classList.add('d-none');

        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await fetch('/api/admin/excel/preview', {
                method: 'POST',
                body: formData
            });

            if (res.ok) {
                const data = await res.json();
                renderDryRunSummary(data);
                if (previewContainer) previewContainer.classList.remove('d-none');
            } else {
                const errJson = await res.json().catch(() => ({}));
                alert('Không thể đọc file Excel. ' + (errJson.message || 'Vui lòng kiểm tra cấu trúc tiêu đề cột theo mẫu chuẩn.'));
            }
        } catch (err) {
            console.error('Lỗi khi gọi dry-run preview', err);
            alert('Lỗi kết nối máy chủ khi phân tích file Excel.');
        } finally {
            if (dryRunSpinner) dryRunSpinner.classList.add('d-none');
        }
    }

    function renderDryRunSummary(data) {
        document.getElementById('statTotalRows').textContent = data.totalRows || 0;
        document.getElementById('statValidRows').textContent = data.validRowsCount || 0;
        document.getElementById('statErrorRows').textContent = data.errorRowsCount || 0;
        document.getElementById('statInsertCount').textContent = data.insertCount || 0;
        document.getElementById('statUpdateCount').textContent = data.updateCount || 0;

        const errorSection = document.getElementById('errorSection');
        const cleanSection = document.getElementById('cleanSection');

        if (previewTableBody) {
            previewTableBody.innerHTML = '';
            if (data.errors && data.errors.length > 0) {
                if (errorSection) errorSection.classList.remove('d-none');
                if (cleanSection) cleanSection.classList.add('d-none');

                data.errors.forEach(err => {
                    const tr = document.createElement('tr');
                    tr.className = 'table-danger';
                    tr.innerHTML = `
                        <td style="font-weight:700">Dòng ${err.rowNumber}</td>
                        <td><span class="badge text-bg-danger">${escapeHtml(err.fieldName || 'Dữ liệu')}</span></td>
                        <td class="text-danger">${escapeHtml(err.errorMessage || 'Lỗi không xác định')}</td>
                        <td><code style="color:var(--gold-light)">${escapeHtml(err.invalidValue || 'Rỗng')}</code></td>
                    `;
                    previewTableBody.appendChild(tr);
                });
            } else {
                if (errorSection) errorSection.classList.add('d-none');
                if (cleanSection) cleanSection.classList.remove('d-none');
            }
        }

        // Enable execution button if there is at least 1 valid row
        if (btnExecuteImport) {
            btnExecuteImport.disabled = (data.validRowsCount || 0) === 0;
        }
    }

    // Execute Import into Database
    if (btnExecuteImport) {
        btnExecuteImport.addEventListener('click', async () => {
            if (!currentFile) {
                alert('Vui lòng chọn file Excel trước.');
                return;
            }

            const inventoryMode = document.querySelector('input[name="inventoryMode"]:checked')?.value || 'ADD';
            const warehouseSelect = document.getElementById('warehouseSelect');
            const warehouseId = warehouseSelect ? warehouseSelect.value : '1';

            const confirmMsg = `Xác nhận nhập dữ liệu vào hầm rượu?\n- Chế độ tồn kho: ${inventoryMode === 'ADD' ? 'Cộng dồn' : 'Ghi đè'}\n- Dữ liệu hợp lệ sẽ được lưu vào CSDL ngay lập tức.`;
            if (!confirm(confirmMsg)) return;

            btnExecuteImport.disabled = true;
            btnExecuteImport.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-2"></i>Đang lưu vào CSDL...';

            const formData = new FormData();
            formData.append('file', currentFile);
            formData.append('inventoryMode', inventoryMode);
            formData.append('warehouseId', warehouseId);

            try {
                const res = await fetch('/api/admin/excel/import', {
                    method: 'POST',
                    body: formData
                });

                if (res.ok) {
                    const result = await res.json();
                    if (importSuccessAlert) {
                        importSuccessAlert.innerHTML = `
                            <i class="fa-solid fa-circle-check me-2 fs-5"></i>
                            <div>
                                <strong>Nhập dữ liệu thành công!</strong>
                                <div>Đã xử lý <strong>${result.validRowsCount}</strong> dòng (${result.insertCount} rượu mới, ${result.updateCount} cập nhật).</div>
                            </div>
                        `;
                        importSuccessAlert.classList.remove('d-none');
                    }
                    if (previewContainer) previewContainer.classList.add('d-none');
                    btnExecuteImport.innerHTML = '<i class="fa-solid fa-check me-2"></i>Đã Hoàn Tất Nhập';
                } else {
                    const err = await res.json().catch(() => ({}));
                    if (importErrorAlert) {
                        importErrorAlert.innerHTML = `<i class="fa-solid fa-triangle-exclamation me-2"></i>Lỗi: ${err.message || 'Không thể lưu vào CSDL.'}`;
                        importErrorAlert.classList.remove('d-none');
                    }
                    btnExecuteImport.disabled = false;
                    btnExecuteImport.innerHTML = '<i class="fa-solid fa-upload me-2"></i>Thử Lại';
                }
            } catch (e) {
                console.error('Lỗi khi import file Excel', e);
                alert('Lỗi kết nối khi gửi yêu cầu nhập dữ liệu.');
                btnExecuteImport.disabled = false;
                btnExecuteImport.innerHTML = '<i class="fa-solid fa-upload me-2"></i>Xác Nhận Nhập';
            }
        });
    }

    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }
});
