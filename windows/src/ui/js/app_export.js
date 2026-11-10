// AANO Windows Desktop App - Unified Export Dropdown & Format Converter
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

function setupExportListeners() {
  // Unified Export Dropdown in Editor
  const btnEditorExport = document.getElementById("btnEditorExport");
  const exportMenu = document.getElementById("exportMenu");
  if (btnEditorExport && exportMenu) {
    btnEditorExport.addEventListener("click", (e) => {
      e.stopPropagation();
      exportMenu.classList.toggle("hidden");
    });

    document.addEventListener("click", (e) => {
      if (!e.target.closest(".export-dropdown-wrapper")) {
        exportMenu.classList.add("hidden");
      }
    });
  }

  const btnExportAano = document.getElementById("btnExportAano");
  if (btnExportAano) {
    btnExportAano.addEventListener("click", async () => {
      if (exportMenu) exportMenu.classList.add("hidden");
      if (!editingNote || !editingNote.id) {
        showNativeAlert({ title: "Notice", message: "Please save the note first before exporting." });
        return;
      }
      const api = getApi();
      if (api && api.export_note_aano) {
        const res = await api.export_note_aano(editingNote.id);
        if (res && res.success && res.path) {
          showNativeAlert({ title: "AANO Package Exported", message: `Direct-opening note package created: ${res.path}\n\nAnyone with AANO can open this package with all text, photos, and voice memos intact!` });
        }
      }
    });
  }

  const btnImportAano = document.getElementById("btnImportAano");
  if (btnImportAano) {
    btnImportAano.addEventListener("click", async () => {
      const api = getApi();
      if (api && api.import_aano_package) {
        const res = await api.import_aano_package();
        if (res && res.success && res.note_id) {
          await loadNotes();
          openEditor(res.note_id);
          showNativeAlert({ title: "Note Imported", message: `Successfully imported "${res.title}" with all attachments!` });
        } else if (res && res.error) {
          showNativeAlert({ title: "Import Failed", message: res.error });
        }
      }
    });
  }

  window.importAanoOnStartup = async (filePath) => {
    const api = getApi();
    if (api && api.import_aano_package) {
      const res = await api.import_aano_package(filePath);
      if (res && res.success && res.note_id) {
        await loadNotes();
        openEditor(res.note_id);
      }
    }
  };

  const btnExportTxt = document.getElementById("btnExportTxt");
  if (btnExportTxt) {
    btnExportTxt.addEventListener("click", async () => {
      if (exportMenu) exportMenu.classList.add("hidden");
      if (!editingNote || !editingNote.id) {
        showNativeAlert({ title: "Notice", message: "Please save the note first before exporting." });
        return;
      }
      const api = getApi();
      if (api && api.export_note_text) {
        const res = await api.export_note_text(editingNote.id);
        if (res && res.success && res.path) {
          showNativeAlert({ title: "Text File Exported", message: `File saved to: ${res.path}` });
        }
      }
    });
  }

  const btnExportPdf = document.getElementById("btnExportPdf");
  if (btnExportPdf) {
    btnExportPdf.addEventListener("click", async () => {
      if (exportMenu) exportMenu.classList.add("hidden");
      if (!editingNote || !editingNote.id) {
        showNativeAlert({ title: "Notice", message: "Please save the note first before exporting." });
        return;
      }
      const api = getApi();
      if (api && api.export_note_html_pdf) {
        const res = await api.export_note_html_pdf(editingNote.id);
        if (res && res.success && res.path) {
          showNativeAlert({ title: "PDF / Printable Exported", message: `Printable document saved to: ${res.path}. Open it in your browser to print or save as PDF.` });
        }
      }
    });
  }

  const btnExportMd = document.getElementById("btnExportMd");
  if (btnExportMd) {
    btnExportMd.addEventListener("click", async () => {
      if (exportMenu) exportMenu.classList.add("hidden");
      if (!editingNote || !editingNote.id) {
        showNativeAlert({ title: "Notice", message: "Please save the note first before exporting." });
        return;
      }
      const api = getApi();
      if (api && api.export_note_markdown) {
        const res = await api.export_note_markdown(editingNote.id);
        if (res && res.success && res.path) {
          showNativeAlert({ title: "Markdown Exported", message: `File saved to: ${res.path}` });
        }
      }
    });
  }

  const btnExportZip = document.getElementById("btnExportZip");
  if (btnExportZip) {
    btnExportZip.addEventListener("click", async () => {
      if (exportMenu) exportMenu.classList.add("hidden");
      if (!editingNote || !editingNote.id) {
        showNativeAlert({ title: "Notice", message: "Please save the note first before exporting." });
        return;
      }
      const api = getApi();
      if (api && api.export_note_zip) {
        const res = await api.export_note_zip(editingNote.id);
        if (res && res.success && res.path) {
          showNativeAlert({ title: "ZIP Archive Exported", message: `Complete bundle with notes, photos, and voice memos saved to: ${res.path}` });
        }
      }
    });
  }

  const btnEditorDrop = document.getElementById("btnEditorDrop");
  if (btnEditorDrop) {
    btnEditorDrop.addEventListener("click", () => {
      openDropModal();
    });
  }

}
