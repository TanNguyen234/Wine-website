/**
 * StrongWine — Executive Cellar Dashboard Visualizations & Live SSE Monitor
 */
document.addEventListener('DOMContentLoaded', () => {
    // 1. Biểu Đồ Doanh Thu 7 Ngày Gần Nhất
    const revenueCtx = document.getElementById('revenueChart');
    if (revenueCtx && typeof Chart !== 'undefined') {
        const gradient = revenueCtx.getContext('2d').createLinearGradient(0, 0, 0, 240);
        gradient.addColorStop(0, 'rgba(201, 168, 76, 0.35)');
        gradient.addColorStop(1, 'rgba(201, 168, 76, 0.0)');

        new Chart(revenueCtx, {
            type: 'line',
            data: {
                labels: ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'],
                datasets: [{
                    label: 'Doanh thu (triệu VNĐ)',
                    data: [12.5, 18.2, 15.4, 24.8, 31.5, 48.0, 39.2],
                    borderColor: '#c9a84c',
                    borderWidth: 2.5,
                    backgroundColor: gradient,
                    fill: true,
                    tension: 0.38,
                    pointBackgroundColor: '#c9a84c',
                    pointBorderColor: '#18120d',
                    pointBorderWidth: 2,
                    pointRadius: 5,
                    pointHoverRadius: 7
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: '#1f1610',
                        titleColor: '#e4c67d',
                        bodyColor: '#f5f0e8',
                        borderColor: '#c9a84c',
                        borderWidth: 1,
                        padding: 10,
                        callbacks: {
                            label: (ctx) => `${ctx.raw} triệu VNĐ`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: 'rgba(201, 168, 76, 0.06)' },
                        ticks: { color: '#a09280', font: { family: 'Lato' } }
                    },
                    y: {
                        grid: { color: 'rgba(201, 168, 76, 0.06)' },
                        ticks: {
                            color: '#a09280',
                            font: { family: 'Lato' },
                            callback: (val) => val + ' tr'
                        }
                    }
                }
            }
        });
    }

    // 2. Biểu Đồ Cơ Cấu Danh Mục Rượu Vang
    const categoryCtx = document.getElementById('categoryPieChart');
    if (categoryCtx && typeof Chart !== 'undefined') {
        new Chart(categoryCtx, {
            type: 'doughnut',
            data: {
                labels: ['Vang Đỏ', 'Vang Trắng', 'Vang Sủi Champagne', 'Vang Hồng'],
                datasets: [{
                    data: [52, 28, 14, 6],
                    backgroundColor: [
                        '#8b1a1a', // Crimson Bordeaux
                        '#e4c67d', // Champagne Gold
                        '#4a7c59', // Emerald Cellar
                        '#d47385'  // Rose Blush
                    ],
                    borderColor: '#18120d',
                    borderWidth: 2,
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            color: '#c7b9a3',
                            font: { family: 'Lato', size: 11 },
                            padding: 12
                        }
                    },
                    tooltip: {
                        backgroundColor: '#1f1610',
                        titleColor: '#e4c67d',
                        bodyColor: '#f5f0e8',
                        borderColor: '#c9a84c',
                        borderWidth: 1
                    }
                },
                cutout: '68%'
            }
        });
    }

    // 3. Lắng nghe SSE Live Channel cho Admin
    try {
        const adminSse = new EventSource('/api/live/admin/stream');
        const alertBadge = document.getElementById('adminLiveBadge');

        adminSse.addEventListener('init', () => {
            if (alertBadge) {
                alertBadge.innerHTML = '<span class="sse-pulse-dot"></span> Live Cockpit';
                alertBadge.className = 'sse-live-pill';
            }
        });

        adminSse.addEventListener('order_status_updated', (e) => {
            const data = JSON.parse(e.data);
            showAdminToast(`Đơn hàng #${data.orderId}`, data.message || `Trạng thái: ${data.status}`);
        });

        adminSse.addEventListener('shipment_step_updated', (e) => {
            const data = JSON.parse(e.data);
            showAdminToast(`Vận chuyển #${data.shipmentId}`, `Đơn #${data.orderId} chuyển sang: ${data.status}`);
        });
    } catch (e) {
        console.debug('SSE admin channel not active or unsupported');
    }

    function showAdminToast(title, msg) {
        const toast = document.getElementById('adminLiveToast');
        if (toast) {
            document.getElementById('adminToastTitle').textContent = title;
            document.getElementById('adminToastBody').textContent = msg;
            toast.classList.remove('d-none');
            toast.classList.add('show');
            setTimeout(() => { toast.classList.add('d-none'); }, 6000);
        }
    }
});
