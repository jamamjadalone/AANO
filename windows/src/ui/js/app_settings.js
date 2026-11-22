// AANO Windows Desktop App - Settings Modal Logic
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines)

async function openSettingsModal() {
  const settingsModal = document.getElementById("settingsModal");
  if (!settingsModal) return;
  settingsModal.classList.remove("hidden");
  await updateThemeToggleFromApi();
}

async function updateThemeToggleFromApi() {
  const themeToggleCheckbox = document.getElementById("themeToggleCheckbox");
  try {
    const api = getApi();
    if (api && api.get_theme) {
      const theme = await api.get_theme();
      if (themeToggleCheckbox) {
        themeToggleCheckbox.checked = theme === "dark";
      }
    } else {
      if (themeToggleCheckbox) {
        const saved = localStorage.getItem("aano_theme");
        themeToggleCheckbox.checked = saved === "dark";
      }
    }
  } catch (e) {
    console.warn("Failed to load theme from API, using localStorage:", e);
    if (themeToggleCheckbox) {
      const saved = localStorage.getItem("aano_theme");
      themeToggleCheckbox.checked = saved === "dark";
    }
  }
}

async function handleSettingsModalOpen() {
  await openSettingsModal();
}

async function handleThemeToggleChange() {
  const themeToggleCheckbox = document.getElementById("themeToggleCheckbox");
  const isChecked = themeToggleCheckbox.checked;
  try {
    const api = getApi();
    if (api && api.set_theme) {
      const newTheme = isChecked ? "dark" : "light";
      await api.set_theme(newTheme);
      applyTheme(newTheme);
    } else {
      applyTheme(isChecked ? "dark" : "light");
    }
  } catch (e) {
    console.warn("Failed to set theme via API, using local applyTheme:", e);
    applyTheme(isChecked ? "dark" : "light");
  }
}