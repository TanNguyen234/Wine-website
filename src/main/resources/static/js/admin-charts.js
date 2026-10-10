(function () {
    "use strict";

    function drawFallbackBarChart(canvas, labels, values) {
        const ctx = canvas.getContext("2d");
        if (!ctx) return;
        const w = (canvas.width = canvas.parentElement ? canvas.parentElement.clientWidth || 640 : 640);
        const h = (canvas.height = 240);
        ctx.clearRect(0, 0, w, h);

        const maxVal = Math.max(1, ...values.map((v) => Number(v) || 0));
        const padLeft = 56;
        const padBottom = 34;
        const padTop = 20;
        const chartW = w - padLeft - 20;
        const chartH = h - padTop - padBottom;

        ctx.strokeStyle = "#e8e2d8";
        ctx.lineWidth = 1;
        for (let i = 0; i <= 4; i++) {
            const y = padTop + (chartH / 4) * i;
            ctx.beginPath();
            ctx.moveTo(padLeft, y);
            ctx.lineTo(w - 20, y);
            ctx.stroke();
        }

        const step = chartW / Math.max(1, labels.length);
        const barW = Math.min(44, step * 0.56);
        labels.forEach((lbl, idx) => {
            const val = Number(values[idx]) || 0;
            const bh = (val / maxVal) * (chartH - 10);
            const x = padLeft + idx * step + (step - barW) / 2;
            const y = padTop + chartH - bh;

            ctx.fillStyle = "#742c3d";
            ctx.beginPath();
            ctx.roundRect(x, y, barW, bh, [6, 6, 0, 0]);
            ctx.fill();

            ctx.fillStyle = "#79717a";
            ctx.font = "11px sans-serif";
            ctx.textAlign = "center";
            ctx.fillText(String(lbl), x + barW / 2, h - 12);
        });
    }

    function drawFallbackDoughnut(canvas, labels, values) {
        const ctx = canvas.getContext("2d");
        if (!ctx) return;
        const w = (canvas.width = canvas.parentElement ? canvas.parentElement.clientWidth || 340 : 340);
        const h = (canvas.height = 220);
        ctx.clearRect(0, 0, w, h);

        const colors = ["#742c3d", "#b08c59", "#356a54", "#ae793d", "#3b1825", "#8f5b66"];
        const total = Math.max(1, values.reduce((acc, v) => acc + (Number(v) || 0), 0));
        const cx = w * 0.36;
        const cy = h * 0.5;
        const r = Math.min(cx, cy) - 22;
        let angle = -Math.PI / 2;

        values.forEach((v, idx) => {
            const slice = ((Number(v) || 0) / total) * Math.PI * 2;
            ctx.beginPath();
            ctx.moveTo(cx, cy);
            ctx.arc(cx, cy, r, angle, angle + slice);
            ctx.closePath();
            ctx.fillStyle = colors[idx % colors.length];
            ctx.fill();
            angle += slice;
        });

        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.58, 0, Math.PI * 2);
        ctx.fillStyle = "#fffdf8";
        ctx.fill();

        labels.forEach((lbl, idx) => {
            const lx = w * 0.66;
            const ly = 44 + idx * 26;
            ctx.fillStyle = colors[idx % colors.length];
            ctx.fillRect(lx, ly - 9, 12, 12);
            ctx.fillStyle = "#272128";
            ctx.font = "12px sans-serif";
            ctx.textAlign = "left";
            ctx.fillText(`${lbl} (${values[idx] || 0})`, lx + 18, ly + 1);
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        const revCanvas = document.getElementById("revenueChart");
        if (revCanvas) {
            let labels = ["T5", "T6", "T7", "T8", "T9", "T10"];
            let values = [0, 0, 0, 0, 0, 0];
            try {
                if (revCanvas.dataset.labels) labels = JSON.parse(revCanvas.dataset.labels);
                if (revCanvas.dataset.values) values = JSON.parse(revCanvas.dataset.values);
            } catch (_) {}

            if (typeof Chart !== "undefined") {
                new Chart(revCanvas, {
                    type: "bar",
                    data: {
                        labels: labels,
                        datasets: [{
                            label: "Doanh thu (VNĐ)",
                            data: values,
                            backgroundColor: "#742c3d",
                            borderRadius: 6
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: { legend: { display: false } }
                    }
                });
            } else {
                drawFallbackBarChart(revCanvas, labels, values);
            }
        }

        const typeCanvas = document.getElementById("wineTypeChart");
        if (typeCanvas) {
            let labels = ["Vang đỏ", "Vang trắng", "Sparkling"];
            let values = [1, 1, 1];
            try {
                if (typeCanvas.dataset.labels) labels = JSON.parse(typeCanvas.dataset.labels);
                if (typeCanvas.dataset.values) values = JSON.parse(typeCanvas.dataset.values);
            } catch (_) {}

            if (typeof Chart !== "undefined") {
                new Chart(typeCanvas, {
                    type: "doughnut",
                    data: {
                        labels: labels,
                        datasets: [{
                            data: values,
                            backgroundColor: ["#742c3d", "#b08c59", "#356a54", "#ae793d", "#3b1825"]
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false
                    }
                });
            } else {
                drawFallbackDoughnut(typeCanvas, labels, values);
            }
        }
    });
})();
