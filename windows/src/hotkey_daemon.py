# AANO Windows Desktop App - Global Win32 Hotkey Daemon (Ctrl + Alt + N)
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import ctypes
from ctypes import wintypes
import threading
import logging

logger = logging.getLogger("AANO.Hotkey")

MOD_ALT = 0x0001
MOD_CONTROL = 0x0002
MOD_NOREPEAT = 0x4000
VK_N = 0x4E
HOTKEY_ID = 1001
WM_HOTKEY = 0x0312

class HotkeyDaemon:
    def __init__(self, callback):
        self.callback = callback
        self.thread = None
        self._running = False
        self._thread_id = None

    def start(self):
        if self._running:
            return
        self._running = True
        self.thread = threading.Thread(target=self._run_message_loop, daemon=True)
        self.thread.start()

    def _run_message_loop(self):
        user32 = ctypes.windll.user32
        kernel32 = ctypes.windll.kernel32
        self._thread_id = kernel32.GetCurrentThreadId()

        # Register Ctrl + Alt + N
        modifiers = MOD_CONTROL | MOD_ALT | MOD_NOREPEAT
        if not user32.RegisterHotKey(None, HOTKEY_ID, modifiers, VK_N):
            # Fallback without MOD_NOREPEAT if older Windows build
            if not user32.RegisterHotKey(None, HOTKEY_ID, MOD_CONTROL | MOD_ALT, VK_N):
                logger.warning("Failed to register global hotkey Ctrl+Alt+N")
                self._running = False
                return

        logger.info("Registered global hotkey (Ctrl + Alt + N) for instant Scratchpad")

        msg = wintypes.MSG()
        while self._running:
            res = user32.GetMessageW(ctypes.byref(msg), None, 0, 0)
            if res <= 0:
                break
            if msg.message == WM_HOTKEY and msg.wParam == HOTKEY_ID:
                try:
                    if self.callback:
                        self.callback()
                except Exception as e:
                    logger.warning("Hotkey callback error: %s", e)
            user32.TranslateMessage(ctypes.byref(msg))
            user32.DispatchMessageW(ctypes.byref(msg))

        user32.UnregisterHotKey(None, HOTKEY_ID)

    def stop(self):
        self._running = False
        if self._thread_id:
            user32 = ctypes.windll.user32
            WM_QUIT = 0x0012
            user32.PostThreadMessageW(self._thread_id, WM_QUIT, 0, 0)
