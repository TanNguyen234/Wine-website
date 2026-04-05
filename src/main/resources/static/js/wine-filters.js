(function () {
    var DEBOUNCE_MS = 420;

    function initWineFilters() {
        var form = document.getElementById('filterForm');
        if (!form) return;

        var submitBtn = document.getElementById('filterSubmit');
        var nameInput = document.getElementById('filterName');

        // Auto-submit on select / toggle change (no debounce needed)
        document.querySelectorAll('.auto-submit').forEach(function (el) {
            el.addEventListener('change', function () {
                resetPage(form);
                submitWithLoader(form, submitBtn);
            });
        });

        // Debounced submit for text search input
        if (nameInput) {
            var debounceTimer = null;
            nameInput.addEventListener('input', function () {
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(function () {
                    resetPage(form);
                    submitWithLoader(form, submitBtn);
                }, DEBOUNCE_MS);
            });

            // Immediately submit on Enter
            nameInput.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') {
                    clearTimeout(debounceTimer);
                    resetPage(form);
                    submitWithLoader(form, submitBtn);
                }
            });
        }

        // Explicit submit button
        form.addEventListener('submit', function () {
            resetPage(form);
            setLoader(submitBtn, true);
        });
    }

    function resetPage(form) {
        var pageInput = form.querySelector('input[name="page"]');
        if (pageInput) pageInput.value = '0';
    }

    function submitWithLoader(form, btn) {
        setLoader(btn, true);
        form.submit();
    }

    function setLoader(btn, loading) {
        if (!btn) return;
        if (loading) {
            btn.disabled = true;
            btn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin me-1"></i>Đang lọc…';
        } else {
            btn.disabled = false;
            btn.innerHTML = '<i class="fa-solid fa-magnifying-glass me-1"></i>Tìm kiếm';
        }
    }

    document.addEventListener('DOMContentLoaded', initWineFilters);
})();
