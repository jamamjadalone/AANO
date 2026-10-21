// AANO Windows Desktop App - Frontend Logic
// Copyright (C) 2026 Jam Amjad Rasheed

const PASTEL_COLORS = [
  "#FFFFFF", // 0: White
  "#E0F2F1", // 1: Emerald Mint
  "#FFFDE7", // 2: Butter Yellow
  "#E8F5E9", // 3: Leaf Green
  "#E3F2FD", // 4: Sky Blue
  "#F3E5F5", // 5: Lavender
  "#FCE4EC", // 6: Rose Pink
  "#FFF3E0", // 7: Peach Orange
  "#FFEBEE"  // 8: Coral Red
];

let allNotes = [];
let currentFilter = "all"; // "all" | "pinned" | "folder:<name>"
let currentSearch = "";
let isListView = false;

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
}

function setupEventListeners() {
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

  // Nav filters
  document.querySelectorAll(".nav-item").forEach(item => {
    item.addEventListener("click", (e) => {
      e.preventDefault();
      document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
      document.querySelectorAll(".folder-item").forEach(f => f.classList.remove("active"));
      item.classList.add("active");
      currentFilter = item.dataset.filter;
      document.getElementById("currentViewTitle").innerText = item.dataset.filter === "pinned" ? "Pinned Notes" : "All Notes";
      renderNotes();
    });
  });

  // Add Folder
  document.getElementById("btnAddFolder").addEventListener("click", () => {
    const folderName = prompt("Enter new folder name:");
    if (folderName && folderName.trim()) {
      openEditor(null, folderName.trim());
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
    if (confirm("Are you sure you want to delete this note?")) {
      const api = getApi();
      if (api) {
        await api.delete_note(editingNote.id);
        closeEditorWithoutSaving();
        await refreshData();
      }
    }
  });

  // Folder Select dropdown in Editor
  const folderSelect = document.getElementById("editorFolderSelect");
  folderSelect.addEventListener("change", () => {
    if (folderSelect.value === "__new__") {
      const newFolder = prompt("Enter new folder name:");
      if (newFolder && newFolder.trim()) {
        const val = newFolder.trim();
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
}

// --------------------- Notes & Folders Rendering ---------------------
async function refreshData() {
  const api = getApi();
  if (!api) return;

  try {
    allNotes = await api.get_notes();
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
    item.innerHTML = `<span>📁 ${folder}</span><span class="badge">${count}</span>`;
    item.addEventListener("click", () => {
      document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
      document.querySelectorAll(".folder-item").forEach(f => f.classList.remove("active"));
      item.classList.add("active");
      currentFilter = "folder:" + folder;
      document.getElementById("currentViewTitle").innerText = folder;
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

  let filtered = allNotes.filter(n => {
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

  document.getElementById("notesCount").innerText = `${filtered.length} notes`;

  if (filtered.length === 0) {
    emptyState.classList.remove("hidden");
    return;
  }
  emptyState.classList.add("hidden");

  filtered.forEach(note => {
    const card = document.createElement("div");
    card.className = "note-card";
    const bg = PASTEL_COLORS[note.color || 0] || "#FFFFFF";
    card.style.background = bg;

    const plain = stripHtml(note.content || "");
    const dateStr = formatDate(note.updatedAt || note.createdAt || Date.now());

    card.innerHTML = `
      ${note.pinned ? '<span class="pin-icon" title="Pinned">📌</span>' : ''}
      <div class="note-card-title">${escapeHtml(note.title || "Untitled")}</div>
      <div class="note-card-preview">${escapeHtml(plain || "No content")}</div>
      <div class="note-card-footer">
        <span class="folder-badge">📁 ${escapeHtml(note.folder || "General")}</span>
        <span>${dateStr}</span>
      </div>
    `;

    card.addEventListener("click", () => openEditor(note));
    container.appendChild(card);
  });
}

// --------------------- Note Editor Modal ---------------------
function renderColorPalette() {
  const container = document.getElementById("paletteDots");
  container.innerHTML = "";
  PASTEL_COLORS.forEach((color, idx) => {
    const dot = document.createElement("div");
    dot.className = "color-dot" + (idx === editingColor ? " active" : "");
    dot.style.background = color;
    dot.title = `Color shade ${idx + 1}`;
    dot.addEventListener("click", () => {
      editingColor = idx;
      document.querySelectorAll(".color-dot").forEach(d => d.classList.remove("active"));
      dot.classList.add("active");
      document.getElementById("editorCard").style.background = color;
    });
    container.appendChild(dot);
  });
}

function openEditor(note = null, defaultFolder = null) {
  editingNote = note;
  const modal = document.getElementById("editorModal");
  const card = document.getElementById("editorCard");
  const titleInput = document.getElementById("editorTitle");
  const contentArea = document.getElementById("editorContent");
  const folderSelect = document.getElementById("editorFolderSelect");

  if (note) {
    titleInput.value = note.title || "";
    contentArea.innerHTML = note.content || "";
    editingColor = note.color || 0;
    editingPinned = !!note.pinned;
    folderSelect.value = note.folder || "General";
  } else {
    titleInput.value = "";
    contentArea.innerHTML = "";
    editingColor = 0;
    editingPinned = false;
    folderSelect.value = defaultFolder || (currentFilter.startsWith("folder:") ? currentFilter.replace("folder:", "") : "General");
  }

  card.style.background = PASTEL_COLORS[editingColor] || "#FFFFFF";
  renderColorPalette();
  updateEditorPinState();
  updateWordCount();

  modal.classList.remove("hidden");
  titleInput.focus();
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
    }
  }

  closeEditorWithoutSaving();
  await refreshData();
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
      statusText.innerText = "Synced: " + (status.email || "Online");
      document.getElementById("cloudLoginForm").classList.add("hidden");
      document.getElementById("cloudActiveSession").classList.remove("hidden");
      document.getElementById("lblConnectedEmail").innerText = status.email || "";
    } else {
      dot.classList.remove("connected");
      statusText.innerText = "Offline";
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
  icon.style.display = "inline-block";
  icon.style.animation = "spin 1s linear infinite";

  const api = getApi();
  if (api) {
    try {
      const res = await api.sync_now();
      await refreshData();
      await checkCloudStatus();
    } catch (e) {
      console.error("Sync error:", e);
    }
  }

  setTimeout(() => {
    icon.style.animation = "";
  }, 1000);
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
        alert(`Successfully imported ${res.count} notes!`);
        document.getElementById("backupModal").classList.add("hidden");
        await refreshData();
      } else {
        alert("Failed to import: " + (res.error || "Unknown error"));
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
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  return `${months[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
}
