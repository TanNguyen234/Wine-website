(function (window, document) {
    "use strict";

    function getCsrfHeaders() {
        var headerMeta = document.querySelector('meta[name="_csrf_header"]');
        var tokenMeta = document.querySelector('meta[name="_csrf"]');
        var headers = {};
        if (tokenMeta && tokenMeta.content) {
            headers[(headerMeta && headerMeta.content) || "X-CSRF-TOKEN"] = tokenMeta.content;
        }
        return headers;
    }

    function formatCurrency(value) {
        var n = Number(value);
        return new Intl.NumberFormat("vi-VN").format(Number.isFinite(n) ? n : 0) + " ₫";
    }

    var state = {
        ids: new Set(),
        items: [],
        loaded: false
    };

    function updateBadge(count) {
        var safe = Math.max(0, Number(count) || 0);
        document.querySelectorAll(".js-wishlist-count").forEach(function (el) {
            el.textContent = String(safe);
        });
    }

    function syncButton(btn) {
        if (!btn) {
            return;
        }
        var wineId = Number(btn.getAttribute("data-wine-id") || btn.getAttribute("data-wishlist-toggle"));
        var saved = Number.isFinite(wineId) && state.ids.has(wineId);
        btn.setAttribute("aria-pressed", saved ? "true" : "false");
        btn.classList.toggle("is-active", saved);
        var name = btn.getAttribute("data-wine-name") || "chai vang";
        btn.setAttribute("aria-label", saved ? "Bỏ " + name + " khỏi yêu thích" : "Thêm " + name + " vào yêu thích");
    }

    function syncAllButtons() {
        document.querySelectorAll("[data-wishlist-toggle]").forEach(syncButton);
    }

    function createBottleSvgFallback() {
        var svgNS = "http://www.w3.org/2000/svg";
        var svg = document.createElementNS(svgNS, "svg");
        svg.setAttribute("viewBox", "0 0 120 280");
        svg.setAttribute("fill", "none");
        svg.setAttribute("aria-hidden", "true");
        svg.classList.add("wine-svg");
        var path = document.createElementNS(svgNS, "path");
        path.setAttribute("d", "M51 18h18v12l-1 40c0 12 22 24 22 50v130c0 10-8 16-30 16s-30-6-30-16V120c0-26 22-38 22-50l-1-40V18Z");
        path.setAttribute("fill", "#2a1216");
        path.setAttribute("stroke", "#c9a84c");
        path.setAttribute("stroke-opacity", "0.5");
        svg.appendChild(path);
        return svg;
    }

    function renderProfileWishlist() {
        var container = document.getElementById("wishlistGrid") || document.querySelector("[data-wishlist-container]");
        if (!container) {
            return;
        }
        while (container.firstChild) {
            container.removeChild(container.firstChild);
        }

        if (!state.items.length) {
            var empty = document.createElement("div");
            empty.className = "panel-soft p12 text-center";
            var msg = document.createElement("p");
            msg.className = "muted mb12";
            msg.textContent = "Bạn chưa lưu chai vang nào vào danh sách yêu thích.";
            var cta = document.createElement("a");
            cta.href = "/wines";
            cta.className = "btn btn-outline btn-sm";
            cta.textContent = "Khám phá hầm rượu";
            empty.appendChild(msg);
            empty.appendChild(cta);
            container.appendChild(empty);
            return;
        }

        var grid = document.createElement("div");
        grid.className = "wine-grid";

        state.items.forEach(function (wine) {
            var card = document.createElement("article");
            card.className = "wine-card";

            var stage = document.createElement("div");
            stage.className = "wine-stage";

            if (wine.typeLabel) {
                var typePill = document.createElement("span");
                typePill.className = "wine-pill";
                typePill.textContent = wine.typeLabel;
                stage.appendChild(typePill);
            }

            var stockPill = document.createElement("span");
            stockPill.className = "stock-pill " + (wine.inStock ? (wine.availableStock <= 5 ? "low" : "") : "out");
            stockPill.textContent = wine.inStock ? ("Còn " + wine.availableStock) : "Hết hàng";
            stage.appendChild(stockPill);

            var link = document.createElement("a");
            link.href = "/wines/" + encodeURIComponent(String(wine.id));
            if (wine.imageUrl) {
                var img = document.createElement("img");
                img.src = wine.imageUrl;
                img.alt = wine.name || "Rượu vang StrongWine";
                img.loading = "lazy";
                img.addEventListener("error", function () {
                    img.remove();
                    link.appendChild(createBottleSvgFallback());
                });
                link.appendChild(img);
            } else {
                link.appendChild(createBottleSvgFallback());
            }
            stage.appendChild(link);
            card.appendChild(stage);

            var body = document.createElement("div");
            body.className = "wine-body";

            var meta = document.createElement("div");
            meta.className = "wine-meta";
            var parts = [];
            if (wine.country) parts.push(wine.country);
            if (wine.year) parts.push(String(wine.year));
            meta.textContent = parts.join(" · ") || "StrongWine Cellar";
            body.appendChild(meta);

            var nameEl = document.createElement("h3");
            nameEl.className = "wine-name";
            var titleLink = document.createElement("a");
            titleLink.href = "/wines/" + encodeURIComponent(String(wine.id));
            titleLink.textContent = wine.name || "Rượu vang";
            nameEl.appendChild(titleLink);
            body.appendChild(nameEl);

            var foot = document.createElement("div");
            foot.className = "wine-foot";

            var priceEl = document.createElement("div");
            priceEl.className = "price tabular-nums";
            priceEl.textContent = formatCurrency(wine.price);
            foot.appendChild(priceEl);

            var btns = document.createElement("div");
            btns.className = "display-flex gap8";

            var removeBtn = document.createElement("button");
            removeBtn.type = "button";
            removeBtn.className = "btn btn-outline btn-sm";
            removeBtn.setAttribute("data-wishlist-toggle", String(wine.id));
            removeBtn.setAttribute("data-wine-id", String(wine.id));
            removeBtn.setAttribute("data-wine-name", wine.name || "");
            removeBtn.setAttribute("aria-pressed", "true");
            removeBtn.textContent = "Bỏ thích";
            btns.appendChild(removeBtn);

            if (wine.inStock) {
                var addBtn = document.createElement("button");
                addBtn.type = "button";
                addBtn.className = "btn btn-sm js-add-to-cart";
                addBtn.setAttribute("data-cart-add", "true");
                addBtn.setAttribute("data-cart-product-id", String(wine.id));
                addBtn.setAttribute("data-cart-name", wine.name || "");
                addBtn.setAttribute("data-cart-price", String(wine.price || 0));
                addBtn.setAttribute("data-cart-image", wine.imageUrl || "");
                addBtn.setAttribute("data-cart-stock", String(wine.availableStock || 0));
                addBtn.textContent = "+ Giỏ";
                btns.appendChild(addBtn);
            }

            foot.appendChild(btns);
            body.appendChild(foot);
            card.appendChild(body);
            grid.appendChild(card);
        });

        container.appendChild(grid);
        syncAllButtons();
    }

    function refresh() {
        return fetch("/api/wishlist", {
            headers: { "Accept": "application/json" },
            credentials: "same-origin"
        }).then(function (resp) {
            if (resp.status === 401 || resp.status === 403 || resp.redirected) {
                return null;
            }
            if (!resp.ok) {
                return null;
            }
            return resp.json().catch(function () { return null; });
        }).then(function (data) {
            if (!data || !Array.isArray(data.wineIds)) {
                state.loaded = true;
                syncAllButtons();
                renderProfileWishlist();
                return data;
            }
            state.ids = new Set(data.wineIds.map(Number).filter(Number.isFinite));
            state.items = Array.isArray(data.items) ? data.items : [];
            state.loaded = true;
            updateBadge(data.count != null ? data.count : state.ids.size);
            syncAllButtons();
            renderProfileWishlist();
            return data;
        }).catch(function () {
            state.loaded = true;
        });
    }

    function toggle(wineId, triggerBtn) {
        var idNum = Number(wineId);
        if (!Number.isFinite(idNum) || idNum <= 0) {
            return Promise.resolve(null);
        }
        if (triggerBtn) {
            triggerBtn.disabled = true;
        }
        return fetch("/api/wishlist/" + encodeURIComponent(String(idNum)) + "/toggle", {
            method: "POST",
            headers: Object.assign({ "Accept": "application/json" }, getCsrfHeaders()),
            credentials: "same-origin"
        }).then(function (resp) {
            if (resp.status === 401 || resp.status === 403 || (resp.redirected && String(resp.url).indexOf("/login") !== -1)) {
                if (window.SwToast && typeof window.SwToast.show === "function") {
                    window.SwToast.show({
                        title: "Yêu thích",
                        message: "Vui lòng đăng nhập để lưu rượu vang yêu thích.",
                        tone: "warn"
                    });
                }
                return null;
            }
            if (!resp.ok) {
                throw new Error("Không thể cập nhật danh sách yêu thích.");
            }
            return resp.json();
        }).then(function (data) {
            if (!data) {
                return null;
            }
            if (data.inWishlist) {
                state.ids.add(idNum);
            } else {
                state.ids.delete(idNum);
                state.items = state.items.filter(function (item) {
                    return Number(item.id) !== idNum;
                });
            }
            updateBadge(data.count != null ? data.count : state.ids.size);
            syncAllButtons();
            if (document.getElementById("wishlistGrid") || document.querySelector("[data-wishlist-container]")) {
                refresh();
            }
            if (window.SwToast && typeof window.SwToast.show === "function") {
                window.SwToast.show({
                    title: "Danh sách yêu thích",
                    message: data.inWishlist ? "Đã thêm vào danh sách yêu thích." : "Đã bỏ khỏi danh sách yêu thích.",
                    tone: "ok"
                });
            }
            return data;
        }).catch(function (err) {
            if (window.SwToast && typeof window.SwToast.show === "function") {
                window.SwToast.show({
                    title: "Yêu thích",
                    message: err.message || "Không thể kết nối máy chủ.",
                    tone: "warn"
                });
            }
            return null;
        }).finally(function () {
            if (triggerBtn) {
                triggerBtn.disabled = false;
            }
        });
    }

    function initWishlist() {
        document.addEventListener("click", function (event) {
            var btn = event.target.closest("[data-wishlist-toggle]");
            if (!btn) {
                return;
            }
            event.preventDefault();
            var wineId = btn.getAttribute("data-wine-id") || btn.getAttribute("data-wishlist-toggle");
            toggle(wineId, btn);
        });

        if (document.querySelector("[data-wishlist-toggle], #wishlistGrid, [data-wishlist-container], .js-wishlist-count")) {
            refresh();
        }
    }

    window.StrongWineWishlist = {
        refresh: refresh,
        toggle: toggle,
        isSaved: function (wineId) {
            return state.ids.has(Number(wineId));
        },
        syncButton: syncButton,
        syncButtons: syncAllButtons
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initWishlist, { once: true });
    } else {
        initWishlist();
    }
})(window, document);
