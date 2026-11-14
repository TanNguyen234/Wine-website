(function (window, document) {
  "use strict";

  var TOAST_REGION_ID = "swToastRegion";
  var FOCUSABLE_SELECTOR = [
    "[data-dialog-autofocus]",
    "button:not([disabled])",
    "a[href]",
    "input:not([disabled]):not([type='hidden'])",
    "select:not([disabled])",
    "textarea:not([disabled])",
    "[tabindex]:not([tabindex='-1'])"
  ].join(",");

  var openerMap = new WeakMap();

  function isDialogElement(el) {
    return Boolean(el && el.tagName === "DIALOG");
  }

  function focusInitial(dialog) {
    var target = dialog.querySelector(FOCUSABLE_SELECTOR);
    if (target && typeof target.focus === "function") {
      target.focus({ preventScroll: true });
      return;
    }
    if (!dialog.hasAttribute("tabindex")) {
      dialog.setAttribute("tabindex", "-1");
    }
    if (typeof dialog.focus === "function") {
      dialog.focus({ preventScroll: true });
    }
  }

  function restoreOpenerFocus(dialog) {
    var opener = openerMap.get(dialog);
    openerMap.delete(dialog);
    if (opener && typeof opener.focus === "function" && document.contains(opener)) {
      opener.focus({ preventScroll: true });
    }
  }

  function openDialog(targetOrId, triggerEl) {
    var dialog =
      typeof targetOrId === "string"
        ? document.getElementById(targetOrId)
        : targetOrId;
    if (!dialog) {
      return null;
    }

    var opener = triggerEl || document.activeElement;
    if (opener && opener !== document.body) {
      openerMap.set(dialog, opener);
    }

    if (isDialogElement(dialog)) {
      if (!dialog.open) {
        if (typeof dialog.showModal === "function") {
          dialog.showModal();
        } else {
          dialog.setAttribute("open", "");
        }
      }
      focusInitial(dialog);
      return dialog;
    }

    dialog.classList.add("open", "show");
    dialog.setAttribute("aria-hidden", "false");
    focusInitial(dialog);
    return dialog;
  }

  function closeDialog(targetOrId) {
    var dialog =
      typeof targetOrId === "string"
        ? document.getElementById(targetOrId)
        : targetOrId;
    if (!dialog) {
      return;
    }

    if (isDialogElement(dialog)) {
      if (dialog.open) {
        if (typeof dialog.close === "function") {
          dialog.close();
        } else {
          dialog.removeAttribute("open");
          restoreOpenerFocus(dialog);
        }
      }
      return;
    }

    dialog.classList.remove("open", "show");
    dialog.setAttribute("aria-hidden", "true");
    restoreOpenerFocus(dialog);
  }

  function isBackdropClickOutsideDialog(dialog, event) {
    if (event.target !== dialog) {
      return false;
    }
    if (event.clientX === 0 && event.clientY === 0) {
      return false;
    }
    var rect = dialog.getBoundingClientRect();
    return (
      event.clientX < rect.left ||
      event.clientX > rect.right ||
      event.clientY < rect.top ||
      event.clientY > rect.bottom
    );
  }

  function wireDialogElement(dialog) {
    if (!isDialogElement(dialog) || dialog.dataset.swDialogWired === "true") {
      return;
    }
    dialog.dataset.swDialogWired = "true";

    dialog.addEventListener("close", function () {
      restoreOpenerFocus(dialog);
    });

    dialog.addEventListener("click", function (event) {
      var closedby = dialog.getAttribute("closedby");
      if (closedby === "none") {
        return;
      }
      if (closedby === "any" || !closedby) {
        if (isBackdropClickOutsideDialog(dialog, event)) {
          closeDialog(dialog);
        }
      }
    });
  }

  function wireLegacyClassSync() {
    var cartDrawer = document.getElementById("cartDrawer");
    if (cartDrawer && isDialogElement(cartDrawer)) {
      var syncingCart = false;
      var cartObserver = new MutationObserver(function () {
        if (syncingCart) {
          return;
        }
        syncingCart = true;
        var hasOpenClass = cartDrawer.classList.contains("open");
        if (hasOpenClass && !cartDrawer.open) {
          openDialog(cartDrawer);
        } else if (!hasOpenClass && cartDrawer.open) {
          closeDialog(cartDrawer);
        }
        syncingCart = false;
      });
      cartObserver.observe(cartDrawer, { attributes: true, attributeFilter: ["class"] });

      cartDrawer.addEventListener("close", function () {
        if (syncingCart) {
          return;
        }
        syncingCart = true;
        cartDrawer.classList.remove("open");
        syncingCart = false;
      });
    }

    var aiPanel = document.getElementById("aiPanel");
    if (aiPanel && isDialogElement(aiPanel)) {
      var syncingAi = false;
      var aiObserver = new MutationObserver(function () {
        if (syncingAi) {
          return;
        }
        syncingAi = true;
        var hasShowClass = aiPanel.classList.contains("show");
        if (hasShowClass && !aiPanel.open) {
          if (typeof aiPanel.show === "function") {
            aiPanel.show();
          } else {
            aiPanel.setAttribute("open", "");
          }
          focusInitial(aiPanel);
        } else if (!hasShowClass && aiPanel.open) {
          closeDialog(aiPanel);
        }
        syncingAi = false;
      });
      aiObserver.observe(aiPanel, { attributes: true, attributeFilter: ["class"] });

      aiPanel.addEventListener("close", function () {
        if (syncingAi) {
          return;
        }
        syncingAi = true;
        aiPanel.classList.remove("show");
        syncingAi = false;
      });
    }

    var adminLiveToast = document.getElementById("adminLiveToast");
    if (adminLiveToast && adminLiveToast.hasAttribute("popover")) {
      var toastObserver = new MutationObserver(function () {
        var shouldShow =
          adminLiveToast.classList.contains("show") &&
          !adminLiveToast.classList.contains("d-none");
        if (shouldShow) {
          adminLiveToast.setAttribute("data-popover-open", "true");
          if (typeof adminLiveToast.showPopover === "function") {
            try {
              if (!adminLiveToast.matches(":popover-open")) {
                adminLiveToast.showPopover();
              }
            } catch (_) {
              // Popover state already active or unsupported
            }
          }
        } else {
          adminLiveToast.removeAttribute("data-popover-open");
          if (typeof adminLiveToast.hidePopover === "function") {
            try {
              if (adminLiveToast.matches(":popover-open")) {
                adminLiveToast.hidePopover();
              }
            } catch (_) {
              // Popover already closed
            }
          }
        }
      });
      toastObserver.observe(adminLiveToast, {
        attributes: true,
        attributeFilter: ["class"]
      });
    }
  }

  function ensureToastRegion() {
    var region = document.getElementById(TOAST_REGION_ID);
    if (!region) {
      region = document.createElement("div");
      region.id = TOAST_REGION_ID;
      region.className = "sw-toast-region";
      region.setAttribute("popover", "manual");
      region.setAttribute("role", "status");
      region.setAttribute("aria-live", "polite");
      if (document.body) {
        document.body.appendChild(region);
      }
    }
    return region;
  }

  function normalizeToastTone(tone) {
    switch (tone) {
      case "ok":
      case "good":
      case "success":
        return "good";
      case "warn":
      case "warning":
        return "warn";
      case "bad":
      case "danger":
      case "error":
        return "bad";
      case "wine":
        return "wine";
      case "info":
        return "info";
      default:
        return "neutral";
    }
  }

  function showToast(options) {
    var opts = typeof options === "string" ? { message: options } : options || {};
    var region = ensureToastRegion();
    if (!region) {
      return null;
    }

    var tone = normalizeToastTone(opts.tone);
    var card = document.createElement("div");
    card.className = "sw-toast sw-toast-card sw-toast-card--" + tone;
    card.setAttribute("data-tone", tone);
    card.setAttribute("role", tone === "bad" ? "alert" : "status");

    var headerRow = document.createElement("div");
    headerRow.style.display = "flex";
    headerRow.style.alignItems = "center";
    headerRow.style.justifyContent = "space-between";
    headerRow.style.gap = "0.75rem";

    var titleEl = document.createElement("strong");
    titleEl.className = "sw-toast-title";
    titleEl.textContent = opts.title ? String(opts.title) : "StrongWine";

    var dismissBtn = document.createElement("button");
    dismissBtn.type = "button";
    dismissBtn.className = "sw-toast-close";
    dismissBtn.setAttribute("aria-label", "Đóng thông báo");
    dismissBtn.textContent = "×";
    dismissBtn.style.background = "transparent";
    dismissBtn.style.border = "none";
    dismissBtn.style.color = "inherit";
    dismissBtn.style.cursor = "pointer";
    dismissBtn.style.fontSize = "1.1rem";
    dismissBtn.style.lineHeight = "1";

    headerRow.appendChild(titleEl);
    headerRow.appendChild(dismissBtn);
    card.appendChild(headerRow);

    if (opts.message) {
      var bodyEl = document.createElement("div");
      bodyEl.className = "sw-toast-body";
      bodyEl.textContent = String(opts.message);
      card.appendChild(bodyEl);
    }

    function dismiss() {
      if (card.parentNode === region) {
        region.removeChild(card);
      }
      if (region.children.length === 0) {
        region.removeAttribute("data-popover-open");
        if (typeof region.hidePopover === "function") {
          try {
            if (region.matches(":popover-open")) {
              region.hidePopover();
            }
          } catch (_) {
            // Ignore popover API errors
          }
        }
      }
    }

    dismissBtn.addEventListener("click", dismiss);
    region.appendChild(card);
    region.setAttribute("data-popover-open", "true");

    if (typeof region.showPopover === "function") {
      try {
        if (!region.matches(":popover-open")) {
          region.showPopover();
        }
      } catch (_) {
        // Ignore if browser lacks popover support; CSS fallback handles visibility
      }
    }

    var duration = typeof opts.duration === "number" ? opts.duration : 4500;
    if (duration > 0) {
      window.setTimeout(dismiss, duration);
    }
    return card;
  }

  function initDialogKit() {
    document.querySelectorAll("dialog").forEach(wireDialogElement);
    wireLegacyClassSync();

    document.addEventListener("click", function (event) {
      var openBtn = event.target.closest("[data-dialog-open]");
      if (openBtn) {
        var targetId = openBtn.getAttribute("data-dialog-open");
        if (targetId) {
          event.preventDefault();
          var targetDialog = document.getElementById(targetId);
          if (targetDialog) {
            wireDialogElement(targetDialog);
            openDialog(targetDialog, openBtn);
          }
        }
        return;
      }

      var closeBtn = event.target.closest("[data-dialog-close]");
      if (closeBtn) {
        var closeTargetId = closeBtn.getAttribute("data-dialog-close");
        var dialogToClose = closeTargetId
          ? document.getElementById(closeTargetId)
          : closeBtn.closest("dialog, .modal");
        if (dialogToClose) {
          event.preventDefault();
          closeDialog(dialogToClose);
        }
        return;
      }

      var bsDismiss = event.target.closest("[data-bs-dismiss='modal']");
      if (bsDismiss && !window.bootstrap) {
        var legacyModal = bsDismiss.closest(".modal");
        if (legacyModal) {
          event.preventDefault();
          closeDialog(legacyModal);
        }
      }
    });

    document.addEventListener("keydown", function (event) {
      if (event.key !== "Escape") {
        return;
      }
      var openLegacyModal = document.querySelector(".modal.open, .modal.show");
      if (openLegacyModal && !window.bootstrap) {
        closeDialog(openLegacyModal);
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initDialogKit);
  } else {
    initDialogKit();
  }

  window.SwDialog = {
    open: openDialog,
    close: closeDialog,
    wire: wireDialogElement
  };

  window.SwToast = {
    show: showToast
  };
})(window, document);
