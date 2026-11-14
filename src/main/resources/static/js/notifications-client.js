(function (window, document) {
    "use strict";

    function getCsrfHeaders() {
        var headerMeta = document.querySelector('meta[name="_csrf_header"]');
        var tokenMeta = document.querySelector('meta[name="_csrf"]');
        var headers = {};
        if (tokenMeta && tokenMeta.content) {
            headers[(headerMeta && headerMeta.content) || "X-CSRF-TOKEN"] = tokenMeta.content;
        }
        return headers;
    }

    function safeRelativeLink(url) {
        if (!url || typeof url !== "string") {
            return null;
        }
        var trimmed = url.trim();
        if (trimmed.indexOf("/") === 0 && trimmed.indexOf("//") !== 0) {
            return trimmed;
        }
        return null;
    }

    function formatTime(isoText) {
        if (!isoText) {
            return "";
        }
        try {
            var d = new Date(isoText);
            if (Number.isNaN(d.getTime())) {
                return "";
            }
            return new Intl.DateTimeFormat("vi-VN", {
                day: "2-digit",
                month: "2-digit",
                hour: "2-digit",
                minute: "2-digit"
            }).format(d);
        } catch (_) {
            return "";
        }
    }

    function severityTone(severity) {
        var norm = String(severity || "").toUpperCase();
        if (norm === "WARNING" || norm === "WARN") return "warn";
        if (norm === "ERROR" || norm === "DANGER") return "bad";
        if (norm === "SUCCESS" || norm === "OK") return "ok";
        return "info";
    }

    function buildNotificationNode(item, onClickRead) {
        var isRead = Boolean(item && (item.isRead || item.read));
        var card = document.createElement("div");
        card.className = "panel-soft p12 notif-item";
        card.setAttribute("data-notif-id", String(item.id || ""));
        if (!isRead) {
            card.style.borderLeft = "3px solid var(--accent-gold)";
        }

        var head = document.createElement("div");
        head.className = "between gap8 mb4";

        var title = document.createElement("strong");
        title.textContent = item.title || "Thông báo StrongWine";
        title.style.fontSize = "13px";
        title.style.color = "var(--text-primary)";

        var metaWrap = document.createElement("span");
        metaWrap.className = "display-flex gap6";
        if (!isRead) {
            var dot = document.createElement("span");
            dot.className = "badge badge-gold";
            dot.textContent = "Mới";
            metaWrap.appendChild(dot);
        }
        var timeLabel = document.createElement("span");
        timeLabel.className = "muted";
        timeLabel.style.fontSize = "11px";
        timeLabel.textContent = formatTime(item.createdAt);
        metaWrap.appendChild(timeLabel);

        head.appendChild(title);
        head.appendChild(metaWrap);
        card.appendChild(head);

        if (item.message) {
            var msg = document.createElement("p");
            msg.className = "muted m-0";
            msg.style.fontSize = "12.5px";
            msg.style.lineHeight = "1.5";
            msg.textContent = item.message;
            card.appendChild(msg);
        }

        var actions = document.createElement("div");
        actions.className = "display-flex gap8 mt8";
        var link = safeRelativeLink(item.linkUrl);
        if (link) {
            var anchor = document.createElement("a");
            anchor.href = link;
            anchor.className = "btn btn-outline btn-sm";
            anchor.textContent = "Xem chi tiết";
            anchor.addEventListener("click", function () {
                if (!isRead && item.id) {
                    onClickRead(item.id, null);
                }
            });
            actions.appendChild(anchor);
        }

        if (!isRead && item.id) {
            var readBtn = document.createElement("button");
            readBtn.type = "button";
            readBtn.className = "btn btn-outline btn-sm";
            readBtn.textContent = "Đã đọc";
            readBtn.addEventListener("click", function () {
                onClickRead(item.id, readBtn);
            });
            actions.appendChild(readBtn);
        }

        if (actions.children.length > 0) {
            card.appendChild(actions);
        }

        return card;
    }

    function initNotificationsClient() {
        var bellBtn = document.getElementById("notifBellBtn");
        var bellList = document.getElementById("notifBellList");
        var markAllBtn = document.getElementById("notifMarkAllBtn");
        var profileList = document.getElementById("profileNotificationsList");
        var profileMarkAllBtn = document.getElementById("profileNotifMarkAllBtn");

        if (!bellBtn && !profileList) {
            return;
        }

        var state = {
            notifications: [],
            unreadCount: 0,
            lastEventId: null,
            retryDelay: 1500,
            maxRetryDelay: 30000,
            eventSource: null,
            closed: false,
            retryTimer: null
        };

        function updateBadges(count) {
            var safe = Math.max(0, Number(count) || 0);
            state.unreadCount = safe;
            document.querySelectorAll(".js-notif-count").forEach(function (el) {
                el.textContent = String(safe);
            });
        }

        function renderContainer(container, emptyText) {
            if (!container) {
                return;
            }
            while (container.firstChild) {
                container.removeChild(container.firstChild);
            }
            if (!state.notifications.length) {
                var empty = document.createElement("p");
                empty.className = "muted m-0";
                empty.style.fontSize = "13px";
                empty.textContent = emptyText;
                container.appendChild(empty);
                return;
            }
            state.notifications.slice(0, container === bellList ? 12 : 40).forEach(function (item) {
                container.appendChild(buildNotificationNode(item, markSingleRead));
            });
        }

        function renderAll() {
            renderContainer(bellList, "Bạn chưa có thông báo mới nào.");
            renderContainer(profileList, "Hộp thư thông báo của bạn đang trống.");
        }

        function markSingleRead(id, triggerBtn) {
            if (!id) {
                return;
            }
            if (triggerBtn) {
                triggerBtn.disabled = true;
            }
            fetch("/api/notifications/" + encodeURIComponent(String(id)) + "/read", {
                method: "POST",
                headers: getCsrfHeaders(),
                credentials: "same-origin"
            }).then(function (resp) {
                if (!resp.ok) {
                    throw new Error("Không thể cập nhật thông báo.");
                }
                return resp.json().catch(function () { return null; });
            }).then(function () {
                state.notifications.forEach(function (n) {
                    if (String(n.id) === String(id)) {
                        n.isRead = true;
                        n.read = true;
                    }
                });
                var unread = state.notifications.filter(function (n) {
                    return !n.isRead && !n.read;
                }).length;
                updateBadges(unread);
                renderAll();
            }).catch(function () {
                if (triggerBtn) {
                    triggerBtn.disabled = false;
                }
            });
        }

        function markAllRead() {
            fetch("/api/notifications/read-all?scope=user", {
                method: "POST",
                headers: getCsrfHeaders(),
                credentials: "same-origin"
            }).then(function (resp) {
                if (!resp.ok) {
                    throw new Error("Không thể đánh dấu tất cả đã đọc.");
                }
                return resp.json().catch(function () { return {}; });
            }).then(function () {
                state.notifications.forEach(function (n) {
                    n.isRead = true;
                    n.read = true;
                });
                updateBadges(0);
                renderAll();
                if (window.SwToast && typeof window.SwToast.show === "function") {
                    window.SwToast.show({
                        title: "Thông báo",
                        message: "Đã đánh dấu tất cả thông báo là đã đọc.",
                        tone: "ok"
                    });
                }
            }).catch(function (err) {
                if (window.SwToast && typeof window.SwToast.show === "function") {
                    window.SwToast.show({
                        title: "Thông báo",
                        message: err.message || "Thao tác thất bại.",
                        tone: "warn"
                    });
                }
            });
        }

        function loadNotifications() {
            return fetch("/api/notifications?scope=user", {
                headers: { "Accept": "application/json" },
                credentials: "same-origin"
            }).then(function (resp) {
                if (resp.status === 401 || resp.status === 403) {
                    return null;
                }
                if (!resp.ok) {
                    throw new Error("Không thể tải thông báo.");
                }
                return resp.json();
            }).then(function (payload) {
                if (!payload || typeof payload !== "object") {
                    return;
                }
                state.notifications = Array.isArray(payload.notifications) ? payload.notifications : [];
                updateBadges(payload.unreadCount);
                renderAll();
            }).catch(function () {
                if (bellList && !bellList.children.length) {
                    renderAll();
                }
            });
        }

        function connectUserStream() {
            if (state.closed || typeof window.EventSource !== "function") {
                return;
            }
            var url = "/api/live/notifications/stream";
            if (state.lastEventId) {
                url += "?lastEventId=" + encodeURIComponent(String(state.lastEventId));
            }
            var es = new window.EventSource(url);
            state.eventSource = es;

            es.addEventListener("init", function (event) {
                if (event.lastEventId) {
                    state.lastEventId = event.lastEventId;
                }
                state.retryDelay = 1500;
            });

            es.addEventListener("notification_created", function (event) {
                if (event.lastEventId) {
                    state.lastEventId = event.lastEventId;
                }
                state.retryDelay = 1500;
                try {
                    var payload = JSON.parse(event.data || "{}");
                    if (payload && payload.id != null) {
                        var exists = state.notifications.some(function (n) {
                            return String(n.id) === String(payload.id);
                        });
                        if (!exists) {
                            state.notifications.unshift(payload);
                        }
                    }
                    updateBadges(state.unreadCount + 1);
                    renderAll();
                    if (window.SwToast && typeof window.SwToast.show === "function") {
                        window.SwToast.show({
                            title: payload.title || "Thông báo mới",
                            message: payload.message || "Bạn có cập nhật mới từ StrongWine.",
                            tone: severityTone(payload.severity)
                        });
                    }
                } catch (_) {
                    loadNotifications();
                }
            });

            es.onerror = function () {
                es.close();
                if (state.closed) {
                    return;
                }
                window.clearTimeout(state.retryTimer);
                state.retryTimer = window.setTimeout(function () {
                    state.retryDelay = Math.min(state.retryDelay * 2, state.maxRetryDelay);
                    connectUserStream();
                }, state.retryDelay);
            };
        }

        if (bellBtn) {
            bellBtn.addEventListener("click", function () {
                loadNotifications();
            });
        }
        if (markAllBtn) {
            markAllBtn.addEventListener("click", markAllRead);
        }
        if (profileMarkAllBtn) {
            profileMarkAllBtn.addEventListener("click", markAllRead);
        }

        loadNotifications();
        connectUserStream();

        window.addEventListener("pagehide", function () {
            state.closed = true;
            window.clearTimeout(state.retryTimer);
            if (state.eventSource) {
                state.eventSource.close();
            }
        }, { once: true });

        window.StrongWineNotifications = {
            refresh: loadNotifications,
            markAllRead: markAllRead
        };
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initNotificationsClient, { once: true });
    } else {
        initNotificationsClient();
    }
})(window, document);
