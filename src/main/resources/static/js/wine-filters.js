(function () {
    function initWineFilters() {
        var form = document.getElementById("filterForm");
        if (!form) {
            return;
        }

        document.querySelectorAll(".auto-submit").forEach(function (el) {
            el.addEventListener("change", function () {
                form.submit();
            });
        });
    }

    document.addEventListener("DOMContentLoaded", initWineFilters);
})();
