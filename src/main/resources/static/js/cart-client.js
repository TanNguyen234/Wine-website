(function (window, document) {
    "use strict";

    var STORAGE_KEY = "cart";
    var VOUCHER_SESSION_KEY = "sw_applied_voucher";

    function toNumber(value, fallback) {
        var n = Number(value);
        return Number.isFinite(n) ? n : fallback;
    }

    function showNotice(title, message, tone) {
        if (window.SwToast && typeof window.SwToast.show === "function") {
            window.SwToast.show({
                title: title || "Thông báo",
                message: message || "",
                tone: tone || "info"
            });
            return true;
        }
        return false;
    }

    function getCsrfHeaders(extra) {
        var headers = Object.assign({}, extra || {});
        var headerMeta = document.querySelector('meta[name="_csrf_header"]');
        var tokenMeta = document.querySelector('meta[name="_csrf"]');
        if (tokenMeta && tokenMeta.content) {
            headers[(headerMeta && headerMeta.content) || "X-CSRF-TOKEN"] = tokenMeta.content;
        }
        return headers;
    }

    function normalizeItem(raw) {
        if (!raw || raw.productId == null) {
            return null;
        }
        var productId = toNumber(raw.productId, 0);
        var quantity = Math.max(0, Math.floor(toNumber(raw.quantity, 0)));
        var price = Math.max(0, toNumber(raw.price, 0));
        var stock = Math.max(0, Math.floor(toNumber(raw.stock != null ? raw.stock : 99, 99)));

        if (productId <= 0 || quantity <= 0) {
            return null;
        }
        return {
            productId: productId,
            name: String(raw.name || "Sản phẩm"),
            price: price,
            quantity: quantity,
            image: raw.image ? String(raw.image) : "",
            stock: stock
        };
    }

    function getCart() {
        try {
            var raw = window.localStorage.getItem(STORAGE_KEY);
            if (!raw) {
                return [];
            }
            var parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) {
                return [];
            }
            return parsed.map(normalizeItem).filter(Boolean);
        } catch (_) {
            return [];
        }
    }

    function notifyCartUpdated() {
        updateBadges();
        window.dispatchEvent(new CustomEvent("strongwine:cart-updated"));
    }

    function saveCart(items) {
        try {
            window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
        } catch (_) {}
        notifyCartUpdated();
    }

    function clearCart() {
        try {
            window.localStorage.removeItem(STORAGE_KEY);
        } catch (_) {}
        notifyCartUpdated();
    }

    function updateBadges() {
        var count = getCart().reduce(function (sum, item) {
            return sum + item.quantity;
        }, 0);
        document.querySelectorAll(".js-cart-count").forEach(function (el) {
            el.textContent = String(count);
        });
    }

    function addToCart(payload, opts) {
        var options = opts || {};
        var productId = toNumber(payload.productId, 0);
        var quantity = Math.max(1, Math.floor(toNumber(payload.quantity, 1)));
        var stock = Math.max(0, Math.floor(toNumber(payload.stock != null ? payload.stock : 99, 99)));

        if (productId <= 0) {
            showNotice("Giỏ hàng", "Sản phẩm không hợp lệ.", "warn");
            return false;
        }
        if (stock <= 0) {
            showNotice("Hết hàng", "Sản phẩm hiện đã hết hàng trong kho.", "warn");
            return false;
        }

        var items = getCart();
        var existing = items.find(function (x) {
            return x.productId === productId;
        });

        if (existing) {
            existing.quantity = Math.min(existing.quantity + quantity, stock);
            existing.name = payload.name || existing.name;
            existing.price = Math.max(0, toNumber(payload.price, existing.price));
            existing.image = payload.image || existing.image;
            existing.stock = stock;
        } else {
            items.push({
                productId: productId,
                name: String(payload.name || "Sản phẩm"),
                price: Math.max(0, toNumber(payload.price, 0)),
                quantity: Math.min(quantity, stock),
                image: payload.image ? String(payload.image) : "",
                stock: stock
            });
        }

        saveCart(items);
        if (!options.silent) {
            showNotice("Đã thêm vào giỏ", String(payload.name || "Chai vang") + " (SL: " + quantity + ")", "ok");
        }
        return true;
    }

    function removeItem(productId) {
        var targetId = toNumber(productId, 0);
        var items = getCart().filter(function (x) {
            return x.productId !== targetId;
        });
        saveCart(items);
    }

    function updateQuantity(productId, quantity) {
        var targetId = toNumber(productId, 0);
        var qty = Math.floor(toNumber(quantity, 0));
        var items = getCart();
        var item = items.find(function (x) {
            return x.productId === targetId;
        });
        if (!item) {
            return;
        }

        if (qty <= 0) {
            removeItem(targetId);
            return;
        }
        item.quantity = Math.min(qty, Math.max(1, item.stock || 99));
        saveCart(items);
    }

    function formatCurrency(value) {
        return new Intl.NumberFormat("vi-VN").format(toNumber(value, 0)) + " ₫";
    }

    function hydrateReorderItemsIfPresent() {
        var nodes = document.querySelectorAll("[data-reorder-item]");
        if (!nodes.length) {
            return;
        }
        var added = 0;
        nodes.forEach(function (node) {
            var ok = addToCart({
                productId: node.getAttribute("data-product-id"),
                name: node.getAttribute("data-name"),
                price: node.getAttribute("data-price"),
                quantity: node.getAttribute("data-quantity"),
                image: node.getAttribute("data-image"),
                stock: node.getAttribute("data-stock")
            }, { silent: true });
            if (ok) {
                added += 1;
            }
        });
        if (added > 0) {
            showNotice("Mua lại đơn hàng", "Đã đồng bộ " + added + " dòng rượu vào giỏ hàng.", "ok");
        }
    }

    function bindAddButtons() {
        document.addEventListener("click", function (event) {
            var btn = event.target.closest("[data-cart-add='true'], .js-add-to-cart");
            if (!btn) {
                return;
            }
            event.preventDefault();

            var quantitySelector = btn.getAttribute("data-cart-quantity-selector");
            var quantity = toNumber(btn.getAttribute("data-cart-quantity") || btn.getAttribute("data-quantity") || 1, 1);
            if (quantitySelector) {
                var quantityInput = document.querySelector(quantitySelector);
                if (quantityInput) {
                    quantity = toNumber(quantityInput.value || quantityInput.textContent, 1);
                }
            }

            var ok = addToCart({
                productId: btn.getAttribute("data-cart-product-id") || btn.getAttribute("data-wine-id"),
                name: btn.getAttribute("data-cart-name") || btn.getAttribute("data-wine-name"),
                price: btn.getAttribute("data-cart-price") || btn.getAttribute("data-wine-price"),
                image: btn.getAttribute("data-cart-image") || btn.getAttribute("data-wine-img"),
                stock: btn.getAttribute("data-cart-stock") || btn.getAttribute("data-wine-stock") || 99,
                quantity: quantity
            });

            if (!ok) {
                return;
            }

            var redirect = btn.getAttribute("data-cart-redirect");
            if (redirect) {
                window.location.href = redirect;
                return;
            }

            if (window.cartDrawer && typeof window.cartDrawer.openDrawer === "function") {
                window.cartDrawer.openDrawer(btn);
            }
        });
    }

    function buildThumbElement(item) {
        var box = document.createElement("div");
        box.className = "cart-image";
        if (item.image) {
            var img = document.createElement("img");
            img.src = item.image;
            img.alt = item.name || "Rượu vang";
            img.addEventListener("error", function () {
                img.remove();
                var mono = document.createElement("span");
                mono.className = "serif";
                mono.style.color = "var(--accent-gold-ink)";
                mono.textContent = "S";
                box.appendChild(mono);
            });
            box.appendChild(img);
        } else {
            var fallback = document.createElement("span");
            fallback.className = "serif";
            fallback.style.color = "var(--accent-gold-ink)";
            fallback.textContent = "S";
            box.appendChild(fallback);
        }
        return box;
    }

    function buildCartRow(item, isTableBody) {
        var lineTotal = item.price * item.quantity;

        if (isTableBody) {
            var tr = document.createElement("tr");

            var tdImg = document.createElement("td");
            tdImg.appendChild(buildThumbElement(item));
            tr.appendChild(tdImg);

            var tdName = document.createElement("td");
            var link = document.createElement("a");
            link.href = "/wines/" + encodeURIComponent(String(item.productId));
            link.style.fontWeight = "700";
            link.textContent = item.name;
            var stockNote = document.createElement("div");
            stockNote.className = "muted";
            stockNote.style.fontSize = "12px";
            stockNote.textContent = "Tồn kho khả dụng: " + item.stock + " chai";
            tdName.appendChild(link);
            tdName.appendChild(stockNote);
            tr.appendChild(tdName);

            var tdPrice = document.createElement("td");
            tdPrice.className = "tabular-nums";
            tdPrice.textContent = formatCurrency(item.price);
            tr.appendChild(tdPrice);

            var tdQty = document.createElement("td");
            var qtyWrap = document.createElement("div");
            qtyWrap.className = "display-flex gap6";

            var decBtn = document.createElement("button");
            decBtn.type = "button";
            decBtn.className = "btn btn-outline btn-sm";
            decBtn.textContent = "−";
            decBtn.setAttribute("aria-label", "Giảm số lượng " + item.name);
            decBtn.setAttribute("data-cart-step-id", String(item.productId));
            decBtn.setAttribute("data-cart-delta", "-1");

            var input = document.createElement("input");
            input.type = "number";
            input.min = "1";
            input.max = String(Math.max(1, item.stock || 99));
            input.value = String(item.quantity);
            input.className = "field tabular-nums";
            input.style.width = "68px";
            input.style.minHeight = "36px";
            input.style.textAlign = "center";
            input.setAttribute("data-cart-qty-id", String(item.productId));
            input.setAttribute("aria-label", "Số lượng " + item.name);

            var incBtn = document.createElement("button");
            incBtn.type = "button";
            incBtn.className = "btn btn-outline btn-sm";
            incBtn.textContent = "+";
            incBtn.disabled = item.stock > 0 && item.quantity >= item.stock;
            incBtn.setAttribute("aria-label", "Tăng số lượng " + item.name);
            incBtn.setAttribute("data-cart-step-id", String(item.productId));
            incBtn.setAttribute("data-cart-delta", "1");

            qtyWrap.appendChild(decBtn);
            qtyWrap.appendChild(input);
            qtyWrap.appendChild(incBtn);
            tdQty.appendChild(qtyWrap);
            tr.appendChild(tdQty);

            var tdTotal = document.createElement("td");
            var strongTotal = document.createElement("strong");
            strongTotal.className = "tabular-nums";
            strongTotal.style.color = "var(--accent-gold-ink)";
            strongTotal.textContent = formatCurrency(lineTotal);
            tdTotal.appendChild(strongTotal);
            tr.appendChild(tdTotal);

            var tdAct = document.createElement("td");
            var delBtn = document.createElement("button");
            delBtn.type = "button";
            delBtn.className = "btn btn-danger btn-sm";
            delBtn.setAttribute("data-cart-remove-id", String(item.productId));
            delBtn.textContent = "Xóa";
            tdAct.appendChild(delBtn);
            tr.appendChild(tdAct);

            return tr;
        }

        var row = document.createElement("div");
        row.className = "order-row";

        var infoWrap = document.createElement("div");
        infoWrap.className = "display-flex gap16";
        infoWrap.appendChild(buildThumbElement(item));

        var meta = document.createElement("div");
        var mLink = document.createElement("a");
        mLink.href = "/wines/" + encodeURIComponent(String(item.productId));
        mLink.style.fontWeight = "700";
        mLink.textContent = item.name;
        meta.appendChild(mLink);

        var pSub = document.createElement("p");
        pSub.className = "muted mt6 mb8";
        pSub.style.fontSize = "13px";
        pSub.textContent = "Đơn giá: " + formatCurrency(item.price) + " · Tồn: " + item.stock;
        meta.appendChild(pSub);

        var controls = document.createElement("div");
        controls.className = "display-flex gap8";
        var qtyInput = document.createElement("input");
        qtyInput.type = "number";
        qtyInput.min = "1";
        qtyInput.max = String(Math.max(1, item.stock || 99));
        qtyInput.value = String(item.quantity);
        qtyInput.className = "field tabular-nums";
        qtyInput.style.width = "76px";
        qtyInput.style.minHeight = "36px";
        qtyInput.setAttribute("data-cart-qty-id", String(item.productId));
        qtyInput.setAttribute("aria-label", "Số lượng " + item.name);

        var rmBtn = document.createElement("button");
        rmBtn.type = "button";
        rmBtn.className = "btn btn-outline btn-sm";
        rmBtn.setAttribute("data-cart-remove-id", String(item.productId));
        rmBtn.textContent = "Xóa";

        controls.appendChild(qtyInput);
        controls.appendChild(rmBtn);
        meta.appendChild(controls);
        infoWrap.appendChild(meta);
        row.appendChild(infoWrap);

        var right = document.createElement("div");
        right.className = "tabular-nums";
        right.style.textAlign = "right";
        var bTotal = document.createElement("b");
        bTotal.style.color = "var(--accent-gold-ink)";
        bTotal.textContent = formatCurrency(lineTotal);
        right.appendChild(bTotal);
        row.appendChild(right);

        return row;
    }

    function renderCartPage() {
        var cartPage = document.querySelector("[data-cart-page='true']");
        if (!cartPage) {
            return;
        }

        var emptyState = document.getElementById("emptyState");
        var cartContent = document.getElementById("cartContent");
        var tbody = document.getElementById("cartTableBody");
        var subtotalEl = document.getElementById("cartSubtotal");
        var totalEl = document.getElementById("cartTotal");
        var checkoutBtn = document.getElementById("checkoutBtn");
        var discountRow = document.getElementById("cartDiscountRow");
        var discountAmountEl = document.getElementById("cartDiscountAmount");
        var voucherInput = document.getElementById("cartVoucherCode");
        var voucherFeedback = document.getElementById("cartVoucherFeedback");

        var items = getCart();
        if (!items.length) {
            if (emptyState) emptyState.classList.remove("d-none");
            if (cartContent) cartContent.classList.add("d-none");
            if (checkoutBtn) checkoutBtn.classList.add("disabled");
            if (subtotalEl) subtotalEl.textContent = "0 ₫";
            if (totalEl) totalEl.textContent = "0 ₫";
            if (discountRow) discountRow.classList.add("d-none");
            return;
        }

        if (emptyState) emptyState.classList.add("d-none");
        if (cartContent) cartContent.classList.remove("d-none");
        if (checkoutBtn) checkoutBtn.classList.remove("disabled");

        var subtotal = 0;
        if (tbody) {
            while (tbody.firstChild) {
                tbody.removeChild(tbody.firstChild);
            }
            var isTableBody = tbody.tagName === "TBODY";
            items.forEach(function (item) {
                subtotal += item.price * item.quantity;
                tbody.appendChild(buildCartRow(item, isTableBody));
            });
        } else {
            items.forEach(function (item) {
                subtotal += item.price * item.quantity;
            });
        }

        if (subtotalEl) subtotalEl.textContent = formatCurrency(subtotal);
        if (totalEl) totalEl.textContent = formatCurrency(subtotal);

        cartPage.querySelectorAll("[data-cart-qty-id]").forEach(function (input) {
            input.addEventListener("change", function () {
                updateQuantity(input.getAttribute("data-cart-qty-id"), input.value);
                renderCartPage();
            });
        });

        cartPage.querySelectorAll("[data-cart-step-id]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var pid = Number(btn.getAttribute("data-cart-step-id"));
                var delta = Number(btn.getAttribute("data-cart-delta") || 0);
                var current = getCart().find(function (x) {
                    return x.productId === pid;
                });
                if (current) {
                    updateQuantity(pid, current.quantity + delta);
                    renderCartPage();
                }
            });
        });

        cartPage.querySelectorAll("[data-cart-remove-id]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                removeItem(btn.getAttribute("data-cart-remove-id"));
                renderCartPage();
            });
        });

        function applyCartVoucher(code) {
            var trimmed = String(code || "").trim().toUpperCase();
            if (!trimmed) {
                try { window.sessionStorage.removeItem(VOUCHER_SESSION_KEY); } catch (_) {}
                if (discountRow) discountRow.classList.add("d-none");
                if (voucherFeedback) {
                    voucherFeedback.textContent = "";
                    voucherFeedback.className = "muted mt8";
                }
                if (totalEl) totalEl.textContent = formatCurrency(subtotal);
                return;
            }
            fetch("/api/vouchers/validate", {
                method: "POST",
                headers: getCsrfHeaders({ "Content-Type": "application/json", "Accept": "application/json" }),
                credentials: "same-origin",
                body: JSON.stringify({ code: trimmed, subtotal: subtotal })
            }).then(function (resp) {
                return resp.json().catch(function () { return null; });
            }).then(function (quote) {
                if (!quote) {
                    return;
                }
                if (quote.valid) {
                    try { window.sessionStorage.setItem(VOUCHER_SESSION_KEY, quote.code || trimmed); } catch (_) {}
                    var discount = toNumber(quote.discountAmount, 0);
                    var finalVal = Math.max(0, subtotal - discount);
                    if (discountRow) discountRow.classList.toggle("d-none", discount <= 0);
                    if (discountAmountEl) discountAmountEl.textContent = "-" + formatCurrency(discount);
                    if (totalEl) totalEl.textContent = formatCurrency(finalVal);
                    if (voucherFeedback) {
                        voucherFeedback.textContent = quote.message || ("Áp dụng mã " + trimmed + " thành công");
                        voucherFeedback.className = "alert-sw success mt8";
                    }
                } else {
                    try { window.sessionStorage.removeItem(VOUCHER_SESSION_KEY); } catch (_) {}
                    if (discountRow) discountRow.classList.add("d-none");
                    if (totalEl) totalEl.textContent = formatCurrency(subtotal);
                    if (voucherFeedback) {
                        voucherFeedback.textContent = quote.message || "Mã giảm giá không hợp lệ.";
                        voucherFeedback.className = "alert-sw warn mt8";
                    }
                }
            }).catch(function () {
                if (voucherFeedback) {
                    voucherFeedback.textContent = "Không thể kiểm tra mã giảm giá lúc này.";
                    voucherFeedback.className = "alert-sw warn mt8";
                }
            });
        }

        if (!cartPage.dataset.voucherBound) {
            cartPage.dataset.voucherBound = "true";
            var applyBtn = document.getElementById("btnApplyCartVoucher");
            var clearBtn = document.getElementById("btnClearCartVoucher");
            if (applyBtn && voucherInput) {
                applyBtn.addEventListener("click", function () {
                    applyCartVoucher(voucherInput.value);
                });
                voucherInput.addEventListener("keydown", function (event) {
                    if (event.key === "Enter") {
                        event.preventDefault();
                        applyCartVoucher(voucherInput.value);
                    }
                });
            }
            if (clearBtn && voucherInput) {
                clearBtn.addEventListener("click", function () {
                    voucherInput.value = "";
                    applyCartVoucher("");
                });
            }
            var savedVoucher = "";
            try { savedVoucher = window.sessionStorage.getItem(VOUCHER_SESSION_KEY) || ""; } catch (_) {}
            if (savedVoucher && voucherInput) {
                voucherInput.value = savedVoucher;
            }
        }

        if (voucherInput && voucherInput.value.trim()) {
            applyCartVoucher(voucherInput.value);
        }
    }

    function toCheckoutPayload() {
        return getCart().map(function (item) {
            return {
                productId: item.productId,
                quantity: item.quantity
            };
        });
    }

    function renderCheckoutPage() {
        var checkoutPage = document.querySelector("[data-checkout-page='true']");
        if (!checkoutPage) {
            return;
        }

        var summary = document.getElementById("checkoutSummary");
        var subtotalEl = document.getElementById("checkoutSubtotal");
        var shippingFeeEl = document.getElementById("checkoutShippingFee");
        var origShippingEl = document.getElementById("checkoutOriginalShippingFee");
        var discountRow = document.getElementById("checkoutDiscountRow");
        var discountEl = document.getElementById("checkoutDiscount");
        var totalEl = document.getElementById("checkoutTotal");
        var countEl = document.getElementById("checkoutItemCount");
        var errorAlert = document.getElementById("checkoutErrorAlert");
        var items = getCart();

        if (!items.length) {
            window.location.href = "/cart";
            return;
        }

        var localSubtotal = 0;
        var totalQty = 0;
        if (summary) {
            while (summary.firstChild) {
                summary.removeChild(summary.firstChild);
            }
        }

        items.forEach(function (item) {
            var lineTotal = item.price * item.quantity;
            localSubtotal += lineTotal;
            totalQty += item.quantity;

            if (summary) {
                var row = document.createElement("div");
                row.className = "order-line";

                var left = document.createElement("div");
                var nameStrong = document.createElement("strong");
                nameStrong.textContent = item.name;
                var subLine = document.createElement("div");
                subLine.className = "muted tabular-nums";
                subLine.style.fontSize = "12.5px";
                subLine.textContent = item.quantity + " × " + formatCurrency(item.price);
                left.appendChild(nameStrong);
                left.appendChild(subLine);

                var rightBold = document.createElement("b");
                rightBold.className = "tabular-nums";
                rightBold.textContent = formatCurrency(lineTotal);

                row.appendChild(left);
                row.appendChild(rightBold);
                summary.appendChild(row);
            }
        });

        if (subtotalEl) subtotalEl.textContent = formatCurrency(localSubtotal);
        if (totalEl) totalEl.textContent = formatCurrency(localSubtotal);
        if (countEl) countEl.textContent = String(totalQty) + " chai";

        var form = document.getElementById("checkoutForm");
        if (!form || form.dataset.boundCheckout) {
            return;
        }
        form.dataset.boundCheckout = "true";

        var savedAddressHidden = document.getElementById("savedAddressId");
        var voucherInput = document.getElementById("voucherCode");
        var voucherStatus = document.getElementById("checkoutVoucherStatus");
        var applyVoucherBtn = document.getElementById("btnApplyCheckoutVoucher");
        var clearVoucherBtn = document.getElementById("btnClearCheckoutVoucher");

        var storedVoucher = "";
        try { storedVoucher = window.sessionStorage.getItem(VOUCHER_SESSION_KEY) || ""; } catch (_) {}
        if (storedVoucher && voucherInput && !voucherInput.value) {
            voucherInput.value = storedVoucher;
        }

        function setCheckoutError(message) {
            if (errorAlert) {
                if (message) {
                    errorAlert.textContent = message;
                    errorAlert.classList.remove("d-none");
                } else {
                    errorAlert.textContent = "";
                    errorAlert.classList.add("d-none");
                }
            }
            if (message) {
                showNotice("Thanh toán", message, "bad");
            }
        }

        function buildQuoteRequest() {
            var latRaw = form.querySelector("#deliveryLat") ? form.querySelector("#deliveryLat").value : "";
            var lngRaw = form.querySelector("#deliveryLng") ? form.querySelector("#deliveryLng").value : "";
            var savedIdRaw = savedAddressHidden ? savedAddressHidden.value : "";
            var latNum = latRaw !== "" ? Number(latRaw) : null;
            var lngNum = lngRaw !== "" ? Number(lngRaw) : null;

            return {
                address: form.querySelector("#address") ? form.querySelector("#address").value.trim() : "",
                deliveryLat: Number.isFinite(latNum) ? latNum : null,
                deliveryLng: Number.isFinite(lngNum) ? lngNum : null,
                savedAddressId: savedIdRaw ? Number(savedIdRaw) : null,
                voucherCode: voucherInput && voucherInput.value ? voucherInput.value.trim() : "",
                items: toCheckoutPayload()
            };
        }

        function refreshServerQuote(notifyVoucher) {
            var quoteReq = buildQuoteRequest();
            return fetch("/cart/checkout/quote", {
                method: "POST",
                headers: getCsrfHeaders({ "Content-Type": "application/json", "Accept": "application/json" }),
                credentials: "same-origin",
                body: JSON.stringify(quoteReq)
            }).then(function (resp) {
                if (!resp.ok) {
                    return null;
                }
                return resp.json().catch(function () { return null; });
            }).then(function (q) {
                if (!q) {
                    return;
                }
                if (subtotalEl && q.subtotal != null) {
                    subtotalEl.textContent = formatCurrency(q.subtotal);
                }
                var origShip = toNumber(q.originalShippingFee, 0);
                var shipFee = toNumber(q.shippingFee, 0);
                var shipDisc = toNumber(q.shippingDiscount, 0);
                if (shippingFeeEl) {
                    shippingFeeEl.textContent = shipFee <= 0 ? "Miễn phí" : formatCurrency(shipFee);
                }
                if (origShippingEl) {
                    if (shipDisc > 0 && origShip > shipFee) {
                        origShippingEl.textContent = formatCurrency(origShip);
                        origShippingEl.classList.remove("d-none");
                    } else {
                        origShippingEl.classList.add("d-none");
                    }
                }
                var disc = toNumber(q.discountAmount, 0) + shipDisc;
                if (discountRow && discountEl) {
                    if (disc > 0) {
                        discountEl.textContent = "-" + formatCurrency(disc);
                        discountRow.classList.remove("d-none");
                    } else {
                        discountRow.classList.add("d-none");
                    }
                }
                if (totalEl && q.finalTotal != null) {
                    totalEl.textContent = formatCurrency(q.finalTotal);
                }

                var hasCode = Boolean(quoteReq.voucherCode);
                if (voucherStatus) {
                    if (!hasCode) {
                        voucherStatus.textContent = "";
                        voucherStatus.className = "muted mt8";
                    } else if (q.voucherValid) {
                        voucherStatus.textContent = q.voucherMessage || "Áp dụng mã giảm giá thành công.";
                        voucherStatus.className = "alert-sw success mt8";
                        try { window.sessionStorage.setItem(VOUCHER_SESSION_KEY, quoteReq.voucherCode); } catch (_) {}
                    } else {
                        voucherStatus.textContent = q.voucherMessage || "Mã giảm giá không hợp lệ.";
                        voucherStatus.className = "alert-sw warn mt8";
                    }
                }
                if (notifyVoucher && hasCode) {
                    showNotice("Mã ưu đãi", q.voucherMessage || (q.voucherValid ? "Đã áp dụng mã." : "Mã không hợp lệ."), q.voucherValid ? "ok" : "warn");
                }
            }).catch(function () {});
        }

        // Saved address book selection wiring
        var addressChoices = checkoutPage.querySelectorAll("[data-saved-address-choice]");
        addressChoices.forEach(function (radio) {
            radio.addEventListener("change", function () {
                if (!radio.checked) {
                    return;
                }
                var id = radio.value || "";
                if (savedAddressHidden) {
                    savedAddressHidden.value = id;
                }
                if (id) {
                    var fullNameInput = document.getElementById("fullName");
                    var phoneInput = document.getElementById("phone");
                    var addressInput = document.getElementById("address");
                    var latInput = document.getElementById("deliveryLat");
                    var lngInput = document.getElementById("deliveryLng");

                    var recName = radio.getAttribute("data-recipient-name");
                    var recPhone = radio.getAttribute("data-phone");
                    var fullAddr = radio.getAttribute("data-full-address");
                    var lat = radio.getAttribute("data-lat");
                    var lng = radio.getAttribute("data-lng");

                    if (fullNameInput && recName) fullNameInput.value = recName;
                    if (phoneInput && recPhone) phoneInput.value = recPhone;
                    if (addressInput && fullAddr) addressInput.value = fullAddr;
                    if (latInput) latInput.value = lat || "";
                    if (lngInput) lngInput.value = lng || "";

                    if (lat && lng) {
                        document.dispatchEvent(new CustomEvent("strongwine:set-map-pin", {
                            detail: { lat: Number(lat), lng: Number(lng), address: fullAddr || "" }
                        }));
                    }
                }
                refreshServerQuote(false);
            });
        });

        var addressInput = document.getElementById("address");
        if (addressInput) {
            addressInput.addEventListener("input", function () {
                if (savedAddressHidden) {
                    savedAddressHidden.value = "";
                }
                var manualChoice = checkoutPage.querySelector("[data-saved-address-choice][value='']");
                if (manualChoice) {
                    manualChoice.checked = true;
                }
            });
        }

        if (applyVoucherBtn && voucherInput) {
            applyVoucherBtn.addEventListener("click", function () {
                refreshServerQuote(true);
            });
            voucherInput.addEventListener("keydown", function (event) {
                if (event.key === "Enter") {
                    event.preventDefault();
                    refreshServerQuote(true);
                }
            });
        }

        if (clearVoucherBtn && voucherInput) {
            clearVoucherBtn.addEventListener("click", function () {
                voucherInput.value = "";
                try { window.sessionStorage.removeItem(VOUCHER_SESSION_KEY); } catch (_) {}
                refreshServerQuote(false);
            });
        }

        document.addEventListener("strongwine:location-updated", function () {
            refreshServerQuote(false);
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            setCheckoutError("");

            var checkoutTokenInput = form.querySelector("input[name='checkoutToken']");
            var paymentMethodEl = form.querySelector("input[name='paymentMethod']:checked, select[name='paymentMethod'], #paymentMethod");
            var savedIdVal = savedAddressHidden && savedAddressHidden.value ? Number(savedAddressHidden.value) : null;

            var requestBody = {
                fullName: form.querySelector("#fullName") ? form.querySelector("#fullName").value : "",
                phone: form.querySelector("#phone") ? form.querySelector("#phone").value : "",
                email: form.querySelector("#email") ? form.querySelector("#email").value : "",
                address: form.querySelector("#address") ? form.querySelector("#address").value : "",
                note: form.querySelector("#note") ? form.querySelector("#note").value : "",
                paymentMethod: paymentMethodEl ? paymentMethodEl.value : "COD",
                checkoutToken: checkoutTokenInput ? checkoutTokenInput.value : "",
                savedAddressId: Number.isFinite(savedIdVal) ? savedIdVal : null,
                voucherCode: voucherInput && voucherInput.value ? voucherInput.value.trim() : "",
                deliveryLat: null,
                deliveryLng: null,
                items: toCheckoutPayload()
            };

            var latRaw = form.querySelector("#deliveryLat") ? form.querySelector("#deliveryLat").value.trim() : "";
            var lngRaw = form.querySelector("#deliveryLng") ? form.querySelector("#deliveryLng").value.trim() : "";
            var hasAddressOrSaved = Boolean(requestBody.address && requestBody.address.trim()) || requestBody.savedAddressId != null;
            if (latRaw && lngRaw) {
                requestBody.deliveryLat = Number(latRaw);
                requestBody.deliveryLng = Number(lngRaw);
                if (!Number.isFinite(requestBody.deliveryLat) || !Number.isFinite(requestBody.deliveryLng)) {
                    setCheckoutError("Tọa độ giao hàng không hợp lệ. Vui lòng chọn lại trên bản đồ.");
                    return;
                }
            } else if (!hasAddressOrSaved) {
                setCheckoutError("Vui lòng nhập địa chỉ giao hàng hoặc chọn vị trí trên bản đồ.");
                var mapSearch = document.getElementById("mapSearchInput");
                if (mapSearch) mapSearch.focus();
                return;
            }

            var submitBtn = form.querySelector("button[type='submit']");
            if (submitBtn) {
                submitBtn.disabled = true;
            }

            fetch(form.getAttribute("action") || "/cart/checkout/process", {
                method: "POST",
                headers: getCsrfHeaders({ "Content-Type": "application/json", "Accept": "application/json" }),
                credentials: "same-origin",
                body: JSON.stringify(requestBody)
            }).then(function (response) {
                if (!response.ok) {
                    return response.text().then(function (errorText) {
                        var message = "Thanh toán thất bại.";
                        if (errorText) {
                            try {
                                var body = JSON.parse(errorText);
                                message = body.message || body.error || errorText;
                            } catch (_) {
                                message = errorText;
                            }
                        }
                        throw new Error(message);
                    });
                }
                return response.json();
            }).then(function (result) {
                if (result && result.redirectUrl) {
                    try { window.sessionStorage.removeItem(VOUCHER_SESSION_KEY); } catch (_) {}
                    if (String(requestBody.paymentMethod).toUpperCase() === "COD") {
                        clearCart();
                    }
                    window.location.href = result.redirectUrl;
                    return;
                }
                throw new Error("Không thể tạo phiên thanh toán.");
            }).catch(function (error) {
                setCheckoutError(error.message || "Thanh toán thất bại. Vui lòng thử lại.");
                if (submitBtn) {
                    submitBtn.disabled = false;
                }
            });
        });

        refreshServerQuote(false);
    }

    function clearCartOnOrderConfirmation() {
        var page = document.querySelector("[data-order-confirmation='true']");
        if (page) {
            clearCart();
        }
    }

    window.StrongWineCart = {
        getCart: getCart,
        addToCart: addToCart,
        removeItem: removeItem,
        updateQuantity: updateQuantity,
        clearCart: clearCart,
        updateBadges: updateBadges,
        formatCurrency: formatCurrency,
        renderCartPage: renderCartPage,
        toCheckoutPayload: toCheckoutPayload
    };

    function boot() {
        hydrateReorderItemsIfPresent();
        updateBadges();
        bindAddButtons();
        renderCartPage();
        renderCheckoutPage();
        clearCartOnOrderConfirmation();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", boot, { once: true });
    } else {
        boot();
    }
})(window, document);
