# AANO Windows Desktop App - Desktop API Lock Mixin
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
# Modularized under Rule 21 (< 300 lines).

import logging
from app_lock import AppLock

logger = logging.getLogger("AANO.ApiLock")

class LockApiMixin:
    """Provides AppLock and Inactivity PIN endpoints for PyWebView DesktopApi."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._app_lock = AppLock()

    def getLockSettings(self):
        try:
            return self._app_lock.get_settings()
        except Exception as e:
            logger.error("getLockSettings failed: %s", e)
            return {"pin_enabled": False, "timeout_minutes": 5, "error": str(e)}

    def setLockPin(self, pin):
        try:
            return self._app_lock.set_pin(pin)
        except Exception as e:
            logger.error("setLockPin failed: %s", e)
            return {"success": False, "error": str(e)}

    def verifyLockPin(self, pin):
        try:
            return self._app_lock.verify_pin(pin)
        except Exception as e:
            logger.error("verifyLockPin failed: %s", e)
            return {"success": False, "error": str(e)}

    def disableLockPin(self, current_pin):
        try:
            return self._app_lock.clear_pin(current_pin)
        except Exception as e:
            logger.error("disableLockPin failed: %s", e)
            return {"success": False, "error": str(e)}

    def setLockTimeout(self, minutes):
        try:
            success = self._app_lock.set_timeout_minutes(minutes)
            return {"success": success}
        except Exception as e:
            logger.error("setLockTimeout failed: %s", e)
            return {"success": False, "error": str(e)}
