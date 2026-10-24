
// AANO Drop Modal Functions
let activePairedPeer = null;

async function checkDropPeerStatus() {
  const api = getApi();
  if (!api || !api.get_paired_peer) return;

  try {
    const peer = await api.get_paired_peer();
    const dot = document.getElementById("dropPeerDot");
    const text = document.getElementById("dropPeerText");
    const badge = document.getElementById("dropPeerBadge");
    const card = document.getElementById("activePeerCard");
    const peerNameEl = document.getElementById("activePeerName");
    const peerEpEl = document.getElementById("activePeerEndpoint");
    const ipInput = document.getElementById("targetPhoneIp");

    if (peer && peer.ip) {
      activePairedPeer = peer;
      if (dot) {
        dot.style.background = "#22c55e";
        dot.style.boxShadow = "0 0 8px #22c55e";
      }
      if (text) {
        text.innerText = "Drop: " + (peer.name || peer.ip);
      }
      if (badge) {
        badge.title = `Connected to ${peer.name || "Device"} (${peer.ip}:${peer.port}). Click to manage.`;
      }
      if (card) card.style.display = "block";
      if (peerNameEl) peerNameEl.innerText = peer.name || "Connected Peer";
      if (peerEpEl) peerEpEl.innerText = `${peer.ip}:${peer.port}`;
      if (ipInput && !ipInput.value) {
        ipInput.value = `${peer.ip}:${peer.port}`;
      }
    } else {
      activePairedPeer = null;
      if (dot) {
        dot.style.background = "#64748b";
        dot.style.boxShadow = "none";
      }
      if (text) {
        text.innerText = "Drop: Standby";
      }
      if (badge) {
        badge.title = "AANO Drop: Standby (Click to connect/pair)";
      }
      if (card) card.style.display = "none";
    }
  } catch (err) {
    console.warn("checkDropPeerStatus error:", err);
  }
}

async function openDropModal() {
  const modal = document.getElementById("aanoDropModal");
  if (!modal) return;
  modal.classList.remove("hidden");
  await checkDropPeerStatus();

  // Populate selective notes picker
  const pickerContainer = document.getElementById("dropNotesPickerContainer");
  const countEl = document.getElementById("dropSelectionCount");
  if (pickerContainer) {
    pickerContainer.replaceChildren();
    const activeNotes = (allNotes || []).filter(n => !n.deleted && !n.archived);

    if (activeNotes.length === 0) {
      const emptyDiv = document.createElement("div");
      emptyDiv.style.cssText = "color: #64748b; font-size: 11px; padding: 4px;";
      emptyDiv.textContent = "No notes available";
      pickerContainer.appendChild(emptyDiv);
    } else {
      activeNotes.forEach(n => {
        const item = document.createElement("label");
        item.style.cssText = "display: flex; align-items: center; gap: 8px; font-size: 12px; color: #cbd5e1; padding: 3px 0; cursor: pointer;";
        const chk = document.createElement("input");
        chk.type = "checkbox";
        chk.value = n.id;
        chk.className = "drop-note-chk";

        // Pre-check if this is current editing note OR in selectedNoteIds
        if (editingNote && editingNote.id === n.id) {
          chk.checked = true;
        } else if (selectedNoteIds && selectedNoteIds.has(n.id)) {
          chk.checked = true;
        }

        chk.addEventListener("change", updateDropSelectionCount);

        const titleSpan = document.createElement("span");
        titleSpan.innerText = n.title ? (n.title.length > 28 ? n.title.slice(0, 28) + "..." : n.title) : "Untitled Note";
        titleSpan.style.cssText = "overflow: hidden; text-overflow: ellipsis; white-space: nowrap;";

        item.appendChild(chk);
        item.appendChild(titleSpan);
        pickerContainer.appendChild(item);
      });
    }
    updateDropSelectionCount();
  }

  const api = getApi();
  if (api && api.get_drop_info) {
    try {
      const info = await api.get_drop_info();
      if (info) {
        const ipEl = document.getElementById("dropServerIp");
        const pinEl = document.getElementById("dropServerPin");
        if (ipEl) ipEl.innerText = `IP: ${info.ip || "127.0.0.1"}:${info.port || 8765}`;
        if (pinEl) pinEl.innerText = info.pin || "------";

        // Render High-Contrast Standard QR Code onto canvas (220px)
        const canvas = document.getElementById("dropQrCanvas");
        if (info.qr_data_url && canvas) {
          const ctx = canvas.getContext("2d");
          const img = new Image();
          img.onload = () => {
            canvas.width = 220;
            canvas.height = 220;
            canvas.style.width = "220px";
            canvas.style.height = "220px";
            ctx.clearRect(0, 0, 220, 220);
            ctx.drawImage(img, 0, 0, 220, 220);
          };
          img.src = info.qr_data_url;
        } else if (typeof renderQrToCanvas === "function") {
          const qrUri = info.qr_data || `aano://drop?ip=${info.ip}&port=${info.port || 8765}&pin=${info.pin}`;
          renderQrToCanvas("dropQrCanvas", qrUri, { size: 220 });
        }
      }
    } catch (e) {
      console.warn("Could not load drop info:", e);
    }
  }
}

function updateDropSelectionCount() {
  const countEl = document.getElementById("dropSelectionCount");
  const checked = document.querySelectorAll(".drop-note-chk:checked");
  if (countEl) {
    countEl.innerText = `${checked.length} selected`;
    countEl.style.color = checked.length > 0 ? "var(--brand)" : "#ef4444";
  }
}

function closeDropModal() {
  const modal = document.getElementById("aanoDropModal");
  if (modal) modal.classList.add("hidden");
}

// AANO Windows Desktop App - Frontend Logic
// Copyright (C) 2026 Jam Amjad Rasheed

const PALETTE_LIGHT = [
  "#FFFFFF", // 0: Pure Studio White
  "#F0FDF4", // 1: Crisp Sage Mint
  "#FEF9C3", // 2: Soft Lemon Chiffon
  "#F0F9FF", // 3: Nordic Glacier Blue
  "#EEF2FF", // 4: Royal Soft Iris
  "#FAF5FF", // 5: Lavender Mist
  "#FFF1F2", // 6: Velvet Peony
  "#FFF7ED", // 7: Peach Sorbet
  "#FEF3C7", // 8: Warm Amber Honey
  "#F8FAFC", // 9: Slate Porcelain
  "#F3F4F6", // 10: Urban Sandstone
  "#1E293B"  // 11: Deep Obsidian Accent
];

const PALETTE_DARK = [
  "#161B22", // 0: Deep Obsidian Canvas
  "#0F291E", // 1: Emerald Forest
  "#2E2510", // 2: Golden Topaz Glow
  "#0E2338", // 3: Arctic Midnight Blue
  "#171C3D", // 4: Deep Royal Indigo
  "#241738", // 5: Twilight Amethyst
  "#331422", // 6: Crimson Velvet
  "#311B11", // 7: Burnt Terracotta
  "#2D2110", // 8: Sahara Espresso
  "#1C2128", // 9: Graphite Slate
  "#21262D", // 10: Charcoal Ash
  "#0B0E14"  // 11: Pure Onyx
];

const PASTEL_COLORS = PALETTE_LIGHT;

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

let allNotes = [];
let archivedNotes = [];
let trashNotes = [];
let currentFilter = "all"; // "all" | "pinned" | "archived" | "trash" | "folder:<name>"
let currentSearch = "";
let isListView = false;

let isSelectionMode = false;
let selectedNoteIds = new Set();
let lastFilteredNotes = [];

let currentAttachedImages = []; // Array of { filename, dataUrl }
let isHdAttachmentQuality = false;
let currentPreviewIndex = -1;
let cropImageObj = null;
let cropRotation = 0;
let cropAspectRatio = "free";
let cropBox = null;
let isDraggingCrop = false;
let cropDragStart = { x: 0, y: 0 };
let cropDragMode = "move";

let editingNote = null; // null if creating new, or note object
let editingColor = 0;
let editingPinned = false;

// Global debounce for opening external links (single tab guarantee)
let lastUrlOpenTime = 0;
let lastUrlOpened = "";
let isAppInitialized = false;

// Wait for PyWebView bridge
function getApi() {
  return window.pywebview ? window.pywebview.api : null;
}

window.addEventListener("pywebviewready", () => {
  initApp();
});

// Fallback if pywebviewready already fired
document.addEventListener("DOMContentLoaded", () => {
  setTimeout(() => {
    if (getApi()) initApp();
  }, 300);
});

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

let editingIsLocal = false;

function updateEditorSyncButton() {
  const btn = document.getElementById("btnEditorSyncToggle");
  const dot = document.getElementById("editorSyncDot");
  const lbl = document.getElementById("editorSyncLabel");
  if (!btn || !lbl) return;

  if (editingIsLocal) {
    btn.classList.add("local");
    btn.title = "On-Device Note (Strictly offline, never uploaded to cloud)";
    if (dot) dot.innerText = "📱";
    lbl.innerText = "On-Device";
  } else {
    btn.classList.remove("local");
    btn.title = "Cloud Synced Note (Will sync automatically to your Google Drive)";
    if (dot) dot.innerText = "☁️";
    lbl.innerText = "Synced";
  }
}

async function initApp() {
  if (isAppInitialized) return;
  isAppInitialized = true;
  initTheme();
  setupEventListeners();
  renderColorPalette();
  await refreshData();
  await checkCloudStatus();
  await checkDropPeerStatus();

  // If user is logged in, perform immediate auto-sync on app open
  const api = getApi();
  if (api) {
    try {
      const status = await api.get_cloud_status();
      updateAutoSyncButton(status.auto_sync !== false);
      if (status.is_logged_in && status.auto_sync !== false) {
        await triggerManualSync();
      }
    } catch (err) {
      console.debug("Initial cloud sync check notice:", err);
    }
  }

  // Smart background auto-sync heartbeat (15s for instant multi-device responsiveness)
  setInterval(runAutoSyncInterval, 15000);

  // Immediate sync when app window gains focus or becomes visible
  window.addEventListener("focus", () => {
    runAutoSyncInterval();
  });
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) {
      runAutoSyncInterval();
    }
  });
}

function updateAutoSyncButton(isEnabled) {
  const btn = document.getElementById("btnAutoSyncToggle");
  if (!btn) return;
  const label = document.getElementById("autoSyncLabel");
  const dot = btn.querySelector(".autosync-dot");
  if (isEnabled) {
    btn.classList.remove("off");
    btn.classList.add("active");
    if (label) label.innerText = "Auto-Sync: ON";
    if (dot) dot.innerText = "●";
  } else {
    btn.classList.add("off");
    btn.classList.remove("active");
    if (label) label.innerText = "Auto-Sync: OFF";
    if (dot) dot.innerText = "○";
  }
}

