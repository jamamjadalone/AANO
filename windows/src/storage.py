# AANO Windows Desktop App - Local Storage & Cloudflare Sync Engine
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import json
import uuid
import time
import urllib.request
import urllib.error
from drive_sync import GoogleDriveSync

APPDATA_DIR = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "AANO")
os.makedirs(APPDATA_DIR, exist_ok=True)

NOTES_FILE = os.path.join(APPDATA_DIR, "notes.json")
CONFIG_FILE = os.path.join(APPDATA_DIR, "config.json")
IMAGES_DIR = os.path.join(APPDATA_DIR, "images")
os.makedirs(IMAGES_DIR, exist_ok=True)

BASE_URL = "https://aano-api.jamamjadalone.workers.dev"
APP_KEY = "aano_shield_key_2026_pk_sec"

import threading
import logging
logger = logging.getLogger("AANO.Storage")

_storage_lock = threading.RLock()
_sync_lock = threading.Lock()

import ctypes
from ctypes import wintypes

class _DATA_BLOB(ctypes.Structure):
    _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_char))]

VAULT_HEADER = b"AANO_VAULT_DPAPI_V1\n"

def _dpapi_encrypt(data: bytes, descr: str = "AANO Secure Vault") -> bytes:
    """Encrypts plaintext bytes using Windows built-in DPAPI."""
    if os.name != "nt" or not hasattr(ctypes, "windll") or not hasattr(ctypes.windll, "crypt32"):
        raise OSError("DPAPI is only supported on Windows NT platforms")
    in_blob = _DATA_BLOB(len(data), ctypes.cast(ctypes.c_char_p(data), ctypes.POINTER(ctypes.c_char)))
    out_blob = _DATA_BLOB()
    if not ctypes.windll.crypt32.CryptProtectData(
        ctypes.byref(in_blob), descr, None, None, None, 0, ctypes.byref(out_blob)
    ):
        raise ctypes.WinError()
    try:
        return ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        ctypes.windll.kernel32.LocalFree(out_blob.pbData)

def _dpapi_decrypt(encrypted: bytes) -> bytes:
    """Decrypts DPAPI-encrypted bytes for the current logged-in Windows user."""
    if os.name != "nt" or not hasattr(ctypes, "windll") or not hasattr(ctypes.windll, "crypt32"):
        raise OSError("DPAPI is only supported on Windows NT platforms")
    in_blob = _DATA_BLOB(len(encrypted), ctypes.cast(ctypes.c_char_p(encrypted), ctypes.POINTER(ctypes.c_char)))
    out_blob = _DATA_BLOB()
    if not ctypes.windll.crypt32.CryptUnprotectData(
        ctypes.byref(in_blob), None, None, None, None, 0, ctypes.byref(out_blob)
    ):
        raise ctypes.WinError()
    try:
        return ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        ctypes.windll.kernel32.LocalFree(out_blob.pbData)


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
    return cfg.get("color_labels", {})

def set_color_label(color_index, label):
    cfg = load_config()
    labels = cfg.get("color_labels", {})
    key = str(color_index)
    clean = (label or "").strip()
    if clean:
        labels[key] = clean
    else:
        labels.pop(key, None)
    cfg["color_labels"] = labels
    save_config(cfg)
    return labels

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
                logger.error("CRITICAL: Failed to parse notes.json (%s). Preserving corrupt file at %s", e, backup_path)
                try:
                    import shutil
                    shutil.copy2(NOTES_FILE, backup_path)
                except OSError as copy_err:
                    logger.error("Failed to create backup of corrupt notes: %s", copy_err)
                # Never overwrite damaged notes with welcome templates; return empty to preserve on-disk backup
                return []

        # First-time clean launch only: initialize workspace with welcome note
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
        
        # Priority 1: Google Drive Sync (15GB Lifetime Free, zero 500MB DB ceiling)
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

        # Google Drive is the sole and authoritative cloud sync provider
        return {"success": False, "error": "Google Drive sync is not connected. Sign in with your Google Account to sync."}
    finally:
        _sync_lock.release()

def subscribe_newsletter(email: str, source: str = "windows_app") -> dict:
    """Subscribes an email to the AANO product newsletter via Cloudflare Worker."""
    return post_json("/api/newsletter/subscribe", {"email": email, "source": source})

def unsubscribe_newsletter(email: str) -> dict:
    """Unsubscribes an email from the AANO product newsletter via Cloudflare Worker."""
    return post_json("/api/newsletter/unsubscribe", {"email": email})



