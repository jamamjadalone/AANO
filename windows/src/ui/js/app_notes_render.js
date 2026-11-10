// AANO Windows Desktop App - Notes Grid & Card Rendering Engine
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).


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
  renderPaletteFilterBar(sourceNotes);

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
    // Color filter
    if (currentColorFilter !== null) {
      const noteColor = (n.color !== undefined && n.color !== null) ? n.color : 0;
      if (noteColor !== currentColorFilter) return false;
    }
    // Search query (accelerated by SQLite FTS5)
    if (currentSearch) {
      if (ftsMatchIds && ftsMatchIds.size > 0) {
        if (!ftsMatchIds.has(n.id)) return false;
      } else {
        const matchTitle = (n.title || "").toLowerCase().includes(currentSearch);
        const plainText = stripHtml(n.content || "").toLowerCase();
        const matchContent = plainText.includes(currentSearch);
        const matchFolder = (n.folder || "").toLowerCase().includes(currentSearch);
        if (!matchTitle && !matchContent && !matchFolder) return false;
      }
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

  const countLabel = currentColorFilter !== null
    ? `${filtered.length} notes • 🎨 ${PALETTE_LABELS[currentColorFilter] || "Custom"}`
    : `${filtered.length} notes`;
  document.getElementById("notesCount").innerText = countLabel;

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
      const lines = plain.split("\n");
      const checkItems = lines.filter(l => {
        const t = l.trim();
        return t.startsWith("☐ ") || t.startsWith("☑ ") || t.startsWith("[ ] ") || t.startsWith("[x] ") || t.startsWith("[X] ");
      });
      let checklistBadge = "";
      if (checkItems.length > 0) {
        const done = checkItems.filter(l => {
          const t = l.trim();
          return t.startsWith("☑ ") || t.startsWith("[x] ") || t.startsWith("[X] ");
        }).length;
        checklistBadge = `<span class="media-pill" style="background: rgba(16, 185, 129, 0.15); color: #059669; font-weight: 600;">☑ ${done}/${checkItems.length}</span>`;
      }
      const reminderBadge = (note.reminderDate && Number(note.reminderDate) > 0)
        ? `<span class="media-pill" style="background: rgba(245, 158, 11, 0.15); color: #d97706; font-weight: 600;">⏰ Reminder</span>`
        : '';
      const mediaBadges = `
        ${checklistBadge}
        ${reminderBadge}
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