async function runAutoSyncInterval() {
  const api = getApi();
  if (!api) return;
  try {
    const status = await api.get_cloud_status();
    if (status.is_logged_in && status.auto_sync !== false) {
      const res = await api.sync_now();
      if (res && res.success) {
        await refreshData();
      }
      await checkCloudStatus();
    }
    await checkDropPeerStatus();
  } catch (err) {
    console.error("Auto-sync tick error:", err);
  }
}

function setupEventListeners() {
  // Click outside dialog to dismiss any modal
  document.querySelectorAll(".modal-backdrop").forEach(backdrop => {
    backdrop.addEventListener("click", (e) => {
      if (e.target === backdrop) {
        backdrop.classList.add("hidden");
      }
    });
  });

  // Global Escape key to dismiss any active modal
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      document.querySelectorAll(".modal-backdrop:not(.hidden)").forEach(modal => {
        modal.classList.add("hidden");
      });
      const editorModal = document.getElementById("editorModal");
      if (editorModal && !editorModal.classList.contains("hidden")) {
        closeEditorWithoutSaving();
      }
    }
  });

  // Theme Switcher Toggle
  const btnThemeToggle = document.getElementById("btnThemeToggle");
  if (btnThemeToggle) {
    btnThemeToggle.addEventListener("click", toggleTheme);
  }

  // Editor Sync vs On-Device Toggle
  const btnEditorSyncToggle = document.getElementById("btnEditorSyncToggle");
  if (btnEditorSyncToggle) {
    btnEditorSyncToggle.addEventListener("click", () => {
      editingIsLocal = !editingIsLocal;
      updateEditorSyncButton();
    });
  }

  // Auto-Sync Toggle Button
  const btnAutoSync = document.getElementById("btnAutoSyncToggle");
  if (btnAutoSync) {
    btnAutoSync.addEventListener("click", async () => {
      const api = getApi();
      if (!api) return;
      const res = await api.toggle_auto_sync();
      updateAutoSyncButton(res.auto_sync);
      if (res.auto_sync) {
        await triggerManualSync();
      }
    });
  }
  // New Note
  document.getElementById("btnNewNote").addEventListener("click", () => openEditor());

  // Search
  const searchInput = document.getElementById("searchInput");
  const btnClearSearch = document.getElementById("btnClearSearch");

  let searchDebounceTimer = null;
  searchInput.addEventListener("input", (e) => {
    currentSearch = e.target.value.trim().toLowerCase();
    btnClearSearch.classList.toggle("hidden", !currentSearch);
    clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
      renderNotes();
    }, 120);
  });

  btnClearSearch.addEventListener("click", () => {
    clearTimeout(searchDebounceTimer);
    searchInput.value = "";
    currentSearch = "";
    btnClearSearch.classList.add("hidden");
    renderNotes();
  });

  // View Toggle (Grid / List)
  document.getElementById("btnViewToggle").addEventListener("click", () => {
    isListView = !isListView;
    const container = document.getElementById("notesContainer");
    container.classList.toggle("notes-list-view", isListView);
  });

  // Batch Selection Toggle
  const btnToggleSelect = document.getElementById("btnToggleSelect");
  if (btnToggleSelect) {
    btnToggleSelect.addEventListener("click", toggleSelectionMode);
  }

  // Batch Bar Actions
  const btnBatchSelectAll = document.getElementById("btnBatchSelectAll");
  if (btnBatchSelectAll) {
    btnBatchSelectAll.addEventListener("click", () => {
      if (selectedNoteIds.size === lastFilteredNotes.length) {
        selectedNoteIds.clear();
      } else {
        selectedNoteIds = new Set(lastFilteredNotes.map(n => n.id));
      }
      updateBatchBar();
      renderNotes();
    });
  }

  const btnBatchCancel = document.getElementById("btnBatchCancel");
  if (btnBatchCancel) {
    btnBatchCancel.addEventListener("click", exitSelectionMode);
  }

  const btnBatchArchive = document.getElementById("btnBatchArchive");
  if (btnBatchArchive) {
    btnBatchArchive.addEventListener("click", async () => {
      if (selectedNoteIds.size === 0) return;
      const api = getApi();
      if (!api) return;
      const ids = Array.from(selectedNoteIds);
      if (currentFilter === "archived") {
        await api.batch_unarchive_notes(ids);
      } else {
        await api.batch_archive_notes(ids);
      }
      try { await api.sync_now(); } catch (err) { console.warn("Background sync after batch archive deferred:", err); }
      exitSelectionMode();
      await refreshData();
      await checkCloudStatus();
    });
  }

  const btnBatchDelete = document.getElementById("btnBatchDelete");
  if (btnBatchDelete) {
    btnBatchDelete.addEventListener("click", async () => {
      if (selectedNoteIds.size === 0) return;
      const count = selectedNoteIds.size;
      const selectedNotes = allNotes.filter(n => selectedNoteIds.has(n.id));
      const hasLocal = selectedNotes.some(n => n.isLocal);
      const allLocal = selectedNotes.length > 0 && selectedNotes.every(n => n.isLocal);
      let title = "Delete Notes";
      let message = `Are you sure you want to delete ${count} selected note${count > 1 ? 's' : ''}? They will be removed across all your synced devices.`;
      let confirmText = "Delete Notes";
      if (allLocal) {
        title = "Delete Local Notes";
        message = `Are you sure you want to permanently delete ${count} selected local note${count > 1 ? 's' : ''}? They will be permanently removed from this device.`;
        confirmText = "Delete Permanently";
      } else if (hasLocal) {
        message = `Are you sure you want to delete ${count} selected notes? Local notes will be permanently removed from this device, and cloud notes will be removed across all synced devices.`;
      }

      const confirmed = await showNativeConfirm({
        title,
        message,
        confirmText,
        cancelText: "Cancel",
        danger: true
      });
      if (confirmed) {
        const api = getApi();
        if (api) {
          await api.batch_delete_notes(Array.from(selectedNoteIds));
          if (!allLocal) {
            try { await api.sync_now(); } catch (err) { console.warn("Background sync after batch delete deferred:", err); }
          }
          exitSelectionMode();
          await refreshData();
          if (!allLocal) {
            await checkCloudStatus();
          }
        }
      }
    });
  }

  // Nav filters
  document.querySelectorAll(".nav-item").forEach(item => {
    item.addEventListener("click", async (e) => {
      e.preventDefault();
      document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
      document.querySelectorAll(".folder-item").forEach(f => f.classList.remove("active"));
      item.classList.add("active");
      currentFilter = item.dataset.filter;
      if (item.dataset.filter === "pinned") {
        document.getElementById("currentViewTitle").innerText = "Pinned Notes";
      } else if (item.dataset.filter === "local") {
        document.getElementById("currentViewTitle").innerText = "Local Notes (On-Device)";
      } else if (item.dataset.filter === "archived") {
        document.getElementById("currentViewTitle").innerText = "Archive";
      } else if (item.dataset.filter === "trash") {
        document.getElementById("currentViewTitle").innerText = "Trash";
      } else {
        document.getElementById("currentViewTitle").innerText = "All Notes";
      }
      exitSelectionMode();
      await refreshData();
    });
  });

  // Empty Trash button
  const btnEmptyTrash = document.getElementById("btnEmptyTrash");
  if (btnEmptyTrash) {
    btnEmptyTrash.addEventListener("click", async () => {
      const confirmed = await showNativeConfirm({
        title: "Empty Trash",
        message: "Are you sure you want to permanently delete all notes in Trash? This cannot be undone.",
        confirmText: "Empty Trash",
        cancelText: "Cancel",
        danger: true
      });
      if (confirmed) {
        const api = getApi();
        if (api && api.empty_trash) {
          await api.empty_trash();
          await refreshData();
        }
      }
    });
  }

  document.getElementById("btnAddFolder").addEventListener("click", async () => {
    const folderName = await showNativePrompt({
      title: "New Folder",
      message: "Enter folder name to organize your notes:",
      placeholder: "e.g. Work, Ideas, Personal"
    });
    if (folderName) {
      openEditor(null, folderName);
    }
  });

  // Editor Close / Save
  document.getElementById("btnEditorClose").addEventListener("click", closeAndSaveEditor);

  // Editor Pin
  document.getElementById("btnEditorPin").addEventListener("click", () => {
    editingPinned = !editingPinned;
    updateEditorPinState();
  });

  // Editor Delete
  document.getElementById("btnEditorDelete").addEventListener("click", async () => {
    if (!editingNote || !editingNote.id) {
      closeEditorWithoutSaving();
      return;
    }
    const isLocal = editingNote.isLocal === true;
    const confirmed = await showNativeConfirm({
      title: isLocal ? "Delete Local Note" : "Delete Note",
      message: isLocal
        ? "Are you sure you want to permanently delete this local note? It will be permanently removed from this device."
        : "Are you sure you want to delete this note? It will be removed across all your synced devices.",
      confirmText: isLocal ? "Delete Permanently" : "Delete Note",
      cancelText: "Cancel",
      danger: true
    });
    if (confirmed) {
      const api = getApi();
      if (api) {
        await api.delete_note(editingNote.id);
        if (!isLocal) {
          try { await api.sync_now(); } catch (err) { console.warn("Background sync after delete deferred:", err); }
        }
        closeEditorWithoutSaving();
        await refreshData();
        if (!isLocal) {
          await checkCloudStatus();
        }
      }
    }
  });

  // Editor Archive / Unarchive
  const btnEditorArchive = document.getElementById("btnEditorArchive");
  if (btnEditorArchive) {
    btnEditorArchive.addEventListener("click", async () => {
      if (!editingNote || !editingNote.id) {
        closeEditorWithoutSaving();
        return;
      }
      const api = getApi();
      if (api) {
        if (editingNote.archived) {
          await api.unarchive_note(editingNote.id);
        } else {
          await api.archive_note(editingNote.id);
        }
        try { await api.sync_now(); } catch (err) { console.warn("Background sync after archive deferred:", err); }
        closeEditorWithoutSaving();
        await refreshData();
        await checkCloudStatus();
      }
    });
  }

  // Folder Select dropdown in Editor
  const folderSelect = document.getElementById("editorFolderSelect");
  folderSelect.addEventListener("change", async () => {
    if (folderSelect.value === "__new__") {
      const newFolder = await showNativePrompt({
        title: "Create Folder",
        message: "Enter new folder name:",
        placeholder: "Folder name"
      });
      if (newFolder) {
        const val = newFolder;
        const opt = document.createElement("option");
        opt.value = val;
        opt.innerText = "📁 " + val;
        opt.selected = true;
        folderSelect.insertBefore(opt, folderSelect.lastElementChild);
      } else {
        folderSelect.value = "General";
      }
    }
  });

  // Editor word counter
  const contentArea = document.getElementById("editorContent");
  contentArea.addEventListener("input", updateWordCount);

  // Seamless switch to Edit Mode when clicking anywhere on note in View Mode
  contentArea.addEventListener("click", (e) => {
    if (isEditorViewMode) {
      if (e.target.closest(".note-link") || e.target.closest("a")) return; // Let link handler open browser
      setEditorMode(false);
      contentArea.focus();
    }
  });

  const titleInput = document.getElementById("editorTitle");
  titleInput.addEventListener("click", () => {
    if (isEditorViewMode) {
      setEditorMode(false);
      titleInput.focus();
    }
  });

  const editorCard = document.getElementById("editorCard");
  if (editorCard) {
    editorCard.addEventListener("click", (e) => {
      if (isEditorViewMode) {
        if (e.target.closest(".note-link") || e.target.closest("a") || e.target.closest("button") || e.target.closest("select")) return;
        setEditorMode(false);
        contentArea.focus();
      }
    });
  }

  // Intercept note link clicks and open via desktop browser (Guaranteed Single Tab)
  document.addEventListener("click", (e) => {
    // In Edit Mode, any click inside editorContent must NOT open any link!
    // This allows cursor placement, text selection, and normal typing.
    if (e.target.closest("#editorContent") && !isEditorViewMode) {
      return;
    }

    const link = e.target.closest(".note-link") || e.target.closest("a");
    if (link) {
      const url = link.getAttribute("data-url") || (link.href && !link.href.startsWith("javascript:") && !link.href.endsWith("#") ? link.href : null);
      if (url) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        const now = Date.now();
        if (now - lastUrlOpenTime < 1000 && lastUrlOpened === url) {
          return;
        }
        lastUrlOpenTime = now;
        lastUrlOpened = url;
        const api = getApi();
        if (api && api.open_external_url) {
          api.open_external_url(url);
        } else {
          window.open(url, "_blank");
        }
      }
    }
  }, true);

  // Formatting Toolbar
  setupFormattingToolbar();

  // Cloud Sync
  document.getElementById("btnCloudSync").addEventListener("click", openCloudModal);
  document.getElementById("btnManualSync").addEventListener("click", triggerManualSync);
  document.getElementById("btnCancelCloud").addEventListener("click", closeCloudModal);
  document.getElementById("btnDoneCloud").addEventListener("click", closeCloudModal);
  
  const btnGoogleSignIn = document.getElementById("btnGoogleSignIn");
  if (btnGoogleSignIn) {
    btnGoogleSignIn.addEventListener("click", handleGoogleSignIn);
  }

  const btnSyncNowAccount = document.getElementById("btnSyncNowAccount");
  if (btnSyncNowAccount) {
    btnSyncNowAccount.addEventListener("click", async () => {
      await triggerManualSync();
    });
  }

  const btnLoginCloud = document.getElementById("btnLoginCloud");
  if (btnLoginCloud) {
    btnLoginCloud.addEventListener("click", handleCloudLogin);
  }
  document.getElementById("btnLogoutCloud").addEventListener("click", handleCloudLogout);

  // Image Attachment & Quality Toggle
  const btnToggleQuality = document.getElementById("btnToggleQualityDesktop");
  if (btnToggleQuality) {
    btnToggleQuality.addEventListener("click", () => {
      isHdAttachmentQuality = !isHdAttachmentQuality;
      if (isHdAttachmentQuality) {
        btnToggleQuality.classList.add("hd-mode");
        btnToggleQuality.innerText = "HD";
      } else {
        btnToggleQuality.classList.remove("hd-mode");
        btnToggleQuality.innerText = "STD";
      }
    });
  }

  const btnAttachImage = document.getElementById("btnAttachImage");
  if (btnAttachImage) {
    btnAttachImage.addEventListener("click", handleAttachImage);
  }

  // Image Preview Modal Listeners
  const btnPreviewClose = document.getElementById("btnPreviewClose");
  if (btnPreviewClose) btnPreviewClose.addEventListener("click", closeImagePreview);

  const btnPreviewSave = document.getElementById("btnPreviewSave");
  if (btnPreviewSave) {
    btnPreviewSave.addEventListener("click", async () => {
      if (currentPreviewIndex >= 0 && currentPreviewIndex < currentAttachedImages.length) {
        const item = currentAttachedImages[currentPreviewIndex];
        const api = getApi();
        if (api && api.save_image_to_disk) {
          const res = await api.save_image_to_disk(item.filename);
          if (res && res.success && res.path) {
            showNativeAlert({ title: "Saved", message: `Image exported to: ${res.path}` });
          }
        }
      }
    });
  }

  const btnPreviewDelete = document.getElementById("btnPreviewDelete");
  if (btnPreviewDelete) {
    btnPreviewDelete.addEventListener("click", async () => {
      if (currentPreviewIndex >= 0 && currentPreviewIndex < currentAttachedImages.length) {
        const item = currentAttachedImages[currentPreviewIndex];
        currentAttachedImages.splice(currentPreviewIndex, 1);
        renderAttachedImages();
        closeImagePreview();
        const api = getApi();
        if (api && api.delete_image_file) {
          api.delete_image_file(item.filename).catch(err => console.debug("Image file deletion notice:", err));
        }
      }
    });
  }

  const btnPreviewCrop = document.getElementById("btnPreviewCrop");
  if (btnPreviewCrop) {
    btnPreviewCrop.addEventListener("click", openCropModal);
  }

  // Image Crop Modal Listeners
  const btnCropClose = document.getElementById("btnCropClose");
  if (btnCropClose) btnCropClose.addEventListener("click", closeCropModal);
  const btnCropCancel = document.getElementById("btnCropCancel");
  if (btnCropCancel) btnCropCancel.addEventListener("click", () => {
    closeCropModal();
    if (currentPreviewIndex >= 0) openImagePreview(currentPreviewIndex);
  });
  const btnCropRotate = document.getElementById("btnCropRotate");
  if (btnCropRotate) {
    btnCropRotate.addEventListener("click", () => {
      cropRotation = (cropRotation + 90) % 360;
      initCropCanvas();
    });
  }
  const btnCropApply = document.getElementById("btnCropApply");
  if (btnCropApply) btnCropApply.addEventListener("click", handleApplyCrop);

  document.querySelectorAll(".btn-ratio").forEach(btn => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".btn-ratio").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      cropAspectRatio = btn.dataset.ratio || "free";
      applyAspectRatioToCropBox();
      drawCropCanvas();
    });
  });

  setupCropCanvasEvents();


  // AANO Drop Modal Listeners
  const dropPeerBadge = document.getElementById("dropPeerBadge");
  if (dropPeerBadge) dropPeerBadge.addEventListener("click", openDropModal);

  const btnAanoDrop = document.getElementById("btnAanoDrop");
  if (btnAanoDrop) btnAanoDrop.addEventListener("click", openDropModal);
  const btnCloseAanoDrop = document.getElementById("btnCloseAanoDrop");
  if (btnCloseAanoDrop) btnCloseAanoDrop.addEventListener("click", closeDropModal);
  const btnDoneAanoDrop = document.getElementById("btnDoneAanoDrop");
  if (btnDoneAanoDrop) btnDoneAanoDrop.addEventListener("click", closeDropModal);

  const btnUnpairPeer = document.getElementById("btnUnpairPeer");
  if (btnUnpairPeer) {
    btnUnpairPeer.addEventListener("click", async () => {
      const api = getApi();
      if (api && api.unpair_peer) {
        await api.unpair_peer();
        await checkDropPeerStatus();
      }
    });
  }

  const btnScanRadar = document.getElementById("btnScanRadar");
  const radarResults = document.getElementById("radarResultsContainer");
  const radarLabel = document.getElementById("radarScanLabel");
  if (btnScanRadar) {
    btnScanRadar.addEventListener("click", async () => {
      const api = getApi();
      if (!api || !api.scan_nearby_devices) return;
      if (radarLabel) radarLabel.textContent = "Scanning Wi-Fi Radar...";
      if (radarResults) {
        radarResults.style.display = "block";
        const scanMsg = document.createElement("div");
        scanMsg.style.cssText = "color: #94a3b8; font-size: 11px; padding: 6px; text-align: center;";
        scanMsg.textContent = "Scanning subnet for active AANO devices...";
        radarResults.replaceChildren(scanMsg);
      }
      try {
        const found = await api.scan_nearby_devices();
        if (radarResults) {
          if (!found || found.length === 0) {
            const noDev = document.createElement("div");
            noDev.style.cssText = "color: #94a3b8; font-size: 11px; padding: 6px; text-align: center;";
            noDev.textContent = "No nearby AANO devices detected on Wi-Fi.";
            radarResults.replaceChildren(noDev);
          } else {
            radarResults.replaceChildren();
            found.forEach(dev => {
              const row = document.createElement("div");
              row.style.cssText = "display: flex; align-items: center; justify-content: space-between; padding: 6px 8px; border-bottom: 1px solid rgba(255,255,255,0.05);";

              const infoCol = document.createElement("div");
              const nameDiv = document.createElement("div");
              nameDiv.style.cssText = "font-weight: 600; font-size: 12px; color: #f1f5f9;";
              nameDiv.textContent = dev.name || "Device";
              const epDiv = document.createElement("div");
              epDiv.style.cssText = "font-size: 11px; color: #64748b;";
              epDiv.textContent = `${dev.ip}:${dev.port}`;
              infoCol.appendChild(nameDiv);
              infoCol.appendChild(epDiv);

              const connBtn = document.createElement("button");
              connBtn.className = "btn-connect-dev";
              connBtn.style.cssText = "background: var(--brand); color: white; border: none; border-radius: 4px; padding: 4px 10px; font-size: 11px; cursor: pointer; font-weight: 600;";
              connBtn.textContent = "Connect & Pair";
              connBtn.addEventListener("click", async () => {
                connBtn.textContent = "Pairing...";
                const res = await api.pair_with_device(dev.ip, dev.port);
                if (res && res.success) {
                  await checkDropPeerStatus();
                  closeDropModal();
                  showNativeAlert({ title: "Connected", message: `Paired successfully with ${dev.name}!` });
                } else {
                  showNativeAlert({ title: "Pairing Failed", message: res?.error || "Could not pair with device." });
                }
                connBtn.textContent = "Connect & Pair";
              });

              row.appendChild(infoCol);
              row.appendChild(connBtn);
              radarResults.appendChild(row);
            });
          }
        }
      } catch (err) {
        if (radarResults) {
          const errMsg = document.createElement("div");
          errMsg.style.cssText = "color: #ef4444; font-size: 11px; padding: 6px;";
          errMsg.textContent = `Scan error: ${String(err)}`;
          radarResults.replaceChildren(errMsg);
        }
      } finally {
        if (radarLabel) radarLabel.textContent = "Radar Scan";
      }
    });
  }

  // Copy PC Drop URI
  const btnCopyDropUri = document.getElementById("btnCopyDropUri");
  if (btnCopyDropUri) {
    btnCopyDropUri.addEventListener("click", () => {
      const ip = document.getElementById("dropServerIp")?.innerText.replace("IP: ", "").trim() || "";
      const pin = document.getElementById("dropServerPin")?.innerText.trim() || "";
      const uri = `aano://drop?ip=${ip.split(":")[0]}&port=${ip.split(":")[1] || "8765"}&pin=${pin}`;
      navigator.clipboard.writeText(uri).then(() => {
        btnCopyDropUri.innerText = "✅";
        setTimeout(() => { btnCopyDropUri.innerText = "📋"; }, 2000);
      }).catch(() => {
        prompt("Copy Drop URI:", uri);
      });
    });
  }

  // PC Camera QR Scanner Modal Logic
  const scannerModal = document.getElementById("aanoScannerModal");
  const scannerVideo = document.getElementById("qrScannerVideo");
  const scannerStatus = document.getElementById("qrScannerStatus");
  const btnOpenQrScanner = document.getElementById("btnOpenQrScanner");
  const btnCloseScannerModal = document.getElementById("btnCloseScannerModal");
  const btnUploadQrImage = document.getElementById("btnUploadQrImage");
  const qrFileInput = document.getElementById("qrFileInput");
  const btnPasteQrCode = document.getElementById("btnPasteQrCode");

  function applyScannedDropResult(val) {
    if (!val) return;
    const parsed = window.AanoQrScanner ? window.AanoQrScanner.parseDropUri(val) : null;
    const targetIp = document.getElementById("targetPhoneIp");
    const targetPin = document.getElementById("targetPhonePin");

    if (parsed && parsed.valid) {
      if (targetIp) targetIp.value = parsed.port && parsed.port !== "8765" ? `${parsed.ip}:${parsed.port}` : parsed.ip;
      if (targetPin) targetPin.value = parsed.pin || "";
      closeScannerModal();
      showNativeAlert({
        title: "⚡ Phone Paired",
        message: `Paired successfully with device at ${parsed.ip}! Choose note(s) below and click 'Send Selected Note(s)'.`
      });
    } else {
      if (targetIp) targetIp.value = val.trim();
      closeScannerModal();
    }
  }

  function openScannerModal() {
    if (!scannerModal || !scannerVideo) return;
    scannerModal.classList.remove("hidden");
    if (scannerStatus) scannerStatus.innerText = "Point camera at phone's AANO Drop QR code";
    if (window.AanoQrScanner) {
      window.AanoQrScanner.startCamera(scannerVideo, (val) => {
        applyScannedDropResult(val);
      }, (err) => {
        if (scannerStatus) scannerStatus.innerText = "Camera not detected or permission denied. Use Screenshot or Paste below.";
      });
    }
  }

  function closeScannerModal() {
    if (window.AanoQrScanner) window.AanoQrScanner.stopCamera();
    if (scannerModal) scannerModal.classList.add("hidden");
  }

  if (btnOpenQrScanner) btnOpenQrScanner.addEventListener("click", openScannerModal);
  if (btnCloseScannerModal) btnCloseScannerModal.addEventListener("click", closeScannerModal);

  if (btnUploadQrImage && qrFileInput) {
    btnUploadQrImage.addEventListener("click", () => qrFileInput.click());
    qrFileInput.addEventListener("change", (e) => {
      const file = e.target.files && e.target.files[0];
      if (file && window.AanoQrScanner) {
        if (scannerStatus) scannerStatus.innerText = "Analyzing image...";
        window.AanoQrScanner.decodeImageFile(file, (val) => {
          applyScannedDropResult(val);
        }, (err) => {
          if (scannerStatus) scannerStatus.innerText = `Could not decode QR: ${err.message || "Unknown error"}`;
        });
      }
    });
  }

  if (btnPasteQrCode) {
    btnPasteQrCode.addEventListener("click", async () => {
      let text = "";
      try {
        text = await navigator.clipboard.readText();
      } catch (err) {
        console.debug("Clipboard read notice:", err);
      }
      if (!text) {
        text = prompt("Paste Phone's AANO Drop URI or IP:PORT (e.g. aano://drop?ip=...):", "");
      }
      if (text && text.trim()) {
        applyScannedDropResult(text.trim());
      }
    });
  }

  const btnBatchDrop = document.getElementById("btnBatchDrop");
  if (btnBatchDrop) {
    btnBatchDrop.addEventListener("click", async () => {
      const selectedIds = Array.from(selectedNoteIds);
      if (selectedIds.length === 0) {
        showNativeAlert({ title: "No Notes Selected", message: "Please select at least one note to drop." });
        return;
      }
      const api = getApi();
      if (!api) return;

      if (activePairedPeer && activePairedPeer.ip) {
        const origText = btnBatchDrop.textContent;
        btnBatchDrop.textContent = `Dropping ${selectedIds.length}...`;
        try {
          const res = await api.send_drop_to_device(activePairedPeer.ip, activePairedPeer.port || 8766, selectedIds);
          if (res && res.success) {
            showNativeAlert({ title: "Drop Complete", message: `Successfully transferred ${res.count || selectedIds.length} notes to ${activePairedPeer.name || "Phone"}!` });
            exitSelectionMode();
          } else {
            showNativeAlert({ title: "Drop Failed", message: res?.error || "Could not reach paired device." });
          }
        } catch (e) {
          showNativeAlert({ title: "Error", message: String(e) });
        } finally {
          btnBatchDrop.textContent = origText;
        }
      } else {
        openDropModal();
      }
    });
  }

  const btnSendDropToPhone = document.getElementById("btnSendDropToPhone");
  if (btnSendDropToPhone) {
    btnSendDropToPhone.addEventListener("click", async () => {
      const ipInput = document.getElementById("targetPhoneIp");
      const pinInput = document.getElementById("targetPhonePin");
      const targetIp = ipInput ? ipInput.value.trim() : "";
      const targetPin = pinInput ? pinInput.value.trim() : null;

      if (!targetIp) {
        showNativeAlert({ title: "Target IP Required", message: "Please enter target IP shown on AANO Android or PC." });
        return;
      }

      // Collect checked note IDs
      const checkedBoxes = document.querySelectorAll(".drop-note-chk:checked");
      const noteIdsToSend = Array.from(checkedBoxes).map(cb => cb.value);

      if (noteIdsToSend.length === 0) {
        showNativeAlert({ title: "No Notes Selected", message: "Please select at least one note to drop from the list." });
        return;
      }

      const api = getApi();
      if (!api || !api.send_drop_to_device) return;
      btnSendDropToPhone.innerText = `Sending ${noteIdsToSend.length} Note(s)...`;
      try {
        const res = await api.send_drop_to_device(targetIp, 8766, noteIdsToSend, targetPin);
        if (res && res.success) {
          await checkDropPeerStatus();
          showNativeAlert({ title: "Drop Complete", message: `Successfully transferred ${res.count || 0} notes to phone!` });
          closeDropModal();
        } else {
          showNativeAlert({ title: "Drop Failed", message: res?.error || "Could not reach phone on Wi-Fi." });
        }
      } catch (err) {
        showNativeAlert({ title: "Error", message: String(err) });
      } finally {
        btnSendDropToPhone.innerText = "⚡ Send Notes to Phone";
      }
    });
  }

  // Unified Export Dropdown in Editor
  const btnEditorExport = document.getElementById("btnEditorExport");
  const exportMenu = document.getElementById("exportMenu");
  if (btnEditorExport && exportMenu) {
    btnEditorExport.addEventListener("click", (e) => {
      e.stopPropagation();
      exportMenu.classList.toggle("hidden");
    });

    document.addEventListener("click", (e) => {
      if (!e.target.closest(".export-dropdown-wrapper")) {
        exportMenu.classList.add("hidden");
      }
    });
  }

  const btnExportAano = document.getElementById("btnExportAano");
  if (btnExportAano) {
    btnExportAano.addEventListener("click", async () => {
      if (exportMenu) exportMenu.classList.add("hidden");
      if (!editingNote || !editingNote.id) {
        showNativeAlert({ title: "Notice", message: "Please save the note first before exporting." });
        return;
      }
      const api = getApi();
      if (api && api.export_note_aano) {
        const res = await api.export_note_aano(editingNote.id);
        if (res && res.success && res.path) {
          showNativeAlert({ title: "AANO Package Exported", message: `Direct-opening note package created: ${res.path}\n\nAnyone with AANO can open this package with all text, photos, and voice memos intact!` });
        }
      }
    });
  }

  const btnImportAano = document.getElementById("btnImportAano");
  if (btnImportAano) {
    btnImportAano.addEventListener("click", async () => {
      const api = getApi();
      if (api && api.import_aano_package) {
        const res = await api.import_aano_package();
        if (res && res.success && res.note_id) {
          await loadNotes();
          openEditor(res.note_id);
          showNativeAlert({ title: "Note Imported", message: `Successfully imported "${res.title}" with all attachments!` });
        } else if (res && res.error) {
          showNativeAlert({ title: "Import Failed", message: res.error });
        }
      }
    });
  }

  window.importAanoOnStartup = async (filePath) => {
    const api = getApi();
    if (api && api.import_aano_package) {
      const res = await api.import_aano_package(filePath);
      if (res && res.success && res.note_id) {
        await loadNotes();
        openEditor(res.note_id);
      }
    }
  };

  const btnExportTxt = document.getElementById("btnExportTxt");
  if (btnExportTxt) {
    btnExportTxt.addEventListener("click", async () => {
      if (exportMenu) exportMenu.classList.add("hidden");
      if (!editingNote || !editingNote.id) {
        showNativeAlert({ title: "Notice", message: "Please save the note first before exporting." });
        return;
      }
      const api = getApi();
      if (api && api.export_note_text) {
        const res = await api.export_note_text(editingNote.id);
        if (res && res.success && res.path) {
          showNativeAlert({ title: "Text File Exported", message: `File saved to: ${res.path}` });
        }
      }
    });
  }

  const btnExportPdf = document.getElementById("btnExportPdf");
  if (btnExportPdf) {
    btnExportPdf.addEventListener("click", async () => {
      if (exportMenu) exportMenu.classList.add("hidden");
      if (!editingNote || !editingNote.id) {
        showNativeAlert({ title: "Notice", message: "Please save the note first before exporting." });
        return;
      }
      const api = getApi();
      if (api && api.export_note_html_pdf) {
        const res = await api.export_note_html_pdf(editingNote.id);
        if (res && res.success && res.path) {
          showNativeAlert({ title: "PDF / Printable Exported", message: `Printable document saved to: ${res.path}. Open it in your browser to print or save as PDF.` });
        }
      }
    });
  }

  const btnExportMd = document.getElementById("btnExportMd");
  if (btnExportMd) {
    btnExportMd.addEventListener("click", async () => {
      if (exportMenu) exportMenu.classList.add("hidden");
      if (!editingNote || !editingNote.id) {
        showNativeAlert({ title: "Notice", message: "Please save the note first before exporting." });
        return;
      }
      const api = getApi();
      if (api && api.export_note_markdown) {
        const res = await api.export_note_markdown(editingNote.id);
        if (res && res.success && res.path) {
          showNativeAlert({ title: "Markdown Exported", message: `File saved to: ${res.path}` });
        }
      }
    });
  }

  const btnExportZip = document.getElementById("btnExportZip");
  if (btnExportZip) {
    btnExportZip.addEventListener("click", async () => {
      if (exportMenu) exportMenu.classList.add("hidden");
      if (!editingNote || !editingNote.id) {
        showNativeAlert({ title: "Notice", message: "Please save the note first before exporting." });
        return;
      }
      const api = getApi();
      if (api && api.export_note_zip) {
        const res = await api.export_note_zip(editingNote.id);
        if (res && res.success && res.path) {
          showNativeAlert({ title: "ZIP Archive Exported", message: `Complete bundle with notes, photos, and voice memos saved to: ${res.path}` });
        }
      }
    });
  }

  const btnEditorDrop = document.getElementById("btnEditorDrop");
  if (btnEditorDrop) {
    btnEditorDrop.addEventListener("click", () => {
      openDropModal();
    });
  }

  // Backup & Restore
  const btnBackup = document.getElementById("btnBackup");
  if (btnBackup) {
    btnBackup.addEventListener("click", () => {
      const backupModal = document.getElementById("backupModal");
      if (backupModal) backupModal.classList.remove("hidden");
    });
  }
  const btnCloseBackup = document.getElementById("btnCloseBackup");
  if (btnCloseBackup) {
    btnCloseBackup.addEventListener("click", () => {
      const backupModal = document.getElementById("backupModal");
      if (backupModal) backupModal.classList.add("hidden");
    });
  }
  const btnExportJSON = document.getElementById("btnExportJSON");
  if (btnExportJSON) btnExportJSON.addEventListener("click", handleExportBackup);
  const backupFileInput = document.getElementById("backupFileInput");
  if (backupFileInput) backupFileInput.addEventListener("change", handleImportBackup);

  // Global Keyboard Shortcuts (Ctrl+N, Ctrl+F, Escape)
  document.addEventListener("keydown", (e) => {
    const confirmModal = document.getElementById("nativeConfirmModal");
    const promptModal = document.getElementById("nativePromptModal");
    const alertModal = document.getElementById("nativeAlertModal");
    if ((confirmModal && !confirmModal.classList.contains("hidden")) ||
        (promptModal && !promptModal.classList.contains("hidden")) ||
        (alertModal && !alertModal.classList.contains("hidden"))) {
      return;
    }

    const editorModal = document.getElementById("editorModal");
    const isEditorOpen = editorModal && !editorModal.classList.contains("hidden");

    if ((e.ctrlKey || e.metaKey) && (e.key === "n" || e.key === "N")) {
      e.preventDefault();
      if (!isEditorOpen) {
        openEditor();
      }
      return;
    }

    if ((e.ctrlKey || e.metaKey) && (e.key === "f" || e.key === "F")) {
      e.preventDefault();
      if (!isEditorOpen) {
        const searchInput = document.getElementById("searchInput");
        if (searchInput) {
          searchInput.focus();
          searchInput.select();
        }
      }
      return;
    }

    if (e.key === "Escape") {
      if (isEditorOpen) {
        e.preventDefault();
        closeAndSaveEditor();
      } else if (isSelectionMode) {
        e.preventDefault();
        exitSelectionMode();
      }
      return;
    }
  });
}

