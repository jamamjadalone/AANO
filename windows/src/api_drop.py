# AANO Windows Desktop App - AANO Drop Local Wi-Fi P2P API Delegate
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import logging
import aano_drop_client

logger = logging.getLogger("AANO.API.Drop")

class DropApiMixin:
    def _on_drop_received(self, count, sender):
        try:
            import webview
            for window in webview.windows:
                js_code = f"window.dispatchEvent(new CustomEvent('aano-drop-received', {{ detail: {{ count: {count}, sender: '{sender}' }} }}));"
                window.evaluate_js(js_code)
        except Exception as e:
            logger.debug("Failed to dispatch drop event to JS: %s", e)

    def get_drop_info(self):
        if not hasattr(self, "_drop_server") or not self._drop_server:
            return {"running": False}
        return self._drop_server.get_status()

    def send_drop_to_device(self, target_ip, note_ids=None, pin=None):
        notes = self.get_notes()
        if note_ids:
            id_set = set(note_ids)
            notes_to_send = [n for n in notes if n.get("id") in id_set]
        else:
            notes_to_send = notes

        if not notes_to_send:
            return {"success": False, "error": "No notes selected to drop"}

        return aano_drop_client.send_notes(target_ip, notes_to_send, pin=pin)

    def get_paired_peer(self):
        if not hasattr(self, "_drop_server") or not self._drop_server:
            return None
        return self._drop_server.get_paired_peer()

    def pair_with_device(self, ip, device_name):
        if not hasattr(self, "_drop_server") or not self._drop_server:
            return False
        return self._drop_server.set_paired_peer(ip, device_name)

    def unpair_peer(self):
        if not hasattr(self, "_drop_server") or not self._drop_server:
            return False
        return self._drop_server.unpair_peer()

    def scan_nearby_devices(self, timeout=2.0):
        return aano_drop_client.scan_nearby_devices(timeout=timeout)
