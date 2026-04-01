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
        const stock = Math.max(0, Math.floor(toNumber(raw.stock, 0)));

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
            if (!raw) {
                return [];
            }
            const parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) {
                return [];
            }
            return parsed.map(normalizeItem).filter(Boolean);
        } catch (e) {
            return [];
        }
    }

    function saveCart(items) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
        updateBadges();
    }

    function clearCart() {
        localStorage.removeItem(STORAGE_KEY);
        updateBadges();
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
        const stock = Math.max(0, Math.floor(toNumber(payload.stock, 0)));

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
        if (!item) {
            return;
        }

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
        return new Intl.NumberFormat("vi-VN").format(toNumber(value, 0)) + " đ";
    }

    function bindAddButtons() {
        document.querySelectorAll("[data-cart-add='true']").forEach((btn) => {
            btn.addEventListener("click", () => {
                const quantitySelector = btn.getAttribute("data-cart-quantity-selector");
                let quantity = toNumber(btn.getAttribute("data-cart-quantity") || 1, 1);
                if (quantitySelector) {
                    const quantityInput = document.querySelector(quantitySelector);
                    if (quantityInput) {
                        quantity = toNumber(quantityInput.value, 1);
                    }
                }

                const ok = addToCart({
                    productId: btn.getAttribute("data-cart-product-id"),
                    name: btn.getAttribute("data-cart-name"),
                    price: btn.getAttribute("data-cart-price"),
                    image: btn.getAttribute("data-cart-image"),
                    stock: btn.getAttribute("data-cart-stock"),
                    quantity
                });

                if (!ok) {
                    return;
                }

                const redirect = btn.getAttribute("data-cart-redirect");
                if (redirect) {
                    window.location.href = redirect;
                }
            });
        });
    }

    function renderCartPage() {
        const cartPage = document.querySelector("[data-cart-page='true']");
        if (!cartPage) {
            return;
        }

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
            return;
        }

        if (emptyState) emptyState.classList.add("d-none");
        if (cartContent) cartContent.classList.remove("d-none");
        if (checkoutBtn) checkoutBtn.classList.remove("disabled");

        let subtotal = 0;
        tbody.innerHTML = "";

        items.forEach((item) => {
            const lineTotal = item.price * item.quantity;
            subtotal += lineTotal;

            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>
                    ${item.image ? `<img src="${item.image}" class="img-fluid rounded" style="width: 80px; height: 80px; object-fit: cover;" alt="Hình ảnh">` : `<div class="bg-secondary rounded d-flex align-items-center justify-content-center" style="width: 80px; height: 80px;"><i class="fa-regular fa-image text-white"></i></div>`}
                </td>
                <td><a href="/wines/${item.productId}" class="text-decoration-none fw-semibold">${item.name}</a></td>
                <td>${formatCurrency(item.price)}</td>
                <td>
                    <input type="number" min="1" max="${Math.max(1, item.stock)}" value="${item.quantity}" class="form-control" style="width: 90px;" data-cart-qty-id="${item.productId}">
                    <small class="text-muted d-block">Tồn: ${item.stock}</small>
                </td>
                <td class="fw-bold text-primary">${formatCurrency(lineTotal)}</td>
                <td>
                    <button type="button" class="btn btn-sm btn-danger" data-cart-remove-id="${item.productId}">
                        <i class="fa-solid fa-trash"></i> Xóa
                    </button>
                </td>
            `;
            tbody.appendChild(tr);
        });

        subtotalEl.textContent = formatCurrency(subtotal);
        totalEl.textContent = formatCurrency(subtotal);

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
        if (!checkoutPage) {
            return;
        }

        const summary = document.getElementById("checkoutSummary");
        const totalEl = document.getElementById("checkoutTotal");
        const items = getCart();

        if (!items.length) {
            window.location.href = "/cart";
            return;
        }

        let total = 0;
        summary.innerHTML = "";

        items.forEach((item) => {
            const lineTotal = item.price * item.quantity;
            total += lineTotal;

            const row = document.createElement("div");
            row.className = "d-flex justify-content-between mb-2 pb-2 border-bottom";
            row.innerHTML = `
                <div>
                    <div class="fw-semibold">${item.name}</div>
                    <small class="text-muted">Số lượng: ${item.quantity}</small>
                </div>
                <div class="text-end">
                    <div class="fw-bold">${formatCurrency(lineTotal)}</div>
                    <small class="text-muted">${formatCurrency(item.price)}/san pham</small>
                </div>
            `;
            summary.appendChild(row);
        });

        totalEl.textContent = formatCurrency(total);

        const form = document.getElementById("checkoutForm");
        if (form) {
            form.addEventListener("submit", async (event) => {
                event.preventDefault();

                const checkoutTokenInput = form.querySelector("input[name='checkoutToken']");
                const requestBody = {
                    fullName: form.querySelector("#fullName")?.value || "",
                    phone: form.querySelector("#phone")?.value || "",
                    address: form.querySelector("#address")?.value || "",
                    note: form.querySelector("#note")?.value || "",
                    paymentMethod: form.querySelector("#paymentMethod")?.value || "",
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
                        } catch (_) {
                            // ignore parsing error
                        }
                        alert(message);
                        return;
                    }

                    const result = await response.json();
                    if (result && result.redirectUrl) {
                        window.location.href = result.redirectUrl;
                        return;
                    }

                    alert("Không thể tạo phiên thanh toán.");
                } catch (_) {
                    alert("Thanh toán thất bại. Vui lòng thử lại.");
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
        clearCart,
        updateBadges
    };

    document.addEventListener("DOMContentLoaded", () => {
        updateBadges();
        bindAddButtons();
        renderCartPage();
        renderCheckoutPage();
        clearCartOnOrderConfirmation();
    });
})();
