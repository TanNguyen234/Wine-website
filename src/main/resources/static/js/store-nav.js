(function (window, document) {
    "use strict";

    function initStoreNav() {
        var toggle = document.getElementById("mobileNavToggle") || document.querySelector(".mobile-nav-toggle");
        var nav = document.getElementById("siteNav");
        if (!toggle || !nav) {
            return;
        }

        function setOpen(open) {
            toggle.setAttribute("aria-expanded", String(open));
            toggle.setAttribute("aria-label", open ? "Đóng menu điều hướng" : "Mở menu điều hướng");
            nav.classList.toggle("is-open", open);
        }

        toggle.addEventListener("click", function () {
            setOpen(toggle.getAttribute("aria-expanded") !== "true");
        });

        nav.addEventListener("click", function (event) {
            if (event.target.closest("a, button[type='submit']")) {
                setOpen(false);
            }
        });

        document.addEventListener("click", function (event) {
            if (!nav.contains(event.target) && !toggle.contains(event.target)) {
                setOpen(false);
            }
        });

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && toggle.getAttribute("aria-expanded") === "true") {
                setOpen(false);
                toggle.focus();
            }
        });

        if (typeof window.matchMedia === "function") {
            var desktopQuery = window.matchMedia("(min-width: 769px)");
            var handleMatch = function (event) {
                if (event.matches) {
                    setOpen(false);
                }
            };
            if (typeof desktopQuery.addEventListener === "function") {
                desktopQuery.addEventListener("change", handleMatch);
            } else if (typeof desktopQuery.addListener === "function") {
                desktopQuery.addListener(handleMatch);
            }
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initStoreNav, { once: true });
    } else {
        initStoreNav();
    }
})(window, document);
