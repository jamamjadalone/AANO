# AANO Windows Desktop App - Cloud & Google Drive Sync API Delegate
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import threading
import logging
import storage

logger = logging.getLogger("AANO.API.Sync")

class SyncApiMixin:
    def _async_auto_sync(self):
        cfg = storage.load_config()
        if cfg.get("auto_sync", True) and (cfg.get("cloud_token") or cfg.get("google_connected")):
            threading.Thread(target=storage.sync_with_cloud, daemon=True).start()

    def toggle_auto_sync(self, enabled):
        cfg = storage.load_config()
        cfg["auto_sync"] = bool(enabled)
        storage.save_config(cfg)
        if enabled:
            self._async_auto_sync()
        return {"success": True, "auto_sync": cfg["auto_sync"]}

    def login_cloud(self, email, token):
        cfg = storage.load_config()
        cfg["cloud_email"] = email
        cfg["cloud_token"] = token
        storage.save_config(cfg)
        threading.Thread(target=storage.sync_with_cloud, daemon=True).start()
        return {"success": True}

    def get_cloud_status(self):
        cfg = storage.load_config()
        token = cfg.get("cloud_token")
        email = cfg.get("cloud_email")
        last_sync = cfg.get("last_sync_time", 0)
        auto_sync = cfg.get("auto_sync", True)
        expired = cfg.get("session_expired", False)
        return {
            "logged_in": bool(token and not expired),
            "email": email,
            "last_sync": last_sync,
            "auto_sync": auto_sync,
            "session_expired": expired,
            "google_connected": bool(cfg.get("google_connected")),
            "google_email": cfg.get("google_email", "")
        }

    def google_login(self):
        res = storage.google_login()
        if res.get("success"):
            self._async_auto_sync()
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
        cfg["session_expired"] = False
        storage.save_config(cfg)
        return {"success": True}

    def sync_now(self):
        return storage.sync_with_cloud()
