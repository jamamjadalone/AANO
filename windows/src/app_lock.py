# AANO Windows Desktop App - App Lock & Brute-Force Protection
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
# Modularized under Rule 21 (< 300 lines).

import time
import hashlib
import secrets
import logging
from storage import load_config, save_config

logger = logging.getLogger("AANO.AppLock")

ITERATIONS = 100_000
MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
BASE_LOCKOUT_SECONDS = 30
MAX_LOCKOUT_SECONDS = 600

class AppLock:
    def __init__(self):
        self._failed_attempts = 0
        self._lockout_until = 0.0

    def is_pin_enabled(self) -> bool:
        cfg = load_config()
        return bool(cfg.get("pin_hash") and cfg.get("pin_salt"))

    def get_timeout_minutes(self) -> int:
        cfg = load_config()
        return int(cfg.get("lock_timeout_minutes", 5))

    def set_timeout_minutes(self, minutes: int) -> bool:
        cfg = load_config()
        cfg["lock_timeout_minutes"] = max(0, min(120, int(minutes)))
        save_config(cfg)
        return True

    def get_settings(self) -> dict:
        return {
            "pin_enabled": self.is_pin_enabled(),
            "timeout_minutes": self.get_timeout_minutes(),
            "is_locked_out": self.is_locked_out(),
            "lockout_seconds_remaining": self.get_lockout_seconds_remaining()
        }

    def set_pin(self, pin: str) -> dict:
        pin = str(pin).strip()
        if not (4 <= len(pin) <= 8 and pin.isdigit()):
            return {"success": False, "error": "PIN must be between 4 and 8 digits"}
        salt = secrets.token_bytes(16)
        h = hashlib.pbkdf2_hmac("sha256", pin.encode("utf-8"), salt, ITERATIONS)
        cfg = load_config()
        cfg["pin_salt"] = salt.hex()
        cfg["pin_hash"] = h.hex()
        if "lock_timeout_minutes" not in cfg:
            cfg["lock_timeout_minutes"] = 5
        save_config(cfg)
        self._failed_attempts = 0
        self._lockout_until = 0.0
        return {"success": True}

    def clear_pin(self, current_pin: str) -> dict:
        verify_res = self.verify_pin(current_pin)
        if not verify_res.get("success"):
            return verify_res
        cfg = load_config()
        cfg.pop("pin_salt", None)
        cfg.pop("pin_hash", None)
        save_config(cfg)
        self._failed_attempts = 0
        self._lockout_until = 0.0
        return {"success": True}

    def is_locked_out(self) -> bool:
        return time.time() < self._lockout_until

    def get_lockout_seconds_remaining(self) -> int:
        remaining = self._lockout_until - time.time()
        return max(0, int(remaining))

    def verify_pin(self, pin: str) -> dict:
        if self.is_locked_out():
            rem = self.get_lockout_seconds_remaining()
            return {
                "success": False,
                "locked_out": True,
                "lockout_seconds": rem,
                "error": f"Too many failed attempts. Try again in {rem}s"
            }

        cfg = load_config()
        stored_hash = cfg.get("pin_hash")
        stored_salt = cfg.get("pin_salt")

        if not stored_hash or not stored_salt:
            return {"success": True, "pin_disabled": True}

        try:
            salt = bytes.fromhex(stored_salt)
            expected = bytes.fromhex(stored_hash)
            calc = hashlib.pbkdf2_hmac("sha256", str(pin).strip().encode("utf-8"), salt, ITERATIONS)
            is_valid = secrets.compare_digest(expected, calc)
        except Exception as e:
            logger.error("PIN verification exception: %s", e)
            return {"success": False, "error": "Internal verification error"}

        if is_valid:
            self._failed_attempts = 0
            self._lockout_until = 0.0
            return {"success": True}
        else:
            self._failed_attempts += 1
            if self._failed_attempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT:
                penalty_level = self._failed_attempts - MAX_ATTEMPTS_BEFORE_LOCKOUT
                duration = min(MAX_LOCKOUT_SECONDS, BASE_LOCKOUT_SECONDS * (2 ** penalty_level))
                self._lockout_until = time.time() + duration
                return {
                    "success": False,
                    "locked_out": True,
                    "lockout_seconds": duration,
                    "error": f"Incorrect PIN. App locked for {duration} seconds."
                }
            attempts_left = MAX_ATTEMPTS_BEFORE_LOCKOUT - self._failed_attempts
            return {
                "success": False,
                "locked_out": False,
                "attempts_left": attempts_left,
                "error": f"Incorrect PIN. {attempts_left} attempts remaining."
            }
