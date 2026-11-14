(function (window, document) {
    "use strict";

    function createBottleMonogram() {
        var span = document.createElement("span");
        span.className = "serif";
        span.style.color = "var(--accent-gold-ink)";
        span.style.fontWeight = "700";
        span.textContent = "S";
        return span;
    }

    function initCartDrawer() {
        var drawerEl = document.getElementById("cartDrawer");
        var itemsEl = document.getElementById("cartDrawerItems");
        var subtotalEl = document.getElementById("cartDrawerSubtotal");
        if (!drawerEl || !itemsEl) {
            return;
        }

        var lastTrigger = null;

        function openDrawer(trigger) {
            renderDrawer();
            lastTrigger = trigger || document.activeElement;
            if (window.SwDialog && typeof window.SwDialog.open === "function") {
                window.SwDialog.open(drawerEl, lastTrigger);
            } else {
                drawerEl.classList.add("open");
                drawerEl.setAttribute("aria-hidden", "false");
            }
        }

        function closeDrawer() {
            if (window.SwDialog && typeof window.SwDialog.close === "function") {
                window.SwDialog.close(drawerEl);
            } else {
                drawerEl.classList.remove("open");
                drawerEl.setAttribute("aria-hidden", "true");
            }
        }

        function renderDrawer() {
            if (!window.StrongWineCart) {
                return;
            }
            var items = window.StrongWineCart.getCart();
            var fmt = window.StrongWineCart.formatCurrency;

            while (itemsEl.firstChild) {
                itemsEl.removeChild(itemsEl.firstChild);
            }

            if (!items.length) {
                var empty = document.createElement("p");
                empty.className = "muted";
                empty.textContent = "Chưa có sản phẩm. Hãy khám phá bộ sưu tập rượu vang.";
                itemsEl.appendChild(empty);
                if (subtotalEl) {
                    subtotalEl.textContent = "0 ₫";
                }
                return;
            }

            var sum = 0;
            items.forEach(function (item) {
                var lineTotal = item.price * item.quantity;
                sum += lineTotal;

                var row = document.createElement("div");
                row.className = "order-row";

                var left = document.createElement("div");
                left.className = "display-flex gap12";
                left.style.flex = "1";

                var imgWrap = document.createElement("div");
                imgWrap.className = "cart-image";
                if (item.image) {
                    var img = document.createElement("img");
                    img.src = item.image;
                    img.alt = item.name || "Rượu vang";
                    img.addEventListener("error", function () {
                        img.remove();
                        imgWrap.appendChild(createBottleMonogram());
                    });
                    imgWrap.appendChild(img);
                } else {
                    imgWrap.appendChild(createBottleMonogram());
                }
                left.appendChild(imgWrap);

                var details = document.createElement("div");
                var title = document.createElement("a");
                title.href = "/wines/" + encodeURIComponent(String(item.productId));
                title.style.fontWeight = "700";
                title.style.display = "block";
                title.textContent = item.name;
                details.appendChild(title);

                var qtyRow = document.createElement("div");
                qtyRow.className = "display-flex gap6 mt4";

                var minusBtn = document.createElement("button");
                minusBtn.type = "button";
                minusBtn.className = "btn btn-outline btn-sm";
                minusBtn.style.minHeight = "28px";
                minusBtn.style.padding = "2px 8px";
                minusBtn.textContent = "−";
                minusBtn.setAttribute("aria-label", "Giảm số lượng " + item.name);
                minusBtn.setAttribute("data-drawer-step-id", String(item.productId));
                minusBtn.setAttribute("data-drawer-delta", "-1");

                var qtyText = document.createElement("span");
                qtyText.className = "tabular-nums";
                qtyText.style.fontSize = "13px";
                qtyText.style.minWidth = "22px";
                qtyText.style.textAlign = "center";
                qtyText.textContent = String(item.quantity);

                var plusBtn = document.createElement("button");
                plusBtn.type = "button";
                plusBtn.className = "btn btn-outline btn-sm";
                plusBtn.style.minHeight = "28px";
                plusBtn.style.padding = "2px 8px";
                plusBtn.textContent = "+";
                plusBtn.disabled = item.stock > 0 && item.quantity >= item.stock;
                plusBtn.setAttribute("aria-label", "Tăng số lượng " + item.name);
                plusBtn.setAttribute("data-drawer-step-id", String(item.productId));
                plusBtn.setAttribute("data-drawer-delta", "1");

                qtyRow.appendChild(minusBtn);
                qtyRow.appendChild(qtyText);
                qtyRow.appendChild(plusBtn);
                details.appendChild(qtyRow);

                var priceSpan = document.createElement("div");
                priceSpan.className = "tabular-nums mt4";
                priceSpan.style.color = "var(--accent-gold-ink)";
                priceSpan.style.fontWeight = "700";
                priceSpan.style.fontSize = "13.5px";
                priceSpan.textContent = fmt(lineTotal);
                details.appendChild(priceSpan);

                left.appendChild(details);
                row.appendChild(left);

                var removeBtn = document.createElement("button");
                removeBtn.type = "button";
                removeBtn.className = "close-btn";
                removeBtn.setAttribute("data-drawer-remove-id", String(item.productId));
                removeBtn.setAttribute("aria-label", "Xóa " + item.name);
                removeBtn.textContent = "×";
                row.appendChild(removeBtn);

                itemsEl.appendChild(row);
            });

            if (subtotalEl) {
                subtotalEl.textContent = fmt(sum);
            }
        }

        document.addEventListener("click", function (event) {
            var openTrigger = event.target.closest(".js-open-cart-drawer");
            if (openTrigger) {
                event.preventDefault();
                openDrawer(openTrigger);
                return;
            }

            if (event.target.closest(".js-close-cart-drawer")) {
                closeDrawer();
                return;
            }

            var stepBtn = event.target.closest("[data-drawer-step-id]");
            if (stepBtn && window.StrongWineCart) {
                var pid = Number(stepBtn.getAttribute("data-drawer-step-id"));
                var delta = Number(stepBtn.getAttribute("data-drawer-delta") || 0);
                var current = window.StrongWineCart.getCart().find(function (item) {
                    return Number(item.productId) === pid;
                });
                if (current) {
                    window.StrongWineCart.updateQuantity(pid, current.quantity + delta);
                    window.StrongWineCart.renderCartPage();
                }
                return;
            }

            var removeBtn = event.target.closest("[data-drawer-remove-id]");
            if (removeBtn && window.StrongWineCart) {
                var removeId = removeBtn.getAttribute("data-drawer-remove-id");
                window.StrongWineCart.removeItem(removeId);
                if (typeof window.StrongWineCart.renderCartPage === "function") {
                    window.StrongWineCart.renderCartPage();
                }
            }
        });

        window.addEventListener("strongwine:cart-updated", renderDrawer);

        window.cartDrawer = {
            openDrawer: openDrawer,
            closeDrawer: closeDrawer,
            renderDrawer: renderDrawer
        };

        renderDrawer();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initCartDrawer, { once: true });
    } else {
        initCartDrawer();
    }
})(window, document);
