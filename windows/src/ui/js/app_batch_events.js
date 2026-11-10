// AANO Windows Desktop App - Batch Action Bar Event Listeners
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

function setupBatchBarListeners() {
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

}
