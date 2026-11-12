# AANO Windows Unit Tests - AppLock & Inactivity PIN Protection
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "windows", "src")))
from app_lock import AppLock

def test_app_lock_lifecycle():
    lock = AppLock()
    
    # 1. Set PIN
    res = lock.set_pin("1234")
    assert res.get("success") is True, f"set_pin failed: {res}"
    assert lock.is_pin_enabled() is True
    
    # 2. Reject invalid PIN lengths
    invalid_short = lock.set_pin("12")
    assert invalid_short.get("success") is False
    invalid_alpha = lock.set_pin("abcd")
    assert invalid_alpha.get("success") is False

    # 3. Verify correct PIN
    v_ok = lock.verify_pin("1234")
    assert v_ok.get("success") is True
    assert v_ok.get("locked_out") is not True

    # 4. Reject incorrect PIN and track attempts
    v_bad = lock.verify_pin("9999")
    assert v_bad.get("success") is False
    assert v_bad.get("attempts_left") == 4

    # 5. Test brute-force lockout after 5 consecutive failures
    for _ in range(4):
        lock.verify_pin("9999")
    
    v_locked = lock.verify_pin("9999")
    assert v_locked.get("locked_out") is True
    assert lock.is_locked_out() is True
    assert lock.get_lockout_seconds_remaining() > 0

    # 6. Reset lockout and clean PIN
    lock._lockout_until = 0.0
    lock._failed_attempts = 0
    c_res = lock.clear_pin("1234")
    assert c_res.get("success") is True
    assert lock.is_pin_enabled() is False

def test_app_lock_timeout_configuration():
    lock = AppLock()
    assert lock.set_timeout_minutes(15) is True
    assert lock.get_timeout_minutes() == 15
    assert lock.set_timeout_minutes(5) is True
    assert lock.get_timeout_minutes() == 5
