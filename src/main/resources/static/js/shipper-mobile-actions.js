(function (window, document) {
  "use strict";

  var MAX_POD_BYTES = 5 * 1024 * 1024;
  var RESEND_COOLDOWN_SECONDS = 60;
  var SSE_INITIAL_DELAY_MS = 1000;
  var SSE_MAX_DELAY_MS = 30000;

  var podPreviewUrl = null;
  var resendTimerId = null;
  var sseSource = null;
  var sseRetryTimerId = null;
  var sseRetryDelay = SSE_INITIAL_DELAY_MS;
  var sseLastEventId = null;
  var sseClosed = false;
  var pendingSseUpdates = 0;

  function getCsrfConfig() {
    var tokenEl = document.querySelector('meta[name="_csrf"]');
    var headerEl = document.querySelector('meta[name="_csrf_header"]');
    return {
      token: tokenEl ? tokenEl.getAttribute("content") || "" : "",
      header: headerEl ? headerEl.getAttribute("content") || "X-CSRF-TOKEN" : "X-CSRF-TOKEN"
    };
  }

  function showToast(title, message, tone) {
    if (window.SwToast && typeof window.SwToast.show === "function") {
      window.SwToast.show({
        title: title || "StrongWine Shipper",
        message: message || "",
        tone: tone || "info",
        duration: 5500
      });
    }
  }

  function openModal(dialogId, triggerEl) {
    var dialog = document.getElementById(dialogId);
    if (!dialog) {
      return null;
    }
    if (window.SwDialog && typeof window.SwDialog.open === "function") {
      return window.SwDialog.open(dialog, triggerEl);
    }
    if (typeof dialog.showModal === "function" && !dialog.open) {
      dialog.showModal();
    } else {
      dialog.setAttribute("open", "");
    }
    return dialog;
  }

  function formatVnd(amount) {
    var rounded = Math.round(Number(amount) || 0);
    return rounded.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",") + " \u20ab";
  }

  function showInlineError(el, message) {
    if (!el) {
      return;
    }
    if (message) {
      el.textContent = message;
      el.hidden = false;
    } else {
      el.textContent = "";
      el.hidden = true;
    }
  }

  /* --------------------------------------------------------------------------
     1. Status Filter Chips (All / Assigned / Delivering / Done / COD)
     -------------------------------------------------------------------------- */
  function initFilterTabs() {
    var tabButtons = document.querySelectorAll("[data-shipper-tab]");
    var cards = document.querySelectorAll("[data-shipment-group]");
    var feedSection = document.getElementById("shipmentsFeedSection");
    var codSection = document.getElementById("codBatchesSection");
    var filterEmpty = document.getElementById("filterEmptyState");

    if (!tabButtons.length) {
      return;
    }

    function applyFilter(targetTab, scrollToCod) {
      tabButtons.forEach(function (btn) {
        var isActive = btn.getAttribute("data-shipper-tab") === targetTab;
        btn.classList.toggle("active", isActive);
        btn.setAttribute("aria-pressed", isActive ? "true" : "false");
      });

      if (targetTab === "cod") {
        if (feedSection) {
          feedSection.hidden = true;
        }
        if (codSection) {
          codSection.hidden = false;
          if (scrollToCod && typeof codSection.scrollIntoView === "function") {
            codSection.scrollIntoView({ behavior: "smooth", block: "start" });
          }
        }
        return;
      }

      if (feedSection) {
        feedSection.hidden = false;
      }
      if (codSection) {
        codSection.hidden = targetTab !== "all";
      }

      var visibleCount = 0;
      cards.forEach(function (card) {
        var group = card.getAttribute("data-shipment-group");
        var visible = targetTab === "all" || group === targetTab;
        card.hidden = !visible;
        if (visible) {
          visibleCount += 1;
        }
      });

      if (filterEmpty) {
        filterEmpty.hidden = !(cards.length > 0 && visibleCount === 0);
      }
    }

    tabButtons.forEach(function (btn) {
      btn.addEventListener("click", function () {
        var target = btn.getAttribute("data-shipper-tab") || "all";
        applyFilter(target, target === "cod");
      });
    });

    if (
      window.location.pathname.indexOf("/shipper/cod-batches") !== -1 ||
      window.location.hash === "#codBatchesSection"
    ) {
      applyFilter("cod", true);
    }
  }

  /* --------------------------------------------------------------------------
     2. POD Photo Modal (Instant Client Preview + Size Check)
     -------------------------------------------------------------------------- */
  function revokePodPreview() {
    if (podPreviewUrl && window.URL && typeof window.URL.revokeObjectURL === "function") {
      window.URL.revokeObjectURL(podPreviewUrl);
      podPreviewUrl = null;
    }
  }

  function initPodModal() {
    var form = document.getElementById("podModalForm");
    var fileInput = document.getElementById("podPhotoInput");
    var previewWrap = document.getElementById("podPreviewWrap");
    var previewImg = document.getElementById("podPreviewImg");
    var previewInfo = document.getElementById("podPreviewInfo");
    var metaEl = document.getElementById("podModalMeta");
    var existingNote = document.getElementById("podModalExistingNote");
    var errorEl = document.getElementById("podModalError");
    var noteInput = document.getElementById("podDeliveryNote");
    var submitBtn = document.getElementById("podSubmitBtn");
    var modal = document.getElementById("podModal");

    if (!form || !fileInput) {
      return;
    }

    function resetPodModalState() {
      revokePodPreview();
      fileInput.value = "";
      if (noteInput) {
        noteInput.value = "";
      }
      if (previewWrap) {
        previewWrap.hidden = true;
      }
      if (previewImg) {
        previewImg.removeAttribute("src");
      }
      if (previewInfo) {
        previewInfo.textContent = "";
      }
      showInlineError(errorEl, "");
      form.dataset.submitting = "false";
      if (submitBtn) {
        submitBtn.disabled = false;
      }
    }

    document.addEventListener("click", function (event) {
      var trigger = event.target.closest(".js-open-pod-modal");
      if (!trigger) {
        return;
      }
      event.preventDefault();
      resetPodModalState();

      var action = trigger.getAttribute("data-pod-action");
      var orderLabel = trigger.getAttribute("data-order-label") || "Vận đơn";
      var recipient = trigger.getAttribute("data-recipient") || "";
      var hasPod = trigger.getAttribute("data-has-pod") === "true";

      if (action) {
        form.setAttribute("action", action);
      }
      if (metaEl) {
        metaEl.textContent = recipient ? orderLabel + " · Người nhận: " + recipient : orderLabel;
      }
      if (existingNote) {
        existingNote.hidden = !hasPod;
      }

      openModal("podModal", trigger);
    });

    fileInput.addEventListener("change", function () {
      showInlineError(errorEl, "");
      revokePodPreview();

      var file = fileInput.files && fileInput.files[0];
      if (!file) {
        if (previewWrap) {
          previewWrap.hidden = true;
        }
        return;
      }

      if (file.size > MAX_POD_BYTES) {
        showInlineError(errorEl, "Ảnh vượt quá giới hạn 5MB. Vui lòng chọn ảnh nhẹ hơn.");
        fileInput.value = "";
        if (previewWrap) {
          previewWrap.hidden = true;
        }
        return;
      }

      var sizeKb = Math.max(1, Math.round(file.size / 1024));
      if (previewInfo) {
        previewInfo.textContent = file.name + " (" + sizeKb + " KB)";
      }

      if (previewImg && window.URL && typeof window.URL.createObjectURL === "function") {
        podPreviewUrl = window.URL.createObjectURL(file);
        previewImg.src = podPreviewUrl;
        if (previewWrap) {
          previewWrap.hidden = false;
        }
      }
    });

    form.addEventListener("submit", function (event) {
      if (form.dataset.submitting === "true") {
        event.preventDefault();
        return;
      }
      var file = fileInput.files && fileInput.files[0];
      if (!file) {
        event.preventDefault();
        showInlineError(errorEl, "Vui lòng chụp hoặc chọn ảnh bằng chứng giao hàng.");
        fileInput.focus();
        return;
      }
      if (file.size > MAX_POD_BYTES) {
        event.preventDefault();
        showInlineError(errorEl, "Ảnh vượt quá 5MB.");
        return;
      }
      form.dataset.submitting = "true";
      if (submitBtn) {
        submitBtn.disabled = true;
      }
    });

    if (modal) {
      modal.addEventListener("close", function () {
        revokePodPreview();
      });
    }
  }

  /* --------------------------------------------------------------------------
     3. Single-Input OTP Modal + Inline Resend Cooldown Timer
     -------------------------------------------------------------------------- */
  function readResendStorage(shipmentId) {
    if (!shipmentId) {
      return 0;
    }
    try {
      var raw = window.sessionStorage.getItem("sw_otp_resend_" + shipmentId);
      var parsed = raw ? parseInt(raw, 10) : 0;
      return isFinite(parsed) ? parsed : 0;
    } catch (_) {
      return 0;
    }
  }

  function writeResendStorage(shipmentId, expiresAtMs) {
    if (!shipmentId) {
      return;
    }
    try {
      window.sessionStorage.setItem("sw_otp_resend_" + shipmentId, String(expiresAtMs));
    } catch (_) {
      // Ignore restricted sessionStorage
    }
  }

  function initOtpModal() {
    var form = document.getElementById("otpModalForm");
    var otpInput = document.getElementById("otpSingleInput");
    var noteInput = document.getElementById("otpDeliveryNote");
    var metaEl = document.getElementById("otpModalMeta");
    var codReminderEl = document.getElementById("otpModalCodReminder");
    var errorEl = document.getElementById("otpModalError");
    var submitBtn = document.getElementById("otpSubmitBtn");
    var resendForm = document.getElementById("resendOtpForm");
    var resendBtn = document.getElementById("resendOtpBtn");
    var resendFeedback = document.getElementById("resendOtpFeedback");
    var modal = document.getElementById("otpModal");
    var activeShipmentId = "";

    if (!form || !otpInput) {
      return;
    }

    function stopCooldownTimer() {
      if (resendTimerId) {
        window.clearInterval(resendTimerId);
        resendTimerId = null;
      }
    }

    function updateCooldownUi(expiresAtMs) {
      if (!resendBtn) {
        return false;
      }
      var remainingSec = Math.ceil((expiresAtMs - Date.now()) / 1000);
      if (remainingSec > 0) {
        resendBtn.disabled = true;
        resendBtn.textContent = "Gửi lại (" + remainingSec + "s)";
        return true;
      }
      resendBtn.disabled = false;
      resendBtn.textContent = "Gửi lại OTP";
      stopCooldownTimer();
      return false;
    }

    function startCooldown(seconds, shipmentId) {
      stopCooldownTimer();
      var expiresAtMs = Date.now() + seconds * 1000;
      if (shipmentId) {
        writeResendStorage(shipmentId, expiresAtMs);
      }
      if (updateCooldownUi(expiresAtMs)) {
        resendTimerId = window.setInterval(function () {
          updateCooldownUi(expiresAtMs);
        }, 1000);
      }
    }

    function computeInitialCooldown(shipmentId, sentAtIso) {
      var now = Date.now();
      var storedExpiry = readResendStorage(shipmentId);
      if (storedExpiry > now) {
        return Math.ceil((storedExpiry - now) / 1000);
      }
      if (sentAtIso) {
        var sentMs = Date.parse(sentAtIso);
        if (!isNaN(sentMs)) {
          var elapsed = Math.floor((now - sentMs) / 1000);
          if (elapsed >= 0 && elapsed < RESEND_COOLDOWN_SECONDS) {
            return RESEND_COOLDOWN_SECONDS - elapsed;
          }
        }
      }
      return 0;
    }

    document.addEventListener("click", function (event) {
      var trigger = event.target.closest(".js-open-otp-modal");
      if (!trigger) {
        return;
      }
      event.preventDefault();
      stopCooldownTimer();

      activeShipmentId = trigger.getAttribute("data-shipment-id") || "";
      var orderLabel = trigger.getAttribute("data-order-label") || "Đơn hàng";
      var recipient = trigger.getAttribute("data-recipient") || "";
      var codReminder = trigger.getAttribute("data-cod-reminder") || "";
      var completeAction = trigger.getAttribute("data-complete-action");
      var resendAction = trigger.getAttribute("data-resend-action");
      var sentAtIso = trigger.getAttribute("data-otp-sent-at") || "";

      if (completeAction) {
        form.setAttribute("action", completeAction);
      }
      if (resendForm && resendAction) {
        resendForm.setAttribute("action", resendAction);
      }
      if (metaEl) {
        metaEl.textContent = recipient ? orderLabel + " · Người nhận: " + recipient : orderLabel;
      }
      if (codReminderEl) {
        codReminderEl.textContent = codReminder;
        codReminderEl.hidden = !codReminder;
      }

      otpInput.value = "";
      if (noteInput) {
        noteInput.value = "";
      }
      showInlineError(errorEl, "");
      if (resendFeedback) {
        resendFeedback.textContent = "";
      }
      form.dataset.submitting = "false";
      if (submitBtn) {
        submitBtn.disabled = false;
      }

      var remaining = computeInitialCooldown(activeShipmentId, sentAtIso);
      if (remaining > 0) {
        startCooldown(remaining, activeShipmentId);
      } else if (resendBtn) {
        resendBtn.disabled = false;
        resendBtn.textContent = "Gửi lại OTP";
      }

      openModal("otpModal", trigger);
    });

    otpInput.addEventListener("input", function () {
      var cleaned = otpInput.value.replace(/\D/g, "").slice(0, 6);
      if (otpInput.value !== cleaned) {
        otpInput.value = cleaned;
      }
      showInlineError(errorEl, "");
    });

    form.addEventListener("submit", function (event) {
      if (form.dataset.submitting === "true") {
        event.preventDefault();
        return;
      }
      var otpVal = (otpInput.value || "").replace(/\D/g, "");
      otpInput.value = otpVal;
      if (!/^\d{6}$/.test(otpVal)) {
        event.preventDefault();
        showInlineError(errorEl, "Vui lòng nhập đủ 6 chữ số mã OTP nhận hàng.");
        otpInput.focus();
        return;
      }
      form.dataset.submitting = "true";
      if (submitBtn) {
        submitBtn.disabled = true;
      }
    });

    if (resendForm && resendBtn) {
      resendForm.addEventListener("submit", function (event) {
        if (typeof window.fetch !== "function") {
          return;
        }
        event.preventDefault();
        if (resendBtn.disabled) {
          return;
        }

        var action = resendForm.getAttribute("action");
        if (!action) {
          return;
        }

        var csrf = getCsrfConfig();
        var headers = { Accept: "text/html" };
        if (csrf.token) {
          headers[csrf.header] = csrf.token;
        }

        resendBtn.disabled = true;
        resendBtn.textContent = "Đang gửi...";
        if (resendFeedback) {
          resendFeedback.textContent = "";
        }

        window
          .fetch(action, {
            method: "POST",
            body: new window.FormData(resendForm),
            headers: headers,
            credentials: "same-origin"
          })
          .then(function (response) {
            return response.text().then(function (htmlText) {
              return { ok: response.ok, htmlText: htmlText };
            });
          })
          .then(function (result) {
            var errorMsg = "";
            if (result.htmlText && typeof window.DOMParser === "function") {
              var doc = new window.DOMParser().parseFromString(result.htmlText, "text/html");
              var badBanner = doc.querySelector(".shipper-alert--bad");
              if (badBanner && badBanner.textContent.trim()) {
                errorMsg = badBanner.textContent.trim();
              }
            }
            if (!result.ok || errorMsg) {
              var msg = errorMsg || "Không thể gửi lại OTP lúc này.";
              if (resendFeedback) {
                resendFeedback.textContent = msg;
              }
              showToast("Gửi lại OTP", msg, "warn");
              resendBtn.disabled = false;
              resendBtn.textContent = "Gửi lại OTP";
              return;
            }

            var okMsg = "Đã gửi mã OTP mới tới email khách hàng.";
            if (resendFeedback) {
              resendFeedback.textContent = okMsg;
            }
            showToast("Đã gửi lại OTP", okMsg, "good");
            startCooldown(RESEND_COOLDOWN_SECONDS, activeShipmentId);
          })
          .catch(function () {
            if (resendFeedback) {
              resendFeedback.textContent = "Lỗi mạng khi gửi lại OTP.";
            }
            showToast("Lỗi kết nối", "Không thể gửi lại OTP. Vui lòng thử lại.", "bad");
            resendBtn.disabled = false;
            resendBtn.textContent = "Gửi lại OTP";
          });
      });
    }

    if (modal) {
      modal.addEventListener("close", function () {
        stopCooldownTimer();
      });
    }
  }

  /* --------------------------------------------------------------------------
     4. Failure Confirmation Modal (Danger Zone Mis-Tap Protection)
     -------------------------------------------------------------------------- */
  function initFailModal() {
    var form = document.getElementById("failModalForm");
    var reasonInput = document.getElementById("failReasonInput");
    var confirmCheck = document.getElementById("failConfirmCheck");
    var metaEl = document.getElementById("failModalMeta");
    var errorEl = document.getElementById("failModalError");
    var submitBtn = document.getElementById("failSubmitBtn");

    if (!form || !reasonInput) {
      return;
    }

    document.addEventListener("click", function (event) {
      var trigger = event.target.closest(".js-open-fail-modal");
      if (trigger) {
        event.preventDefault();
        var action = trigger.getAttribute("data-fail-action");
        var orderLabel = trigger.getAttribute("data-order-label") || "Vận đơn";
        var recipient = trigger.getAttribute("data-recipient") || "";

        if (action) {
          form.setAttribute("action", action);
        }
        if (metaEl) {
          metaEl.textContent =
            (recipient ? orderLabel + " (" + recipient + ")" : orderLabel) +
            " sẽ chuyển sang trạng thái thất bại và hủy mã OTP hiện tại.";
        }
        reasonInput.value = "";
        if (confirmCheck) {
          confirmCheck.checked = false;
        }
        showInlineError(errorEl, "");
        form.dataset.submitting = "false";
        if (submitBtn) {
          submitBtn.disabled = false;
        }
        openModal("failModal", trigger);
        return;
      }

      var preset = event.target.closest(".js-fail-preset");
      if (preset) {
        event.preventDefault();
        var text = preset.getAttribute("data-reason") || preset.textContent || "";
        reasonInput.value = text.trim();
        showInlineError(errorEl, "");
        reasonInput.focus();
      }
    });

    form.addEventListener("submit", function (event) {
      if (form.dataset.submitting === "true") {
        event.preventDefault();
        return;
      }
      if (!reasonInput.value.trim()) {
        event.preventDefault();
        showInlineError(errorEl, "Vui lòng nhập hoặc chọn lý do sự cố.");
        reasonInput.focus();
        return;
      }
      if (confirmCheck && !confirmCheck.checked) {
        event.preventDefault();
        showInlineError(errorEl, "Vui lòng đánh dấu xác nhận trước khi gửi báo cáo sự cố.");
        confirmCheck.focus();
        return;
      }
      form.dataset.submitting = "true";
      if (submitBtn) {
        submitBtn.disabled = true;
      }
    });
  }

  /* --------------------------------------------------------------------------
     5. COD Batch Multi-Select + Live Sum Calculator
     -------------------------------------------------------------------------- */
  function initCodBatchModal() {
    var form = document.getElementById("codBatchForm");
    var checkboxes = document.querySelectorAll(".cod-shipment-checkbox");
    var selectedCountEl = document.getElementById("codSelectedCount");
    var calculatedTotalEl = document.getElementById("codCalculatedTotal");
    var submittedInput = document.getElementById("codSubmittedAmount");
    var syncBtn = document.getElementById("codSyncExpectedBtn");
    var toggleAllBtn = document.getElementById("codToggleAllBtn");
    var errorEl = document.getElementById("codBatchModalError");
    var submitBtn = document.getElementById("codBatchSubmitBtn");
    var openBtn = document.getElementById("openCodBatchModalBtn");

    if (!form) {
      return;
    }

    var currentSum = 0;

    function recalculate() {
      var count = 0;
      var total = 0;
      checkboxes.forEach(function (cb) {
        if (cb.checked) {
          count += 1;
          total += Number(cb.getAttribute("data-cod-amount")) || 0;
        }
      });
      currentSum = total;

      if (selectedCountEl) {
        selectedCountEl.textContent = count + " đơn được chọn";
      }
      if (calculatedTotalEl) {
        calculatedTotalEl.textContent = formatVnd(total);
      }
      if (submittedInput && submittedInput.dataset.manualOverride !== "true") {
        submittedInput.value = String(Math.round(total));
      }
      showInlineError(errorEl, "");
    }

    checkboxes.forEach(function (cb) {
      cb.addEventListener("change", recalculate);
    });

    if (submittedInput) {
      submittedInput.addEventListener("input", function () {
        submittedInput.dataset.manualOverride = "true";
        showInlineError(errorEl, "");
      });
    }

    if (syncBtn && submittedInput) {
      syncBtn.addEventListener("click", function () {
        submittedInput.dataset.manualOverride = "false";
        submittedInput.value = String(Math.round(currentSum));
      });
    }

    if (toggleAllBtn) {
      toggleAllBtn.addEventListener("click", function () {
        var allChecked = checkboxes.length > 0 && Array.prototype.every.call(checkboxes, function (cb) {
          return cb.checked;
        });
        checkboxes.forEach(function (cb) {
          cb.checked = !allChecked;
        });
        recalculate();
      });
    }

    if (openBtn) {
      openBtn.addEventListener("click", function () {
        if (submittedInput) {
          submittedInput.dataset.manualOverride = "false";
        }
        recalculate();
      });
    }

    form.addEventListener("submit", function (event) {
      if (form.dataset.submitting === "true") {
        event.preventDefault();
        return;
      }
      var checkedCount = 0;
      checkboxes.forEach(function (cb) {
        if (cb.checked) {
          checkedCount += 1;
        }
      });
      if (checkboxes.length > 0 && checkedCount === 0) {
        event.preventDefault();
        showInlineError(errorEl, "Vui lòng chọn ít nhất 1 đơn COD để tạo bảng kê.");
        return;
      }
      var amountVal = submittedInput ? Number(submittedInput.value) : 0;
      if (isNaN(amountVal) || amountVal < 0) {
        event.preventDefault();
        showInlineError(errorEl, "Số tiền thực nộp không hợp lệ.");
        if (submittedInput) {
          submittedInput.focus();
        }
        return;
      }
      form.dataset.submitting = "true";
      if (submitBtn) {
        submitBtn.disabled = true;
      }
    });

    recalculate();
  }

  /* --------------------------------------------------------------------------
     6. Inline Action Forms Double-Submit Guard
     -------------------------------------------------------------------------- */
  function initInlineActionForms() {
    document.querySelectorAll(".js-shipper-action-form").forEach(function (form) {
      form.addEventListener("submit", function (event) {
        if (form.dataset.submitting === "true") {
          event.preventDefault();
          return;
        }
        form.dataset.submitting = "true";
        var btn = form.querySelector("button[type='submit']");
        if (btn) {
          btn.disabled = true;
        }
      });
    });
  }

  /* --------------------------------------------------------------------------
     7. Real-Time Shipper SSE (Last-Event-ID + Exponential Backoff, ZERO reload)
     -------------------------------------------------------------------------- */
  function playShipperChime() {
    try {
      var AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) {
        return;
      }
      var ctx = new AudioCtx();
      var osc = ctx.createOscillator();
      var gain = ctx.createGain();
      osc.type = "triangle";
      osc.frequency.setValueAtTime(523.25, ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.18);
      gain.gain.setValueAtTime(0.12, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.35);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.35);
    } catch (_) {
      // Browser autoplay restriction
    }
  }

  function initShipperSse() {
    var phoneContainer = document.querySelector(".shipper-phone");
    var metaEl = document.querySelector('meta[name="shipper-id"]');
    var rawShipperId =
      (phoneContainer && phoneContainer.getAttribute("data-shipper-id")) ||
      (metaEl && metaEl.getAttribute("content")) ||
      "";
    var shipperId = String(rawShipperId).trim();
    var badge = document.getElementById("shipperOnlineBadge");
    var refreshChip = document.getElementById("shipperRefreshChip");
    var refreshLabel = document.getElementById("shipperRefreshLabel");
    var pendingHint = document.getElementById("shipperPendingUpdatesHint");

    if (!/^\d+$/.test(shipperId) || typeof window.EventSource !== "function") {
      return;
    }

    function setBadge(text, stateClass) {
      if (!badge) {
        return;
      }
      badge.textContent = text;
      badge.className = "status " + stateClass;
    }

    function recordIncomingUpdate(event, title, defaultMsg) {
      if (event && event.lastEventId) {
        sseLastEventId = String(event.lastEventId);
      }

      var payload = {};
      if (event && event.data) {
        try {
          payload = JSON.parse(event.data);
        } catch (_) {
          payload = {};
        }
      }

      pendingSseUpdates += 1;
      if (refreshChip) {
        refreshChip.classList.add("is-highlighted");
        refreshChip.setAttribute("data-pending-count", String(pendingSseUpdates));
      }
      if (refreshLabel) {
        refreshLabel.textContent = "Làm mới danh sách (" + pendingSseUpdates + " mới)";
      }
      if (pendingHint) {
        pendingHint.hidden = false;
      }

      var msg = (payload && payload.message) ? String(payload.message) : defaultMsg;
      playShipperChime();
      showToast(title, msg, "wine");
    }

    function buildStreamUrl() {
      var baseUrl = "/api/live/shipper/" + encodeURIComponent(shipperId) + "/stream";
      if (sseLastEventId) {
        return baseUrl + "?lastEventId=" + encodeURIComponent(sseLastEventId);
      }
      return baseUrl;
    }

    function scheduleReconnect() {
      if (sseClosed) {
        return;
      }
      var delay = sseRetryDelay;
      sseRetryDelay = Math.min(sseRetryDelay * 2, SSE_MAX_DELAY_MS);
      window.clearTimeout(sseRetryTimerId);
      sseRetryTimerId = window.setTimeout(connect, delay);
    }

    function connect() {
      if (sseClosed) {
        return;
      }
      try {
        var source = new window.EventSource(buildStreamUrl());
        sseSource = source;

        source.addEventListener("init", function () {
          sseRetryDelay = SSE_INITIAL_DELAY_MS;
          setBadge("\u25cf Trực tuyến", "good");
        });

        source.addEventListener("heartbeat", function () {
          setBadge("\u25cf Trực tuyến", "good");
        });

        source.addEventListener("new_shipment_assigned", function (event) {
          recordIncomingUpdate(
            event,
            "Chuyến giao mới",
            "Bạn vừa được điều phối một đơn hàng mới. Nhấn Làm mới danh sách khi sẵn sàng."
          );
        });

        source.addEventListener("shipment_step_updated", function (event) {
          recordIncomingUpdate(
            event,
            "Cập nhật vận đơn",
            "Trạng thái vận đơn vừa được cập nhật từ hệ thống."
          );
        });

        source.addEventListener("cod_batch_updated", function (event) {
          recordIncomingUpdate(
            event,
            "Đối soát bảng kê COD",
            "Bảng kê COD của bạn vừa có kết quả đối soát mới."
          );
        });

        source.onerror = function () {
          source.close();
          if (sseSource === source) {
            sseSource = null;
          }
          if (sseClosed) {
            return;
          }
          setBadge("\u25cb Đang kết nối lại...", "warn");
          scheduleReconnect();
        };
      } catch (_) {
        setBadge("\u25cb Ngoại tuyến", "bad");
        scheduleReconnect();
      }
    }

    window.addEventListener(
      "pagehide",
      function () {
        sseClosed = true;
        window.clearTimeout(sseRetryTimerId);
        if (sseSource) {
          sseSource.close();
          sseSource = null;
        }
      },
      { once: true }
    );

    connect();
  }

  function init() {
    initFilterTabs();
    initPodModal();
    initOtpModal();
    initFailModal();
    initCodBatchModal();
    initInlineActionForms();
    initShipperSse();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})(window, document);
