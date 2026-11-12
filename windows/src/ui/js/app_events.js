// AANO Windows Desktop App - Navigation, Search & Global Shortcuts
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

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
    searchDebounceTimer = setTimeout(async () => {
      if (currentSearch && window.pywebview && window.pywebview.api && window.pywebview.api.search_notes_fts) {
        try {
          const matches = await window.pywebview.api.search_notes_fts(currentSearch);
          ftsMatchIds = new Set((matches || []).map(m => m.id));
        } catch (err) {
          console.debug("FTS search fallback triggered:", err);
          ftsMatchIds = null;
        }
      } else {
        ftsMatchIds = null;
      }
      renderNotes();
    }, 120);
  });

  btnClearSearch.addEventListener("click", () => {
    clearTimeout(searchDebounceTimer);
    searchInput.value = "";
    currentSearch = "";
    ftsMatchIds = null;
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


  // Nav filters
  document.querySelectorAll(".nav-item").forEach(item => {
    item.addEventListener("click", async (e) => {
      e.preventDefault();
      document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
      document.querySelectorAll(".folder-item").forEach(f => f.classList.remove("active"));
      item.classList.add("active");
      currentColorFilter = null;
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
