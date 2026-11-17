(function (window, document) {
    "use strict";

    function initializeWineList() {
        var filterDisclosure = document.getElementById("catalogFilters");
        if (filterDisclosure && typeof window.matchMedia === "function") {
            var mobileBreakpoint = window.matchMedia("(max-width: 768px)");
            var hasActiveFilters = function () {
                return Array.prototype.slice.call(
                    document.querySelectorAll("#filterForm input[name], #filterForm select[name]")
                ).some(function (field) {
                    if (["page", "size", "sortBy", "sortDir"].indexOf(field.name) !== -1) {
                        return false;
                    }
                    if (field.type === "range" && field.value === field.max) {
                        return false;
                    }
                    return field.type === "checkbox" ? field.checked : field.value.trim() !== "";
                });
            };
            var syncFilterDisclosure = function () {
                filterDisclosure.open = !mobileBreakpoint.matches || hasActiveFilters();
            };
            syncFilterDisclosure();
            if (typeof mobileBreakpoint.addEventListener === "function") {
                mobileBreakpoint.addEventListener("change", syncFilterDisclosure);
            } else if (typeof mobileBreakpoint.addListener === "function") {
                mobileBreakpoint.addListener(syncFilterDisclosure);
            }
        }

        var filterForm = document.getElementById("filterForm");
        var priceSlider = document.getElementById("priceRangeSlider");
        var priceOutput = document.getElementById("priceRangeOutput");
        var priceSliderAdjusted = false;
        var initialUrlHasMaxPrice = false;
        try {
            var searchParams = new window.URLSearchParams(window.location.search || "");
            initialUrlHasMaxPrice = Boolean(searchParams.get("maxPrice") && searchParams.get("maxPrice").trim());
        } catch (_e) {
            initialUrlHasMaxPrice = false;
        }

        if (priceSlider && priceOutput) {
            priceSlider.addEventListener("input", function (event) {
                var val = parseInt(event.target.value, 10);
                var maxVal = parseInt(priceSlider.max, 10);
                if (Number.isFinite(val) && Number.isFinite(maxVal) && val < maxVal) {
                    priceSliderAdjusted = true;
                }
                priceOutput.textContent = new Intl.NumberFormat("vi-VN").format(Number.isFinite(val) ? val : 0) + " ₫";
            });
        }

        if (filterForm && priceSlider) {
            var disableDefaultMaxPriceIfUnchanged = function () {
                if (!initialUrlHasMaxPrice && !priceSliderAdjusted && priceSlider.value === priceSlider.max) {
                    priceSlider.disabled = true;
                }
            };
            filterForm.addEventListener("submit", disableDefaultMaxPriceIfUnchanged);
            if (typeof filterForm.submit === "function") {
                var nativeFormSubmit = filterForm.submit.bind(filterForm);
                filterForm.submit = function () {
                    disableDefaultMaxPriceIfUnchanged();
                    nativeFormSubmit();
                };
            }
        }

        document.querySelectorAll(".btn-quick-view").forEach(function (btn) {
            btn.addEventListener("click", function (event) {
                event.preventDefault();
                var attr = function (current, legacy) {
                    return btn.getAttribute(current) || btn.getAttribute(legacy) || "";
                };
                var id = attr("data-id", "data-wine-id");
                var name = attr("data-name", "data-wine-name");
                var rawPrice = attr("data-price", "data-wine-raw-price") || attr("data-wine-price", "");
                var type = attr("data-type", "data-wine-type");
                var year = attr("data-year", "data-wine-year");
                var country = attr("data-country", "data-wine-country");
                var desc = attr("data-desc", "data-wine-desc");
                var img = attr("data-img", "data-wine-img");
                var stock = attr("data-stock", "data-wine-stock") || "0";

                var modal = document.getElementById("quickViewModal");
                if (!modal) {
                    return;
                }

                var setTxt = function (selector, value) {
                    var el = modal.querySelector(selector);
                    if (el) {
                        el.textContent = value;
                    }
                };

                var priceValue = Number(String(rawPrice).replace(/[^0-9.]/g, ""));
                var formattedPrice = Number.isFinite(priceValue)
                    ? new Intl.NumberFormat("vi-VN").format(priceValue) + " ₫"
                    : rawPrice;

                setTxt("#qvName", name);
                setTxt("#qvPrice", formattedPrice);
                setTxt("#qvType", type);
                setTxt("#qvYear", year || "—");
                setTxt("#qvCountry", country || "Quốc tế");
                setTxt("#qvDesc", desc || "Hương vị cân bằng, hậu vị thanh lịch từ nhà làm vang tuyển chọn.");

                var stockValue = Math.max(0, parseInt(stock, 10) || 0);
                setTxt("#qvStockBadge", stockValue > 0 ? ("Còn " + stockValue + " chai") : "Hết hàng");

                var imgEl = modal.querySelector("#qvImg");
                var imgFallback = modal.querySelector("#qvFallback");
                if (imgEl) {
                    imgEl.alt = name || "Rượu vang";
                    imgEl.onerror = function () {
                        imgEl.classList.add("d-none");
                        if (imgFallback) imgFallback.classList.remove("d-none");
                    };
                    if (img.trim()) {
                        if (imgFallback) imgFallback.classList.add("d-none");
                        imgEl.classList.remove("d-none");
                        imgEl.src = img;
                    } else {
                        imgEl.removeAttribute("src");
                        imgEl.classList.add("d-none");
                        if (imgFallback) imgFallback.classList.remove("d-none");
                    }
                }

                var detailLink = modal.querySelector("#qvDetailLink");
                if (detailLink && id) {
                    detailLink.setAttribute("href", "/wines/" + encodeURIComponent(id));
                }

                var wishBtn = modal.querySelector("#qvWishlistBtn");
                if (wishBtn && id) {
                    wishBtn.setAttribute("data-wine-id", id);
                    wishBtn.setAttribute("data-wine-name", name || "");
                    if (window.StrongWineWishlist && typeof window.StrongWineWishlist.syncButton === "function") {
                        window.StrongWineWishlist.syncButton(wishBtn);
                    }
                }

                var addBtn = modal.querySelector(".modal-add-to-cart-btn");
                if (addBtn) {
                    addBtn.setAttribute("data-cart-add", "true");
                    addBtn.setAttribute("data-cart-product-id", id || "");
                    addBtn.setAttribute("data-cart-name", name || "");
                    addBtn.setAttribute("data-cart-price", String(rawPrice).replace(/[^0-9.]/g, ""));
                    addBtn.setAttribute("data-cart-image", img || "");
                    addBtn.setAttribute("data-cart-stock", stock);
                    addBtn.disabled = stockValue <= 0;
                }

                if (window.SwDialog && typeof window.SwDialog.open === "function") {
                    window.SwDialog.open(modal, btn);
                } else if (typeof modal.showModal === "function") {
                    modal.showModal();
                } else {
                    modal.classList.add("open");
                }
            });
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initializeWineList, { once: true });
    } else {
        initializeWineList();
    }
})(window, document);
