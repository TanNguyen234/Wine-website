(function (window, document) {
    "use strict";

    var NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    var NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";

    var MAINLAND_VIETNAM_POLYGON = [
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
        var inside = false;
        var j = polygon.length - 1;
        for (var i = 0; i < polygon.length; i += 1) {
            var yi = polygon[i][0];
            var xi = polygon[i][1];
            var yj = polygon[j][0];
            var xj = polygon[j][1];
            var intersect = ((yi > lat) !== (yj > lat))
                && (lng < ((xj - xi) * (lat - yi)) / ((yj - yi) + 1e-12) + xi);
            if (intersect) {
                inside = !inside;
            }
            j = i;
        }
        return inside;
    }

    function parseCoordinateQuery(value) {
        var match = String(value || "").trim().match(/^([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*[,;]\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))$/);
        if (!match) {
            return null;
        }

        var lat = Number(match[1]);
        var lng = Number(match[2]);
        return {
            lat: lat,
            lng: lng,
            valid: Number.isFinite(lat) && Number.isFinite(lng)
                && lat >= -90 && lat <= 90
                && lng >= -180 && lng <= 180
        };
    }

    function ensureLeafletCss() {
        if (document.querySelector('link[href*="leaflet"]')) {
            return;
        }
        var link = document.createElement("link");
        link.rel = "stylesheet";
        link.href = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css";
        document.head.appendChild(link);
    }

    function notifyMap(message, tone) {
        if (window.SwToast && typeof window.SwToast.show === "function") {
            window.SwToast.show({
                title: "Bản đồ giao hàng",
                message: message,
                tone: tone || "warn"
            });
        }
    }

    function initCheckoutMap() {
        var checkoutPage = document.querySelector("[data-checkout-page='true']");
        if (!checkoutPage) {
            return;
        }
        ensureLeafletCss();
        if (typeof window.L === "undefined") {
            return;
        }

        var L = window.L;
        var mapEl = document.getElementById("checkoutMap");
        var latInput = document.getElementById("deliveryLat");
        var lngInput = document.getElementById("deliveryLng");
        var locationLabel = document.getElementById("selectedMapLocation");
        var useCurrentBtn = document.getElementById("btnUseCurrentLocation");
        var addressInput = document.getElementById("address");
        var searchInput = document.getElementById("mapSearchInput");
        var searchBtn = document.getElementById("btnMapSearch");
        var searchResult = document.getElementById("mapSearchResult");

        if (!mapEl || !latInput || !lngInput) {
            return;
        }

        var vietnamCenter = [16.2, 106.1];
        var map = L.map(mapEl, {
            minZoom: 5,
            maxZoom: 18
        }).setView(vietnamCenter, 6);

        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 18,
            attribution: "&copy; OpenStreetMap contributors"
        }).addTo(map);

        var marker = null;
        var isBusy = false;

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
            searchResult.style.color = isError ? "var(--status-danger-ink)" : "var(--text-muted)";
        }

        function buildDisplayLabel(placeName, lat, lng) {
            var coordText = lat.toFixed(6) + ", " + lng.toFixed(6);
            if (placeName) {
                return "Đã chọn: " + placeName + " (" + coordText + ")";
            }
            return "Đã chọn: " + coordText;
        }

        function emitLocationUpdated(lat, lng, placeName) {
            document.dispatchEvent(new CustomEvent("strongwine:location-updated", {
                detail: { lat: lat, lng: lng, placeName: placeName || "" }
            }));
        }

        function updateCoordinateFields(lat, lng, placeName) {
            latInput.value = String(lat.toFixed(6));
            lngInput.value = String(lng.toFixed(6));
            setLocationLabel(buildDisplayLabel(placeName, lat, lng));

            if (addressInput) {
                var currentAddress = addressInput.value.trim();
                const addressIsMapFallback = currentAddress === "Vị trí được chọn trên bản đồ";

                if ((!currentAddress || addressIsMapFallback) && placeName) {
                    addressInput.value = placeName;
                } else if (!currentAddress) {
                    addressInput.value = "Vị trí được chọn trên bản đồ";
                }
            }
            emitLocationUpdated(lat, lng, placeName);
        }

        function reverseGeocode(lat, lng) {
            var url = NOMINATIM_REVERSE_URL
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
            var trimmed = (keyword || "").trim();
            if (!trimmed) {
                return Promise.resolve([]);
            }

            var url = NOMINATIM_SEARCH_URL
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

        function setMarkerAt(lat, lng, skipReverse) {
            if (!pointInsidePolygon(lat, lng, MAINLAND_VIETNAM_POLYGON)) {
                setSearchResultText("Vị trí phải nằm trong phạm vi đất liền Việt Nam.", true);
                notifyMap("Vị trí phải nằm trong phạm vi đất liền Việt Nam.", "warn");
                return false;
            }

            if (!marker) {
                marker = L.marker([lat, lng], { draggable: true }).addTo(map);
                marker.on("dragend", function () {
                    var next = marker.getLatLng();
                    if (!pointInsidePolygon(next.lat, next.lng, MAINLAND_VIETNAM_POLYGON)) {
                        setSearchResultText("Vị trí phải nằm trong phạm vi đất liền Việt Nam.", true);
                        notifyMap("Vị trí phải nằm trong phạm vi đất liền Việt Nam.", "warn");
                        marker.setLatLng([
                            Number(latInput.value || lat),
                            Number(lngInput.value || lng)
                        ]);
                        return;
                    }
                    var savedHidden = document.getElementById("savedAddressId");
                    if (savedHidden) savedHidden.value = "";
                    updateMarkerLabelFromCoordinates(next.lat, next.lng);
                });
            } else {
                marker.setLatLng([lat, lng]);
            }

            updateCoordinateFields(lat, lng, null);
            if (!skipReverse) {
                updateMarkerLabelFromCoordinates(lat, lng);
            }
            return true;
        }

        map.on("click", function (event) {
            var savedHidden = document.getElementById("savedAddressId");
            if (savedHidden) savedHidden.value = "";
            setMarkerAt(event.latlng.lat, event.latlng.lng);
        });

        document.addEventListener("strongwine:set-map-pin", function (event) {
            var detail = event.detail || {};
            if (Number.isFinite(detail.lat) && Number.isFinite(detail.lng)) {
                if (setMarkerAt(detail.lat, detail.lng, true)) {
                    map.setView([detail.lat, detail.lng], 15);
                    if (detail.address) {
                        setLocationLabel(buildDisplayLabel(detail.address, detail.lat, detail.lng));
                    }
                }
            }
        });

        if (useCurrentBtn) {
            useCurrentBtn.addEventListener("click", function () {
                if (!navigator.geolocation) {
                    setSearchResultText("Trình duyệt không hỗ trợ định vị.", true);
                    notifyMap("Trình duyệt không hỗ trợ định vị.", "warn");
                    return;
                }
                navigator.geolocation.getCurrentPosition(function (position) {
                    var lat = position.coords.latitude;
                    var lng = position.coords.longitude;
                    var savedHidden = document.getElementById("savedAddressId");
                    if (savedHidden) savedHidden.value = "";
                    if (setMarkerAt(lat, lng)) {
                        map.setView([lat, lng], 14);
                    }
                }, function () {
                    setSearchResultText("Không thể lấy vị trí hiện tại. Hãy chọn trực tiếp trên bản đồ.", true);
                    notifyMap("Không thể lấy vị trí hiện tại. Hãy chọn trực tiếp trên bản đồ.", "warn");
                });
            });
        }

        function onSearch() {
            if (isBusy || !searchInput) {
                return;
            }
            var keyword = searchInput.value || "";
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

                var first = items[0];
                var lat = Number(first.lat);
                var lng = Number(first.lon);
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

        window.setTimeout(function () {
            map.invalidateSize();
            var initLat = parseFloat(latInput.value);
            var initLng = parseFloat(lngInput.value);
            var initialAddressText = addressInput ? addressInput.value.trim() : "";
            var hasInitialAddress = Boolean(initialAddressText && initialAddressText !== "Vị trí được chọn trên bản đồ");
            if (!Number.isNaN(initLat) && !Number.isNaN(initLng) && Math.abs(initLat) > 0.001 && Math.abs(initLng) > 0.001) {
                if (setMarkerAt(initLat, initLng, hasInitialAddress)) {
                    map.setView([initLat, initLng], 15);
                    if (hasInitialAddress) {
                        setLocationLabel(buildDisplayLabel(initialAddressText, initLat, initLng));
                    }
                }
            } else if (hasInitialAddress) {
                searchLocationByName(initialAddressText).then(function (items) {
                    if (items && items.length > 0) {
                        var lat = Number(items[0].lat);
                        var lng = Number(items[0].lon);
                        if (!Number.isNaN(lat) && !Number.isNaN(lng) && setMarkerAt(lat, lng, true)) {
                            map.setView([lat, lng], 15);
                            setLocationLabel(buildDisplayLabel(initialAddressText, lat, lng));
                        }
                    }
                }).catch(function () {});
            }
        }, 250);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initCheckoutMap, { once: true });
    } else {
        initCheckoutMap();
    }
})(window, document);