// --------------------- Selection Mode Helpers ---------------------
function toggleSelectionMode() {
  isSelectionMode = !isSelectionMode;
  if (!isSelectionMode) {
    exitSelectionMode();
  } else {
    const bar = document.getElementById("batchActionBar");
    if (bar) bar.classList.remove("hidden");
    const btn = document.getElementById("btnToggleSelect");
    if (btn) {
      btn.classList.add("active");
      btn.querySelector("span").innerText = "Done";
    }
    updateBatchBar();
    renderNotes();
  }
}

function exitSelectionMode() {
  isSelectionMode = false;
  selectedNoteIds.clear();
  const bar = document.getElementById("batchActionBar");
  if (bar) bar.classList.add("hidden");
  const btn = document.getElementById("btnToggleSelect");
  if (btn) {
    btn.classList.remove("active");
    btn.querySelector("span").innerText = "Select";
  }
  renderNotes();
}

function updateBatchBar() {
  const countSpan = document.getElementById("batchCount");
  if (countSpan) countSpan.innerText = `${selectedNoteIds.size} selected`;
  const archiveLabel = document.getElementById("batchArchiveLabel");
  if (archiveLabel) {
    archiveLabel.innerText = currentFilter === "archived" ? "Unarchive" : "Archive";
  }
}

