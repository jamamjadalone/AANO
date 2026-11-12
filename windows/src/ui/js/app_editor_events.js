// AANO Windows Desktop App - Note Editor Event Listeners & Shortcuts
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

function setupEditorEventListeners() {
  if (typeof setupFormattingToolbar === "function") {
    setupFormattingToolbar();
  }

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

}
