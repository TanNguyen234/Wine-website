(function () {
    "use strict";

    var selectedFile = null;
    var lastPreviewValidCount = 0;

    function notify(title, message, tone) {
        if (window.SwToast && typeof window.SwToast.show === "function") {
            window.SwToast.show({
                title: title,
                message: message,
                tone: tone || "info",
                duration: 4800
            });
        }
    }

    function getCsrfHeaders() {
        var headers = {};
        var tokenMeta = document.querySelector("meta[name='_csrf']");
        var headerMeta = document.querySelector("meta[name='_csrf_header']");
        var tokenInput = document.querySelector("input[name='_csrf']");
        var token = tokenMeta ? tokenMeta.getAttribute("content") : (tokenInput ? tokenInput.value : "");
        var headerName = headerMeta ? headerMeta.getAttribute("content") : "X-CSRF-TOKEN";
        if (token && headerName) {
            headers[headerName] = token;
        }
        return headers;
    }

    function clearElement(el) {
        if (!el) {
            return;
        }
        while (el.firstChild) {
            el.removeChild(el.firstChild);
        }
    }

    function renderErrorMessageRow(tbody, text, isNeutral) {
        clearElement(tbody);
        var tr = document.createElement("tr");
        var td = document.createElement("td");
        td.colSpan = 4;
        td.className = isNeutral ? "text-secondary" : "text-danger";
        td.textContent = text;
        tr.appendChild(td);
        tbody.appendChild(tr);
    }

    function renderImportResult(panel, titleEl, bodyEl, isSuccess, titleText, summaryLines) {
        if (!panel || !titleEl || !bodyEl) {
            return;
        }
        panel.classList.remove("d-none");
        panel.dataset.state = isSuccess ? "success" : "error";
        titleEl.textContent = titleText;
        clearElement(bodyEl);

        summaryLines.forEach(function (line) {
            var p = document.createElement("p");
            p.className = "mb-2";
            p.textContent = line;
            bodyEl.appendChild(p);
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        var dropZone = document.getElementById("excelDropZone");
        var fileInput = document.getElementById("excelFileInput");
        var selectedFileName = document.getElementById("selectedFileName");
        var clearBtn = document.getElementById("clearExcelFileBtn");
        var previewBtn = document.getElementById("previewBtn");
        var commitBtn = document.getElementById("commitImportBtn");
        var inventoryModeSelect = document.getElementById("inventoryModeSelect");
        var warehouseIdSelect = document.getElementById("warehouseIdSelect");
        var strictModeCheckbox = document.getElementById("strictModeCheckbox");

        var previewSection = document.getElementById("previewSection");
        var previewTotalRows = document.getElementById("previewTotalRows");
        var previewValidRows = document.getElementById("previewValidRows");
        var previewErrorRows = document.getElementById("previewErrorRows");
        var previewErrorsWrap = document.getElementById("previewErrorsWrap");
        var previewErrorsBody = document.getElementById("previewErrorsBody");
        var previewSuccessNote = document.getElementById("previewSuccessNote");

        var resultPanel = document.getElementById("importResultPanel");
        var resultTitle = document.getElementById("importResultTitle");
        var resultBody = document.getElementById("importResultBody");

        var confirmDialog = document.getElementById("confirmExcelImportDialog");
        var confirmSummary = document.getElementById("confirmExcelImportSummary");
        var confirmProceedBtn = document.getElementById("confirmExcelImportProceedBtn");

        if (!dropZone || !fileInput) {
            return;
        }

        function resetFileState() {
            selectedFile = null;
            lastPreviewValidCount = 0;
            fileInput.value = "";
            if (selectedFileName) {
                selectedFileName.textContent = "Chưa có tệp nào được chọn";
            }
            if (previewBtn) {
                previewBtn.disabled = true;
            }
            if (commitBtn) {
                commitBtn.disabled = true;
            }
            if (clearBtn) {
                clearBtn.classList.add("d-none");
            }
            if (previewSection) {
                previewSection.classList.add("d-none");
            }
            if (resultPanel) {
                resultPanel.classList.add("d-none");
            }
            clearElement(previewErrorsBody);
        }

        function acceptFile(file) {
            if (!file) {
                return;
            }
            var lowerName = (file.name || "").toLowerCase();
            if (!lowerName.endsWith(".xlsx")) {
                notify("Định dạng không hợp lệ", "Vui lòng chọn tệp Excel đúng định dạng .xlsx.", "bad");
                return;
            }
            if (file.size > 5 * 1024 * 1024) {
                notify("Tệp vượt quá dung lượng", "Dung lượng tệp tối đa là 5MB.", "bad");
                return;
            }
            selectedFile = file;
            lastPreviewValidCount = 0;
            if (selectedFileName) {
                var sizeKb = Math.max(1, Math.round(file.size / 1024));
                selectedFileName.textContent = file.name + " (" + sizeKb + " KB)";
            }
            if (previewBtn) {
                previewBtn.disabled = false;
            }
            if (commitBtn) {
                commitBtn.disabled = true;
            }
            if (clearBtn) {
                clearBtn.classList.remove("d-none");
            }
            if (previewSection) {
                previewSection.classList.add("d-none");
            }
            if (resultPanel) {
                resultPanel.classList.add("d-none");
            }
        }

        function buildFormData() {
            var fd = new FormData();
            fd.append("file", selectedFile);
            fd.append("inventoryMode", inventoryModeSelect ? inventoryModeSelect.value : "DELTA");
            fd.append("warehouseId", warehouseIdSelect ? warehouseIdSelect.value : "1");
            fd.append("strictMode", strictModeCheckbox && strictModeCheckbox.checked ? "true" : "false");
            return fd;
        }

        dropZone.addEventListener("click", function () {
            fileInput.click();
        });

        dropZone.addEventListener("keydown", function (event) {
            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                fileInput.click();
            }
        });

        dropZone.addEventListener("dragover", function (event) {
            event.preventDefault();
            dropZone.classList.add("is-dragover");
        });

        dropZone.addEventListener("dragleave", function () {
            dropZone.classList.remove("is-dragover");
        });

        dropZone.addEventListener("drop", function (event) {
            event.preventDefault();
            dropZone.classList.remove("is-dragover");
            var files = event.dataTransfer ? event.dataTransfer.files : null;
            if (files && files.length > 0) {
                acceptFile(files[0]);
            }
        });

        fileInput.addEventListener("change", function () {
            if (fileInput.files && fileInput.files.length > 0) {
                acceptFile(fileInput.files[0]);
            }
        });

        if (clearBtn) {
            clearBtn.addEventListener("click", function (event) {
                event.stopPropagation();
                resetFileState();
            });
        }

        if (previewBtn) {
            previewBtn.addEventListener("click", async function () {
                if (!selectedFile) {
                    return;
                }
                previewBtn.disabled = true;
                previewBtn.textContent = "Đang kiểm tra...";
                if (commitBtn) {
                    commitBtn.disabled = true;
                }
                try {
                    var response = await window.fetch("/api/admin/excel/preview", {
                        method: "POST",
                        headers: getCsrfHeaders(),
                        body: buildFormData()
                    });
                    var data = await response.json();
                    if (!response.ok) {
                        throw new Error((data && data.message) ? data.message : ("HTTP " + response.status));
                    }

                    var totalRows = Number(data.totalRows) || 0;
                    var validCount = Number(data.validRowsCount) || 0;
                    var errorCount = Number(data.errorRowsCount) || 0;
                    lastPreviewValidCount = validCount;

                    if (previewSection) {
                        previewSection.classList.remove("d-none");
                    }
                    if (previewTotalRows) {
                        previewTotalRows.textContent = String(totalRows);
                    }
                    if (previewValidRows) {
                        previewValidRows.textContent = String(validCount);
                    }
                    if (previewErrorRows) {
                        previewErrorRows.textContent = String(errorCount);
                    }

                    clearElement(previewErrorsBody);
                    var errors = Array.isArray(data.errors) ? data.errors : [];
                    if (errors.length > 0) {
                        if (previewErrorsWrap) {
                            previewErrorsWrap.classList.remove("d-none");
                        }
                        if (previewSuccessNote) {
                            previewSuccessNote.classList.add("d-none");
                        }
                        errors.forEach(function (err) {
                            var tr = document.createElement("tr");

                            var tdRow = document.createElement("td");
                            tdRow.setAttribute("data-label", "Dòng");
                            tdRow.className = "admin-num";
                            tdRow.textContent = "Dòng " + (err.rowNumber != null ? String(err.rowNumber) : "?");

                            var tdField = document.createElement("td");
                            tdField.setAttribute("data-label", "Trường lỗi");
                            var badge = document.createElement("span");
                            badge.className = "status bad";
                            badge.textContent = err.fieldName || err.column || "Dữ liệu";
                            tdField.appendChild(badge);

                            var tdVal = document.createElement("td");
                            tdVal.setAttribute("data-label", "Giá trị lỗi");
                            var code = document.createElement("code");
                            code.textContent = err.invalidValue != null && err.invalidValue !== "" ? String(err.invalidValue) : "(trống)";
                            tdVal.appendChild(code);

                            var tdMsg = document.createElement("td");
                            tdMsg.setAttribute("data-label", "Mô tả");
                            tdMsg.textContent = err.errorMessage || "Giá trị không hợp lệ";

                            tr.appendChild(tdRow);
                            tr.appendChild(tdField);
                            tr.appendChild(tdVal);
                            tr.appendChild(tdMsg);
                            previewErrorsBody.appendChild(tr);
                        });
                    } else {
                        if (previewErrorsWrap) {
                            previewErrorsWrap.classList.add("d-none");
                        }
                        if (previewSuccessNote) {
                            previewSuccessNote.classList.remove("d-none");
                        }
                    }

                    var strictEnabled = Boolean(strictModeCheckbox && strictModeCheckbox.checked);
                    var canCommit = validCount > 0 && (!strictEnabled || errorCount === 0);
                    if (commitBtn) {
                        commitBtn.disabled = !canCommit;
                    }
                    notify(
                        "Hoàn tất kiểm tra tệp",
                        "Hợp lệ " + validCount + "/" + totalRows + " dòng" + (errorCount > 0 ? (", có " + errorCount + " lỗi") : "."),
                        errorCount > 0 ? "warn" : "good"
                    );
                } catch (err) {
                    if (previewSection) {
                        previewSection.classList.remove("d-none");
                    }
                    if (previewErrorsWrap) {
                        previewErrorsWrap.classList.remove("d-none");
                    }
                    if (previewSuccessNote) {
                        previewSuccessNote.classList.add("d-none");
                    }
                    renderErrorMessageRow(previewErrorsBody, "Lỗi kiểm tra dữ liệu: " + (err.message || "Không xác định"), false);
                    notify("Không thể đọc tệp Excel", err.message || "Vui lòng kiểm tra lại định dạng tệp.", "bad");
                } finally {
                    previewBtn.disabled = false;
                    previewBtn.textContent = "Xem trước & kiểm tra";
                }
            });
        }

        async function executeCommitImport() {
            if (!selectedFile || !commitBtn) {
                return;
            }
            commitBtn.disabled = true;
            commitBtn.textContent = "Đang nhập dữ liệu...";
            try {
                var response = await window.fetch("/api/admin/excel/import", {
                    method: "POST",
                    headers: getCsrfHeaders(),
                    body: buildFormData()
                });
                var data = await response.json();
                if (!response.ok) {
                    throw new Error((data && data.message) ? data.message : ("HTTP " + response.status));
                }

                var insertedCount = data.insertCount ?? data.insertedCount ?? 0;
                var updatedCount = data.updateCount ?? data.updatedCount ?? 0;
                var skippedCount = data.errorRowsCount ?? data.skippedCount ?? 0;
                var processedCount = data.validRowsCount ?? data.inventoryProcessedCount ?? 0;
                var lines = [
                    "Tổng dòng đã đọc: " + (data.totalRows != null ? data.totalRows : 0),
                    "Sản phẩm tạo mới: " + insertedCount,
                    "Sản phẩm cập nhật: " + updatedCount,
                    "Dòng bỏ qua / lỗi: " + skippedCount,
                    "Tồn kho đã xử lý: " + processedCount
                ];
                renderImportResult(resultPanel, resultTitle, resultBody, true, "Nhập dữ liệu rượu thành công", lines);
                notify("Nhập Excel thành công", "Đã tạo mới " + insertedCount + " và cập nhật " + updatedCount + " sản phẩm.", "good");
            } catch (err) {
                renderImportResult(resultPanel, resultTitle, resultBody, false, "Nhập dữ liệu thất bại", [
                    err.message || "Không thể ghi dữ liệu vào hệ thống."
                ]);
                notify("Nhập Excel thất bại", err.message || "Lỗi hệ thống", "bad");
            } finally {
                commitBtn.disabled = false;
                commitBtn.textContent = "Xác nhận nhập vào hệ thống";
            }
        }

        if (commitBtn) {
            commitBtn.addEventListener("click", function () {
                if (!selectedFile || commitBtn.disabled) {
                    return;
                }
                if (confirmDialog && window.SwDialog && typeof window.SwDialog.open === "function") {
                    if (confirmSummary) {
                        var modeLabel = inventoryModeSelect && inventoryModeSelect.value === "ABSOLUTE"
                            ? "Ghi đè tồn kho tuyệt đối (ABSOLUTE)"
                            : "Cộng dồn tồn kho (DELTA)";
                        confirmSummary.textContent = "Bạn sắp nhập " + lastPreviewValidCount + " dòng hợp lệ từ tệp \""
                            + selectedFile.name + "\" với chế độ: " + modeLabel + ".";
                    }
                    window.SwDialog.open(confirmDialog, commitBtn);
                } else {
                    executeCommitImport();
                }
            });
        }

        if (confirmProceedBtn) {
            confirmProceedBtn.addEventListener("click", function () {
                if (confirmDialog && window.SwDialog && typeof window.SwDialog.close === "function") {
                    window.SwDialog.close(confirmDialog);
                }
                executeCommitImport();
            });
        }
    });
})();
