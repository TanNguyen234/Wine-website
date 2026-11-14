(function () {
    "use strict";

    var STORAGE_EVENT_KEY = "sw_admin_last_event_id";
    var eventSource = null;
    var reconnectTimer = null;
    var reconnectAttempt = 0;
    var seenEventIds = Object.create(null);
    var lastEventId = "";

    try {
        lastEventId = window.sessionStorage ? (window.sessionStorage.getItem(STORAGE_EVENT_KEY) || "") : "";
    } catch (_e) {
        lastEventId = "";
    }

    function getCsrfHeaders() {
        var headers = {};
        var tokenMeta = document.querySelector("meta[name='_csrf']");
        var headerMeta = document.querySelector("meta[name='_csrf_header']");
        var token = tokenMeta ? tokenMeta.getAttribute("content") : "";
        var headerName = headerMeta ? headerMeta.getAttribute("content") : "X-CSRF-TOKEN";
        if (token && headerName) {
            headers[headerName] = token;
        }
        return headers;
    }

    function clearElement(el) {
        if (!el) {
            return;
        }
        while (el.firstChild) {
            el.removeChild(el.firstChild);
        }
    }

    function sanitizeInternalLink(url) {
        if (typeof url !== "string" || !url.startsWith("/") || url.startsWith("//")) {
            return "/admin/orders";
        }
        return url;
    }

    function updateBadgeTextPreserveDot(badge, text, state) {
        if (!badge) {
            return;
        }
        var labelSpan = badge.querySelector("[data-live-status-text]");
        if (!labelSpan) {
            var dot = badge.querySelector(".live-pulse-dot");
            var existingNodes = Array.prototype.slice.call(badge.childNodes);
            existingNodes.forEach(function (node) {
                if (node !== dot) {
                    badge.removeChild(node);
                }
            });
            labelSpan = document.createElement("span");
            labelSpan.setAttribute("data-live-status-text", "");
            badge.appendChild(labelSpan);
        }
        labelSpan.textContent = text;
        badge.dataset.liveState = state || "connecting";
    }

    function setUnreadBadgeCount(count) {
        var badge = document.getElementById("adminNotifBadgeCount");
        if (!badge) {
            return;
        }
        var safeCount = Math.max(0, Number(count) || 0);
        badge.textContent = safeCount > 99 ? "99+" : String(safeCount);
        if (safeCount > 0) {
            badge.classList.remove("d-none");
        } else {
            badge.classList.add("d-none");
        }
    }

    function parseNotificationsPayload(data) {
        if (Array.isArray(data)) {
            return data;
        }
        if (data && Array.isArray(data.items)) {
            return data.items;
        }
        if (data && Array.isArray(data.notifications)) {
            return data.notifications;
        }
        if (data && Array.isArray(data.content)) {
            return data.content;
        }
        return [];
    }

    function markNotificationRead(id) {
        if (!id) {
            return Promise.resolve();
        }
        return window.fetch("/api/notifications/" + encodeURIComponent(id) + "/read", {
            method: "POST",
            headers: getCsrfHeaders()
        }).catch(function () {
            return null;
        });
    }

    function renderNotificationItems(items) {
        var list = document.getElementById("adminNotifList");
        if (!list) {
            return;
        }
        clearElement(list);

        if (!items || items.length === 0) {
            var emptyLi = document.createElement("li");
            emptyLi.className = "text-secondary small p-3";
            emptyLi.setAttribute("data-empty-placeholder", "true");
            emptyLi.textContent = "Chưa có thông báo mới.";
            list.appendChild(emptyLi);
            return;
        }

        items.slice(0, 20).forEach(function (item) {
            list.appendChild(buildNotificationElement(item));
        });
    }

    function buildNotificationElement(item) {
        var li = document.createElement("li");
        li.className = "notif-item" + (item.isRead || item.read ? "" : " is-unread");

        var link = document.createElement("a");
        link.className = "notif-link";
        link.href = sanitizeInternalLink(item.link || item.targetUrl || "/admin/orders");

        var title = document.createElement("strong");
        title.className = "notif-title";
        title.textContent = item.title || "Thông báo vận hành";

        var body = document.createElement("span");
        body.className = "notif-body";
        body.textContent = item.message || item.content || item.body || "";

        var time = document.createElement("span");
        time.className = "notif-time";
        time.textContent = item.createdAt ? String(item.createdAt).replace("T", " ").slice(0, 16) : "Vừa xong";

        link.appendChild(title);
        link.appendChild(body);
        link.appendChild(time);

        if (item.id != null) {
            link.addEventListener("click", function () {
                markNotificationRead(item.id);
                li.classList.remove("is-unread");
            });
        }

        li.appendChild(link);
        return li;
    }

    function prependLiveNotification(payload) {
        var list = document.getElementById("adminNotifList");
        if (list) {
            var emptyNode = list.querySelector("[data-empty-placeholder]");
            if (emptyNode) {
                list.removeChild(emptyNode);
            }
            var node = buildNotificationElement(payload);
            list.insertBefore(node, list.firstChild);
            while (list.children.length > 20) {
                list.removeChild(list.lastChild);
            }
        }

        var dashFeed = document.getElementById("dashboardLiveActivityList");
        if (dashFeed) {
            var feedEmpty = dashFeed.querySelector("[data-empty-placeholder]");
            if (feedEmpty) {
                dashFeed.removeChild(feedEmpty);
            }
            dashFeed.insertBefore(buildNotificationElement(payload), dashFeed.firstChild);
            while (dashFeed.children.length > 8) {
                dashFeed.removeChild(dashFeed.lastChild);
            }
        }
    }

    function showToast(title, message, tone) {
        if (window.SwToast && typeof window.SwToast.show === "function") {
            window.SwToast.show({
                title: title,
                message: message,
                tone: tone || "info",
                duration: 5500
            });
            return;
        }

        var toastEl = document.getElementById("adminLiveToast");
        var titleEl = document.getElementById("adminToastTitle");
        var bodyEl = document.getElementById("adminToastBody");
        if (!toastEl || !titleEl || !bodyEl) {
            return;
        }
        titleEl.textContent = title || "Cập nhật mới";
        bodyEl.textContent = message || "";
        toastEl.classList.remove("d-none");
        toastEl.classList.add("show");
        window.setTimeout(function () {
            toastEl.classList.remove("show");
            toastEl.classList.add("d-none");
        }, 5500);
    }

    function loadInitialNotifications() {
        window.fetch("/api/notifications?limit=15")
            .then(function (resp) {
                if (!resp.ok) {
                    return null;
                }
                return resp.json();
            })
            .then(function (data) {
                if (!data) {
                    return;
                }
                var items = parseNotificationsPayload(data);
                renderNotificationItems(items);
                if (typeof data.unreadCount === "number") {
                    setUnreadBadgeCount(data.unreadCount);
                }
            })
            .catch(function () {
                // Ignore transient startup network errors
            });

        window.fetch("/api/notifications/unread-count")
            .then(function (resp) {
                return resp.ok ? resp.json() : null;
            })
            .then(function (data) {
                if (data && typeof data.unreadCount === "number") {
                    setUnreadBadgeCount(data.unreadCount);
                } else if (typeof data === "number") {
                    setUnreadBadgeCount(data);
                }
            })
            .catch(function () {
                // Ignore
            });
    }

    function saveLastEventId(id) {
        if (!id) {
            return;
        }
        lastEventId = String(id);
        try {
            if (window.sessionStorage) {
                window.sessionStorage.setItem(STORAGE_EVENT_KEY, lastEventId);
            }
        } catch (_e) {
            // Ignore private-mode storage failures
        }
    }

    function handleBusinessEvent(eventName, event) {
        if (event.lastEventId) {
            if (seenEventIds[event.lastEventId]) {
                return;
            }
            seenEventIds[event.lastEventId] = true;
            saveLastEventId(event.lastEventId);
        }

        var payload;
        try {
            payload = JSON.parse(event.data);
        } catch (_e) {
            payload = { title: "Thông báo mới", message: String(event.data || "") };
        }

        var defaultTitles = {
            admin_order_notification: "Đơn hàng mới / cập nhật",
            admin_order_paid: "Thanh toán đơn hàng thành công",
            admin_shipment_notification: "Cập nhật vận đơn",
            low_stock_alert: "Cảnh báo tồn kho thấp",
            admin_alert: "Cảnh báo vận hành",
            notification_created: "Thông báo mới"
        };

        var tone = "info";
        if (eventName === "low_stock_alert" || eventName === "admin_alert") {
            tone = "warn";
        } else if (eventName === "admin_order_paid") {
            tone = "good";
        }

        var title = payload.title || defaultTitles[eventName] || "Thông báo vận hành";
        var message = payload.message || payload.content || payload.body || "Có sự kiện mới trong hệ thống.";

        prependLiveNotification({
            id: payload.id || null,
            title: title,
            message: message,
            link: payload.link || payload.targetUrl || (eventName === "low_stock_alert" ? "/admin/inventory" : "/admin/orders"),
            createdAt: payload.createdAt || null,
            read: false
        });

        var countEl = document.getElementById("adminNotifBadgeCount");
        var current = countEl && countEl.textContent ? parseInt(countEl.textContent, 10) || 0 : 0;
        setUnreadBadgeCount(current + 1);
        showToast(title, message, tone);
    }

    function scheduleReconnect() {
        if (reconnectTimer) {
            return;
        }
        var delay = Math.min(30000, Math.round(1500 * Math.pow(2, reconnectAttempt)));
        reconnectAttempt += 1;
        reconnectTimer = window.setTimeout(function () {
            reconnectTimer = null;
            connectSse();
        }, delay);
    }

    function connectSse() {
        var liveBadge = document.getElementById("adminLiveBadge");
        if (typeof window.EventSource !== "function") {
            updateBadgeTextPreserveDot(liveBadge, "SSE không khả dụng", "offline");
            return;
        }

        if (eventSource) {
            try {
                eventSource.close();
            } catch (_e) {
                // ignore
            }
        }

        var streamUrl = "/api/live/admin/notifications";
        if (lastEventId) {
            streamUrl += "?lastEventId=" + encodeURIComponent(lastEventId);
        }

        updateBadgeTextPreserveDot(liveBadge, "Đang kết nối...", "connecting");
        eventSource = new window.EventSource(streamUrl);

        eventSource.onopen = function () {
            reconnectAttempt = 0;
            updateBadgeTextPreserveDot(liveBadge, "Trực tuyến", "live");
        };

        eventSource.addEventListener("init", function (event) {
            reconnectAttempt = 0;
            if (event.lastEventId) {
                saveLastEventId(event.lastEventId);
            }
            updateBadgeTextPreserveDot(liveBadge, "Trực tuyến", "live");
        });

        eventSource.addEventListener("heartbeat", function (event) {
            if (event.lastEventId) {
                saveLastEventId(event.lastEventId);
            }
            updateBadgeTextPreserveDot(liveBadge, "Trực tuyến", "live");
        });

        [
            "admin_order_notification",
            "admin_order_paid",
            "admin_shipment_notification",
            "low_stock_alert",
            "admin_alert",
            "notification_created"
        ].forEach(function (eventName) {
            eventSource.addEventListener(eventName, function (event) {
                handleBusinessEvent(eventName, event);
            });
        });

        eventSource.onmessage = function (event) {
            if (event && event.data && event.data !== "ping") {
                handleBusinessEvent("message", event);
            }
        };

        eventSource.onerror = function () {
            updateBadgeTextPreserveDot(liveBadge, "Đang nối lại...", "reconnecting");
            if (eventSource) {
                try {
                    eventSource.close();
                } catch (_e) {
                    // ignore
                }
                eventSource = null;
            }
            scheduleReconnect();
        };
    }

    document.addEventListener("DOMContentLoaded", function () {
        loadInitialNotifications();
        connectSse();

        var markAllBtn = document.querySelector("[data-admin-mark-all-read], #adminMarkAllReadBtn");
        if (markAllBtn) {
            markAllBtn.addEventListener("click", function () {
                window.fetch("/api/notifications/read-all", {
                    method: "POST",
                    headers: getCsrfHeaders()
                }).then(function (resp) {
                    if (resp.ok) {
                        setUnreadBadgeCount(0);
                        var list = document.getElementById("adminNotifList");
                        if (list) {
                            Array.prototype.forEach.call(list.querySelectorAll(".is-unread"), function (el) {
                                el.classList.remove("is-unread");
                            });
                        }
                    }
                }).catch(function () {
                    // Ignore
                });
            });
        }
    });

    window.addEventListener("beforeunload", function () {
        if (eventSource) {
            try {
                eventSource.close();
            } catch (_e) {
                // ignore
            }
        }
    });
})();
