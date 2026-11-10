// AANO Windows Desktop App - Note Editor Core & Lifecycle Engine
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

function updateEditorBackgroundColor() {
  const resolvedBg = resolveNoteColor(editingColor);
  applyEditorColor(resolvedBg);
}

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
  const readingMins = Math.max(1, Math.ceil(words / 200));
  document.getElementById("editorWordCount").innerText = `${words} words • ${chars} chars • ~${readingMins}m read`;
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
    type: editingNote ? (editingNote.type || 0) : 0,
    reminderType: editingNote ? (editingNote.reminderType || 0) : 0,
    reminderDate: editingNote ? (editingNote.reminderDate || 0) : 0,
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
