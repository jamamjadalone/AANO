# AANO Windows Desktop App - Trash & Recycle Bin API Delegate
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import os
import time
import json
import logging
import storage

logger = logging.getLogger("AANO.API.Trash")

class TrashApiMixin:
    def _purge_expired_trash(self, max_age_days=30):
        try:
            cutoff = int(time.time() * 1000) - (max_age_days * 24 * 60 * 60 * 1000)
            notes = storage.load_notes()
            changed = False
            remaining = []
            for n in notes:
                is_expired = (n.get("deleted", False) or n.get("purged", False)) and (n.get("deletedAt", 0) > 0) and (n.get("deletedAt", 0) < cutoff)
                if is_expired:
                    self._cleanup_media_files(n)
                    changed = True
                else:
                    remaining.append(n)
            if changed:
                storage.save_notes(remaining)
        except (json.JSONDecodeError, OSError) as e:
            logger.warning("Trash purge failed: %s", e)

    def restore_note(self, note_id):
        notes = storage.load_notes()
        for n in notes:
            if n.get("id") == note_id:
                n["deleted"] = False
                n["deletedAt"] = 0
                n["archived"] = False
                n["updatedAt"] = int(time.time() * 1000)
                break
        storage.save_notes(notes)
        return True

    def _cleanup_media_files(self, note):
        try:
            for fn in note.get("images", []):
                if fn and isinstance(fn, str):
                    p = os.path.join(storage.IMAGES_DIR, fn)
                    if os.path.exists(p):
                        try:
                            os.remove(p)
                        except OSError as e:
                            logger.debug("Image cleanup error: %s", e)
            audio = note.get("audioUri")
            if audio and isinstance(audio, str) and os.path.exists(audio):
                try:
                    os.remove(audio)
                except OSError as e:
                    logger.debug("Audio cleanup error: %s", e)
        except Exception as e:
            logger.warning("Media cleanup error: %s", e)

    def permanent_delete_note(self, note_id):
        notes = storage.load_notes()
        target = next((n for n in notes if n.get("id") == note_id), None)
        if target:
            self._cleanup_media_files(target)
            target["deleted"] = True
            target["purged"] = True
            target["deletedAt"] = int(time.time() * 1000)
            target["updatedAt"] = int(time.time() * 1000)
            target["title"] = ""
            target["content"] = ""
            target["images"] = []
            target["audioUri"] = None
            target["tags"] = []
            target["folder"] = ""
        storage.save_notes(notes)
        return True

    def empty_trash(self):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n.get("deleted", False) and not n.get("purged", False):
                self._cleanup_media_files(n)
                n["deleted"] = True
                n["purged"] = True
                n["deletedAt"] = now
                n["updatedAt"] = now
                n["title"] = ""
                n["content"] = ""
                n["images"] = []
                n["audioUri"] = None
                n["tags"] = []
                n["folder"] = ""
        storage.save_notes(notes)
        return True