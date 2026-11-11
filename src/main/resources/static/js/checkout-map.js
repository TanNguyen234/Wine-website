(function () {
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
        [16.80, 108.40],
        [16.00, 108.40],
        [15.00, 109.10],
        [14.00, 109.30],
        [12.30, 109.50],
        [10.80, 107.50],
        [10.30, 107.30],
        [9.40, 105.80],
        [8.50, 104.80],
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

    function parseCoordinateQuery(value) {
        const match = String(value || "").trim().match(/^([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*[,;]\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))$/);
        if (!match) {
            return null;
        }

        const lat = Number(match[1]);
        const lng = Number(match[2]);
        return {
            lat,
            lng,
            valid: Number.isFinite(lat) && Number.isFinite(lng)
                && lat >= -90 && lat <= 90
                && lng >= -180 && lng <= 180
        };
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

        const map = L.map(mapEl, {
            minZoom: 5,
            maxZoom: 18
        }).setView(vietnamCenter, 6);

        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 18,
            attribution: "&copy; OpenStreetMap contributors"
        }).addTo(map);

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

            if (addressInput) {
                const currentAddress = addressInput.value.trim();
                const addressIsMapFallback = currentAddress === "Vị trí được chọn trên bản đồ";

                if ((!currentAddress || addressIsMapFallback) && placeName) {
                    addressInput.value = placeName;
                } else if (!currentAddress) {
                    addressInput.value = "Vị trí được chọn trên bản đồ";
                }
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
                        throw new Error("Không thể lấy địa chỉ từ tọa độ");
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
                + "&q=" + encodeURIComponent(trimmed)
                + "&limit=5"
                + "&addressdetails=1"
                + "&accept-language=vi";

            return fetch(url)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Không thể tìm kiếm địa điểm");
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

            const coordinates = parseCoordinateQuery(keyword);
            if (coordinates) {
                if (!coordinates.valid) {
                    setSearchResultText("Tọa độ không hợp lệ. Vui lòng nhập theo dạng vĩ độ, kinh độ.", true);
                    return;
                }
                if (!setMarkerAt(coordinates.lat, coordinates.lng)) {
                    setSearchResultText("Địa điểm nằm ngoài phạm vi giao hàng đất liền Việt Nam", true);
                    return;
                }

                map.setView([coordinates.lat, coordinates.lng], 14);
                setSearchResultText("Đã chọn vị trí theo tọa độ", false);
                return;
            }

            isBusy = true;
            setSearchResultText("Đang tìm địa điểm...", false);
            searchLocationByName(keyword).then(function (items) {
                if (!items.length) {
                    setSearchResultText("Không tìm thấy địa điểm phù hợp", true);
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

        // Auto-center and place pin for prefilled profile coordinates or address
        setTimeout(function () {
            map.invalidateSize();
            const initLat = parseFloat(latInput.value);
            const initLng = parseFloat(lngInput.value);
            if (!isNaN(initLat) && !isNaN(initLng) && Math.abs(initLat) > 0.001 && Math.abs(initLng) > 0.001) {
                if (setMarkerAt(initLat, initLng)) {
                    map.setView([initLat, initLng], 15);
                }
            } else if (addressInput && addressInput.value.trim() && addressInput.value.trim() !== "Vị trí được chọn trên bản đồ") {
                searchLocationByName(addressInput.value.trim()).then(function (items) {
                    if (items && items.length > 0) {
                        const lat = Number(items[0].lat);
                        const lng = Number(items[0].lon);
                        if (!isNaN(lat) && !isNaN(lng) && setMarkerAt(lat, lng)) {
                            map.setView([lat, lng], 15);
                        }
                    }
                }).catch(function () {});
            }
        }, 250);
    }

    document.addEventListener("DOMContentLoaded", initCheckoutMap);
})();
