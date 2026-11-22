# AANO Windows Desktop App - Desktop API Theme Mixin
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
# Modularized under Rule 21 (< 300 lines).

import logging
import storage

logger = logging.getLogger("AANO.ApiTheme")

class ThemeApiMixin:
    """Provides theme preference endpoints for PyWebView DesktopApi."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

    def get_theme(self):
        try:
            return storage.get_theme()
        except Exception as e:
            logger.error("get_theme failed: %s", e)
            return "light"

    def set_theme(self, theme: str):
        try:
            return storage.set_theme(theme)
        except Exception as e:
            logger.error("set_theme failed: %s", e)
            return {"success": False, "error": str(e)}
