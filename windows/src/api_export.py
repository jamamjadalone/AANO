# AANO Windows Desktop App - Export, Import & Package API Delegate
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import os
import json
import time
import uuid
import zipfile
import webbrowser
import logging
import storage
import block_model

logger = logging.getLogger("AANO.API.Export")

class ExportApiMixin:
    def open_external_url(self, url):
        clean_url = (url or "").strip()
        if not (clean_url.startswith("http://") or clean_url.startswith("https://") or clean_url.startswith("mailto:")):
            return {"success": False, "error": "Invalid URL scheme"}
        now = time.time()
        if clean_url == getattr(self, "_last_url", "") and (now - getattr(self, "_last_open_time", 0.0)) < 1.5:
            return {"success": True, "debounced": True}
        self._last_url = clean_url
        self._last_open_time = now
        try:
            webbrowser.open(clean_url, new=2)
            return {"success": True}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def export_backup_json(self):
        notes = storage.load_notes()
        return json.dumps(notes, ensure_ascii=False, indent=2)

    def import_backup_json(self, json_string):
        try:
            imported = json.loads(json_string)
            if not isinstance(imported, list):
                return {"success": False, "error": "Invalid format, must be note list"}
            notes = storage.load_notes()
            existing_ids = {n.get("id") for n in notes}
            added = 0
            for item in imported:
                if item.get("id") and item.get("id") not in existing_ids:
                    notes.append(item)
                    added += 1
            storage.save_notes(notes)
            return {"success": True, "count": added}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def export_vault_zip(self):
        notes = storage.load_notes()
        downloads = os.path.join(os.path.expanduser("~"), "Downloads")
        os.makedirs(downloads, exist_ok=True)
        ts = time.strftime("%Y%m%d_%H%M%S")
        fn = f"AANO_Vault_Backup_{ts}.zip"
        zip_path = os.path.join(downloads, fn)
        try:
            with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
                zf.writestr("notes.json", json.dumps(notes, ensure_ascii=False, indent=2))
                if os.path.exists(storage.IMAGES_DIR):
                    for img in os.listdir(storage.IMAGES_DIR):
                        ip = os.path.join(storage.IMAGES_DIR, img)
                        if os.path.isfile(ip):
                            zf.write(ip, arcname=f"images/{img}")
            return {"success": True, "path": zip_path, "filename": fn}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def restore_vault_zip(self, zip_path):
        if not os.path.exists(zip_path):
            return {"success": False, "error": "File not found"}
        try:
            with zipfile.ZipFile(zip_path, "r") as zf:
                names = zf.namelist()
                if "notes.json" not in names:
                    return {"success": False, "error": "Invalid AANO Vault archive"}
                notes_data = json.loads(zf.read("notes.json").decode("utf-8"))
                notes = storage.load_notes()
                existing_ids = {n.get("id") for n in notes}
                added = 0
                for item in notes_data:
                    if item.get("id") and item.get("id") not in existing_ids:
                        notes.append(item)
                        added += 1
                storage.save_notes(notes)
                for name in names:
                    if name.startswith("images/") and not name.endswith("/"):
                        fn = os.path.basename(name)
                        dest = os.path.join(storage.IMAGES_DIR, fn)
                        if not os.path.exists(dest):
                            with open(dest, "wb") as out:
                                out.write(zf.read(name))
            return {"success": True, "restored_count": added}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def export_note_text(self, note_id):
        notes = storage.load_notes()
        target = next((n for n in notes if n.get("id") == note_id), None)
        if not target:
            return {"success": False, "error": "Note not found"}
        clean_title = (target.get("title") or "Untitled").strip().replace("/", "_").replace("\\", "_")
        plain = block_model.to_plain_text(block_model.html_to_blocks(target.get("content", "")))
        text = f"{target.get('title', '')}\n{'='*len(target.get('title', ''))}\n\n{plain}\n"
        downloads = os.path.join(os.path.expanduser("~"), "Downloads")
        dest = os.path.join(downloads, f"{clean_title}.txt")
        try:
            with open(dest, "w", encoding="utf-8") as f:
                f.write(text)
            return {"success": True, "path": dest}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def export_note_markdown(self, note_id):
        notes = storage.load_notes()
        target = next((n for n in notes if n.get("id") == note_id), None)
        if not target:
            return {"success": False, "error": "Note not found"}
        clean_title = (target.get("title") or "Untitled").strip().replace("/", "_").replace("\\", "_")
        md_body = block_model.to_markdown(block_model.html_to_blocks(target.get("content", "")))
        text = f"# {target.get('title', '')}\n\n{md_body}\n"
        downloads = os.path.join(os.path.expanduser("~"), "Downloads")
        dest = os.path.join(downloads, f"{clean_title}.md")
        try:
            with open(dest, "w", encoding="utf-8") as f:
                f.write(text)
            return {"success": True, "path": dest}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def export_note_html_pdf(self, note_id):
        notes = storage.load_notes()
        target = next((n for n in notes if n.get("id") == note_id), None)
        if not target:
            return {"success": False, "error": "Note not found"}
        clean_title = (target.get("title") or "Untitled").strip().replace("/", "_").replace("\\", "_")
        html = f"""<!DOCTYPE html><html><head><meta charset="utf-8"><title>{clean_title}</title>
<style>body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 40px; color: #222; max-width: 800px; margin: auto; }}</style>
</head><body><h1>{clean_title}</h1><div>{target.get('content', '')}</div></body></html>"""
        downloads = os.path.join(os.path.expanduser("~"), "Downloads")
        dest = os.path.join(downloads, f"{clean_title}.html")
        try:
            with open(dest, "w", encoding="utf-8") as f:
                f.write(html)
            return {"success": True, "path": dest}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def export_note_zip(self, note_id):
        notes = storage.load_notes()
        target = next((n for n in notes if n.get("id") == note_id), None)
        if not target:
            return {"success": False, "error": "Note not found"}
        clean_title = (target.get("title") or "Untitled").strip().replace("/", "_").replace("\\", "_")
        downloads = os.path.join(os.path.expanduser("~"), "Downloads")
        dest = os.path.join(downloads, f"{clean_title}_archive.zip")
        try:
            with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as zf:
                zf.writestr("note.json", json.dumps(target, ensure_ascii=False, indent=2))
                for img in target.get("images", []):
                    ip = self._resolve_image_path(img)
                    if ip:
                        zf.write(ip, arcname=f"media/{img}")
            return {"success": True, "path": dest}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def export_note_aano(self, note_id):
        notes = storage.load_notes()
        target = next((n for n in notes if n.get("id") == note_id), None)
        if not target:
            return {"success": False, "error": "Note not found"}
        clean_title = (target.get("title") or "Untitled").strip().replace("/", "_").replace("\\", "_")
        downloads = os.path.join(os.path.expanduser("~"), "Downloads")
        dest = os.path.join(downloads, f"{clean_title}.aano")
        try:
            with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as zf:
                zf.writestr("note.json", json.dumps(target, ensure_ascii=False, indent=2))
                for img in target.get("images", []):
                    ip = self._resolve_image_path(img)
                    if ip:
                        zf.write(ip, arcname=f"media/{img}")
            return {"success": True, "path": dest}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def import_aano_package(self, package_path):
        if not os.path.exists(package_path):
            return {"success": False, "error": "File not found"}
        try:
            with zipfile.ZipFile(package_path, "r") as zf:
                note_data = json.loads(zf.read("note.json").decode("utf-8"))
                for name in zf.namelist():
                    if name.startswith("media/") and not name.endswith("/"):
                        fn = os.path.basename(name)
                        dest = os.path.join(storage.IMAGES_DIR, fn)
                        if not os.path.exists(dest):
                            with open(dest, "wb") as out:
                                out.write(zf.read(name))
                notes = storage.load_notes()
                note_data["id"] = str(uuid.uuid4())
                notes.insert(0, note_data)
                storage.save_notes(notes)
            return {"success": True, "note": note_data}
        except Exception as e:
            return {"success": False, "error": str(e)}
