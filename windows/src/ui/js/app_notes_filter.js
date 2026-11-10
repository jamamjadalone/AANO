// AANO Windows Desktop App - Notes Filter, Folders & Batch Selection Engine
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

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
    await loadCustomColorLabels();
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
      currentColorFilter = null;
      currentFilter = "folder:" + folder;
      document.getElementById("currentViewTitle").innerText = folder;
      exitSelectionMode();
      await refreshData();
    });
    container.appendChild(item);
  });
}

function renderPaletteFilterBar(sourceNotes) {
  const container = document.getElementById("paletteSwatchesList");
  if (!container) return;
  container.replaceChildren();

  const isDark = isCurrentThemeDark();
  const palette = isDark ? PALETTE_DARK : PALETTE_LIGHT;
  const notes = sourceNotes || (currentFilter === "archived" ? archivedNotes : (currentFilter === "trash" ? trashNotes : allNotes));

  // Count by color
  const counts = {};
  notes.forEach(n => {
    const c = (n.color !== undefined && n.color !== null) ? n.color : 0;
    counts[c] = (counts[c] || 0) + 1;
  });

  // "All" pill
  const allPill = document.createElement("button");
  allPill.type = "button";
  allPill.className = "palette-pill" + (currentColorFilter === null ? " active" : "");
  
  const allLabel = document.createElement("span");
  allLabel.textContent = "🎨 All";
  allPill.appendChild(allLabel);

  const allCount = document.createElement("span");
  allCount.className = "palette-pill-count";
  allCount.textContent = `(${notes.length})`;
  allPill.appendChild(allCount);

  allPill.addEventListener("click", () => {
    currentColorFilter = null;
    renderNotes();
  });
  container.appendChild(allPill);

  // Preset color pills
  PALETTE_LABELS.forEach((defaultLabel, idx) => {
    const count = counts[idx] || 0;
    if (count > 0 || currentColorFilter === idx) {
      const label = getColorLabel(idx, defaultLabel);
      const pill = document.createElement("button");
      pill.type = "button";
      pill.className = "palette-pill" + (currentColorFilter === idx ? " active" : "");
      pill.title = `${label} (${count}) • Right-click or double-click to rename`;
      
      const dot = document.createElement("span");
      dot.className = "palette-pill-dot";
      dot.style.background = palette[idx] || "#FFFFFF";
      pill.appendChild(dot);

      const text = document.createElement("span");
      text.textContent = label;
      pill.appendChild(text);

      const countSpan = document.createElement("span");
      countSpan.className = "palette-pill-count";
      countSpan.textContent = `(${count})`;
      pill.appendChild(countSpan);

      pill.addEventListener("click", () => {
        if (currentColorFilter === idx) {
          currentColorFilter = null;
        } else {
          currentColorFilter = idx;
        }
        renderNotes();
      });
      pill.addEventListener("contextmenu", (e) => {
        e.preventDefault();
        promptRenameColor(idx);
      });
      pill.addEventListener("dblclick", (e) => {
        e.preventDefault();
        promptRenameColor(idx);
      });
      container.appendChild(pill);
    }
  });

  // Custom colors (if any)
  Object.keys(counts).forEach(cStr => {
    const cVal = parseInt(cStr, 10);
    if (!isNaN(cVal) && (cVal < 0 || cVal >= PALETTE_LABELS.length)) {
      const count = counts[cVal] || 0;
      if (count > 0) {
        const pill = document.createElement("button");
        pill.type = "button";
        pill.className = "palette-pill" + (currentColorFilter === cVal ? " active" : "");
        
        const dot = document.createElement("span");
        dot.className = "palette-pill-dot";
        dot.style.background = resolveNoteColor(cVal);
        pill.appendChild(dot);

        const text = document.createElement("span");
        text.textContent = "Custom";
        pill.appendChild(text);

        const countSpan = document.createElement("span");
        countSpan.className = "palette-pill-count";
        countSpan.textContent = `(${count})`;
        pill.appendChild(countSpan);

        pill.addEventListener("click", () => {
          if (currentColorFilter === cVal) {
            currentColorFilter = null;
          } else {
            currentColorFilter = cVal;
          }
          renderNotes();
        });
        container.appendChild(pill);
      }
    }
  });
}
