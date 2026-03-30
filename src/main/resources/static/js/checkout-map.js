(function () {
    const MAINLAND_VIETNAM_POLYGON = [
        [23.23, 102.14],
        [22.50, 104.70],
        [22.85, 106.70],
        [21.55, 108.20],
        [20.50, 108.10],
        [19.20, 107.70],
        [18.00, 107.50],
        [16.80, 108.20],
        [15.50, 108.10],
        [14.00, 109.10],
        [12.30, 109.30],
        [10.80, 106.80],
        [9.40, 105.80],
        [8.70, 104.90],
        [9.70, 104.00],
        [10.90, 103.30],
        [12.20, 104.60],
        [14.10, 107.40],
        [16.00, 106.80],
        [18.10, 105.80],
        [20.40, 104.10],
        [22.00, 103.10]
    ];

    function pointInsidePolygon(lat, lng, polygon) {
        let inside = false;
        let j = polygon.length - 1;
        for (let i = 0; i < polygon.length; i += 1) {
            const yi = polygon[i][0];
            const xi = polygon[i][1];
            const yj = polygon[j][0];
            const xj = polygon[j][1];
            const intersect = ((yi > lat) !== (yj > lat))
                && (lng < ((xj - xi) * (lat - yi)) / ((yj - yi) + 1e-12) + xi);
            if (intersect) {
                inside = !inside;
            }
            j = i;
        }
        return inside;
    }

    function initCheckoutMap() {
        const checkoutPage = document.querySelector("[data-checkout-page='true']");
        if (!checkoutPage || typeof L === "undefined") {
            return;
        }

        const mapEl = document.getElementById("checkoutMap");
        const latInput = document.getElementById("deliveryLat");
        const lngInput = document.getElementById("deliveryLng");
        const locationLabel = document.getElementById("selectedMapLocation");
        const useCurrentBtn = document.getElementById("btnUseCurrentLocation");
        const addressInput = document.getElementById("address");

        if (!mapEl || !latInput || !lngInput) {
            return;
        }

        const vietnamCenter = [16.2, 106.1];
        const maxBounds = [[8.1, 101.9], [23.8, 109.9]];

        const map = L.map(mapEl, {
            minZoom: 5,
            maxZoom: 18,
            maxBounds,
            maxBoundsViscosity: 1.0
        }).setView(vietnamCenter, 6);

        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 18,
            attribution: "&copy; OpenStreetMap contributors"
        }).addTo(map);

        const mainlandPolygon = L.polygon(MAINLAND_VIETNAM_POLYGON, {
            color: "#14532d",
            weight: 2,
            fillOpacity: 0.08
        }).addTo(map);

        map.fitBounds(mainlandPolygon.getBounds(), { padding: [16, 16] });

        let marker = null;

        function updateCoordinateFields(lat, lng) {
            latInput.value = String(lat.toFixed(6));
            lngInput.value = String(lng.toFixed(6));
            if (locationLabel) {
                locationLabel.textContent = "Đã chọn: " + lat.toFixed(6) + ", " + lng.toFixed(6);
            }

            if (addressInput && !addressInput.value.trim()) {
                addressInput.value = "Vị trí được chọn trên bản đồ";
            }
        }

        function setMarkerAt(lat, lng) {
            if (!pointInsidePolygon(lat, lng, MAINLAND_VIETNAM_POLYGON)) {
                alert("Vị trí phải nằm trong phạm vi đất liền Việt Nam.");
                return false;
            }

            if (!marker) {
                marker = L.marker([lat, lng], { draggable: true }).addTo(map);
                marker.on("dragend", function () {
                    const next = marker.getLatLng();
                    if (!pointInsidePolygon(next.lat, next.lng, MAINLAND_VIETNAM_POLYGON)) {
                        alert("Vị trí phải nằm trong phạm vi đất liền Việt Nam.");
                        marker.setLatLng([latInput.value || lat, lngInput.value || lng]);
                        return;
                    }
                    updateCoordinateFields(next.lat, next.lng);
                });
            } else {
                marker.setLatLng([lat, lng]);
            }

            updateCoordinateFields(lat, lng);
            return true;
        }

        map.on("click", function (event) {
            setMarkerAt(event.latlng.lat, event.latlng.lng);
        });

        if (useCurrentBtn) {
            useCurrentBtn.addEventListener("click", function () {
                if (!navigator.geolocation) {
                    alert("Trình duyệt không hỗ trợ định vị.");
                    return;
                }
                navigator.geolocation.getCurrentPosition(function (position) {
                    const lat = position.coords.latitude;
                    const lng = position.coords.longitude;
                    if (setMarkerAt(lat, lng)) {
                        map.setView([lat, lng], 14);
                    }
                }, function () {
                    alert("Không thể lấy vị trí hiện tại. Hãy chọn trực tiếp trên bản đồ.");
                });
            });
        }
    }

    document.addEventListener("DOMContentLoaded", initCheckoutMap);
})();
