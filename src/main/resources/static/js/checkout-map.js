(function () {
    const VIETNAM_BORDER_GEOJSON_URL = "https://raw.githubusercontent.com/johan/world.geo.json/master/countries/VNM.geo.json";
    const NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    const NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";

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
        const searchInput = document.getElementById("mapSearchInput");
        const searchBtn = document.getElementById("btnMapSearch");
        const searchResult = document.getElementById("mapSearchResult");

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

        fetch(VIETNAM_BORDER_GEOJSON_URL)
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("Khong the tai duong bien gioi Viet Nam");
                }
                return response.json();
            })
            .then(function (geojson) {
                L.geoJSON(geojson, {
                    style: {
                        color: "#166534",
                        weight: 2,
                        fillColor: "#86efac",
                        fillOpacity: 0.05
                    }
                }).addTo(map);
            })
            .catch(function () {
                // Keep fallback polygon when external border source is unavailable.
            });

        map.fitBounds(mainlandPolygon.getBounds(), { padding: [16, 16] });

        let marker = null;
        let isBusy = false;

        function setLocationLabel(text) {
            if (locationLabel) {
                locationLabel.textContent = text;
            }
        }

        function setSearchResultText(text, isError) {
            if (!searchResult) {
                return;
            }
            searchResult.textContent = text;
            searchResult.classList.remove("text-danger", "text-muted");
            searchResult.classList.add(isError ? "text-danger" : "text-muted");
        }

        function buildDisplayLabel(placeName, lat, lng) {
            const coordText = lat.toFixed(6) + ", " + lng.toFixed(6);
            if (placeName) {
                return "Đã chọn: " + placeName + " (" + coordText + ")";
            }
            return "Đã chọn: " + coordText;
        }

        function updateCoordinateFields(lat, lng, placeName) {
            latInput.value = String(lat.toFixed(6));
            lngInput.value = String(lng.toFixed(6));
            setLocationLabel(buildDisplayLabel(placeName, lat, lng));

            if (addressInput && !addressInput.value.trim()) {
                addressInput.value = placeName || "Vị trí được chọn trên bản đồ";
            }
        }

        function reverseGeocode(lat, lng) {
            const url = NOMINATIM_REVERSE_URL
                + "?format=jsonv2"
                + "&lat=" + encodeURIComponent(String(lat))
                + "&lon=" + encodeURIComponent(String(lng))
                + "&accept-language=vi"
                + "&zoom=18";

            return fetch(url)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Khong the lay dia chi tu toa do");
                    }
                    return response.json();
                })
                .then(function (payload) {
                    if (!payload || !payload.display_name) {
                        return null;
                    }
                    return payload.display_name;
                })
                .catch(function () {
                    return null;
                });
        }

        function searchLocationByName(keyword) {
            const trimmed = (keyword || "").trim();
            if (!trimmed) {
                return Promise.resolve([]);
            }

            const url = NOMINATIM_SEARCH_URL
                + "?format=jsonv2"
                + "&q=" + encodeURIComponent(trimmed + ", Vietnam")
                + "&countrycodes=vn"
                + "&limit=5"
                + "&addressdetails=1"
                + "&accept-language=vi";

            return fetch(url)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Khong the tim kiem dia diem");
                    }
                    return response.json();
                })
                .then(function (payload) {
                    return Array.isArray(payload) ? payload : [];
                })
                .catch(function () {
                    return [];
                });
        }

        function updateMarkerLabelFromCoordinates(lat, lng) {
            return reverseGeocode(lat, lng).then(function (placeName) {
                updateCoordinateFields(lat, lng, placeName);
            });
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
                        marker.setLatLng([
                            Number(latInput.value || lat),
                            Number(lngInput.value || lng)
                        ]);
                        return;
                    }
                    updateMarkerLabelFromCoordinates(next.lat, next.lng);
                });
            } else {
                marker.setLatLng([lat, lng]);
            }

            updateCoordinateFields(lat, lng, null);
            updateMarkerLabelFromCoordinates(lat, lng);
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

        function onSearch() {
            if (isBusy || !searchInput) {
                return;
            }
            const keyword = searchInput.value || "";
            if (!keyword.trim()) {
                setSearchResultText("Nhập tên địa điểm để tìm", false);
                return;
            }

            isBusy = true;
            setSearchResultText("Đang tìm địa điểm...", false);
            searchLocationByName(keyword).then(function (items) {
                if (!items.length) {
                    setSearchResultText("Không tìm thấy địa điểm phù hợp ở Việt Nam", true);
                    return;
                }

                const first = items[0];
                const lat = Number(first.lat);
                const lng = Number(first.lon);
                if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
                    setSearchResultText("Kết quả tìm kiếm không có tọa độ hợp lệ", true);
                    return;
                }

                if (!setMarkerAt(lat, lng)) {
                    setSearchResultText("Địa điểm nằm ngoài phạm vi giao hàng đất liền Việt Nam", true);
                    return;
                }

                map.setView([lat, lng], 14);
                updateCoordinateFields(lat, lng, first.display_name || null);
                setSearchResultText("Đã chọn: " + (first.display_name || "địa điểm tìm được"), false);
            }).finally(function () {
                isBusy = false;
            });
        }

        if (searchBtn) {
            searchBtn.addEventListener("click", onSearch);
        }
        if (searchInput) {
            searchInput.addEventListener("keydown", function (event) {
                if (event.key === "Enter") {
                    event.preventDefault();
                    onSearch();
                }
            });
        }
    }

    document.addEventListener("DOMContentLoaded", initCheckoutMap);
})();
