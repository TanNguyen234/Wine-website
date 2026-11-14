(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var dialog = document.getElementById("inventoryModal");
        var form = document.getElementById("inventoryActionForm");
        var titleEl = document.getElementById("inventoryModalLabel");
        var subtitleEl = document.getElementById("inventoryModalSubtitle");
        var wineNameEl = document.getElementById("inventoryWineName");
        var availableEl = document.getElementById("inventoryCurrentAvailable");
        var warehouseInput = document.getElementById("inventoryWarehouseId");
        var warehouseNameEl = document.getElementById("inventoryWarehouseName");
        var quantityInput = document.getElementById("inventoryQuantity");
        var noteInput = document.getElementById("inventoryNote");
        var submitBtn = document.getElementById("inventorySubmitBtn");

        if (!dialog || !form) {
            return;
        }

        function openInventoryDialog(trigger) {
            var action = trigger.getAttribute("data-action") === "export" ? "export" : "import";
            var wineId = trigger.getAttribute("data-wine-id") || "";
            var wineName = trigger.getAttribute("data-wine-name") || "Sản phẩm";
            var warehouseId = trigger.getAttribute("data-warehouse-id") || "1";
            var warehouseName = trigger.getAttribute("data-warehouse-name") || "Kho mặc định";
            var available = trigger.getAttribute("data-available") || "0";

            form.action = "/admin/inventory/" + action + "/" + encodeURIComponent(wineId);

            if (titleEl) {
                titleEl.textContent = action === "export" ? "Xuất kho thủ công" : "Nhập kho bổ sung";
            }
            if (subtitleEl) {
                subtitleEl.textContent = action === "export"
                    ? "Giảm số lượng khả dụng tại kho và ghi nhận nhật ký biến động."
                    : "Cộng thêm số lượng khả dụng vào kho và cập nhật cảnh báo tồn.";
            }
            if (wineNameEl) {
                wineNameEl.textContent = wineName;
            }
            if (availableEl) {
                availableEl.textContent = available + " chai";
            }
            if (warehouseInput) {
                warehouseInput.value = warehouseId;
            }
            if (warehouseNameEl) {
                warehouseNameEl.textContent = warehouseName;
            }
            if (quantityInput) {
                quantityInput.value = "1";
            }
            if (noteInput) {
                noteInput.value = "";
            }
            if (submitBtn) {
                submitBtn.textContent = action === "export" ? "Xác nhận xuất kho" : "Xác nhận nhập kho";
                submitBtn.classList.toggle("btn-danger", action === "export");
                submitBtn.classList.toggle("btn-primary", action !== "export");
            }

            if (window.SwDialog && typeof window.SwDialog.open === "function") {
                window.SwDialog.open(dialog, trigger);
            } else if (typeof dialog.showModal === "function" && !dialog.open) {
                dialog.showModal();
            }
        }

        document.addEventListener("click", function (event) {
            var trigger = event.target.closest("[data-inventory-action]");
            if (!trigger) {
                return;
            }
            event.preventDefault();
            openInventoryDialog(trigger);
        });
    });
})();
