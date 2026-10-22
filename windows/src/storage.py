# AANO Windows Desktop App - Local Storage & Cloudflare Sync Engine
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import json
import uuid
import time
import urllib.request
import urllib.error

APPDATA_DIR = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "AANO")
os.makedirs(APPDATA_DIR, exist_ok=True)

NOTES_FILE = os.path.join(APPDATA_DIR, "notes.json")
CONFIG_FILE = os.path.join(APPDATA_DIR, "config.json")

BASE_URL = "https://aano-api.jamamjadalone.workers.dev"
APP_KEY = "aano_shield_key_2026_pk_sec"

def load_config():
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {
        "cloud_token": None,
        "cloud_email": None,
        "cloud_user_id": None,
        "last_sync_time": 0,
        "auto_sync": True,
        "view_mode": "grid"
    }

def save_config(cfg):
    tmp = CONFIG_FILE + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, CONFIG_FILE)
    except Exception as e:
        print(f"[AANO Storage] save_config error: {e}")

def load_notes():
    if os.path.exists(NOTES_FILE):
        try:
            with open(NOTES_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    now = int(time.time() * 1000)
    welcome_notes = [
        {
            "id": str(uuid.uuid4()),
            "title": "Welcome to AANO Notes",
            "content": "<h2>Welcome to your private notes workspace!</h2><p>AANO Notes provides private, hardware-accelerated note taking with <b>rich text formatting</b>, folders, and seamless 2-way sync across phone and PC.</p><ul><li><b>Formatting:</b> Bold, Italic, Headings, Highlights & Colors.</li><li><b>Folders:</b> Organize your thoughts into Work, Personal or custom folders.</li><li><b>Sync:</b> Sign in with your Gmail to sync in real time.</li></ul>",
            "createdAt": now,
            "updatedAt": now,
            "deleted": False,
            "deletedAt": 0,
            "pinned": True,
            "color": 1,
            "folder": "General",
            "tags": ["Welcome", "Getting Started"]
        }
    ]
    save_notes(welcome_notes)
    return welcome_notes

def save_notes(notes):
    tmp = NOTES_FILE + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(notes, f, indent=2, ensure_ascii=False)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, NOTES_FILE)
    except Exception as e:
        print(f"[AANO Storage] save_notes error: {e}")

def post_json(endpoint, payload, token=None):
    url = f"{BASE_URL}{endpoint}"
    data = json.dumps(payload).encode("utf-8")
    headers = {
        "Content-Type": "application/json; charset=UTF-8",
        "X-AANO-KEY": APP_KEY,
        "User-Agent": "AANO-Windows/1.0"
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=12) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        try:
            return json.loads(error_body)
        except Exception:
            return {"error": f"HTTP {e.code}: {e.reason}"}
    except Exception as e:
        return {"error": str(e)}

def sync_with_cloud():
    cfg = load_config()
    token = cfg.get("cloud_token")
    if not token:
        return {"success": False, "error": "Not logged in"}

    notes = load_notes()
    payload = {
        "notes": notes,
        "tasks": [],
        "lastSyncTime": cfg.get("last_sync_time", 0)
    }

    res = post_json("/api/sync", payload, token)
    if res.get("success"):
        server_notes = res.get("notes", [])
        if server_notes:
            local_map = {n["id"]: n for n in notes}
            for sn in server_notes:
                existing = local_map.get(sn["id"])
                if not existing:
                    local_map[sn["id"]] = sn
                else:
                    is_deleted = bool(existing.get("deleted", False) or sn.get("deleted", False))
                    is_archived = bool(existing.get("archived", False) or sn.get("archived", False))
                    sn_updated = sn.get("updatedAt", 0)
                    ex_updated = existing.get("updatedAt", 0)

                    if sn_updated >= ex_updated:
                        merged_note = dict(sn)
                        merged_note["deleted"] = is_deleted
                        merged_note["archived"] = is_archived
                        if is_deleted:
                            merged_note["deletedAt"] = max(existing.get("deletedAt", 0), sn.get("deletedAt", 0), sn_updated)
                        local_map[sn["id"]] = merged_note
                    else:
                        existing["deleted"] = is_deleted
                        existing["archived"] = is_archived
                        if is_deleted:
                            existing["deletedAt"] = max(existing.get("deletedAt", 0), sn.get("deletedAt", 0), ex_updated)
                        local_map[sn["id"]] = existing
            notes = list(local_map.values())
            save_notes(notes)

        cfg["last_sync_time"] = res.get("serverTime", int(time.time() * 1000))
        cfg["session_expired"] = False
        save_config(cfg)
        return {"success": True, "count": len(server_notes)}
    else:
        err_msg = res.get("error", "Sync failed")
        if err_msg == "Unauthorized access token." or "401" in str(err_msg):
            cfg["cloud_token"] = None
            cfg["session_expired"] = True
            save_config(cfg)
            return {"success": False, "error": "Session expired. Please sign in again.", "auth_expired": True}
        return {"success": False, "error": err_msg}