async function refreshData() {
  const api = getApi();
  if (!api) return;

  try {
    allNotes = await api.get_notes();
    archivedNotes = (await api.get_archived_notes()) || [];
    trashNotes = (await api.get_trashed_notes()) || [];
    const folders = await api.get_all_folders();
    renderFolders(folders);
    renderNotes();
  } catch (err) {
    console.error("Error refreshing notes:", err);
  }
}

function renderFolders(folders) {
  const container = document.getElementById("foldersList");
  container.replaceChildren();

  // Unique list ensuring defaults
  const fSet = new Set(["General", "Work", "Personal", ...folders]);
  const sorted = Array.from(fSet).sort();

  // Also update Editor folder select
  const select = document.getElementById("editorFolderSelect");
  select.replaceChildren();
  sorted.forEach(f => {
    const opt = document.createElement("option");
    opt.value = f;
    opt.textContent = f;
    select.appendChild(opt);
  });
  const newOpt = document.createElement("option");
  newOpt.value = "__new__";
  newOpt.textContent = "+ New Folder...";
  select.appendChild(newOpt);

  sorted.forEach(folder => {
    const count = allNotes.filter(n => n.folder === folder).length;
    const item = document.createElement("div");
    item.className = "folder-item" + (currentFilter === "folder:" + folder ? " active" : "");
    const folderSvg = `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right:6px;vertical-align:-2px"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>`;
    // fixmyslop-ignore[sec/raw-html] Safe static SVG with escaped folder name and numeric count
    item.innerHTML = `<span>${folderSvg}${escapeHtml(folder)}</span><span class="badge">${count}</span>`;
    item.addEventListener("click", async () => {
      document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
      document.querySelectorAll(".folder-item").forEach(f => f.classList.remove("active"));
      item.classList.add("active");
      currentFilter = "folder:" + folder;
      document.getElementById("currentViewTitle").innerText = folder;
      exitSelectionMode();
      await refreshData();
    });
    container.appendChild(item);
  });
}

