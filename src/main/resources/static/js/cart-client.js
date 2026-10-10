(function () {
    const STORAGE_KEY = "cart";

    function toNumber(value, fallback) {
        const n = Number(value);
        return Number.isFinite(n) ? n : fallback;
    }

    function normalizeItem(raw) {
        if (!raw || raw.productId == null) {
            return null;
        }
        const productId = toNumber(raw.productId, 0);
        const quantity = Math.max(0, Math.floor(toNumber(raw.quantity, 0)));
        const price = Math.max(0, toNumber(raw.price, 0));
        const stock = Math.max(0, Math.floor(toNumber(raw.stock, 99)));

        if (productId <= 0 || quantity <= 0) {
            return null;
        }
        return {
            productId,
            name: String(raw.name || "Sản phẩm"),
            price,
            quantity,
            image: raw.image ? String(raw.image) : "",
            stock
        };
    }

    function getCart() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            if (!raw) return [];
            const parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) return [];
            return parsed.map(normalizeItem).filter(Boolean);
        } catch (e) {
            return [];
        }
    }

    function notifyCartUpdated() {
        updateBadges();
        window.dispatchEvent(new CustomEvent("strongwine:cart-updated"));
    }

    function saveCart(items) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
        notifyCartUpdated();
    }

    function clearCart() {
        localStorage.removeItem(STORAGE_KEY);
        notifyCartUpdated();
    }

    function updateBadges() {
        const count = getCart().reduce((sum, item) => sum + item.quantity, 0);
        document.querySelectorAll(".js-cart-count").forEach((el) => {
            el.textContent = String(count);
        });
    }

    function addToCart(payload) {
        const productId = toNumber(payload.productId, 0);
        const quantity = Math.max(1, Math.floor(toNumber(payload.quantity, 1)));
        const stock = Math.max(0, Math.floor(toNumber(payload.stock != null ? payload.stock : 99, 99)));

        if (productId <= 0) {
            alert("Sản phẩm không hợp lệ.");
            return false;
        }
        if (stock <= 0) {
            alert("Sản phẩm hiện đã hết hàng.");
            return false;
        }

        const items = getCart();
        const existing = items.find((x) => x.productId === productId);

        if (existing) {
            existing.quantity = Math.min(existing.quantity + quantity, stock);
            existing.name = payload.name || existing.name;
            existing.price = Math.max(0, toNumber(payload.price, existing.price));
            existing.image = payload.image || existing.image;
            existing.stock = stock;
        } else {
            items.push({
                productId,
                name: String(payload.name || "Sản phẩm"),
                price: Math.max(0, toNumber(payload.price, 0)),
                quantity: Math.min(quantity, stock),
                image: payload.image ? String(payload.image) : "",
                stock
            });
        }

        saveCart(items);
        return true;
    }

    function removeItem(productId) {
        const targetId = toNumber(productId, 0);
        const items = getCart().filter((x) => x.productId !== targetId);
        saveCart(items);
    }

    function updateQuantity(productId, quantity) {
        const targetId = toNumber(productId, 0);
        const qty = Math.floor(toNumber(quantity, 0));
        const items = getCart();
        const item = items.find((x) => x.productId === targetId);
        if (!item) return;

        if (qty <= 0) {
            removeItem(targetId);
            return;
        }
        item.quantity = Math.min(qty, Math.max(0, item.stock));
        if (item.quantity <= 0) {
            removeItem(targetId);
            return;
        }
        saveCart(items);
    }

    function formatCurrency(value) {
        return new Intl.NumberFormat("vi-VN").format(toNumber(value, 0)) + " ₫";
    }

    function escapeHtml(str) {
        if (!str) return "";
        const d = document.createElement("div");
        d.textContent = String(str);
        return d.innerHTML;
    }

    function bindAddButtons() {
        document.addEventListener("click", (e) => {
            const btn = e.target.closest("[data-cart-add='true'], .js-add-to-cart");
            if (!btn) return;
            e.preventDefault();

            const quantitySelector = btn.getAttribute("data-cart-quantity-selector");
            let quantity = toNumber(btn.getAttribute("data-cart-quantity") || btn.getAttribute("data-quantity") || 1, 1);
            if (quantitySelector) {
                const quantityInput = document.querySelector(quantitySelector);
                if (quantityInput) {
                    quantity = toNumber(quantityInput.value || quantityInput.textContent, 1);
                }
            }

            const ok = addToCart({
                productId: btn.getAttribute("data-cart-product-id") || btn.getAttribute("data-wine-id"),
                name: btn.getAttribute("data-cart-name") || btn.getAttribute("data-wine-name"),
                price: btn.getAttribute("data-cart-price") || btn.getAttribute("data-wine-price"),
                image: btn.getAttribute("data-cart-image") || btn.getAttribute("data-wine-img"),
                stock: btn.getAttribute("data-cart-stock") || btn.getAttribute("data-wine-stock") || 99,
                quantity
            });

            if (!ok) return;

            const redirect = btn.getAttribute("data-cart-redirect");
            if (redirect) {
                window.location.href = redirect;
                return;
            }

            if (window.cartDrawer && typeof window.cartDrawer.openDrawer === "function") {
                window.cartDrawer.openDrawer();
            }
        });
    }

    function renderCartPage() {
        const cartPage = document.querySelector("[data-cart-page='true']");
        if (!cartPage) return;

        const emptyState = document.getElementById("emptyState");
        const cartContent = document.getElementById("cartContent");
        const tbody = document.getElementById("cartTableBody");
        const subtotalEl = document.getElementById("cartSubtotal");
        const totalEl = document.getElementById("cartTotal");
        const checkoutBtn = document.getElementById("checkoutBtn");

        const items = getCart();
        if (!items.length) {
            if (emptyState) emptyState.classList.remove("d-none");
            if (cartContent) cartContent.classList.add("d-none");
            if (checkoutBtn) checkoutBtn.classList.add("disabled");
            if (subtotalEl) subtotalEl.textContent = "0 ₫";
            if (totalEl) totalEl.textContent = "0 ₫";
            return;
        }

        if (emptyState) emptyState.classList.add("d-none");
        if (cartContent) cartContent.classList.remove("d-none");
        if (checkoutBtn) checkoutBtn.classList.remove("disabled");

        let subtotal = 0;
        if (tbody) {
            tbody.innerHTML = "";
            items.forEach((item) => {
                const lineTotal = item.price * item.quantity;
                subtotal += lineTotal;

                const row = document.createElement(tbody.tagName === "TBODY" ? "tr" : "div");
                if (tbody.tagName === "TBODY") {
                    row.innerHTML = `
                        <td>
                            <div class="cart-image">
                                ${item.image ? `<img src="${escapeHtml(item.image)}" alt="${escapeHtml(item.name)}">` : `<span class="serif" style="color:var(--sw-bordeaux)">S</span>`}
                            </div>
                        </td>
                        <td>
                            <a href="/wines/${item.productId}" style="font-weight:700">${escapeHtml(item.name)}</a>
                            <div class="muted small">Tồn kho khả dụng: ${item.stock} chai</div>
                        </td>
                        <td>${formatCurrency(item.price)}</td>
                        <td>
                            <input type="number" min="1" max="${Math.max(1, item.stock)}" value="${item.quantity}" class="field" style="width:84px;min-height:36px;padding:6px 10px" data-cart-qty-id="${item.productId}" aria-label="Số lượng ${escapeHtml(item.name)}">
                        </td>
                        <td><strong style="color:var(--sw-bordeaux)">${formatCurrency(lineTotal)}</strong></td>
                        <td>
                            <button type="button" class="btn btn-danger btn-sm" data-cart-remove-id="${item.productId}">Xóa</button>
                        </td>
                    `;
                } else {
                    row.className = "order-row";
                    row.innerHTML = `
                        <div class="display-flex gap16">
                            <div class="cart-image">
                                ${item.image ? `<img src="${escapeHtml(item.image)}" alt="${escapeHtml(item.name)}">` : `<span class="serif" style="color:var(--sw-bordeaux)">S</span>`}
                            </div>
                            <div>
                                <a href="/wines/${item.productId}" style="font-weight:700;font-size:15px">${escapeHtml(item.name)}</a>
                                <p class="muted small mt8">Đơn giá: ${formatCurrency(item.price)} · Tồn: ${item.stock}</p>
                                <div class="display-flex gap10 mt8">
                                    <label class="small muted" for="qty-${item.productId}">SL:</label>
                                    <input id="qty-${item.productId}" type="number" min="1" max="${Math.max(1, item.stock)}" value="${item.quantity}" class="field" style="width:78px;min-height:34px;padding:4px 8px" data-cart-qty-id="${item.productId}">
                                    <button type="button" class="btn btn-outline btn-sm" data-cart-remove-id="${item.productId}">Xóa sản phẩm</button>
                                </div>
                            </div>
                        </div>
                        <div style="text-align:right">
                            <b style="color:var(--sw-bordeaux);font-size:15px">${formatCurrency(lineTotal)}</b>
                            <div class="muted small mt8">${item.quantity} chai</div>
                        </div>
                    `;
                }
                tbody.appendChild(row);
            });
        }

        if (subtotalEl) subtotalEl.textContent = formatCurrency(subtotal);
        if (totalEl) totalEl.textContent = formatCurrency(subtotal);

        document.querySelectorAll("[data-cart-qty-id]").forEach((input) => {
            input.addEventListener("change", () => {
                updateQuantity(input.getAttribute("data-cart-qty-id"), input.value);
                renderCartPage();
            });
        });

        document.querySelectorAll("[data-cart-remove-id]").forEach((btn) => {
            btn.addEventListener("click", () => {
                removeItem(btn.getAttribute("data-cart-remove-id"));
                renderCartPage();
            });
        });
    }

    function toCheckoutPayload() {
        return getCart().map((item) => ({
            productId: item.productId,
            quantity: item.quantity
        }));
    }

    function renderCheckoutPage() {
        const checkoutPage = document.querySelector("[data-checkout-page='true']");
        if (!checkoutPage) return;

        const summary = document.getElementById("checkoutSummary");
        const totalEl = document.getElementById("checkoutTotal");
        const countEl = document.getElementById("checkoutItemCount");
        const items = getCart();

        if (!items.length) {
            window.location.href = "/cart";
            return;
        }

        let total = 0;
        let totalQty = 0;
        if (summary) summary.innerHTML = "";

        items.forEach((item) => {
            const lineTotal = item.price * item.quantity;
            total += lineTotal;
            totalQty += item.quantity;

            if (summary) {
                const row = document.createElement("div");
                row.className = "order-line";
                row.innerHTML = `
                    <div>
                        <strong>${escapeHtml(item.name)}</strong>
                        <div class="muted small">${item.quantity} &times; ${formatCurrency(item.price)}</div>
                    </div>
                    <b>${formatCurrency(lineTotal)}</b>
                `;
                summary.appendChild(row);
            }
        });

        if (totalEl) totalEl.textContent = formatCurrency(total);
        if (countEl) countEl.textContent = String(totalQty);

        const form = document.getElementById("checkoutForm");
        if (form && !form.dataset.boundCheckout) {
            form.dataset.boundCheckout = "true";
            form.addEventListener("submit", async (event) => {
                event.preventDefault();

                const checkoutTokenInput = form.querySelector("input[name='checkoutToken']");
                const paymentMethodEl = form.querySelector("input[name='paymentMethod']:checked, select[name='paymentMethod'], #paymentMethod");
                const requestBody = {
                    fullName: form.querySelector("#fullName")?.value || "",
                    phone: form.querySelector("#phone")?.value || "",
                    email: form.querySelector("#email")?.value || "",
                    address: form.querySelector("#address")?.value || "",
                    note: form.querySelector("#note")?.value || "",
                    paymentMethod: paymentMethodEl ? paymentMethodEl.value : "STRIPE",
                    checkoutToken: checkoutTokenInput ? checkoutTokenInput.value : "",
                    deliveryLat: null,
                    deliveryLng: null,
                    items: toCheckoutPayload()
                };

                const latRaw = form.querySelector("#deliveryLat")?.value;
                const lngRaw = form.querySelector("#deliveryLng")?.value;
                if (!latRaw || !lngRaw) {
                    alert("Vui lòng chọn vị trí giao hàng trên bản đồ.");
                    return;
                }
                requestBody.deliveryLat = Number(latRaw);
                requestBody.deliveryLng = Number(lngRaw);
                if (!Number.isFinite(requestBody.deliveryLat) || !Number.isFinite(requestBody.deliveryLng)) {
                    alert("Tọa độ giao hàng không hợp lệ. Vui lòng chọn lại trên bản đồ.");
                    return;
                }

                const submitBtn = form.querySelector("button[type='submit']");
                if (submitBtn) submitBtn.disabled = true;

                try {
                    const response = await fetch(form.getAttribute("action") || "/cart/checkout/process", {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json",
                            "Accept": "application/json"
                        },
                        body: JSON.stringify(requestBody)
                    });

                    if (!response.ok) {
                        let message = "Thanh toán thất bại.";
                        try {
                            const errorText = await response.text();
                            if (errorText) {
                                try {
                                    const errorBody = JSON.parse(errorText);
                                    message = errorBody.message || errorBody.error || errorText;
                                } catch (_) {
                                    message = errorText;
                                }
                            }
                        } catch (_) {}
                        alert(message);
                        if (submitBtn) submitBtn.disabled = false;
                        return;
                    }

                    const result = await response.json();
                    if (result && result.redirectUrl) {
                        window.location.href = result.redirectUrl;
                        return;
                    }

                    alert("Không thể tạo phiên thanh toán.");
                    if (submitBtn) submitBtn.disabled = false;
                } catch (_) {
                    alert("Thanh toán thất bại. Vui lòng thử lại.");
                    if (submitBtn) submitBtn.disabled = false;
                }
            });
        }
    }

    function clearCartOnOrderConfirmation() {
        const page = document.querySelector("[data-order-confirmation='true']");
        if (page) {
            clearCart();
        }
    }

    window.StrongWineCart = {
        getCart,
        addToCart,
        removeItem,
        updateQuantity,
        clearCart,
        updateBadges,
        formatCurrency,
        renderCartPage
    };

    document.addEventListener("DOMContentLoaded", () => {
        updateBadges();
        bindAddButtons();
        renderCartPage();
        renderCheckoutPage();
        clearCartOnOrderConfirmation();
    });
})();
