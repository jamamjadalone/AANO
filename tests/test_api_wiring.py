# AANO Windows Unit Tests - API Facade & Mixin Wiring
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "windows", "src")))
from api import DesktopApi

def test_desktop_api_wiring():
    # Verify that DesktopApi class possesses all expected endpoints from mixins
    expected_endpoints = [
        "get_notes", "save_note", "delete_note", "get_trashed_notes", "restore_note",
        "permanent_delete_note", "sync_now", "send_drop_to_device", "crop_image",
        "export_note_aano", "getLockSettings", "verifyLockPin"
    ]
    for endpoint in expected_endpoints:
        assert hasattr(DesktopApi, endpoint), f"DesktopApi missing mixin method: {endpoint}"

def test_desktop_api_instance_lock_initialization():
    api = DesktopApi()
    assert hasattr(api, "_app_lock"), "DesktopApi instance missing _app_lock attribute"
    settings = api.getLockSettings()
    assert isinstance(settings, dict)
    assert "pin_enabled" in settings
    if hasattr(api, "_drop_server") and api._drop_server:
        api._drop_server.stop()