function renderNotes() {
  const container = document.getElementById("notesContainer");
  const emptyState = document.getElementById("emptyState");
  container.replaceChildren();

  const nonLocalNotes = allNotes.filter(n => !n.isLocal);
  const localNotes = allNotes.filter(n => n.isLocal);
  document.getElementById("allCount").innerText = allNotes.length;
  const localCountEl = document.getElementById("localCount");
  if (localCountEl) localCountEl.innerText = localNotes.length;
  const archivedCountEl = document.getElementById("archivedCount");
  if (archivedCountEl) archivedCountEl.innerText = archivedNotes.length;
  const trashCountEl = document.getElementById("trashCount");
  if (trashCountEl) trashCountEl.innerText = trashNotes.length;

  const btnEmptyTrash = document.getElementById("btnEmptyTrash");
  if (btnEmptyTrash) {
    if (currentFilter === "trash" && trashNotes.length > 0) {
      btnEmptyTrash.classList.remove("hidden");
    } else {
      btnEmptyTrash.classList.add("hidden");
    }
  }

  const sourceNotes = (currentFilter === "archived") ? archivedNotes : (currentFilter === "trash" ? trashNotes : allNotes);

  let filtered = sourceNotes.filter(n => {
    // Nav / Folder filter
    if (currentFilter === "local") {
      if (!n.isLocal) return false;
    } else if (currentFilter === "all") {
      // Unified feed: show all notes (cloud and on-device)
    } else if (currentFilter === "pinned") {
      if (!n.pinned) return false;
    } else if (currentFilter.startsWith("folder:")) {
      const fName = currentFilter.replace("folder:", "");
      if (n.folder !== fName) return false;
    }
    // Search query
    if (currentSearch) {
      const matchTitle = (n.title || "").toLowerCase().includes(currentSearch);
      const plainText = stripHtml(n.content || "").toLowerCase();
      const matchContent = plainText.includes(currentSearch);
      const matchFolder = (n.folder || "").toLowerCase().includes(currentSearch);
      if (!matchTitle && !matchContent && !matchFolder) return false;
    }
    return true;
  });

  // Sort: Pinned first, then newest updatedAt
  filtered.sort((a, b) => {
    if (a.pinned !== b.pinned) return b.pinned ? 1 : -1;
    return (b.updatedAt || 0) - (a.updatedAt || 0);
  });

  lastFilteredNotes = filtered;
  updateBatchBar();

  document.getElementById("notesCount").innerText = `${filtered.length} notes`;

  if (filtered.length === 0) {
    emptyState.classList.remove("hidden");
    return;
  }
  emptyState.classList.add("hidden");

  const pinSvg = `<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="17" x2="12" y2="22"/><path d="M5 17h14v-2l-2-2V5a2 2 0 0 0-2-2H9a2 2 0 0 0-2 2v8l-2 2v2z"/></svg>`;
  const cardFolderSvg = `<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>`;

  filtered.forEach(note => {
    const card = document.createElement("div");
    const isSelected = selectedNoteIds.has(note.id);
    const bg = resolveNoteColor(note.color);
    const isDark = isDarkColor(bg);
    card.className = "note-card" + (isSelectionMode ? " selectable" : "") + (isSelected ? " selected" : "") + (isDark ? " dark-card" : "");
    card.style.background = bg;

    const plain = stripHtml(note.content || "");
    const dateStr = formatDate(note.updatedAt || note.createdAt || Date.now());

    if (currentFilter === "trash") {
      // fixmyslop-ignore[sec/raw-html] Dynamic fields sanitized via escapeHtml and formatLinksInHtml
      card.innerHTML = `
        <div class="note-card-title">${escapeHtml(note.title || "Untitled")}</div>
        <div class="note-card-preview">${formatLinksInHtml(escapeHtml(plain || "No content"))}</div>
        <div class="note-card-footer" style="display: flex; justify-content: space-between; align-items: center; margin-top: 10px;">
          <span>${dateStr}</span>
          <div style="display: flex; gap: 6px;">
            <button class="btn-trash-restore" style="background: #10B981; color: white; border: none; border-radius: 4px; padding: 4px 8px; font-size: 11px; cursor: pointer; display: flex; align-items: center; gap: 4px;" title="Restore to Active Notes">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 10h10a5 5 0 0 1 5 5v2"/><polyline points="7 6 3 10 7 14"/></svg> Restore
            </button>
            <button class="btn-trash-delete" style="background: #EF4444; color: white; border: none; border-radius: 4px; padding: 4px 8px; font-size: 11px; cursor: pointer;" title="Delete Permanently">
              ✕
            </button>
          </div>
        </div>
      `;

      const btnRestore = card.querySelector(".btn-trash-restore");
      if (btnRestore) {
        btnRestore.addEventListener("click", async (e) => {
          e.stopPropagation();
          const api = getApi();
          if (api && api.restore_note) {
            await api.restore_note(note.id);
            await refreshData();
          }
        });
      }

      const btnDelete = card.querySelector(".btn-trash-delete");
      if (btnDelete) {
        btnDelete.addEventListener("click", async (e) => {
          e.stopPropagation();
          const confirmed = await showNativeConfirm({
            title: "Delete Forever",
            message: "Permanently delete this note? This action cannot be undone.",
            confirmText: "Delete",
            cancelText: "Cancel",
            danger: true
          });
          if (confirmed) {
            const api = getApi();
            if (api && api.permanent_delete_note) {
              await api.permanent_delete_note(note.id);
              await refreshData();
            }
          }
        });
      }
    } else {
      const imageCount = (note.images && Array.isArray(note.images)) ? note.images.length : 0;
      const hasAudio = !!note.audioUri;
      const mediaBadges = `
        ${imageCount > 0 ? `<span class="media-pill">📷 ${imageCount}</span>` : ''}
        ${hasAudio ? `<span class="media-pill">🎙️ Audio</span>` : ''}
      `;
      const localBadge = note.isLocal ? `<span class="badge" style="background: rgba(56, 189, 248, 0.18); color: #0284c7; font-weight: 700; font-size: 10px; padding: 2px 6px; border-radius: 4px;">⚡ On-Device</span>` : '';

      // fixmyslop-ignore[sec/raw-html] Dynamic fields sanitized via escapeHtml and formatLinksInHtml
      card.innerHTML = `
        ${isSelectionMode ? `<input type="checkbox" class="card-select-checkbox" ${isSelected ? 'checked' : ''}>` : ''}
        ${note.pinned ? `<span class="pin-icon" title="Pinned">${pinSvg}</span>` : ''}
        <div class="note-card-title">${escapeHtml(note.title || "Untitled")}</div>
        <div class="note-card-preview">${formatLinksInHtml(escapeHtml(plain || "No content"))}</div>
        <div class="note-card-footer">
          <span class="folder-badge">${cardFolderSvg} ${escapeHtml(note.folder || "General")}</span>
          <div style="display: flex; align-items: center; gap: 4px; flex-wrap: wrap;">
            ${mediaBadges}
            ${localBadge}
          </div>
          <span>${dateStr}</span>
        </div>
      `;

      if (isSelectionMode) {
        card.addEventListener("click", (e) => {
          e.stopPropagation();
          if (selectedNoteIds.has(note.id)) {
            selectedNoteIds.delete(note.id);
          } else {
            selectedNoteIds.add(note.id);
          }
          updateBatchBar();
          renderNotes();
        });
      } else {
        card.addEventListener("click", (e) => {
          if (e.target.closest(".note-link") || e.target.closest("a")) return; // Clicked link directly on card: open browser, do not open editor!
          openEditor(note);
        });
      }
    }
    container.appendChild(card);
  });
}

