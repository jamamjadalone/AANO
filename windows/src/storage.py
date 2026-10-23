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

import logging
logger = logging.getLogger("AANO.Storage")

def load_config():
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
    tmp = CONFIG_FILE + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, CONFIG_FILE)
    except OSError as e:
        logger.error("Failed to save config: %s", e)

def load_notes():
    if os.path.exists(NOTES_FILE):
        try:
            with open(NOTES_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError) as e:
            logger.warning("Failed to load notes: %s", e)
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
    except OSError as e:
        logger.error("Failed to save notes: %s", e)

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
    cfg = load_config()
    
    # Priority 1: Google Drive Sync (15GB Lifetime Free, zero 500MB DB ceiling)
    if cfg.get("google_connected"):
        notes = load_notes()
        res = drive_sync_instance.sync_notes(notes)
        if res.get("success"):
            merged_notes = res.get("merged_notes", [])
            save_notes(merged_notes)
            cfg["last_sync_time"] = int(time.time() * 1000)
            cfg["session_expired"] = False
            save_config(cfg)
            return {"success": True, "count": res.get("count", len(merged_notes)), "mode": "google_drive"}
        else:
            if res.get("auth_expired"):
                cfg["google_connected"] = False
                cfg["session_expired"] = True
                save_config(cfg)
            return res

    # Priority 2: Legacy Cloudflare Worker Sync (Fallback)
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
        return {"success": True, "count": len(server_notes), "mode": "cloudflare"}
    else:
        err_msg = res.get("error", "Sync failed")
        if err_msg == "Unauthorized access token." or "401" in str(err_msg):
            cfg["cloud_token"] = None
            cfg["session_expired"] = True
            save_config(cfg)
            return {"success": False, "error": "Session expired. Please sign in again.", "auth_expired": True}
        return {"success": False, "error": err_msg}


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


