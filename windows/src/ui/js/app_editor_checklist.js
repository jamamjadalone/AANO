// AANO Windows Desktop App - Note Checklist & Task Actions Engine
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

function toggleChecklistInEditor() {
  const content = document.getElementById("editorContent");
  if (!content) return;
  content.focus();

  const sel = window.getSelection();
  if (!sel || !sel.rangeCount) {
    document.execCommand("insertText", false, "☐ ");
    return;
  }

  const range = sel.getRangeAt(0);
  const selectedText = range.toString();

  if (selectedText.length > 0) {
    const lines = selectedText.split("\n");
    const allChecked = lines.filter(l => l.trim().length > 0).every(l => {
      const t = l.trim();
      return t.startsWith("☐ ") || t.startsWith("☑ ") || t.startsWith("[ ] ") || t.startsWith("[x] ");
    });
    const converted = lines.map(line => {
      const trimmed = line.trimStart();
      if (allChecked) {
        return line.replace(/^(\s*)(?:☐ |☑ |\[ \] |\[x\] )/, "$1");
      }
      if (!trimmed.startsWith("☐ ") && !trimmed.startsWith("☑ ") && !trimmed.startsWith("[ ] ") && !trimmed.startsWith("[x] ")) {
        return (trimmed.startsWith("• ")) ? line.replace("• ", "☐ ") : `☐ ${line}`;
      }
      return line;
    }).join("\n");
    document.execCommand("insertText", false, converted);
  } else {
    const node = sel.anchorNode;
    if (node && node.nodeType === Node.TEXT_NODE) {
      const text = node.textContent || "";
      const offset = sel.anchorOffset;
      const lineStart = text.lastIndexOf("\n", Math.max(0, offset - 1)) + 1;
      const lineEnd = text.indexOf("\n", offset);
      const endPos = lineEnd === -1 ? text.length : lineEnd;
      const curLine = text.substring(lineStart, endPos);
      const trimmed = curLine.trimStart();

      if (trimmed.startsWith("☐ ")) {
        const replaceIdx = curLine.indexOf("☐ ");
        node.textContent = text.substring(0, lineStart + replaceIdx) + "☑ " + text.substring(lineStart + replaceIdx + 2);
        return;
      }
      if (trimmed.startsWith("☑ ")) {
        const replaceIdx = curLine.indexOf("☑ ");
        node.textContent = text.substring(0, lineStart + replaceIdx) + "☐ " + text.substring(lineStart + replaceIdx + 2);
        return;
      }
      if (trimmed.startsWith("[ ] ")) {
        const replaceIdx = curLine.indexOf("[ ] ");
        node.textContent = text.substring(0, lineStart + replaceIdx) + "[x] " + text.substring(lineStart + replaceIdx + 4);
        return;
      }
      if (trimmed.startsWith("[x] ") || trimmed.startsWith("[X] ")) {
        const replaceIdx = curLine.indexOf(trimmed.substring(0, 4));
        node.textContent = text.substring(0, lineStart + replaceIdx) + "[ ] " + text.substring(lineStart + replaceIdx + 4);
        return;
      }
    }
    document.execCommand("insertText", false, "☐ ");
  }
}

async function promptChecklistActions() {
  const choice = await showNativePrompt({
    title: "Checklist Actions",
    message: "Choose an action:\n1: Convert Note to Checklist\n2: Sort Completed to Bottom\n3: Mark All Completed\n4: Uncheck All Items\n5: Remove All Checkboxes",
    placeholder: "1, 2, 3, 4, or 5",
    defaultValue: "2",
    confirmText: "Execute",
    cancelText: "Cancel"
  });
  if (!choice) return;

  const content = document.getElementById("editorContent");
  if (!content) return;
  const raw = content.innerText || "";
  const lines = raw.split("\n");

  if (choice === "1") {
    content.innerText = lines.map(line => {
      const t = line.trimStart();
      if (t.length > 0 && !t.startsWith("☐ ") && !t.startsWith("☑ ") && !t.startsWith("[ ] ") && !t.startsWith("[x] ")) {
        return (t.startsWith("• ")) ? line.replace("• ", "☐ ") : `☐ ${line}`;
      }
      return line;
    }).join("\n");
  } else if (choice === "2") {
    const active = [];
    const completed = [];
    lines.forEach(line => {
      const t = line.trimStart();
      if (t.startsWith("☑") || t.startsWith("[x]") || t.startsWith("[X]")) {
        completed.push(line);
      } else {
        active.push(line);
      }
    });
    content.innerText = [...active, ...completed].join("\n");
  } else if (choice === "3") {
    content.innerText = lines.map(line => {
      const t = line.trimStart();
      if (t.startsWith("☐ ")) return line.replace("☐ ", "☑ ");
      if (t.startsWith("[ ] ")) return line.replace("[ ] ", "[x] ");
      return line;
    }).join("\n");
  } else if (choice === "4") {
    content.innerText = lines.map(line => {
      const t = line.trimStart();
      if (t.startsWith("☑ ")) return line.replace("☑ ", "☐ ");
      if (t.startsWith("[x] ") || t.startsWith("[X] ")) return line.replace(/^(\s*)\[[xX]\] /, "$1[ ] ");
      return line;
    }).join("\n");
  } else if (choice === "5") {
    content.innerText = lines.map(line => line.replace(/^(\s*)(?:☐ |☑ |\[ \] |\[x\] |\[X\] )/, "$1")).join("\n");
  }
}
