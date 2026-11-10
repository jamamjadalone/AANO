// AANO Windows Desktop App - Inactivity Auto-Lock & Vault Keypad
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

var AppLockManager = (function() {
  let pinBuffer = "";
  let lockSettings = { pin_enabled: false, timeout_minutes: 5 };
  let inactivityTimer = null;
  let lockoutTimer = null;
  let isAppLocked = false;

  async function init() {
    try {
      if (window.pywebview && window.pywebview.api && window.pywebview.api.getLockSettings) {
        lockSettings = await window.pywebview.api.getLockSettings();
        if (lockSettings.pin_enabled) {
          startInactivityWatcher();
          if (lockSettings.is_locked_out) {
            lockNow();
          }
        }
      }
    } catch (err) {
      console.warn("AppLock initialization warning:", err);
    }
  }

  function startInactivityWatcher() {
    resetInactivityTimer();
    const events = ["mousemove", "mousedown", "keydown", "wheel", "touchstart"];
    events.forEach(evt => {
      window.addEventListener(evt, onUserActivity, { passive: true });
    });
  }

  function onUserActivity() {
    if (isAppLocked) return;
    resetInactivityTimer();
  }

  function resetInactivityTimer() {
    if (inactivityTimer) clearTimeout(inactivityTimer);
    if (!lockSettings.pin_enabled || lockSettings.timeout_minutes <= 0) return;

    const ms = lockSettings.timeout_minutes * 60 * 1000;
    inactivityTimer = setTimeout(() => {
      lockNow();
    }, ms);
  }

  function lockNow() {
    if (!lockSettings.pin_enabled) return;
    isAppLocked = true;
    pinBuffer = "";
    updateDots();
    setErrorMessage("");

    const overlay = document.getElementById("lockOverlay");
    if (overlay) {
      overlay.classList.add("active");
    }
    checkLockoutState();
  }

  async function checkLockoutState() {
    if (!window.pywebview || !window.pywebview.api) return;
    try {
      const state = await window.pywebview.api.getLockSettings();
      if (state.is_locked_out && state.lockout_seconds_remaining > 0) {
        startLockoutCountdown(state.lockout_seconds_remaining);
      }
    } catch (e) {
      console.warn("Error checking lockout state:", e);
    }
  }

  function startLockoutCountdown(seconds) {
    if (lockoutTimer) clearInterval(lockoutTimer);
    setErrorMessage(`Too many failed attempts. Try again in ${seconds}s`);
    disableKeypad(true);

    let rem = seconds;
    lockoutTimer = setInterval(() => {
      rem--;
      if (rem <= 0) {
        clearInterval(lockoutTimer);
        lockoutTimer = null;
        disableKeypad(false);
        setErrorMessage("");
      } else {
        setErrorMessage(`Too many failed attempts. Try again in ${rem}s`);
      }
    }, 1000);
  }

  function disableKeypad(disabled) {
    const keys = document.querySelectorAll("#lockOverlay .lock-key");
    keys.forEach(k => {
      k.disabled = disabled;
      k.style.opacity = disabled ? "0.4" : "1";
    });
  }

  function enterDigit(digit) {
    if (lockoutTimer || pinBuffer.length >= 8) return;
    pinBuffer += digit;
    updateDots();
    setErrorMessage("");
    if (pinBuffer.length >= 4) {
      submitPin();
    }
  }

  function backspace() {
    if (lockoutTimer || pinBuffer.length === 0) return;
    pinBuffer = pinBuffer.slice(0, -1);
    updateDots();
    setErrorMessage("");
  }

  function clearAll() {
    if (lockoutTimer) return;
    pinBuffer = "";
    updateDots();
    setErrorMessage("");
  }

  function updateDots() {
    const dots = document.querySelectorAll("#lockOverlay .lock-dot");
    dots.forEach((dot, idx) => {
      if (idx < pinBuffer.length) {
        dot.classList.add("filled");
      } else {
        dot.classList.remove("filled");
      }
    });
  }

  function setErrorMessage(msg) {
    const el = document.getElementById("lockErrorMsg");
    if (el) el.textContent = msg;
  }

  async function submitPin() {
    if (!pinBuffer) return;
    try {
      const res = await window.pywebview.api.verifyLockPin(pinBuffer);
      if (res.success) {
        unlockSuccess();
      } else {
        pinBuffer = "";
        updateDots();
        shakeCard();
        if (res.locked_out) {
          startLockoutCountdown(res.lockout_seconds || 30);
        } else {
          setErrorMessage(res.error || "Incorrect PIN");
        }
      }
    } catch (err) {
      setErrorMessage("Verification error: " + err);
    }
  }

  function unlockSuccess() {
    isAppLocked = false;
    pinBuffer = "";
    updateDots();
    setErrorMessage("");
    const overlay = document.getElementById("lockOverlay");
    if (overlay) overlay.classList.remove("active");
    resetInactivityTimer();
  }

  function shakeCard() {
    const card = document.querySelector("#lockOverlay .lock-card");
    if (!card) return;
    card.style.animation = "none";
    card.offsetHeight; // reflow
    card.style.animation = "shake 0.35s cubic-bezier(0.36, 0.07, 0.19, 0.97) both";
  }

  window.addEventListener("keydown", (e) => {
    if (!isAppLocked) return;
    if (e.key >= "0" && e.key <= "9") {
      enterDigit(e.key);
    } else if (e.key === "Backspace") {
      backspace();
    } else if (e.key === "Escape") {
      clearAll();
    }
  });

  return {
    init,
    lockNow,
    enterDigit,
    backspace,
    clearAll,
    getSettings: () => lockSettings,
    refreshSettings: async () => {
      if (window.pywebview && window.pywebview.api) {
        lockSettings = await window.pywebview.api.getLockSettings();
      }
    }
  };
})();

window.lockAppNow = function() {
  AppLockManager.lockNow();
};

window.addEventListener("pywebviewready", () => {
  AppLockManager.init();
});
