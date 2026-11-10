// AANO Windows Desktop App - Custom Native Modals (Zero Browser Popups)
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

// --------------------- Native Custom Modals (No Browser Popups) ---------------------
function showNativeConfirm({ title = "Confirm", message = "Are you sure?", confirmText = "Confirm", cancelText = "Cancel", danger = true } = {}) {
  return new Promise((resolve) => {
    const modal = document.getElementById("nativeConfirmModal");
    const titleEl = document.getElementById("nativeConfirmTitle");
    const msgEl = document.getElementById("nativeConfirmMessage");
    const btnOk = document.getElementById("btnNativeConfirmOk");
    const btnCancel = document.getElementById("btnNativeConfirmCancel");

    titleEl.innerText = title;
    msgEl.innerText = message;
    btnOk.innerText = confirmText;
    btnCancel.innerText = cancelText;

    if (danger) {
      btnOk.className = "btn-danger-filled";
    } else {
      btnOk.className = "btn-primary";
    }

    const cleanup = () => {
      modal.classList.add("hidden");
      btnOk.removeEventListener("click", onOk);
      btnCancel.removeEventListener("click", onCancel);
      document.removeEventListener("keydown", onKeyDown);
    };

    const onOk = () => { cleanup(); resolve(true); };
    const onCancel = () => { cleanup(); resolve(false); };
    const onKeyDown = (e) => {
      if (e.key === "Escape") { cleanup(); resolve(false); }
      if (e.key === "Enter") { cleanup(); resolve(true); }
    };

    btnOk.addEventListener("click", onOk);
    btnCancel.addEventListener("click", onCancel);
    document.addEventListener("keydown", onKeyDown);

    modal.classList.remove("hidden");
    btnOk.focus();
  });
}

function showNativePrompt({ title = "Enter Value", message = "", placeholder = "", defaultValue = "", confirmText = "OK", cancelText = "Cancel" } = {}) {
  return new Promise((resolve) => {
    const modal = document.getElementById("nativePromptModal");
    const titleEl = document.getElementById("nativePromptTitle");
    const msgEl = document.getElementById("nativePromptMessage");
    const inputEl = document.getElementById("nativePromptInput");
    const btnOk = document.getElementById("btnNativePromptOk");
    const btnCancel = document.getElementById("btnNativePromptCancel");

    titleEl.innerText = title;
    msgEl.innerText = message;
    inputEl.placeholder = placeholder;
    inputEl.value = defaultValue;
    btnOk.innerText = confirmText;
    btnCancel.innerText = cancelText;

    const cleanup = () => {
      modal.classList.add("hidden");
      btnOk.removeEventListener("click", onOk);
      btnCancel.removeEventListener("click", onCancel);
      document.removeEventListener("keydown", onKeyDown);
    };

    const onOk = () => {
      const val = inputEl.value.trim();
      cleanup();
      resolve(val.length > 0 ? val : null);
    };
    const onCancel = () => { cleanup(); resolve(null); };
    const onKeyDown = (e) => {
      if (e.key === "Escape") { cleanup(); resolve(null); }
      if (e.key === "Enter") { onOk(); }
    };

    btnOk.addEventListener("click", onOk);
    btnCancel.addEventListener("click", onCancel);
    document.addEventListener("keydown", onKeyDown);

    modal.classList.remove("hidden");
    setTimeout(() => { inputEl.focus(); inputEl.select(); }, 50);
  });
}

function showNativeAlert({ title = "Notice", message = "", okText = "OK" } = {}) {
  return new Promise((resolve) => {
    const modal = document.getElementById("nativeAlertModal");
    const titleEl = document.getElementById("nativeAlertTitle");
    const msgEl = document.getElementById("nativeAlertMessage");
    const btnOk = document.getElementById("btnNativeAlertOk");

    titleEl.innerText = title;
    msgEl.innerText = message;
    btnOk.innerText = okText;

    const cleanup = () => {
      modal.classList.add("hidden");
      btnOk.removeEventListener("click", onOk);
      document.removeEventListener("keydown", onKeyDown);
    };

    const onOk = () => { cleanup(); resolve(); };
    const onKeyDown = (e) => {
      if (e.key === "Escape" || e.key === "Enter") { cleanup(); resolve(); }
    };

    btnOk.addEventListener("click", onOk);
    document.addEventListener("keydown", onKeyDown);

    modal.classList.remove("hidden");
    btnOk.focus();
  });
}