def process_and_save_image(src_path, quality_hd=False):
    """
    Copies or converts an image into %APPDATA%/AANO/images/
    Standard: Resized to max 1280px dimension, saved as WebP (quality 80) ~100-150KB.
    HD: Original resolution and quality preserved.
    """
    import random
    from PIL import Image, ImageOps

    if not os.path.exists(src_path):
        return {"success": False, "error": "Source file not found"}

    try:
        ts = int(time.time() * 1000)
        rand = random.randint(100, 999)
        with Image.open(src_path) as im:
            im = ImageOps.exif_transpose(im)
            if quality_hd:
                ext = os.path.splitext(src_path)[1].lower()
                if ext not in [".jpg", ".jpeg", ".png", ".webp"]:
                    ext = ".jpg"
                dest_fn = f"img_{ts}_{rand}{ext}"
                dest_path = os.path.join(IMAGES_DIR, dest_fn)
                if ext in [".jpg", ".jpeg"]:
                    if im.mode in ("RGBA", "P"):
                        im = im.convert("RGB")
                    im.save(dest_path, "JPEG", quality=95)
                elif ext == ".png":
                    im.save(dest_path, "PNG", optimize=True)
                else:
                    im.save(dest_path, "WEBP", quality=95)
            else:
                dest_fn = f"img_{ts}_{rand}.webp"
                dest_path = os.path.join(IMAGES_DIR, dest_fn)
                max_dim = 1280
                w, h = im.size
                if w > max_dim or h > max_dim:
                    ratio = w / h
                    if w > h:
                        new_w = max_dim
                        new_h = int(max_dim / ratio)
                    else:
                        new_h = max_dim
                        new_w = int(max_dim * ratio)
                    im = im.resize((new_w, new_h), Image.Resampling.LANCZOS)
                if im.mode not in ("RGB", "RGBA"):
                    im = im.convert("RGB")
                im.save(dest_path, "WEBP", quality=80)

            return {
                "success": True,
                "filename": dest_fn,
                "path": dest_path,
                "size": os.path.getsize(dest_path),
                "width": im.width,
                "height": im.height
            }
    except Exception as e:
        logger.warning("Failed to process image %s: %s", src_path, e)
        return {"success": False, "error": str(e)}


def crop_and_rotate_image(filename, crop_box=None, rotation_deg=0, quality_hd=False):
    """
    Applies crop and/or rotation to an image in %APPDATA%/AANO/images/
    crop_box: [left, top, right, bottom] in original image pixel space
    rotation_deg: Clockwise rotation in degrees (e.g. 90, 180, 270)
    """
    import random
    from PIL import Image

    src_path = os.path.join(IMAGES_DIR, filename)
    if not os.path.exists(src_path):
        return {"success": False, "error": "Target image not found"}

    try:
        ts = int(time.time() * 1000)
        rand = random.randint(100, 999)
        dest_fn = f"img_crop_{ts}_{rand}.webp"
        dest_path = os.path.join(IMAGES_DIR, dest_fn)

        with Image.open(src_path) as im:
            if rotation_deg and (rotation_deg % 360 != 0):
                im = im.rotate(-rotation_deg, expand=True)

            if crop_box and len(crop_box) == 4:
                left, top, right, bottom = crop_box
                w, h = im.size
                left = max(0, min(int(left), w - 1))
                top = max(0, min(int(top), h - 1))
                right = max(left + 1, min(int(right), w))
                bottom = max(top + 1, min(int(bottom), h))
                im = im.crop((left, top, right, bottom))

            if not quality_hd:
                max_dim = 1280
                w, h = im.size
                if w > max_dim or h > max_dim:
                    ratio = w / h
                    if w > h:
                        new_w = max_dim
                        new_h = int(max_dim / ratio)
                    else:
                        new_h = max_dim
                        new_w = int(max_dim * ratio)
                    im = im.resize((new_w, new_h), Image.Resampling.LANCZOS)

            if im.mode not in ("RGB", "RGBA"):
                im = im.convert("RGB")

            q = 95 if quality_hd else 80
            im.save(dest_path, "WEBP", quality=q)

        return {
            "success": True,
            "filename": dest_fn,
            "path": dest_path,
            "size": os.path.getsize(dest_path),
            "width": im.width,
            "height": im.height
        }
    except Exception as e:
        logger.warning("Failed to crop image %s: %s", filename, e)
        return {"success": False, "error": str(e)}


