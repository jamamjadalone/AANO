# AANO Windows Desktop App - Python/JS Bridge API
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import time
import uuid
import json
import threading
import storage

class DesktopApi:
    def __init__(self):
        # Trigger background sync on startup if logged in
        self._async_auto_sync()

    def _async_auto_sync(self):
        cfg = storage.load_config()
        if cfg.get("auto_sync", True) and cfg.get("cloud_token"):
            threading.Thread(target=storage.sync_with_cloud, daemon=True).start()

    def get_notes(self):
        notes = storage.load_notes()
        return [n for n in notes if not n.get("deleted", False)]

    def get_all_folders(self):
        notes = storage.load_notes()
        folders = set()
        for n in notes:
            if not n.get("deleted", False):
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
                        "color": int(note_data.get("color", 0)),
                        "tags": note_data.get("tags", []),
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
                "color": int(note_data.get("color", 0)),
                "tags": note_data.get("tags", []),
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
        for n in notes:
            if n["id"] == note_id:
                n["deleted"] = True
                n["deletedAt"] = now
                n["updatedAt"] = now
                break
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
        except Exception as e:
            return {"success": False, "error": str(e)}
