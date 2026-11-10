# AANO Windows Desktop App - Local Storage & Cloudflare Sync Engine
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import json
import uuid
import time
import urllib.request
import urllib.error
import threading
import logging
from drive_sync import GoogleDriveSync

from storage_vault import _dpapi_encrypt, _dpapi_decrypt, VAULT_HEADER  # noqa: F401
from image_processor import process_and_save_image, crop_and_rotate_image, IMAGES_DIR  # noqa: F401

logger = logging.getLogger("AANO.Storage")

APPDATA_DIR = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "AANO")
os.makedirs(APPDATA_DIR, exist_ok=True)

NOTES_FILE = os.path.join(APPDATA_DIR, "notes.json")
CONFIG_FILE = os.path.join(APPDATA_DIR, "config.json")

BASE_URL = "https://aano-api.jamamjadalone.workers.dev"
APP_KEY = "aano_shield_key_2026_pk_sec"

_storage_lock = threading.RLock()
_sync_lock = threading.Lock()

def load_config():
    with _storage_lock:
        if os.path.exists(CONFIG_FILE):
            try:
                with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                    return json.load(f)
            except (json.JSONDecodeError, OSError) as e:
                logger.warning("Failed to load config: %s", e)
        return {
            "cloud_token": None,
            "cloud_email": None,
            "cloud_user_id": None,
            "last_sync_time": 0,
            "auto_sync": True,
            "view_mode": "grid"
        }

def save_config(cfg):
    with _storage_lock:
        tmp = CONFIG_FILE + ".tmp"
        try:
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(cfg, f, indent=2)
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, CONFIG_FILE)
        except OSError as e:
            logger.error("Failed to save config: %s", e)

def get_color_labels():
    cfg = load_config()
    default_labels = {
        "0": "Default", "1": "Yellow", "2": "Green", "3": "Teal",
        "4": "Blue", "5": "Purple", "6": "Magenta", "7": "Red",
        "8": "Orange", "9": "Coral", "10": "Sand", "11": "Slate", "12": "Dark"
    }
    custom = cfg.get("color_labels", {})
    default_labels.update(custom)
    return default_labels

def set_color_label(color_index, label):
    with _storage_lock:
        cfg = load_config()
        if "color_labels" not in cfg:
            cfg["color_labels"] = {}
        cfg["color_labels"][str(color_index)] = label.strip() if label else ""
        save_config(cfg)
        return get_color_labels()

def load_notes():
    with _storage_lock:
        if os.path.exists(NOTES_FILE):
            try:
                with open(NOTES_FILE, "rb") as f:
                    raw = f.read()
                if raw.startswith(VAULT_HEADER):
                    enc_blob = raw[len(VAULT_HEADER):]
                    decrypted = _dpapi_decrypt(enc_blob)
                    return json.loads(decrypted.decode("utf-8"))
                else:
                    return json.loads(raw.decode("utf-8"))
            except (json.JSONDecodeError, OSError, Exception) as e:
                backup_path = f"{NOTES_FILE}.corrupt.{int(time.time())}.bak"
                logger.error("CRITICAL: Failed to parse notes.json (%s). Preserving at %s", e, backup_path)
                try:
                    import shutil
                    shutil.copy2(NOTES_FILE, backup_path)
                except OSError as copy_err:
                    logger.error("Failed to backup corrupt notes: %s", copy_err)
                return []

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
    with _storage_lock:
        tmp = NOTES_FILE + ".tmp"
        try:
            json_text = json.dumps(notes, indent=2, ensure_ascii=False)
            json_bytes = json_text.encode("utf-8")
            try:
                enc_data = _dpapi_encrypt(json_bytes)
                payload = VAULT_HEADER + enc_data
            except Exception as enc_err:
                logger.warning("DPAPI encryption fallback to plaintext: %s", enc_err)
                payload = json_bytes

            with open(tmp, "wb") as f:
                f.write(payload)
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, NOTES_FILE)
            try:
                try:
                    from . import fts_index
                except ImportError:
                    import fts_index
                fts_index.sync_all(notes)
            except Exception as fe:
                logger.debug("FTS sync_all deferred: %s", fe)
            return True
        except OSError as e:
            logger.error("Failed to save notes: %s", e)
            if os.path.exists(tmp):
                try:
                    os.remove(tmp)
                except OSError:
                    pass
            return False

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
        except (json.JSONDecodeError, UnicodeDecodeError):
            return {"error": f"HTTP {e.code}: {e.reason}"}
    except (urllib.error.URLError, TimeoutError) as e:
        logger.warning("Network request failed: %s", e)
        return {"error": str(e)}
    except Exception as e:
        logger.exception("Unexpected error in post_json: %s", e)
        return {"error": str(e)}

drive_sync_instance = GoogleDriveSync(load_config, save_config)

def google_login():
    return drive_sync_instance.start_login_flow()

def google_logout():
    return drive_sync_instance.logout()

def get_google_status():
    cfg = load_config()
    return {
        "connected": bool(cfg.get("google_connected")),
        "email": cfg.get("google_email", ""),
        "name": cfg.get("google_name", "")
    }

def sync_with_cloud():
    if not _sync_lock.acquire(blocking=False):
        logger.info("Sync already running in another thread, skipping concurrent execution.")
        return {"success": False, "in_progress": True, "error": "Sync already in progress"}

    try:
        cfg = load_config()
        if cfg.get("google_connected"):
            notes = load_notes()
            res = drive_sync_instance.sync_notes(notes)
            if res.get("success"):
                merged_notes = res.get("merged_notes", [])
                with _storage_lock:
                    fresh_local_notes = load_notes()
                    fresh_map = {n.get("id"): n for n in fresh_local_notes}
                    final_notes = []
                    for mn in merged_notes:
                        nid = mn.get("id")
                        if nid in fresh_map:
                            fl = fresh_map[nid]
                            if fl.get("updatedAt", 0) > mn.get("updatedAt", 0):
                                final_notes.append(fl)
                            else:
                                final_notes.append(mn)
                        else:
                            final_notes.append(mn)
                    merged_ids = {n.get("id") for n in merged_notes}
                    for fl in fresh_local_notes:
                        if fl.get("id") not in merged_ids:
                            final_notes.append(fl)

                    save_notes(final_notes)
                    cfg = load_config()
                    cfg["last_sync_time"] = int(time.time() * 1000)
                    cfg["session_expired"] = False
                    save_config(cfg)
                return {"success": True, "count": res.get("count", len(final_notes)), "mode": "google_drive"}
            else:
                if res.get("auth_expired"):
                    with _storage_lock:
                        cfg = load_config()
                        cfg["google_connected"] = False
                        cfg["session_expired"] = True
                        save_config(cfg)
                return res

        return {"success": False, "error": "Google Drive sync is not connected. Sign in with your Google Account."}
    finally:
        _sync_lock.release()

def subscribe_newsletter(email: str, source: str = "windows_app") -> dict:
    return post_json("/api/newsletter/subscribe", {"email": email, "source": source})

def unsubscribe_newsletter(email: str) -> dict:
    return post_json("/api/newsletter/unsubscribe", {"email": email})
