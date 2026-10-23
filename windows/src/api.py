# AANO Windows Desktop App - Python/JS Bridge API
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import time
import uuid
import json
import base64
import shutil
import threading
import logging
import storage
import aano_drop_server
import aano_drop_client

logger = logging.getLogger("AANO.API")

def _parse_color(c):
    if isinstance(c, int):
        return c
    if isinstance(c, str):
        if c.startswith("#"):
            try:
                val = int(c[1:], 16)
                if val <= 0xFFFFFF:
                    val = 0xFF000000 | val
                if val > 0x7FFFFFFF:
                    val -= 0x100000000
                return val
            except (ValueError, TypeError):
                return 0
        try:
            return int(c)
        except (ValueError, TypeError):
            return 0
    return 0

class DesktopApi:
    def __init__(self):
        self._last_url = ""
        self._last_open_time = 0.0
        self._purge_expired_trash()
        # Trigger background sync on startup if logged in
        self._async_auto_sync()
        # Start AANO Drop Wi-Fi Receiver Daemon on port 8765
        self._drop_server = aano_drop_server.AanoDropServer(on_receive_callback=self._on_drop_received)
        self._drop_server.start()

    def _purge_expired_trash(self, max_age_days=30):
        try:
            cutoff = int(time.time() * 1000) - (max_age_days * 24 * 60 * 60 * 1000)
            notes = storage.load_notes()
            changed = False
            remaining = []
            for n in notes:
                is_expired = (n.get("deleted", False) or n.get("purged", False)) and (n.get("deletedAt", 0) > 0) and (n.get("deletedAt", 0) < cutoff)
                if is_expired:
                    changed = True
                else:
                    remaining.append(n)
            if changed:
                storage.save_notes(remaining)
        except (json.JSONDecodeError, OSError) as e:
            logger.warning("Trash purge failed: %s", e)

    def _async_auto_sync(self):
        cfg = storage.load_config()
        if cfg.get("auto_sync", True) and (cfg.get("cloud_token") or cfg.get("google_connected")):
            threading.Thread(target=storage.sync_with_cloud, daemon=True).start()

    def get_notes(self):
        notes = storage.load_notes()
        return [n for n in notes if not n.get("deleted", False) and not n.get("archived", False)]

    def get_archived_notes(self):
        notes = storage.load_notes()
        return [n for n in notes if not n.get("deleted", False) and n.get("archived", False)]

    def get_trashed_notes(self):
        notes = storage.load_notes()
        return [n for n in notes if n.get("deleted", False) and not n.get("purged", False)]

    def restore_note(self, note_id):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n["id"] == note_id:
                now = max(now, n.get("updatedAt", 0) + 1000)
                n["deleted"] = False
                n["deletedAt"] = 0
                n["purged"] = False
                n["updatedAt"] = now
                break
        storage.save_notes(notes)
        self._async_auto_sync()
        return {"success": True}

    def permanent_delete_note(self, note_id):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        updated = []
        for n in notes:
            if n["id"] == note_id:
                if n.get("isLocal", False):
                    continue  # Local-only: drop immediately
                else:
                    stamp = max(now, n.get("updatedAt", 0) + 1000)
                    n["title"] = ""
                    n["content"] = ""
                    n["images"] = []
                    n["audioUri"] = None
                    n["deleted"] = True
                    n["purged"] = True
                    n["deletedAt"] = n.get("deletedAt") or stamp
                    n["updatedAt"] = stamp
                    updated.append(n)
            else:
                updated.append(n)
        storage.save_notes(updated)
        self._async_auto_sync()
        return {"success": True}

    def empty_trash(self):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        updated = []
        for n in notes:
            if n.get("deleted", False) and not n.get("purged", False):
                if n.get("isLocal", False):
                    continue  # Local notes dropped
                else:
                    stamp = max(now, n.get("updatedAt", 0) + 1000)
                    n["title"] = ""
                    n["content"] = ""
                    n["images"] = []
                    n["audioUri"] = None
                    n["deleted"] = True
                    n["purged"] = True
                    n["deletedAt"] = n.get("deletedAt") or stamp
                    n["updatedAt"] = stamp
                    updated.append(n)
            else:
                updated.append(n)
        storage.save_notes(updated)
        self._async_auto_sync()
        return {"success": True}

    def get_all_folders(self):
        notes = storage.load_notes()
        folders = set()
        for n in notes:
            if not n.get("deleted", False) and not n.get("archived", False):
                f = n.get("folder", "").strip()
                if f:
                    folders.add(f)
        return sorted(list(folders))

    def save_note(self, note_data):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        note_id = note_data.get("id")

        if note_id:
            for idx, n in enumerate(notes):
                if n["id"] == note_id:
                    notes[idx].update({
                        "title": note_data.get("title", ""),
                        "content": note_data.get("content", ""),
                        "folder": note_data.get("folder", "General"),
                        "color": _parse_color(note_data.get("color", 0)),
                        "tags": note_data.get("tags", []),
                        "images": note_data.get("images", []),
                        "pinned": bool(note_data.get("pinned", False)),
                        "updatedAt": now
                    })
                    break
        else:
            new_note = {
                "id": str(uuid.uuid4()),
                "title": note_data.get("title", ""),
                "content": note_data.get("content", ""),
                "folder": note_data.get("folder", "General"),
                "color": _parse_color(note_data.get("color", 0)),
                "tags": note_data.get("tags", []),
                "images": note_data.get("images", []),
                "pinned": bool(note_data.get("pinned", False)),
                "createdAt": now,
                "updatedAt": now,
                "deleted": False,
                "deletedAt": 0
            }
            notes.insert(0, new_note)

        storage.save_notes(notes)
        # Automatic cloud sync on every note creation/edit
        self._async_auto_sync()
        return {"success": True}

    def delete_note(self, note_id):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        target = None
        for n in notes:
            if n["id"] == note_id:
                target = n
                break
        if target:
            if target.get("isLocal", False):
                # Local notes bypass trash completely and are permanently removed from disk
                notes = [n for n in notes if n["id"] != note_id]
                storage.save_notes(notes)
                return {"success": True}
            now = max(now, target.get("updatedAt", 0) + 1000)
            target["deleted"] = True
            target["deletedAt"] = now
            target["updatedAt"] = now
            storage.save_notes(notes)
            self._async_auto_sync()
        return {"success": True}

    def archive_note(self, note_id):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n["id"] == note_id:
                now = max(now, n.get("updatedAt", 0) + 1000)
                n["archived"] = True
                n["updatedAt"] = now
                break
        storage.save_notes(notes)
        self._async_auto_sync()
        return {"success": True}

    def unarchive_note(self, note_id):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n["id"] == note_id:
                now = max(now, n.get("updatedAt", 0) + 1000)
                n["archived"] = False
                n["updatedAt"] = now
                break
        storage.save_notes(notes)
        self._async_auto_sync()
        return {"success": True}

    def batch_delete_notes(self, note_ids):
        id_set = set(note_ids)
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        modified = False
        remaining = []
        has_sync_deletes = False
        for n in notes:
            if n["id"] in id_set:
                modified = True
                if n.get("isLocal", False):
                    continue  # Local-only: drop immediately, bypass trash
                now = max(now, n.get("updatedAt", 0) + 1000)
                n["deleted"] = True
                n["deletedAt"] = now
                n["updatedAt"] = now
                has_sync_deletes = True
                remaining.append(n)
            else:
                remaining.append(n)
        if modified:
            storage.save_notes(remaining)
            if has_sync_deletes:
                self._async_auto_sync()
        return {"success": True}

    def batch_archive_notes(self, note_ids):
        id_set = set(note_ids)
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        modified = False
        for n in notes:
            if n["id"] in id_set:
                now = max(now, n.get("updatedAt", 0) + 1000)
                n["archived"] = True
                n["updatedAt"] = now
                modified = True
        if modified:
            storage.save_notes(notes)
            self._async_auto_sync()
        return {"success": True}

    def batch_unarchive_notes(self, note_ids):
        id_set = set(note_ids)
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        modified = False
        for n in notes:
            if n["id"] in id_set:
                now = max(now, n.get("updatedAt", 0) + 1000)
                n["archived"] = False
                n["updatedAt"] = now
                modified = True
        if modified:
            storage.save_notes(notes)
            self._async_auto_sync()
        return {"success": True}

    def toggle_pin(self, note_id):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n["id"] == note_id:
                n["pinned"] = not n.get("pinned", False)
                n["updatedAt"] = now
                break
        storage.save_notes(notes)
        self._async_auto_sync()
        return {"success": True}

    def get_cloud_status(self):
        cfg = storage.load_config()
        return {
            "is_logged_in": bool(cfg.get("cloud_token")),
            "email": cfg.get("cloud_email"),
            "last_sync_time": cfg.get("last_sync_time", 0),
            "auto_sync": cfg.get("auto_sync", True),
            "session_expired": bool(cfg.get("session_expired", False))
        }

    def toggle_auto_sync(self):
        cfg = storage.load_config()
        current = cfg.get("auto_sync", True)
        cfg["auto_sync"] = not current
        storage.save_config(cfg)
        if cfg["auto_sync"] and cfg.get("cloud_token"):
            self._async_auto_sync()
        return {"success": True, "auto_sync": cfg["auto_sync"]}

    def login_cloud(self, email, password):
        res = storage.post_json("/api/login", {"email": email, "password": password})
        if res.get("success"):
            cfg = storage.load_config()
            cfg["cloud_token"] = res.get("token")
            cfg["cloud_email"] = res.get("email")
            cfg["cloud_user_id"] = res.get("userId")
            storage.save_config(cfg)
            # Immediate bi-directional sync upon login
            storage.sync_with_cloud()
            return {"success": True, "email": res.get("email")}
        else:
            return {"success": False, "error": res.get("error", "Login failed")}

    def get_cloud_status(self):
        cfg = storage.load_config()
        if cfg.get("google_connected"):
            return {
                "is_logged_in": True,
                "email": cfg.get("google_email", ""),
                "name": cfg.get("google_name", ""),
                "provider": "google_drive",
                "auto_sync": cfg.get("auto_sync", True),
                "session_expired": False
            }
        is_logged = bool(cfg.get("cloud_token"))
        return {
            "is_logged_in": is_logged,
            "email": cfg.get("cloud_email"),
            "provider": "cloudflare",
            "auto_sync": cfg.get("auto_sync", True),
            "session_expired": cfg.get("session_expired", False)
        }

    def google_login(self):
        res = storage.google_login()
        if res.get("success"):
            storage.sync_with_cloud()
        return res

    def google_logout(self):
        return storage.google_logout()

    def get_google_status(self):
        return storage.get_google_status()

    def logout_cloud(self):
        cfg = storage.load_config()
        cfg["cloud_token"] = None
        cfg["cloud_email"] = None
        cfg["cloud_user_id"] = None
        cfg["last_sync_time"] = 0
        storage.save_config(cfg)
        return {"success": True}

    def sync_now(self):
        return storage.sync_with_cloud()

    def export_backup_json(self):
        notes = storage.load_notes()
        return json.dumps(notes, indent=2, ensure_ascii=False)

    def import_backup_json(self, json_content):
        try:
            incoming = json.loads(json_content)
            if not isinstance(incoming, list):
                return {"success": False, "error": "Invalid backup format"}
            existing = storage.load_notes()
            local_map = {n["id"]: n for n in existing}
            for n in incoming:
                local_map[n["id"]] = n
            storage.save_notes(list(local_map.values()))
            self._async_auto_sync()
            return {"success": True, "count": len(incoming)}
        except (json.JSONDecodeError, KeyError, OSError) as e:
            logger.warning("Import notes error: %s", e)
            return {"success": False, "error": str(e)}

    def export_vault_zip(self):
        try:
            import zipfile
            from datetime import datetime
            notes = storage.load_notes()
            local_notes = [n for n in notes if n.get("isLocal", False) and not n.get("deleted", False)]
            downloads = os.path.join(os.path.expanduser("~"), "Downloads", "AANO")
            os.makedirs(downloads, exist_ok=True)
            ts = datetime.now().strftime("%Y%m%d_%H%M%S")
            zip_filename = f"AANO-LocalVault-{ts}.zip"
            zip_path = os.path.join(downloads, zip_filename)

            with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
                zf.writestr("notes.json", json.dumps(local_notes, indent=2, ensure_ascii=False))
                images_dir = os.path.join(storage.APPDATA_DIR, "images")
                if os.path.exists(images_dir):
                    for img in os.listdir(images_dir):
                        full_img = os.path.join(images_dir, img)
                        if os.path.isfile(full_img):
                            zf.write(full_img, arcname=f"media/images/{img}")

            return {"success": True, "path": zip_path, "count": len(local_notes)}
        except (OSError, Exception) as e:
            logger.warning("Export vault zip error: %s", e)
            return {"success": False, "error": str(e)}

    def restore_vault_zip(self, zip_path):
        try:
            import zipfile
            if not os.path.exists(zip_path):
                return {"success": False, "error": "File does not exist"}
            images_dir = os.path.join(storage.APPDATA_DIR, "images")
            os.makedirs(images_dir, exist_ok=True)
            notes_json_str = None
            with zipfile.ZipFile(zip_path, "r") as zf:
                for member in zf.namelist():
                    if member in ("notes.json", "local_notes.json"):
                        notes_json_str = zf.read(member).decode("utf-8")
                    elif member.startswith("media/images/") or member.startswith("images/"):
                        fn = os.path.basename(member)
                        if fn:
                            with open(os.path.join(images_dir, fn), "wb") as f:
                                f.write(zf.read(member))
            if not notes_json_str:
                return {"success": False, "error": "No notes.json found in zip"}
            restored = json.loads(notes_json_str)
            existing = storage.load_notes()
            existing_map = {n["id"]: n for n in existing}
            for n in restored:
                n["isLocal"] = True
                existing_map[n["id"]] = n
            storage.save_notes(list(existing_map.values()))
            return {"success": True, "count": len(restored)}
        except (zipfile.BadZipFile, json.JSONDecodeError, OSError) as e:
            logger.warning("Restore vault zip error: %s", e)
            return {"success": False, "error": str(e)}

    def open_external_url(self, url):
        if url and (url.startswith("http://") or url.startswith("https://") or url.startswith("mailto:")):
            try:
                now = time.time()
                if url == self._last_url and (now - self._last_open_time) < 1.0:
                    return True
                self._last_url = url
                self._last_open_time = now
                import webbrowser
                webbrowser.open(url)
                return True
            except Exception as e:
                logger.warning("Failed to open external URL %s: %s", url, e)
                return False
        return False

    def _choose_files_dialog(self, save=False, save_filename="", file_types=None):
        try:
            import webview
            if webview.windows:
                w = webview.windows[0]
                if save:
                    ft = file_types or ('Image Files (*.webp;*.jpg;*.png)', 'All files (*.*)')
                    res = w.create_file_dialog(webview.SAVE_DIALOG, save_filename=save_filename, file_types=ft)
                else:
                    ft = file_types or ('Image Files (*.png;*.jpg;*.jpeg;*.webp)', 'All files (*.*)')
                    res = w.create_file_dialog(webview.OPEN_DIALOG, allow_multiple=True, file_types=ft)
                if res:
                    return res if isinstance(res, (list, tuple)) else [res]
        except Exception as e:
            logger.debug("webview file dialog fallback: %s", e)

        try:
            import tkinter as tk
            from tkinter import filedialog
            root = tk.Tk()
            root.withdraw()
            root.attributes("-topmost", True)
            if save:
                path = filedialog.asksaveasfilename(initialfile=save_filename)
                root.destroy()
                return [path] if path else []
            else:
                paths = filedialog.askopenfilenames()
                root.destroy()
                return list(paths) if paths else []
        except Exception as e:
            logger.warning("Tkinter fallback dialog failed: %s", e)
            return []

    def _file_to_data_url(self, filepath):
        if not os.path.exists(filepath):
            return ""
        ext = os.path.splitext(filepath)[1].lower()
        mime = "image/jpeg"
        if ext == ".png":
            mime = "image/png"
        elif ext == ".webp":
            mime = "image/webp"
        try:
            with open(filepath, "rb") as f:
                b64 = base64.b64encode(f.read()).decode("ascii")
                return f"data:{mime};base64,{b64}"
        except Exception:
            return ""

    def attach_images(self, quality_hd=False):
        try:
            selected_files = self._choose_files_dialog(save=False)
            if not selected_files:
                return {"success": False, "cancelled": True}

            results = []
            for src in selected_files:
                if not os.path.isfile(src):
                    continue
                res = storage.process_and_save_image(src, quality_hd=quality_hd)
                if res.get("success"):
                    data_url = self._file_to_data_url(res["path"])
                    results.append({
                        "filename": res["filename"],
                        "path": res["path"],
                        "dataUrl": data_url,
                        "size": res.get("size", 0)
                    })
            return {"success": True, "images": results}
        except Exception as e:
            logger.warning("attach_images error: %s", e)
            return {"success": False, "error": str(e)}

    def get_image_data_url(self, filename):
        try:
            fn = os.path.basename(filename)
            filepath = os.path.join(storage.IMAGES_DIR, fn)
            if not os.path.exists(filepath):
                return {"success": False, "error": "Image not found"}
            data_url = self._file_to_data_url(filepath)
            return {"success": True, "filename": fn, "dataUrl": data_url}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def crop_image(self, filename, crop_box=None, rotation_deg=0, quality_hd=False):
        try:
            fn = os.path.basename(filename)
            res = storage.crop_and_rotate_image(fn, crop_box=crop_box, rotation_deg=rotation_deg, quality_hd=quality_hd)
            if res.get("success"):
                data_url = self._file_to_data_url(res["path"])
                return {
                    "success": True,
                    "filename": res["filename"],
                    "path": res["path"],
                    "dataUrl": data_url,
                    "size": res.get("size", 0)
                }
            return res
        except Exception as e:
            logger.warning("crop_image error: %s", e)
            return {"success": False, "error": str(e)}

    def save_image_to_disk(self, filename):
        try:
            fn = os.path.basename(filename)
            src_path = os.path.join(storage.IMAGES_DIR, fn)
            if not os.path.exists(src_path):
                return {"success": False, "error": "Image not found"}

            save_paths = self._choose_files_dialog(save=True, save_filename=fn)
            if not save_paths:
                return {"success": False, "cancelled": True}

            dest_path = save_paths[0]
            shutil.copy2(src_path, dest_path)
            return {"success": True, "path": dest_path}
        except Exception as e:
            logger.warning("save_image_to_disk error: %s", e)
            return {"success": False, "error": str(e)}

    def delete_image_file(self, filename):
        try:
            fn = os.path.basename(filename)
            src_path = os.path.join(storage.IMAGES_DIR, fn)
            if os.path.exists(src_path):
                os.remove(src_path)
            return {"success": True}
        except Exception as e:
            logger.warning("delete_image_file error: %s", e)
            return {"success": False, "error": str(e)}


    def _on_drop_received(self, notes_count, images_count):
        try:
            import winsound
            winsound.MessageBeep(winsound.MB_ICONASTERISK)
        except Exception:
            pass
        try:
            import webview
            if webview.windows:
                w = webview.windows[0]
                w.evaluate_js(f"window.onDropReceived && window.onDropReceived({notes_count}, {images_count});")
        except Exception as e:
            logger.debug("Failed to evaluate onDropReceived in webview: %s", e)

    def get_drop_info(self):
        if not self._drop_server:
            self._drop_server = aano_drop_server.AanoDropServer(on_receive_callback=self._on_drop_received)
            self._drop_server.start()
        return self._drop_server.get_info()

    def send_drop_to_device(self, target_ip, target_port=8766, note_ids=None, pin=None):
        try:
            all_notes = storage.load_notes()
            if note_ids:
                id_set = set(note_ids)
                selected_notes = [n for n in all_notes if n.get("id") in id_set]
            else:
                selected_notes = [n for n in all_notes if not n.get("deleted", False)]

            if not selected_notes:
                return {"success": False, "error": "No notes found to drop"}

            return aano_drop_client.send_drop_to_device(
                target_ip=target_ip,
                target_port=int(target_port or 8766),
                notes=selected_notes,
                pin=pin
            )
        except Exception as e:
            logger.warning("send_drop_to_device error: %s", e)
            return {"success": False, "error": str(e)}

    def export_note_markdown(self, note_id):
        try:
            notes = storage.load_notes()
            target = next((n for n in notes if n.get("id") == note_id), None)
            if not target:
                return {"success": False, "error": "Note not found"}

            title = target.get("title", "Untitled Note")
            clean_title = "".join(c for c in title if c.isalnum() or c in " _-").strip() or "Note"
            default_fn = f"{clean_title}.md"

            save_paths = self._choose_files_dialog(
                save=True,
                save_filename=default_fn,
                file_types=('Markdown Files (*.md)', 'All files (*.*)')
            )
            if not save_paths:
                return {"success": False, "cancelled": True}

            dest = save_paths[0]
            # Convert basic HTML to clean markdown text
            content = target.get("content", "")
            plain = content.replace("<br>", "\n").replace("</div>", "\n").replace("</p>", "\n\n")
            import re
            plain = re.sub(r'<[^>]+>', '', plain)

            md_text = f"# {title}\n\n"
            folder = target.get("folder", "General")
            md_text += f"> **Folder:** {folder} | **Updated:** {time.strftime('%Y-%m-%d %H:%M')}\n\n"
            md_text += plain

            with open(dest, "w", encoding="utf-8") as f:
                f.write(md_text)

            return {"success": True, "path": dest}
        except Exception as e:
            logger.warning("export_note_markdown error: %s", e)
            return {"success": False, "error": str(e)}

    def export_note_html_pdf(self, note_id):
        try:
            notes = storage.load_notes()
            target = next((n for n in notes if n.get("id") == note_id), None)
            if not target:
                return {"success": False, "error": "Note not found"}

            title = target.get("title", "Untitled Note")
            clean_title = "".join(c for c in title if c.isalnum() or c in " _-").strip() or "Note"
            default_fn = f"{clean_title}.html"

            save_paths = self._choose_files_dialog(
                save=True,
                save_filename=default_fn,
                file_types=('Printable HTML / PDF (*.html)', 'All files (*.*)')
            )
            if not save_paths:
                return {"success": False, "cancelled": True}

            dest = save_paths[0]
            content = target.get("content", "")
            folder = target.get("folder", "General")

            html_doc = f"""<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>{title}</title>
  <style>
    @media print {{ @page {{ margin: 20mm; }} }}
    body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; max-width: 800px; margin: 30px auto; padding: 20px; color: #1e293b; }}
    h1 {{ color: #004D40; border-bottom: 2px solid #E2E8F0; padding-bottom: 10px; font-size: 26px; }}
    .meta {{ font-size: 13px; color: #64748B; margin-bottom: 25px; }}
    .content {{ font-size: 15px; color: #334155; }}
    .footer {{ margin-top: 50px; font-size: 11px; color: #94A3B8; text-align: center; border-top: 1px solid #E2E8F0; padding-top: 15px; }}
  </style>
</head>
<body onload="window.print()">
  <h1>{title}</h1>
  <div class="meta">Folder: {folder} • Exported from AANO Notes</div>
  <div class="content">
    {content}
  </div>
  <div class="footer">Exported from AANO Notes • Private &amp; Offline</div>
</body>
</html>"""

            with open(dest, "w", encoding="utf-8") as f:
                f.write(html_doc)

            return {"success": True, "path": dest}
        except Exception as e:
            logger.warning("export_note_html_pdf error: %s", e)
            return {"success": False, "error": str(e)}
