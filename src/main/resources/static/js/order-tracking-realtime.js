(function (window, document) {
    "use strict";

    function OrderTrackingRealtime(orderId, options) {
        this.orderId = orderId;
        this.options = Object.assign({
            onStatusChange: null,
            onShipmentChange: null,
            onOrderPaid: null,
            onConnect: null,
            onDisconnect: null
        }, options || {});

        this.eventSource = null;
        this.lastEventId = null;
        this.retryDelay = 1000;
        this.maxRetryDelay = 16000;
        this.isClosedManually = false;
        this.retryTimer = null;

        this.connect();
    }

    OrderTrackingRealtime.prototype.trackEventId = function (event) {
        if (event && event.lastEventId) {
            this.lastEventId = event.lastEventId;
        }
    };

    OrderTrackingRealtime.prototype.connect = function () {
        var self = this;
        if (self.isClosedManually || !self.orderId || typeof window.EventSource !== "function") {
            return;
        }

        var streamUrl = "/api/live/orders/" + encodeURIComponent(String(self.orderId)) + "/stream";
        if (self.lastEventId) {
            streamUrl += "?lastEventId=" + encodeURIComponent(String(self.lastEventId));
        }

        self.eventSource = new window.EventSource(streamUrl);

        self.eventSource.addEventListener("init", function (event) {
            self.trackEventId(event);
            self.retryDelay = 1000;
            if (typeof self.options.onConnect === "function") {
                self.options.onConnect();
            }
        });

        self.eventSource.addEventListener("ping", function () {});
        self.eventSource.addEventListener("heartbeat", function () {});

        self.eventSource.addEventListener("order_status_updated", function (event) {
            self.trackEventId(event);
            self.retryDelay = 1000;
            try {
                var data = JSON.parse(event.data || "{}");
                if (typeof self.options.onStatusChange === "function") {
                    self.options.onStatusChange(data);
                }
                self.playChime();
            } catch (_) {}
        });

        self.eventSource.addEventListener("shipment_step_updated", function (event) {
            self.trackEventId(event);
            self.retryDelay = 1000;
            try {
                var data = JSON.parse(event.data || "{}");
                if (typeof self.options.onShipmentChange === "function") {
                    self.options.onShipmentChange(data);
                }
                self.playChime();
            } catch (_) {}
        });

        self.eventSource.addEventListener("order_paid", function (event) {
            self.trackEventId(event);
            self.retryDelay = 1000;
            try {
                var data = JSON.parse(event.data || "{}");
                if (typeof self.options.onOrderPaid === "function") {
                    self.options.onOrderPaid(data);
                }
                self.playChime();
            } catch (_) {}
        });

        self.eventSource.onerror = function () {
            if (self.eventSource) {
                self.eventSource.close();
            }
            if (typeof self.options.onDisconnect === "function") {
                self.options.onDisconnect();
            }
            if (self.isClosedManually) {
                return;
            }
            window.clearTimeout(self.retryTimer);
            self.retryTimer = window.setTimeout(function () {
                self.retryDelay = Math.min(self.retryDelay * 2, self.maxRetryDelay);
                self.connect();
            }, self.retryDelay);
        };
    };

    OrderTrackingRealtime.prototype.playChime = function () {
        try {
            var AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextClass) {
                return;
            }
            var audioCtx = new AudioContextClass();
            var osc = audioCtx.createOscillator();
            var gain = audioCtx.createGain();
            osc.type = "sine";
            osc.frequency.setValueAtTime(587.33, audioCtx.currentTime);
            osc.frequency.exponentialRampToValueAtTime(880, audioCtx.currentTime + 0.15);
            gain.gain.setValueAtTime(0.08, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.25);
            osc.connect(gain);
            gain.connect(audioCtx.destination);
            osc.start();
            osc.stop(audioCtx.currentTime + 0.26);
        } catch (_) {}
    };

    OrderTrackingRealtime.prototype.disconnect = function () {
        this.isClosedManually = true;
        window.clearTimeout(this.retryTimer);
        if (this.eventSource) {
            this.eventSource.close();
        }
    };

    window.StrongWineTracking = OrderTrackingRealtime;
})(window, document);
