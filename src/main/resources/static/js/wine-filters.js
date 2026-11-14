(function (window, document) {
    "use strict";

    var DEBOUNCE_MS = 420;

    function resetPage(form) {
        var pageInput = form.querySelector('input[name="page"]');
        if (pageInput) {
            pageInput.value = "0";
        }
    }

    function setLoader(btn, loading) {
        if (!btn) {
            return;
        }
        btn.disabled = Boolean(loading);
        btn.classList.toggle("is-loading", Boolean(loading));
        btn.textContent = loading ? "Đang lọc…" : (btn.getAttribute("data-default-label") || "Áp dụng bộ lọc");
    }

    function submitWithLoader(form, btn) {
        setLoader(btn, true);
        form.submit();
    }

    function initWineFilters() {
        var form = document.getElementById("filterForm");
        if (!form) {
            return;
        }

        var submitBtn = document.getElementById("filterSubmit");
        if (submitBtn && !submitBtn.getAttribute("data-default-label")) {
            submitBtn.setAttribute("data-default-label", submitBtn.textContent.trim() || "Áp dụng bộ lọc");
        }

        var nameInput = document.getElementById("filterName");

        document.querySelectorAll(".auto-submit").forEach(function (el) {
            el.addEventListener("change", function () {
                resetPage(form);
                submitWithLoader(form, submitBtn);
            });
        });

        if (nameInput) {
            var debounceTimer = null;
            nameInput.addEventListener("input", function () {
                window.clearTimeout(debounceTimer);
                debounceTimer = window.setTimeout(function () {
                    resetPage(form);
                    submitWithLoader(form, submitBtn);
                }, DEBOUNCE_MS);
            });

            nameInput.addEventListener("keydown", function (event) {
                if (event.key === "Enter") {
                    window.clearTimeout(debounceTimer);
                    resetPage(form);
                    submitWithLoader(form, submitBtn);
                }
            });
        }

        form.addEventListener("submit", function () {
            resetPage(form);
            setLoader(submitBtn, true);
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initWineFilters, { once: true });
    } else {
        initWineFilters();
    }
})(window, document);
