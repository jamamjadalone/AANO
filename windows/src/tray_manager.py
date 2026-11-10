# AANO Windows Desktop App - System Tray Manager
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
# Modularized under Rule 21 (< 300 lines).

import os
import sys
import logging
from PIL import Image

logger = logging.getLogger("AANO.Tray")

class TrayManager:
    """Manages the Windows System Tray icon, menu actions, and minimize-to-tray lifecycle."""

    def __init__(self, on_open, on_scratchpad=None, on_lock=None, on_exit=None):
        self.on_open = on_open
        self.on_scratchpad = on_scratchpad
        self.on_lock = on_lock
        self.on_exit = on_exit
        self.icon = None
        self._is_running = False

    def _get_icon_image(self):
        base_dir = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
        icon_path = os.path.join(base_dir, "ui", "aano.png")
        if os.path.exists(icon_path):
            try:
                return Image.open(icon_path)
            except Exception as e:
                logger.warning("Failed to open %s: %s", icon_path, e)

        # Fallback 64x64 RGBA canvas with blue dot
        img = Image.new("RGBA", (64, 64), (30, 41, 59, 255))
        return img

    def start(self):
        try:
            import pystray
        except ImportError:
            logger.warning("pystray not installed; system tray disabled.")
            return False

        try:
            image = self._get_icon_image()
            menu_items = [
                pystray.MenuItem("Open AANO Notes", self._handle_open, default=True),
            ]
            if self.on_scratchpad:
                menu_items.append(pystray.MenuItem("New Quick Scratchpad", self._handle_scratchpad))
            if self.on_lock:
                menu_items.append(pystray.MenuItem("Lock App Now", self._handle_lock))
            menu_items.append(pystray.Menu.SEPARATOR)
            menu_items.append(pystray.MenuItem("Exit", self._handle_exit))

            menu = pystray.Menu(*menu_items)
            self.icon = pystray.Icon("AANO Notes", image, "AANO Notes", menu)
            self.icon.run_detached()
            self._is_running = True
            logger.info("AANO System Tray daemon started.")
            return True
        except Exception as e:
            logger.error("Failed to initialize system tray icon: %s", e)
            return False

    def stop(self):
        if self.icon and self._is_running:
            self._is_running = False
            try:
                self.icon.stop()
            except Exception as e:
                logger.debug("Tray icon stop exception: %s", e)

    def _handle_open(self, icon=None, item=None):
        try:
            if self.on_open:
                self.on_open()
        except Exception as e:
            logger.error("Tray on_open error: %s", e)

    def _handle_scratchpad(self, icon=None, item=None):
        try:
            if self.on_scratchpad:
                self.on_scratchpad()
        except Exception as e:
            logger.error("Tray on_scratchpad error: %s", e)

    def _handle_lock(self, icon=None, item=None):
        try:
            if self.on_lock:
                self.on_lock()
        except Exception as e:
            logger.error("Tray on_lock error: %s", e)

    def _handle_exit(self, icon=None, item=None):
        self.stop()
        try:
            if self.on_exit:
                self.on_exit()
        except Exception as e:
            logger.error("Tray on_exit error: %s", e)
