(function (window, document) {
    "use strict";

    function initWineDetails() {
        var detailRoot = document.querySelector("[data-wine-detail-id]");
        if (!detailRoot) {
            return;
        }

        // Gallery Carousel
        var slides = Array.prototype.slice.call(document.querySelectorAll(".wine-carousel-slide"));
        var dots = Array.prototype.slice.call(document.querySelectorAll(".carousel-dot"));
        var thumbs = Array.prototype.slice.call(document.querySelectorAll(".carousel-thumb"));
        var prevBtn = document.getElementById("carouselPrev");
        var nextBtn = document.getElementById("carouselNext");
        var currentIndex = 0;

        function goToSlide(index) {
            if (!slides.length) {
                return;
            }
            currentIndex = (index + slides.length) % slides.length;
            slides.forEach(function (slide, idx) {
                var active = idx === currentIndex;
                slide.classList.toggle("active", active);
                slide.hidden = !active;
            });
            dots.forEach(function (dot, idx) {
                var active = idx === currentIndex;
                dot.classList.toggle("active", active);
                dot.setAttribute("aria-selected", active ? "true" : "false");
            });
            thumbs.forEach(function (thumb, idx) {
                var active = idx === currentIndex;
                thumb.classList.toggle("active", active);
                thumb.setAttribute("aria-pressed", active ? "true" : "false");
            });
        }

        if (prevBtn) {
            prevBtn.addEventListener("click", function () { goToSlide(currentIndex - 1); });
        }
        if (nextBtn) {
            nextBtn.addEventListener("click", function () { goToSlide(currentIndex + 1); });
        }
        dots.forEach(function (dot, idx) {
            dot.addEventListener("click", function () { goToSlide(idx); });
        });
        thumbs.forEach(function (thumb, idx) {
            thumb.addEventListener("click", function () { goToSlide(idx); });
        });

        // Image fallback to bottleSvg
        detailRoot.querySelectorAll("img[data-bottle-fallback]").forEach(function (img) {
            img.addEventListener("error", function () {
                img.classList.add("d-none");
                var target = document.getElementById(img.getAttribute("data-bottle-fallback"));
                if (target) {
                    target.classList.remove("d-none");
                }
            });
        });

        // Dynamic verified-purchase badges from ReviewApiController
        var wineId = detailRoot.getAttribute("data-wine-detail-id");
        if (wineId) {
            fetch("/api/reviews/wine/" + encodeURIComponent(wineId), {
                headers: { "Accept": "application/json" },
                credentials: "same-origin"
            }).then(function (resp) {
                if (!resp.ok) {
                    return null;
                }
                return resp.json().catch(function () { return null; });
            }).then(function (rows) {
                if (!Array.isArray(rows)) {
                    return;
                }
                var verifiedMap = {};
                rows.forEach(function (row) {
                    if (row && row.id != null) {
                        verifiedMap[String(row.id)] = Boolean(row.verifiedPurchase);
                    }
                });
                document.querySelectorAll("[data-review-id]").forEach(function (el) {
                    var rid = el.getAttribute("data-review-id");
                    if (rid && verifiedMap[rid]) {
                        var badge = el.querySelector("[data-verified-badge]");
                        if (badge) {
                            badge.classList.remove("d-none");
                        }
                    }
                });
            }).catch(function () {});
        }

        // Review Edit Dialog
        var editDialog = document.getElementById("editReviewModal");
        var editForm = document.getElementById("editReviewForm");
        var editRating = document.getElementById("editReviewRating");
        var editComment = document.getElementById("editReviewComment");
        document.addEventListener("click", function (event) {
            var btn = event.target.closest("[data-edit-review-id]");
            if (!btn || !editDialog || !editForm) {
                return;
            }
            var reviewId = btn.getAttribute("data-edit-review-id");
            var rating = btn.getAttribute("data-review-rating") || "5";
            var comment = btn.getAttribute("data-review-comment") || "";
            editForm.setAttribute("action", "/reviews/" + encodeURIComponent(reviewId) + "/edit");
            if (editRating) editRating.value = rating;
            if (editComment) editComment.value = comment;

            if (window.SwDialog && typeof window.SwDialog.open === "function") {
                window.SwDialog.open(editDialog, btn);
            } else if (typeof editDialog.showModal === "function") {
                editDialog.showModal();
            }
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initWineDetails, { once: true });
    } else {
        initWineDetails();
    }
})(window, document);
