(function () {
    const toggle = document.querySelector(".mobile-nav-toggle");
    const nav = document.getElementById("siteNav");
    if (!toggle || !nav) return;

    function setOpen(open) {
        toggle.setAttribute("aria-expanded", String(open));
        toggle.setAttribute("aria-label", open ? "Đóng menu điều hướng" : "Mở menu điều hướng");
        nav.classList.toggle("is-open", open);
    }

    toggle.addEventListener("click", function () {
        setOpen(toggle.getAttribute("aria-expanded") !== "true");
    });

    nav.addEventListener("click", function (event) {
        if (event.target.closest("a, button[type='submit']")) setOpen(false);
    });

    document.addEventListener("click", function (event) {
        if (!nav.contains(event.target) && !toggle.contains(event.target)) setOpen(false);
    });

    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && toggle.getAttribute("aria-expanded") === "true") {
            setOpen(false);
            toggle.focus();
        }
    });

    const desktopQuery = window.matchMedia("(min-width: 769px)");
    desktopQuery.addEventListener("change", function (event) {
        if (event.matches) setOpen(false);
    });
})();
