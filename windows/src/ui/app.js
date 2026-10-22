// AANO Windows Desktop App - Frontend Logic
// Copyright (C) 2026 Jam Amjad Rasheed

const PASTEL_COLORS = [
  "#FFFFFF", // 0: White Default
  "#FEF9C3", // 1: Pastel Lemon
  "#DCFCE7", // 2: Pastel Mint
  "#CCFBF1", // 3: Pastel Teal
  "#E0F2FE", // 4: Pastel Sky
  "#E0E7FF", // 5: Pastel Indigo
  "#F3E8FF", // 6: Pastel Lavender
  "#FCE7F3", // 7: Pastel Rose
  "#FFEDD5", // 8: Pastel Peach
  "#FEE2E2", // 9: Pastel Coral
  "#F5F5F4", // 10: Muted Sand
  "#E2E8F0", // 11: Muted Slate
  "#1E293B"  // 12: Charcoal Dark
];

function resolveNoteColor(colorVal) {
  if (colorVal === undefined || colorVal === null) return "#FFFFFF";
  if (typeof colorVal === "string" && colorVal.startsWith("#")) return colorVal;
  if (typeof colorVal === "number") {
    if (colorVal >= 0 && colorVal < PASTEL_COLORS.length) {
      return PASTEL_COLORS[colorVal];
    }
    const unsigned = (colorVal >>> 0).toString(16).padStart(8, "0");
    return "#" + unsigned.slice(2);
  }
  return "#FFFFFF";
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
let currentFilter = "all"; // "all" | "pinned" | "archived" | "folder:<name>"
let currentSearch = "";
let isListView = false;

let isSelectionMode = false;
let selectedNoteIds = new Set();
let lastFilteredNotes = [];

let editingNote = null; // null if creating new, or note object
let editingColor = 0;
let editingPinned = false;

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

async function initApp() {
  setupEventListeners();
  renderColorPalette();
  await refreshData();
  await checkCloudStatus();

  // If user is logged in, perform immediate auto-sync on app open
  const api = getApi();
  if (api) {
    try {
      const status = await api.get_cloud_status();
      updateAutoSyncButton(status.auto_sync !== false);
      if (status.is_logged_in && status.auto_sync !== false) {
        await triggerManualSync();
      }
    } catch (_) {}
  }

  // Fast real-time periodic background auto-sync polling every 2.5 seconds
  setInterval(runAutoSyncInterval, 2500);

  // Immediate sync when app window gains focus
  window.addEventListener("focus", () => {
    runAutoSyncInterval();
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
      if (res && res.success && res.count > 0) {
        await refreshData();
      }
      await checkCloudStatus();
    }
  } catch (err) {
    console.error("Auto-sync tick error:", err);
  }
}

function setupEventListeners() {
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

  searchInput.addEventListener("input", (e) => {
    currentSearch = e.target.value.trim().toLowerCase();
    btnClearSearch.classList.toggle("hidden", !currentSearch);
    renderNotes();
  });

  btnClearSearch.addEventListener("click", () => {
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
      try { await api.sync_now(); } catch (_) {}
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
      const confirmed = await showNativeConfirm({
        title: "Delete Notes",
        message: `Are you sure you want to delete ${count} selected note${count > 1 ? 's' : ''}? They will be removed across all your synced devices.`,
        confirmText: "Delete Notes",
        cancelText: "Cancel",
        danger: true
      });
      if (confirmed) {
        const api = getApi();
        if (api) {
          await api.batch_delete_notes(Array.from(selectedNoteIds));
          try { await api.sync_now(); } catch (_) {}
          exitSelectionMode();
          await refreshData();
          await checkCloudStatus();
        }
      }
    });
  }

  // Nav filters
  document.querySelectorAll(".nav-item").forEach(item => {
    item.addEventListener("click", (e) => {
      e.preventDefault();
      document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
      document.querySelectorAll(".folder-item").forEach(f => f.classList.remove("active"));
      item.classList.add("active");
      currentFilter = item.dataset.filter;
      if (item.dataset.filter === "pinned") {
        document.getElementById("currentViewTitle").innerText = "Pinned Notes";
      } else if (item.dataset.filter === "archived") {
        document.getElementById("currentViewTitle").innerText = "Archive";
      } else {
        document.getElementById("currentViewTitle").innerText = "All Notes";
      }
      exitSelectionMode();
      renderNotes();
    });
  });

  // Add Folder
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
    const confirmed = await showNativeConfirm({
      title: "Delete Note",
      message: "Are you sure you want to delete this note? It will be removed across all your synced devices.",
      confirmText: "Delete Note",
      cancelText: "Cancel",
      danger: true
    });
    if (confirmed) {
      const api = getApi();
      if (api) {
        await api.delete_note(editingNote.id);
        try { await api.sync_now(); } catch (_) {}
        closeEditorWithoutSaving();
        await refreshData();
        await checkCloudStatus();
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
        try { await api.sync_now(); } catch (_) {}
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
      if (e.target.closest("a")) return; // Let link handler open browser
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
        if (e.target.closest("a") || e.target.closest("button") || e.target.closest("select")) return;
        setEditorMode(false);
        contentArea.focus();
      }
    });
  }

  // Intercept all note link clicks and open via desktop browser (Prevent double tabs)
  let lastUrlOpenTime = 0;
  let lastUrlOpened = "";
  document.addEventListener("click", (e) => {
    const link = e.target.closest("a.note-link") || e.target.closest("a");
    if (link) {
      const url = link.getAttribute("data-url") || (link.href && !link.href.startsWith("javascript:") && !link.href.endsWith("#") ? link.href : null);
      if (url) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        const now = Date.now();
        if (now - lastUrlOpenTime < 700 && lastUrlOpened === url) {
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
  document.getElementById("btnLoginCloud").addEventListener("click", handleCloudLogin);
  document.getElementById("btnLogoutCloud").addEventListener("click", handleCloudLogout);

  // Backup & Restore
  document.getElementById("btnBackup").addEventListener("click", () => {
    document.getElementById("backupModal").classList.remove("hidden");
  });
  document.getElementById("btnCloseBackup").addEventListener("click", () => {
    document.getElementById("backupModal").classList.add("hidden");
  });
  document.getElementById("btnExportJSON").addEventListener("click", handleExportBackup);
  document.getElementById("backupFileInput").addEventListener("change", handleImportBackup);

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

// --------------------- Notes & Folders Rendering ---------------------
async function refreshData() {
  const api = getApi();
  if (!api) return;

  try {
    allNotes = await api.get_notes();
    archivedNotes = (await api.get_archived_notes()) || [];
    const folders = await api.get_all_folders();
    renderFolders(folders);
    renderNotes();
  } catch (err) {
    console.error("Error refreshing notes:", err);
  }
}

function renderFolders(folders) {
  const container = document.getElementById("foldersList");
  container.innerHTML = "";

  // Unique list ensuring defaults
  const fSet = new Set(["General", "Work", "Personal", ...folders]);
  const sorted = Array.from(fSet).sort();

  // Also update Editor folder select
  const select = document.getElementById("editorFolderSelect");
  select.innerHTML = "";
  sorted.forEach(f => {
    const opt = document.createElement("option");
    opt.value = f;
    opt.innerText = "📁 " + f;
    select.appendChild(opt);
  });
  const newOpt = document.createElement("option");
  newOpt.value = "__new__";
  newOpt.innerText = "+ New Folder...";
  select.appendChild(newOpt);

  sorted.forEach(folder => {
    const count = allNotes.filter(n => n.folder === folder).length;
    const item = document.createElement("div");
    item.className = "folder-item" + (currentFilter === "folder:" + folder ? " active" : "");
    const folderSvg = `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right:6px;vertical-align:-2px"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>`;
    item.innerHTML = `<span>${folderSvg}${escapeHtml(folder)}</span><span class="badge">${count}</span>`;
    item.addEventListener("click", () => {
      document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
      document.querySelectorAll(".folder-item").forEach(f => f.classList.remove("active"));
      item.classList.add("active");
      currentFilter = "folder:" + folder;
      document.getElementById("currentViewTitle").innerText = folder;
      exitSelectionMode();
      renderNotes();
    });
    container.appendChild(item);
  });
}

function renderNotes() {
  const container = document.getElementById("notesContainer");
  const emptyState = document.getElementById("emptyState");
  container.innerHTML = "";

  document.getElementById("allCount").innerText = allNotes.length;
  const archivedCountEl = document.getElementById("archivedCount");
  if (archivedCountEl) archivedCountEl.innerText = archivedNotes.length;

  const sourceNotes = (currentFilter === "archived") ? archivedNotes : allNotes;

  let filtered = sourceNotes.filter(n => {
    // Nav / Folder filter
    if (currentFilter === "pinned" && !n.pinned) return false;
    if (currentFilter.startsWith("folder:")) {
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

    card.innerHTML = `
      ${isSelectionMode ? `<input type="checkbox" class="card-select-checkbox" ${isSelected ? 'checked' : ''}>` : ''}
      ${note.pinned ? `<span class="pin-icon" title="Pinned">${pinSvg}</span>` : ''}
      <div class="note-card-title">${escapeHtml(note.title || "Untitled")}</div>
      <div class="note-card-preview">${formatLinksInHtml(escapeHtml(plain || "No content"))}</div>
      <div class="note-card-footer">
        <span class="folder-badge">${cardFolderSvg} ${escapeHtml(note.folder || "General")}</span>
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
        if (e.target.closest("a")) return; // Clicked link directly on card: open browser, do not open editor!
        openEditor(note);
      });
    }
    container.appendChild(card);
  });
}

// --------------------- Note Editor Modal ---------------------
function renderColorPalette() {
  const container = document.getElementById("paletteDots");
  container.innerHTML = "";
  PASTEL_COLORS.forEach((color, idx) => {
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
    return `<a href="javascript:void(0)" data-url="${href}" class="note-link">${url}</a>`;
  });
}

function autoLinkify(element) {
  if (!element) return;
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null, false);
  const nodesToReplace = [];
  let node;
  while ((node = walker.nextNode())) {
    if (node.parentElement && node.parentElement.tagName.toLowerCase() === "a") continue;
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
        const a = document.createElement("a");
        let href = part;
        if (href.toLowerCase().startsWith("www.")) {
          href = "https://" + href;
        }
        a.href = "javascript:void(0)";
        a.setAttribute("data-url", href);
        a.textContent = part;
        a.className = "note-link";
        fragment.appendChild(a);
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
    contentArea.innerHTML = note.content || "";
    editingColor = note.color !== undefined ? note.color : 0;
    editingPinned = !!note.pinned;
    folderSelect.value = note.folder || "General";
    autoLinkify(contentArea);
    setEditorMode(true);
  } else {
    titleInput.value = "";
    contentArea.innerHTML = "";
    editingColor = 0;
    editingPinned = false;
    folderSelect.value = defaultFolder || (currentFilter.startsWith("folder:") ? currentFilter.replace("folder:", "") : "General");
    setEditorMode(false);
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

  // Don't save empty notes
  const isCompletelyEmpty = !title && (!content || content === "<br>");

  if (!isCompletelyEmpty) {
    const api = getApi();
    if (api) {
      await api.save_note({
        id: editingNote ? editingNote.id : null,
        title: title || "Untitled Note",
        content: content,
        folder: folder,
        color: editingColor,
        pinned: editingPinned
      });
      try {
        await api.sync_now();
      } catch (err) {
        console.warn("Immediate sync failed, queued for auto-sync:", err);
      }
    }
  }

  closeEditorWithoutSaving();
  await refreshData();
  await checkCloudStatus();
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

async function handleCloudLogout() {
  const api = getApi();
  if (api) {
    await api.logout_cloud();
    await checkCloudStatus();
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
  const tmp = document.createElement("div");
  tmp.innerHTML = html;
  return tmp.textContent || tmp.innerText || "";
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