// --------------------- Note Editor Modal ---------------------
function renderColorPalette() {
  const container = document.getElementById("paletteDots");
  container.replaceChildren();
  const palette = isCurrentThemeDark() ? PALETTE_DARK : PALETTE_LIGHT;
  palette.forEach((color, idx) => {
    const dot = document.createElement("div");
    const isMatched = (typeof editingColor === "number" && editingColor === idx);
    dot.className = "color-dot" + (isMatched ? " active" : "");
    dot.style.background = color;
    dot.title = `Color shade ${idx + 1}`;
    dot.addEventListener("click", () => {
      editingColor = idx;
      applyEditorColor(color);
      document.querySelectorAll(".color-dot").forEach(d => d.classList.remove("active"));
      dot.classList.add("active");
    });
    container.appendChild(dot);
  });

  const customBtn = document.getElementById("btnCustomColor");
  const customPicker = document.getElementById("customColorPicker");
  if (customBtn && customPicker) {
    customBtn.onclick = () => customPicker.click();
    customPicker.onchange = () => {
      const hex = customPicker.value;
      editingColor = hexToColorInt(hex);
      applyEditorColor(hex);
      document.querySelectorAll(".color-dot").forEach(d => d.classList.remove("active"));
    };
  }
}

function applyEditorColor(colorHex) {
  const card = document.getElementById("editorCard");
  if (!card) return;
  card.style.background = colorHex;
  if (isDarkColor(colorHex)) {
    card.classList.add("dark-card");
  } else {
    card.classList.remove("dark-card");
  }
}

let isEditorViewMode = false;

