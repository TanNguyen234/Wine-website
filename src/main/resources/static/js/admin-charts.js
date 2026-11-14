(function () {
    "use strict";

    var charts = [];

    function readCssToken(name, fallback) {
        try {
            var val = window.getComputedStyle(document.documentElement).getPropertyValue(name);
            return val && val.trim() ? val.trim() : fallback;
        } catch (_e) {
            return fallback;
        }
    }

    function parseJsonArray(raw, fallback) {
        if (!raw) {
            return fallback;
        }
        try {
            var parsed = JSON.parse(raw);
            return Array.isArray(parsed) ? parsed : fallback;
        } catch (_e) {
            return fallback;
        }
    }

    function getThemePalette() {
        var theme = (document.documentElement.getAttribute("data-theme") || "cellar").toLowerCase();
        var isIvory = theme === "ivory";

        return {
            isIvory: isIvory,
            textPrimary: readCssToken("--text-primary", isIvory ? "#161618" : "#f3f1ec"),
            textSecondary: readCssToken("--text-secondary", isIvory ? "#52504c" : "#a8a49c"),
            gridLine: readCssToken("--border-hairline", isIvory ? "rgba(21, 22, 25, 0.09)" : "rgba(244, 243, 239, 0.08)"),
            surfaceElevated: readCssToken("--surface-elevated", isIvory ? "#ffffff" : "#121418"),
            crimson: readCssToken("--accent-crimson", "#6f1d2b"),
            gold: readCssToken("--accent-gold", "#b5a68e"),
            goldInk: readCssToken("--accent-gold-ink", isIvory ? "#5b4f3d" : "#dbd2c2"),
            fontSans: readCssToken("--font-sans", "'Plus Jakarta Sans', system-ui, sans-serif"),
            fontMono: readCssToken("--font-mono", "ui-monospace, monospace"),
            slicePalette: [
                readCssToken("--accent-crimson", "#6f1d2b"),
                readCssToken("--accent-gold", "#b5a68e"),
                isIvory ? "#2e6f56" : "#4b9176",
                isIvory ? "#3b6b96" : "#5e8ebd",
                isIvory ? "#6e5586" : "#8b72a4"
            ]
        };
    }

    function destroyChartForCanvas(canvas) {
        for (var i = charts.length - 1; i >= 0; i--) {
            if (charts[i].canvas === canvas) {
                try {
                    charts[i].instance.destroy();
                } catch (_e) {
                    // ignore already destroyed chart
                }
                charts.splice(i, 1);
            }
        }
    }

    function formatCurrencyVnd(value) {
        var num = Number(value) || 0;
        return new Intl.NumberFormat("vi-VN").format(num) + " d";
    }

    function drawCanvasFallbackRevenue(canvas, labels, values, palette) {
        var ctx = canvas.getContext("2d");
        if (!ctx) {
            return;
        }
        var dpr = window.devicePixelRatio || 1;
        var width = canvas.clientWidth || 560;
        var height = canvas.clientHeight || 280;
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, width, height);

        if (!values.length) {
            ctx.fillStyle = palette.textSecondary;
            ctx.font = "13px " + palette.fontSans;
            ctx.fillText("Chưa có dữ liệu doanh thu.", 24, height / 2);
            return;
        }

        var padL = 68;
        var padR = 20;
        var padT = 22;
        var padB = 38;
        var plotW = Math.max(120, width - padL - padR);
        var plotH = Math.max(80, height - padT - padB);
        var maxVal = Math.max.apply(null, values.concat([1]));

        ctx.strokeStyle = palette.gridLine;
        ctx.lineWidth = 1;
        for (var g = 0; g <= 4; g++) {
            var y = padT + (plotH * g) / 4;
            ctx.beginPath();
            ctx.moveTo(padL, y);
            ctx.lineTo(padL + plotW, y);
            ctx.stroke();

            var labelVal = Math.round(maxVal * (1 - g / 4));
            ctx.fillStyle = palette.textSecondary;
            ctx.font = "11px " + palette.fontMono;
            ctx.fillText(new Intl.NumberFormat("vi-VN").format(labelVal), 6, y + 4);
        }

        ctx.beginPath();
        var stepX = values.length > 1 ? plotW / (values.length - 1) : plotW / 2;
        var pts = values.map(function (v, idx) {
            return {
                x: padL + (values.length > 1 ? idx * stepX : plotW / 2),
                y: padT + plotH - ((Number(v) || 0) / maxVal) * plotH
            };
        });

        pts.forEach(function (pt, idx) {
            if (idx === 0) {
                ctx.moveTo(pt.x, pt.y);
            } else {
                ctx.lineTo(pt.x, pt.y);
            }
        });
        ctx.strokeStyle = palette.gold;
        ctx.lineWidth = 2.5;
        ctx.stroke();

        pts.forEach(function (pt, idx) {
            ctx.beginPath();
            ctx.arc(pt.x, pt.y, 4, 0, Math.PI * 2);
            ctx.fillStyle = palette.gold;
            ctx.fill();
            ctx.strokeStyle = palette.crimson;
            ctx.lineWidth = 1.5;
            ctx.stroke();

            ctx.fillStyle = palette.textSecondary;
            ctx.font = "11px " + palette.fontSans;
            var lbl = String(labels[idx] || "");
            ctx.fillText(lbl, Math.max(padL, pt.x - 16), height - 12);
        });
    }

    function drawCanvasFallbackCategory(canvas, labels, values, palette) {
        var ctx = canvas.getContext("2d");
        if (!ctx) {
            return;
        }
        var dpr = window.devicePixelRatio || 1;
        var width = canvas.clientWidth || 340;
        var height = canvas.clientHeight || 280;
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, width, height);

        var total = values.reduce(function (sum, v) {
            return sum + (Number(v) || 0);
        }, 0);
        if (total <= 0) {
            ctx.fillStyle = palette.textSecondary;
            ctx.font = "13px " + palette.fontSans;
            ctx.fillText("Chưa có dữ liệu phân loại rượu.", 24, height / 2);
            return;
        }

        var cx = Math.min(width * 0.34, 120);
        var cy = height * 0.5;
        var r = Math.min(cx - 18, height * 0.36);
        var startAngle = -Math.PI / 2;

        values.forEach(function (v, idx) {
            var val = Number(v) || 0;
            var angle = (val / total) * Math.PI * 2;
            ctx.beginPath();
            ctx.moveTo(cx, cy);
            ctx.arc(cx, cy, r, startAngle, startAngle + angle);
            ctx.closePath();
            ctx.fillStyle = palette.slicePalette[idx % palette.slicePalette.length];
            ctx.fill();
            startAngle += angle;
        });

        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.58, 0, Math.PI * 2);
        ctx.fillStyle = palette.surfaceElevated;
        ctx.fill();

        var legendX = cx + r + 24;
        var legendY = Math.max(24, cy - (labels.length * 22) / 2);
        labels.forEach(function (lbl, idx) {
            var y = legendY + idx * 24;
            ctx.fillStyle = palette.slicePalette[idx % palette.slicePalette.length];
            ctx.fillRect(legendX, y, 10, 10);
            ctx.fillStyle = palette.textPrimary;
            ctx.font = "12px " + palette.fontSans;
            ctx.fillText(String(lbl) + ": " + String(values[idx] || 0), legendX + 16, y + 9);
        });
    }

    function renderRevenueChart(palette) {
        var canvas = document.getElementById("revenueChart");
        if (!canvas) {
            return;
        }
        var labels = parseJsonArray(canvas.dataset.labels || canvas.dataset.revenueLabels, []);
        var values = parseJsonArray(canvas.dataset.values || canvas.dataset.revenueValues, []);

        destroyChartForCanvas(canvas);

        if (typeof window.Chart !== "function") {
            drawCanvasFallbackRevenue(canvas, labels, values, palette);
            return;
        }

        var ctx = canvas.getContext("2d");
        var gradient = ctx.createLinearGradient(0, 0, 0, 280);
        gradient.addColorStop(0, palette.isIvory ? "rgba(106, 28, 41, 0.16)" : "rgba(181, 166, 142, 0.2)");
        gradient.addColorStop(1, "rgba(181, 166, 142, 0.0)");

        var instance = new window.Chart(canvas, {
            type: "line",
            data: {
                labels: labels,
                datasets: [{
                    label: "Doanh thu (VND)",
                    data: values,
                    borderColor: palette.gold,
                    backgroundColor: gradient,
                    borderWidth: 2.5,
                    pointBackgroundColor: palette.gold,
                    pointBorderColor: palette.crimson,
                    pointBorderWidth: 2,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    fill: true,
                    tension: 0.34
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        backgroundColor: palette.surfaceElevated,
                        titleColor: palette.textPrimary,
                        bodyColor: palette.goldInk,
                        borderColor: palette.gridLine,
                        borderWidth: 1,
                        padding: 10,
                        callbacks: {
                            label: function (context) {
                                return " Doanh thu: " + formatCurrencyVnd(context.parsed.y);
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: palette.gridLine, drawBorder: false },
                        ticks: {
                            color: palette.textSecondary,
                            font: { family: palette.fontSans, size: 11 }
                        }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: palette.gridLine, drawBorder: false },
                        ticks: {
                            color: palette.textSecondary,
                            font: { family: palette.fontMono, size: 11 },
                            callback: function (value) {
                                return new Intl.NumberFormat("vi-VN").format(value);
                            }
                        }
                    }
                }
            }
        });

        charts.push({ canvas: canvas, instance: instance });
    }

    function renderCategoryChart(palette) {
        var canvas = document.getElementById("categoryChart");
        if (!canvas) {
            return;
        }
        var labels = parseJsonArray(canvas.dataset.labels || canvas.dataset.categoryLabels, []);
        var values = parseJsonArray(canvas.dataset.values || canvas.dataset.categoryValues, []);

        destroyChartForCanvas(canvas);

        if (typeof window.Chart !== "function") {
            drawCanvasFallbackCategory(canvas, labels, values, palette);
            return;
        }

        var instance = new window.Chart(canvas, {
            type: "doughnut",
            data: {
                labels: labels,
                datasets: [{
                    data: values,
                    backgroundColor: labels.map(function (_l, i) {
                        return palette.slicePalette[i % palette.slicePalette.length];
                    }),
                    borderColor: palette.surfaceElevated,
                    borderWidth: 2,
                    hoverOffset: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: "66%",
                plugins: {
                    legend: {
                        position: "bottom",
                        labels: {
                            color: palette.textPrimary,
                            padding: 14,
                            usePointStyle: true,
                            pointStyle: "circle",
                            font: { family: palette.fontSans, size: 12 }
                        }
                    },
                    tooltip: {
                        backgroundColor: palette.surfaceElevated,
                        titleColor: palette.textPrimary,
                        bodyColor: palette.goldInk,
                        borderColor: palette.gridLine,
                        borderWidth: 1,
                        padding: 10
                    }
                }
            }
        });

        charts.push({ canvas: canvas, instance: instance });
    }

    function renderAllCharts() {
        var palette = getThemePalette();
        renderRevenueChart(palette);
        renderCategoryChart(palette);
    }

    document.addEventListener("DOMContentLoaded", renderAllCharts);
    document.addEventListener("sw:themechange", function () {
        if (typeof window.requestAnimationFrame === "function") {
            window.requestAnimationFrame(renderAllCharts);
        } else {
            window.setTimeout(renderAllCharts, 20);
        }
    });
})();
