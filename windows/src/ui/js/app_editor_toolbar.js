// AANO Windows Desktop App - Rich Text Toolbar & Block Formatting Engine
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

// --------------------- Note Editor Modal ---------------------
function renderColorPalette() {
  const container = document.getElementById("paletteDots");
  container.replaceChildren();
  const palette = isCurrentThemeDark() ? PALETTE_DARK : PALETTE_LIGHT;
  palette.forEach((color, idx) => {
    const dot = document.createElement("div");
    const isMatched = (typeof editingColor === "number" && editingColor === idx);
    const label = getColorLabel(idx, PALETTE_LABELS[idx] || `Color ${idx + 1}`);
    dot.className = "color-dot" + (isMatched ? " active" : "");
    dot.style.background = color;
    dot.title = `${label} • Right-click to rename`;
    dot.addEventListener("click", () => {
      editingColor = idx;
      applyEditorColor(color);
      document.querySelectorAll(".color-dot").forEach(d => d.classList.remove("active"));
      dot.classList.add("active");
    });
    dot.addEventListener("contextmenu", (e) => {
      e.preventDefault();
      promptRenameColor(idx);
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
      const rawColor = link.dataset.highlight;
      const color = rawColor && rawColor.startsWith("var(")
        ? getComputedStyle(document.documentElement).getPropertyValue(rawColor.slice(4, -1).trim()).trim()
        : rawColor;
      document.execCommand("hiliteColor", false, color);
      document.getElementById("editorContent").focus();
    });
  });

  // Text color options
  document.querySelectorAll("[data-forecolor]").forEach(link => {
    link.addEventListener("click", (e) => {
      e.preventDefault();
      const rawColor = link.dataset.forecolor;
      const color = rawColor && rawColor.startsWith("var(")
        ? getComputedStyle(document.documentElement).getPropertyValue(rawColor.slice(4, -1).trim()).trim()
        : rawColor;
      document.execCommand("foreColor", false, color);
      document.getElementById("editorContent").focus();
    });
  });

  // Checklist toggle
  const btnChecklist = document.getElementById("btnToggleChecklist");
  if (btnChecklist) {
    btnChecklist.addEventListener("click", (e) => {
      e.preventDefault();
      toggleChecklistInEditor();
    });
    btnChecklist.addEventListener("contextmenu", (e) => {
      e.preventDefault();
      promptChecklistActions();
    });
  }

  const editorContent = document.getElementById("editorContent");
  if (editorContent) {
    editorContent.addEventListener("click", () => {
      const sel = window.getSelection();
      if (!sel || !sel.rangeCount) return;
      const node = sel.anchorNode;
      if (node && node.nodeType === Node.TEXT_NODE) {
        const text = node.textContent || "";
        const offset = sel.anchorOffset;
        const startCheck = Math.max(0, offset - 2);
        const endCheck = Math.min(text.length, offset + 2);
        const nearby = text.substring(startCheck, endCheck);
        if (nearby.includes("☐")) {
          const idx = text.indexOf("☐", startCheck);
          if (idx !== -1 && Math.abs(idx - offset) <= 2) {
            node.textContent = text.substring(0, idx) + "☑" + text.substring(idx + 1);
          }
        } else if (nearby.includes("☑")) {
          const idx = text.indexOf("☑", startCheck);
          if (idx !== -1 && Math.abs(idx - offset) <= 2) {
            node.textContent = text.substring(0, idx) + "☐" + text.substring(idx + 1);
          }
        }
      }
    });

    const btnCode = document.getElementById("btnInsertCodeBlock");
    if (btnCode) {
      btnCode.addEventListener("click", (e) => {
        e.preventDefault();
        document.execCommand("formatBlock", false, "pre");
        editorContent.focus();
      });
    }

    editorContent.addEventListener("keydown", (e) => {
      if (e.altKey && (e.key === "ArrowUp" || e.key === "ArrowDown")) {
        e.preventDefault();
        moveCurrentBlock(e.key === "ArrowUp" ? -1 : 1);
      }
    });
  }
}

function moveCurrentBlock(direction) {
  const content = document.getElementById("editorContent");
  if (!content) return;
  const sel = window.getSelection();
  if (!sel || !sel.rangeCount) return;

  let curBlock = sel.anchorNode;
  while (curBlock && curBlock.parentNode !== content) {
    curBlock = curBlock.parentNode;
  }
  if (curBlock && curBlock.parentNode === content && curBlock !== content) {
    if (direction < 0 && curBlock.previousElementSibling) {
      content.insertBefore(curBlock, curBlock.previousElementSibling);
      return;
    }
    if (direction > 0 && curBlock.nextElementSibling) {
      content.insertBefore(curBlock.nextElementSibling, curBlock);
      return;
    }
  }

  const text = content.innerText || "";
  const lines = text.split("\n");
  if (lines.length <= 1) return;
  const range = sel.getRangeAt(0);
  const preCaretRange = range.cloneRange();
  preCaretRange.selectNodeContents(content);
  preCaretRange.setEnd(range.endContainer, range.endOffset);
  const caretPos = preCaretRange.toString().length;

  let charCount = 0;
  let activeLineIdx = 0;
  for (let i = 0; i < lines.length; i++) {
    charCount += lines[i].length + 1;
    if (caretPos <= charCount) {
      activeLineIdx = i;
      break;
    }
  }

  const targetLineIdx = activeLineIdx + direction;
  if (targetLineIdx >= 0 && targetLineIdx < lines.length) {
    const temp = lines[activeLineIdx];
    lines[activeLineIdx] = lines[targetLineIdx];
    lines[targetLineIdx] = temp;
    content.innerText = lines.join("\n");
  }
}
