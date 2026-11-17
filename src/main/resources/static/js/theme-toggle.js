(function (window, document) {
  "use strict";

  var STORAGE_KEY = "sw_theme";
  var DEFAULT_THEME = "cellar";
  var VALID_THEMES = { cellar: true, ivory: true };

  function normalizeTheme(value) {
    return VALID_THEMES[value] ? value : DEFAULT_THEME;
  }

  function readStoredTheme() {
    try {
      var saved = window.localStorage.getItem(STORAGE_KEY);
      if (VALID_THEMES[saved]) {
        return saved;
      }
    } catch (_) {
      // Ignore storage failures in restricted contexts
    }
    var current = document.documentElement.getAttribute("data-theme");
    return normalizeTheme(current);
  }

  function syncMetaColorScheme(theme) {
    var meta = document.querySelector('meta[name="color-scheme"]');
    if (!meta) {
      meta = document.createElement("meta");
      meta.setAttribute("name", "color-scheme");
      if (document.head) {
        document.head.appendChild(meta);
      }
    }
    if (meta) {
      meta.setAttribute("content", theme === "ivory" ? "light dark" : "dark light");
    }
  }

  function syncButtons(theme) {
    var isIvory = theme === "ivory";
    var nextLabel = isIvory
      ? "Chuyển sang giao diện tối Cellar"
      : "Chuyển sang giao diện sáng Ivory";
    var shortLabel = isIvory ? "Sáng Ivory" : "Tối Cellar";

    var buttons = document.querySelectorAll("[data-theme-toggle]");
    buttons.forEach(function (btn) {
      btn.setAttribute("aria-pressed", isIvory ? "true" : "false");
      btn.setAttribute("aria-label", nextLabel);
      btn.setAttribute("title", nextLabel);
      btn.setAttribute("data-current-theme", theme);

      var sunIcon = btn.querySelector('[data-theme-icon="sun"]');
      var moonIcon = btn.querySelector('[data-theme-icon="moon"]');
      if (sunIcon) {
        sunIcon.hidden = isIvory;
      }
      if (moonIcon) {
        moonIcon.hidden = !isIvory;
      }

      var labelEl = btn.querySelector("[data-theme-label]");
      if (labelEl) {
        labelEl.textContent = shortLabel;
      }
    });
  }

  function applyTheme(theme, persist) {
    var resolved = normalizeTheme(theme);
    document.documentElement.setAttribute("data-theme", resolved);
    if (document.body) {
      document.body.setAttribute("data-theme", resolved);
    }
    if (persist) {
      try {
        window.localStorage.setItem(STORAGE_KEY, resolved);
      } catch (_) {
        // Ignore storage quota / privacy mode errors
      }
    }
    syncMetaColorScheme(resolved);
    syncButtons(resolved);
    document.dispatchEvent(
      new CustomEvent("sw:themechange", { detail: { theme: resolved } })
    );
    return resolved;
  }

  function getTheme() {
    return normalizeTheme(document.documentElement.getAttribute("data-theme"));
  }

  function toggleTheme() {
    var current = getTheme();
    return applyTheme(current === "cellar" ? "ivory" : "cellar", true);
  }

  function bindControls() {
    var activeTheme = getTheme();
    if (document.body) {
      document.body.setAttribute("data-theme", activeTheme);
    }
    syncButtons(activeTheme);
    document.addEventListener("click", function (event) {
      var trigger = event.target.closest("[data-theme-toggle]");
      if (!trigger) {
        return;
      }
      event.preventDefault();
      toggleTheme();
    });
  }

  applyTheme(readStoredTheme(), false);

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bindControls);
  } else {
    bindControls();
  }

  window.SwTheme = {
    getTheme: getTheme,
    setTheme: function (theme) {
      return applyTheme(theme, true);
    },
    toggleTheme: toggleTheme,
    syncButtons: function () {
      syncButtons(getTheme());
    }
  };
})(window, document);