const URL_REGEX = /(https?:\/\/[^\s<>"'`]+|www\.[a-zA-Z0-9-]+\.[a-zA-Z0-9-._~:/?#[\]@!$&'()*+,;=]+)/gi;

function formatLinksInHtml(html) {
  if (!html) return "";
  return html.replace(URL_REGEX, (url) => {
    let href = url;
    if (href.toLowerCase().startsWith("www.")) {
      href = "https://" + href;
    }
    return `<span data-url="${href}" class="note-link">${url}</span>`;
  });
}

function autoLinkify(element) {
  if (!element) return;
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null, false);
  const nodesToReplace = [];
  let node;
  while ((node = walker.nextNode())) {
    if (node.parentElement && (node.parentElement.classList.contains("note-link") || node.parentElement.tagName.toLowerCase() === "a")) continue;
    URL_REGEX.lastIndex = 0;
    if (URL_REGEX.test(node.nodeValue)) {
      nodesToReplace.push(node);
    }
  }
  for (const textNode of nodesToReplace) {
    const parent = textNode.parentNode;
    if (!parent) continue;
    const parts = textNode.nodeValue.split(URL_REGEX);
    const fragment = document.createDocumentFragment();
    for (const part of parts) {
      URL_REGEX.lastIndex = 0;
      if (URL_REGEX.test(part)) {
        const span = document.createElement("span");
        let href = part;
        if (href.toLowerCase().startsWith("www.")) {
          href = "https://" + href;
        }
        span.setAttribute("data-url", href);
        span.textContent = part;
        span.className = "note-link";
        fragment.appendChild(span);
      } else if (part) {
        fragment.appendChild(document.createTextNode(part));
      }
    }
    parent.replaceChild(fragment, textNode);
  }
}

function setEditorMode(isViewMode) {
  isEditorViewMode = isViewMode;
  const contentArea = document.getElementById("editorContent");
  const titleInput = document.getElementById("editorTitle");
  const fmtToolbar = document.querySelector(".formatting-toolbar");

  if (isEditorViewMode) {
    contentArea.setAttribute("contenteditable", "false");
    titleInput.readOnly = true;
    if (fmtToolbar) fmtToolbar.style.display = "none";
    autoLinkify(contentArea);
  } else {
    contentArea.setAttribute("contenteditable", "true");
    titleInput.readOnly = false;
    if (fmtToolbar) fmtToolbar.style.display = "flex";
  }
}

function openEditor(note = null, defaultFolder = null) {
  editingNote = note;
  editingIsLocal = note ? Boolean(note.isLocal) : (currentFilter === "local");
  updateEditorSyncButton();
  const modal = document.getElementById("editorModal");
  const card = document.getElementById("editorCard");
  const titleInput = document.getElementById("editorTitle");
  const contentArea = document.getElementById("editorContent");
  const folderSelect = document.getElementById("editorFolderSelect");
  const btnArchive = document.getElementById("btnEditorArchive");

  if (btnArchive) {
    if (note && note.archived) {
      btnArchive.title = "Unarchive Note";
    } else {
      btnArchive.title = "Archive Note";
    }
  }

  if (note) {
    titleInput.value = note.title || "";
    // fixmyslop-ignore[sec/raw-html] Contenteditable rich-text editor loading user note HTML
    contentArea.innerHTML = note.content || "";
    editingColor = note.color !== undefined ? note.color : 0;
    editingPinned = !!note.pinned;
    folderSelect.value = note.folder || "General";
    autoLinkify(contentArea);
    setEditorMode(true);
    if (note.images && Array.isArray(note.images) && note.images.length > 0) {
      loadAttachedImages(note.images);
    } else {
      currentAttachedImages = [];
      renderAttachedImages();
    }
  } else {
    titleInput.value = "";
    contentArea.replaceChildren();
    editingColor = 0;
    editingPinned = false;
    folderSelect.value = defaultFolder || (currentFilter.startsWith("folder:") ? currentFilter.replace("folder:", "") : "General");
    setEditorMode(false);
    currentAttachedImages = [];
    renderAttachedImages();
  }

  const resolvedBg = resolveNoteColor(editingColor);
  applyEditorColor(resolvedBg);
  renderColorPalette();
  updateEditorPinState();
  updateWordCount();

  modal.classList.remove("hidden");
  if (!isEditorViewMode) {
    setTimeout(() => titleInput.focus(), 80);
  }
}

function updateEditorPinState() {
  const pinBtn = document.getElementById("btnEditorPin");
  if (editingPinned) {
    pinBtn.style.opacity = "1";
    pinBtn.title = "Unpin Note";
  } else {
    pinBtn.style.opacity = "0.4";
    pinBtn.title = "Pin Note";
  }
}

function updateWordCount() {
  const text = document.getElementById("editorContent").innerText.trim();
  const words = text ? text.split(/\s+/).length : 0;
  const chars = text.length;
  document.getElementById("editorWordCount").innerText = `${words} words • ${chars} chars`;
}

async function closeAndSaveEditor() {
  const title = document.getElementById("editorTitle").value.trim();
  const content = document.getElementById("editorContent").innerHTML.trim();
  const folder = document.getElementById("editorFolderSelect").value;
  const isLocalNote = Boolean(editingIsLocal);
  const noteToSave = {
    id: editingNote ? editingNote.id : null,
    title: title || "Untitled Note",
    content: content,
    folder: folder,
    color: editingColor,
    pinned: editingPinned,
    isLocal: isLocalNote,
    images: currentAttachedImages.map(img => img.filename)
  };

  const isCompletelyEmpty = !title && (!content || content === "<br>") && currentAttachedImages.length === 0;

  // 1. Close modal IMMEDIATELY (0ms latency, instant response)
  closeEditorWithoutSaving();

  // 2. Perform save & refresh asynchronously without blocking the UI
  if (!isCompletelyEmpty) {
    const api = getApi();
    if (api) {
      try {
        await api.save_note(noteToSave);
        await refreshData();
        if (!isLocalNote) {
          api.sync_now().then(() => checkCloudStatus()).catch(err => console.debug("Post-save background sync deferred:", err));
        }
      } catch (err) {
        console.warn("Save note failed:", err);
      }
    }
  }
}

function closeEditorWithoutSaving() {
  document.getElementById("editorModal").classList.add("hidden");
  editingNote = null;
}

// --------------------- Rich Text Toolbar ---------------------
function setupFormattingToolbar() {
  const toolbar = document.querySelector(".formatting-toolbar");

  toolbar.querySelectorAll("button[data-cmd]").forEach(btn => {
    btn.addEventListener("click", (e) => {
      e.preventDefault();
      const cmd = btn.dataset.cmd;
      const val = btn.dataset.val || null;
      document.execCommand(cmd, false, val);
      document.getElementById("editorContent").focus();
    });
  });

  // Highlighter options
  document.querySelectorAll("[data-highlight]").forEach(link => {
    link.addEventListener("click", (e) => {
      e.preventDefault();
      const color = link.dataset.highlight;
      document.execCommand("hiliteColor", false, color);
      document.getElementById("editorContent").focus();
    });
  });

  // Text color options
  document.querySelectorAll("[data-forecolor]").forEach(link => {
    link.addEventListener("click", (e) => {
      e.preventDefault();
      const color = link.dataset.forecolor;
      document.execCommand("foreColor", false, color);
      document.getElementById("editorContent").focus();
    });
  });
}

// --------------------- Cloud Sync Integration ---------------------
async function checkCloudStatus() {
  const api = getApi();
  if (!api) return;

  try {
    const status = await api.get_cloud_status();
    const dot = document.querySelector(".status-dot");
    const statusText = document.getElementById("cloudStatusText");

    if (status.is_logged_in) {
      dot.classList.add("connected");
      dot.style.background = "";
      statusText.innerText = "Synced: " + (status.email || "Online");
      updateAutoSyncButton(status.auto_sync !== false);
      document.getElementById("cloudLoginForm").classList.add("hidden");
      document.getElementById("cloudActiveSession").classList.remove("hidden");
      document.getElementById("lblConnectedEmail").innerText = status.email || "";
    } else {
      dot.classList.remove("connected");
      if (status.session_expired) {
        dot.style.background = "#F59E0B";
        statusText.innerText = "Session Expired (Click to Sign In)";
      } else {
        dot.style.background = "";
        statusText.innerText = "Offline (Click to Sign In)";
      }
      document.getElementById("cloudLoginForm").classList.remove("hidden");
      document.getElementById("cloudActiveSession").classList.add("hidden");
    }
  } catch (err) {
    console.error("Cloud status error:", err);
  }
}

function openCloudModal() {
  document.getElementById("cloudError").classList.add("hidden");
  document.getElementById("cloudModal").classList.remove("hidden");
}

function closeCloudModal() {
  document.getElementById("cloudModal").classList.add("hidden");
}

async function handleCloudLogin() {
  const email = document.getElementById("cloudEmailInput").value.trim();
  const pass = document.getElementById("cloudPassInput").value.trim();
  const errDiv = document.getElementById("cloudError");

  if (!email || !pass) {
    errDiv.innerText = "Please enter email and password.";
    errDiv.classList.remove("hidden");
    return;
  }

  errDiv.innerText = "Connecting to AANO Cloud...";
  errDiv.classList.remove("hidden");

  const api = getApi();
  if (api) {
    const res = await api.login_cloud(email, pass);
    if (res.success) {
      errDiv.classList.add("hidden");
      await checkCloudStatus();
      await refreshData();
      closeCloudModal();
    } else {
      errDiv.innerText = res.error || "Failed to sign in.";
      errDiv.classList.remove("hidden");
    }
  }
}

async function handleGoogleSignIn() {
  const btn = document.getElementById("btnGoogleSignIn");
  const errDiv = document.getElementById("cloudError");
  if (errDiv) errDiv.classList.add("hidden");

  if (btn) {
    btn.disabled = true;
    btn.innerText = "Opening browser for Google login...";
  }

  const api = getApi();
  if (api && api.google_login) {
    try {
      const res = await api.google_login();
      if (res && res.success) {
        await checkCloudStatus();
        await refreshData();
        closeCloudModal();
      } else {
        if (errDiv) {
          errDiv.innerText = (res && res.error) ? res.error : "Google login failed or was cancelled.";
          errDiv.classList.remove("hidden");
        }
      }
    } catch (e) {
      if (errDiv) {
        errDiv.innerText = "Error: " + e;
        errDiv.classList.remove("hidden");
      }
    }
  }

  if (btn) {
    btn.disabled = false;
    // fixmyslop-ignore[sec/raw-html] Static SVG logo template without dynamic inputs
    btn.innerHTML = `
      <svg viewBox="0 0 24 24" width="20" height="20" style="margin-right: 12px;">
        <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
        <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
        <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
        <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
      </svg>
      Sign in with Google
    `;
  }
}

async function handleCloudLogout() {
  const api = getApi();
  if (api) {
    if (api.google_logout) await api.google_logout();
    await api.logout_cloud();
    await checkCloudStatus();
    closeCloudModal();
  }
}


async function triggerManualSync() {
  const btn = document.getElementById("btnManualSync");
  const icon = btn.querySelector(".sync-icon");
  if (icon) {
    icon.style.display = "inline-block";
    icon.style.animation = "spin 1s linear infinite";
  }

  const api = getApi();
  if (api) {
    try {
      const res = await api.sync_now();
      if (!res.success && (res.auth_expired || res.error?.includes("Unauthorized") || res.error?.includes("Session expired") || res.error?.includes("Not logged in"))) {
        openCloudModal();
        const errDiv = document.getElementById("cloudError");
        if (errDiv) {
          errDiv.innerText = res.error || "Please sign in with your Gmail password to connect.";
          errDiv.classList.remove("hidden");
        }
      }
      await refreshData();
      await checkCloudStatus();
    } catch (e) {
      console.error("Sync error:", e);
    }
  }

  if (icon) {
    setTimeout(() => {
      icon.style.animation = "";
    }, 1000);
  }
}

// --------------------- Backup & Restore ---------------------
async function handleExportBackup() {
  const api = getApi();
  if (!api) return;

  const jsonStr = await api.export_backup_json();
  const blob = new Blob([jsonStr], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `aano-backup-${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

async function handleImportBackup(e) {
  const file = e.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = async (evt) => {
    const content = evt.target.result;
    const api = getApi();
    if (api) {
      const res = await api.import_backup_json(content);
      if (res.success) {
        await showNativeAlert({
          title: "Backup Restored",
          message: `Successfully imported ${res.count} notes into AANO.`
        });
        document.getElementById("backupModal").classList.add("hidden");
        await refreshData();
      } else {
        await showNativeAlert({
          title: "Import Failed",
          message: "Failed to import: " + (res.error || "Unknown error")
        });
      }
    }
  };
  reader.readAsText(file);
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

// --------------------- Media Vault & Image Processing ---------------------
async function loadAttachedImages(imageFilenames) {
  currentAttachedImages = [];
  const api = getApi();
  if (!api || !imageFilenames || !imageFilenames.length) {
    renderAttachedImages();
    return;
  }
  for (const fn of imageFilenames) {
    try {
      const cleanFn = fn.split("/").pop().split("\\").pop();
      const res = await api.get_image_data_url(cleanFn);
      if (res && res.success && res.dataUrl) {
        currentAttachedImages.push({ filename: cleanFn, dataUrl: res.dataUrl });
      }
    } catch (e) {
      console.warn("Failed to load image data URL:", fn, e);
    }
  }
  renderAttachedImages();
}

function renderAttachedImages() {
  const container = document.getElementById("editorImagesContainer");
  if (!container) return;
  if (!currentAttachedImages || currentAttachedImages.length === 0) {
    container.classList.add("hidden");
    container.replaceChildren();
    return;
  }
  container.classList.remove("hidden");
  container.replaceChildren();

  currentAttachedImages.forEach((img, idx) => {
    const thumb = document.createElement("div");
    thumb.className = "editor-image-thumb";
    thumb.title = img.filename || "Attached Image";

    const imgEl = document.createElement("img");
    imgEl.src = img.dataUrl;
    imgEl.alt = "Attachment";

    const delBtn = document.createElement("button");
    delBtn.type = "button";
    delBtn.className = "thumb-del-btn";
    delBtn.title = "Remove";
    delBtn.innerText = "✕";
    delBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      currentAttachedImages.splice(idx, 1);
      renderAttachedImages();
    });

    thumb.appendChild(imgEl);
    thumb.appendChild(delBtn);
    thumb.addEventListener("click", () => {
      openImagePreview(idx);
    });
    container.appendChild(thumb);
  });
}

async function handleAttachImage() {
  const api = getApi();
  if (!api || !api.attach_images) return;
  try {
    const res = await api.attach_images(isHdAttachmentQuality);
    if (res && res.success && res.images && res.images.length) {
      for (const item of res.images) {
        currentAttachedImages.push({
          filename: item.filename,
          dataUrl: item.dataUrl
        });
      }
      renderAttachedImages();
    }
  } catch (err) {
    console.warn("Attach image failed:", err);
  }
}

function openImagePreview(index) {
  if (index < 0 || index >= currentAttachedImages.length) return;
  currentPreviewIndex = index;
  const modal = document.getElementById("imagePreviewModal");
  const imgEl = document.getElementById("previewModalImg");
  const nameEl = document.getElementById("previewImageName");
  if (!modal || !imgEl) return;

  const item = currentAttachedImages[index];
  imgEl.src = item.dataUrl;
  if (nameEl) nameEl.innerText = item.filename || "Image Preview";
  modal.classList.remove("hidden");
}

function closeImagePreview() {
  const modal = document.getElementById("imagePreviewModal");
  if (modal) modal.classList.add("hidden");
  currentPreviewIndex = -1;
}

function openCropModal() {
  if (currentPreviewIndex < 0 || currentPreviewIndex >= currentAttachedImages.length) return;
  const item = currentAttachedImages[currentPreviewIndex];
  closeImagePreview();

  const modal = document.getElementById("imageCropModal");
  const canvas = document.getElementById("cropCanvas");
  if (!modal || !canvas) return;

  cropRotation = 0;
  cropAspectRatio = "free";
  document.querySelectorAll(".btn-ratio").forEach(b => {
    b.classList.toggle("active", b.dataset.ratio === "free");
  });

  cropImageObj = new Image();
  cropImageObj.onload = () => {
    initCropCanvas();
    modal.classList.remove("hidden");
  };
  cropImageObj.src = item.dataUrl;
}

function closeCropModal() {
  const modal = document.getElementById("imageCropModal");
  if (modal) modal.classList.add("hidden");
  cropImageObj = null;
}

function initCropCanvas() {
  const canvas = document.getElementById("cropCanvas");
  if (!canvas || !cropImageObj) return;

  const isRotated90or270 = (cropRotation % 180 !== 0);
  const srcW = isRotated90or270 ? cropImageObj.height : cropImageObj.width;
  const srcH = isRotated90or270 ? cropImageObj.width : cropImageObj.height;

  const maxW = Math.min(window.innerWidth * 0.7, 700);
  const maxH = Math.min(window.innerHeight * 0.55, 480);
  let scale = 1;
  if (srcW > maxW || srcH > maxH) {
    scale = Math.min(maxW / srcW, maxH / srcH);
  }
  canvas.width = Math.max(100, Math.round(srcW * scale));
  canvas.height = Math.max(100, Math.round(srcH * scale));

  const insetX = canvas.width * 0.08;
  const insetY = canvas.height * 0.08;
  cropBox = {
    x: insetX,
    y: insetY,
    w: canvas.width - (insetX * 2),
    h: canvas.height - (insetY * 2)
  };
  applyAspectRatioToCropBox();
  drawCropCanvas();
}

function applyAspectRatioToCropBox() {
  if (!cropBox || cropAspectRatio === "free") return;
  const canvas = document.getElementById("cropCanvas");
  if (!canvas) return;

  let targetRatio = 1;
  if (cropAspectRatio === "1:1") targetRatio = 1;
  else if (cropAspectRatio === "4:3") targetRatio = 4 / 3;
  else if (cropAspectRatio === "16:9") targetRatio = 16 / 9;

  let newH = cropBox.w / targetRatio;
  if (newH > canvas.height - cropBox.y) {
    newH = canvas.height - cropBox.y;
    cropBox.w = newH * targetRatio;
  }
  cropBox.h = newH;
}

function drawCropCanvas() {
  const canvas = document.getElementById("cropCanvas");
  if (!canvas || !cropImageObj || !cropBox) return;
  const ctx = canvas.getContext("2d");

  ctx.clearRect(0, 0, canvas.width, canvas.height);

  ctx.save();
  ctx.translate(canvas.width / 2, canvas.height / 2);
  ctx.rotate((cropRotation * Math.PI) / 180);
  const isRotated90or270 = (cropRotation % 180 !== 0);
  const drawW = isRotated90or270 ? canvas.height : canvas.width;
  const drawH = isRotated90or270 ? canvas.width : canvas.height;
  ctx.drawImage(cropImageObj, -drawW / 2, -drawH / 2, drawW, drawH);
  ctx.restore();

  ctx.fillStyle = "rgba(0, 0, 0, 0.55)";
  ctx.fillRect(0, 0, canvas.width, cropBox.y);
  ctx.fillRect(0, cropBox.y + cropBox.h, canvas.width, canvas.height - (cropBox.y + cropBox.h));
  ctx.fillRect(0, cropBox.y, cropBox.x, cropBox.h);
  ctx.fillRect(cropBox.x + cropBox.w, cropBox.y, canvas.width - (cropBox.x + cropBox.w), cropBox.h);

  ctx.strokeStyle = "#00897B";
  ctx.lineWidth = 2;
  ctx.strokeRect(cropBox.x, cropBox.y, cropBox.w, cropBox.h);

  ctx.strokeStyle = "rgba(255, 255, 255, 0.35)";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(cropBox.x + cropBox.w / 3, cropBox.y);
  ctx.lineTo(cropBox.x + cropBox.w / 3, cropBox.y + cropBox.h);
  ctx.moveTo(cropBox.x + (cropBox.w * 2) / 3, cropBox.y);
  ctx.lineTo(cropBox.x + (cropBox.w * 2) / 3, cropBox.y + cropBox.h);
  ctx.moveTo(cropBox.x, cropBox.y + cropBox.h / 3);
  ctx.lineTo(cropBox.x + cropBox.w, cropBox.y + cropBox.h / 3);
  ctx.moveTo(cropBox.x, cropBox.y + (cropBox.h * 2) / 3);
  ctx.lineTo(cropBox.x + cropBox.w, cropBox.y + (cropBox.h * 2) / 3);
  ctx.stroke();

  const handleSize = 8;
  ctx.fillStyle = "#FFFFFF";
  ctx.fillRect(cropBox.x - handleSize / 2, cropBox.y - handleSize / 2, handleSize, handleSize);
  ctx.fillRect(cropBox.x + cropBox.w - handleSize / 2, cropBox.y - handleSize / 2, handleSize, handleSize);
  ctx.fillRect(cropBox.x - handleSize / 2, cropBox.y + cropBox.h - handleSize / 2, handleSize, handleSize);
  ctx.fillRect(cropBox.x + cropBox.w - handleSize / 2, cropBox.y + cropBox.h - handleSize / 2, handleSize, handleSize);
}

function setupCropCanvasEvents() {
  const canvas = document.getElementById("cropCanvas");
  if (!canvas) return;

  canvas.addEventListener("mousedown", (e) => {
    if (!cropBox) return;
    const rect = canvas.getBoundingClientRect();
    const mx = e.clientX - rect.left;
    const my = e.clientY - rect.top;

    isDraggingCrop = true;
    cropDragStart = { x: mx, y: my };

    if (mx >= cropBox.x && mx <= cropBox.x + cropBox.w && my >= cropBox.y && my <= cropBox.y + cropBox.h) {
      cropDragMode = "move";
    } else {
      cropDragMode = "new";
      cropBox.x = mx;
      cropBox.y = my;
      cropBox.w = 10;
      cropBox.h = 10;
    }
  });

  window.addEventListener("mousemove", (e) => {
    if (!isDraggingCrop || !cropBox) return;
    const canvas = document.getElementById("cropCanvas");
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const mx = Math.max(0, Math.min(canvas.width, e.clientX - rect.left));
    const my = Math.max(0, Math.min(canvas.height, e.clientY - rect.top));

    const dx = mx - cropDragStart.x;
    const dy = my - cropDragStart.y;
    cropDragStart = { x: mx, y: my };

    if (cropDragMode === "move") {
      cropBox.x = Math.max(0, Math.min(canvas.width - cropBox.w, cropBox.x + dx));
      cropBox.y = Math.max(0, Math.min(canvas.height - cropBox.h, cropBox.y + dy));
    } else {
      cropBox.w = Math.max(30, mx - cropBox.x);
      cropBox.h = Math.max(30, my - cropBox.y);
      applyAspectRatioToCropBox();
    }
    drawCropCanvas();
  });

  window.addEventListener("mouseup", () => {
    isDraggingCrop = false;
  });
}

async function handleApplyCrop() {
  if (currentPreviewIndex < 0 || !cropImageObj || !cropBox) return;
  const canvas = document.getElementById("cropCanvas");
  if (!canvas) return;

  const isRotated90or270 = (cropRotation % 180 !== 0);
  const srcW = isRotated90or270 ? cropImageObj.height : cropImageObj.width;
  const srcH = isRotated90or270 ? cropImageObj.width : cropImageObj.height;

  const scaleX = srcW / canvas.width;
  const scaleY = srcH / canvas.height;

  const cropX = Math.round(cropBox.x * scaleX);
  const cropY = Math.round(cropBox.y * scaleY);
  const cropW = Math.round(cropBox.w * scaleX);
  const cropH = Math.round(cropBox.h * scaleY);

  const api = getApi();
  if (!api || !api.crop_image) return;

  const item = currentAttachedImages[currentPreviewIndex];
  try {
    const res = await api.crop_image(item.filename, [cropX, cropY, cropX + cropW, cropY + cropH], cropRotation, isHdAttachmentQuality);
    if (res && res.success) {
      currentAttachedImages[currentPreviewIndex] = {
        filename: res.filename,
        dataUrl: res.dataUrl
      };
      renderAttachedImages();
      closeCropModal();
      openImagePreview(currentPreviewIndex);
    }
  } catch (e) {
    console.warn("Apply crop failed:", e);
  }
}
