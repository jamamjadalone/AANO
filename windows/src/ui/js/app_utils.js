// AANO Windows Desktop App - Formatting, Color & Theme Utilities
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

async function loadCustomColorLabels() {
  try {
    if (window.pywebview && window.pywebview.api && window.pywebview.api.get_color_labels) {
      customColorLabels = await window.pywebview.api.get_color_labels() || {};
    } else {
      const stored = localStorage.getItem("aano_color_labels");
      customColorLabels = stored ? JSON.parse(stored) : {};
    }
  } catch (_e) {
    const stored = localStorage.getItem("aano_color_labels");
    try {
      customColorLabels = stored ? JSON.parse(stored) : {};
    } catch (_ex) {
      customColorLabels = {};
    }
  }
}

function getColorLabel(idx, defaultLabel) {
  const key = String(idx);
  if (customColorLabels && customColorLabels[key]) {
    return customColorLabels[key];
  }
  return defaultLabel || PALETTE_LABELS[idx] || `Color ${idx}`;
}

async function saveCustomColorLabel(idx, newLabel) {
  const clean = (newLabel || "").trim();
  const key = String(idx);
  if (clean) {
    customColorLabels[key] = clean;
  } else {
    delete customColorLabels[key];
  }
  try {
    localStorage.setItem("aano_color_labels", JSON.stringify(customColorLabels));
  } catch (err) {
    console.warn("Failed to cache color labels in localStorage:", err);
  }
  try {
    if (window.pywebview && window.pywebview.api && window.pywebview.api.set_color_label) {
      await window.pywebview.api.set_color_label(idx, clean);
    }
  } catch (err) {
    console.warn("Failed to persist color label via API:", err);
  }
  renderPaletteFilterBar();
  renderNotes();
  renderColorPalette();
}

async function promptRenameColor(idx) {
  const def = PALETTE_LABELS[idx] || `Color ${idx}`;
  const current = getColorLabel(idx, def);
  const result = await showNativePrompt({
    title: "Rename Color Label",
    message: `Set a custom category or name for "${def}":`,
    placeholder: def,
    defaultValue: current !== def ? current : "",
    confirmText: "Save",
    cancelText: "Cancel"
  });
  if (result !== null) {
    await saveCustomColorLabel(idx, result);
  }
}

function isCurrentThemeDark() {
  return document.documentElement.getAttribute("data-theme") === "dark";
}

function resolveNoteColor(colorVal) {
  const isDark = isCurrentThemeDark();
  const palette = isDark ? PALETTE_DARK : PALETTE_LIGHT;
  const defaultBg = isDark ? "#161B22" : "#FFFFFF";

  if (colorVal === undefined || colorVal === null) return defaultBg;
  if (typeof colorVal === "string" && colorVal.startsWith("#")) {
    if (isDark && (colorVal.toUpperCase() === "#FFFFFF" || colorVal === "#00000000")) return defaultBg;
    if (!isDark && (colorVal.toUpperCase() === "#161B22" || colorVal.toUpperCase() === "#182234")) return "#FFFFFF";
    return colorVal;
  }
  if (typeof colorVal === "number") {
    if (colorVal === 0 || colorVal === 0x00000000) return defaultBg;
    if (colorVal >= 0 && colorVal < palette.length) {
      return palette[colorVal];
    }
    const unsigned = (colorVal >>> 0).toString(16).padStart(8, "0");
    return "#" + unsigned.slice(2);
  }
  return defaultBg;
}

function hexToColorInt(hex) {
  if (!hex.startsWith("#")) hex = "#" + hex;
  const num = parseInt(hex.slice(1), 16);
  return ((0xFF000000 | num) >> 0);
}

function isDarkColor(hexColor) {
  if (!hexColor || !hexColor.startsWith("#")) return false;
  let hex = hexColor.slice(1);
  if (hex.length === 3) hex = hex.split("").map(c => c + c).join("");
  const r = parseInt(hex.substr(0, 2), 16) || 0;
  const g = parseInt(hex.substr(2, 2), 16) || 0;
  const b = parseInt(hex.substr(4, 2), 16) || 0;
  const yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000;
  return yiq < 128;
}

// Theme Engine (Dark / Light)
function applyTheme(theme) {
  const root = document.documentElement;
  const sun = document.getElementById("themeIconSun");
  const moon = document.getElementById("themeIconMoon");
  if (theme === "dark") {
    root.setAttribute("data-theme", "dark");
    if (sun) sun.classList.remove("hidden");
    if (moon) moon.classList.add("hidden");
  } else {
    root.removeAttribute("data-theme");
    if (sun) sun.classList.add("hidden");
    if (moon) moon.classList.remove("hidden");
  }
  localStorage.setItem("aano_theme", theme);
  renderNotes();
  renderColorPalette();
  if (document.getElementById("editorModal") && !document.getElementById("editorModal").classList.contains("hidden")) {
    updateEditorBackgroundColor();
  }
}

function updateEditorBackgroundColor() {
  const resolvedBg = resolveNoteColor(editingColor);
  applyEditorColor(resolvedBg);
}

function initTheme() {
  const saved = localStorage.getItem("aano_theme");
  if (saved) {
    applyTheme(saved);
  } else {
    applyTheme("light");
  }
}

function toggleTheme() {
  const isDark = isCurrentThemeDark();
  applyTheme(isDark ? "light" : "dark");
}

// --------------------- Utility Helpers ---------------------
function stripHtml(html) {
  if (!html) return "";
  const doc = new DOMParser().parseFromString(html, "text/html");
  return doc.body.textContent || "";
}

function escapeHtml(text) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function formatDate(ms) {
  if (!ms) return "";
  const d = new Date(ms);
  const now = new Date();
  const timeStr = d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  const isToday = d.getFullYear() === now.getFullYear() &&
                  d.getMonth() === now.getMonth() &&
                  d.getDate() === now.getDate();
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const isYesterday = d.getFullYear() === yesterday.getFullYear() &&
                      d.getMonth() === yesterday.getMonth() &&
                      d.getDate() === yesterday.getDate();

  if (isToday) return `Today, ${timeStr}`;
  if (isYesterday) return `Yesterday, ${timeStr}`;
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  if (d.getFullYear() === now.getFullYear()) {
    return `${months[d.getMonth()]} ${d.getDate()}, ${timeStr}`;
  }
  return `${months[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
}
