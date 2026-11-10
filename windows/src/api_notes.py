# AANO Windows Desktop App - Notes API Delegate
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import time
import uuid
import logging
import storage
import block_model

logger = logging.getLogger("AANO.API.Notes")

def _parse_color(c):
    if isinstance(c, int):
        return c
    if isinstance(c, str):
        if c.startswith("#"):
            try:
                val = int(c[1:], 16)
                if val <= 0xFFFFFF:
                    val = 0xFF000000 | val
                if val > 0x7FFFFFFF:
                    val -= 0x100000000
                return val
            except (ValueError, TypeError):
                return 0
        try:
            return int(c)
        except (ValueError, TypeError):
            return 0
    return 0

class NotesApiMixin:
    def get_notes(self):
        notes = storage.load_notes()
        return [n for n in notes if not n.get("deleted", False) and not n.get("archived", False)]

    def get_archived_notes(self):
        notes = storage.load_notes()
        return [n for n in notes if not n.get("deleted", False) and n.get("archived", False)]

    def get_trashed_notes(self):
        notes = storage.load_notes()
        return [n for n in notes if n.get("deleted", False) and not n.get("purged", False)]

    def search_notes_fts(self, query):
        clean = (query or "").strip()
        notes = self.get_notes()
        if not clean:
            return notes
        try:
            try:
                from . import fts_index
            except ImportError:
                import fts_index
            matching_ids = set(fts_index.search(clean))
            if matching_ids:
                return [n for n in notes if n.get("id") in matching_ids]
            return [n for n in notes if clean.lower() in n.get("title", "").lower() or clean.lower() in n.get("content", "").lower()]
        except Exception as e:
            logger.warning("FTS search fallback to substring: %s", e)
            return [n for n in notes if clean.lower() in n.get("title", "").lower() or clean.lower() in n.get("content", "").lower()]

    def get_color_labels(self):
        return storage.get_color_labels()

    def set_color_label(self, color_index, label):
        return storage.set_color_label(color_index, label)

    def get_all_folders(self):
        notes = storage.load_notes()
        folders = set()
        for n in notes:
            if not n.get("deleted", False):
                f = (n.get("folder") or "").strip()
                if f:
                    folders.add(f)
        return sorted(list(folders))

    def save_note(self, note_data):
        now = int(time.time() * 1000)
        note_id = note_data.get("id") or str(uuid.uuid4())
        notes = storage.load_notes()

        color_val = _parse_color(note_data.get("color", 0))

        existing = next((n for n in notes if n.get("id") == note_id), None)
        if existing:
            existing["title"] = note_data.get("title", "").strip() or "Untitled Note"
            existing["content"] = note_data.get("content", "")
            existing["updatedAt"] = now
            existing["color"] = color_val
            existing["colorIndex"] = note_data.get("colorIndex", color_val)
            existing["colorLabel"] = note_data.get("colorLabel", "")
            existing["folder"] = note_data.get("folder", "").strip()
            existing["tags"] = note_data.get("tags", [])
            existing["pinned"] = bool(note_data.get("pinned", False))
            existing["isLocal"] = bool(note_data.get("isLocal", False))
            existing["type"] = note_data.get("type", existing.get("type", 0))
            existing["reminderType"] = note_data.get("reminderType", existing.get("reminderType", 0))
            existing["reminderDate"] = note_data.get("reminderDate", existing.get("reminderDate", 0))
            if "images" in note_data:
                existing["images"] = note_data.get("images")
            if "audioUri" in note_data:
                existing["audioUri"] = note_data.get("audioUri")
            if "audioDuration" in note_data:
                existing["audioDuration"] = note_data.get("audioDuration")
            saved_note = existing
        else:
            saved_note = {
                "id": note_id,
                "title": note_data.get("title", "").strip() or "Untitled Note",
                "content": note_data.get("content", ""),
                "createdAt": now,
                "updatedAt": now,
                "deleted": False,
                "deletedAt": 0,
                "archived": False,
                "pinned": bool(note_data.get("pinned", False)),
                "isLocal": bool(note_data.get("isLocal", False)),
                "color": color_val,
                "colorIndex": note_data.get("colorIndex", color_val),
                "colorLabel": note_data.get("colorLabel", ""),
                "folder": note_data.get("folder", "").strip(),
                "tags": note_data.get("tags", []),
                "images": note_data.get("images", []),
                "audioUri": note_data.get("audioUri"),
                "audioDuration": note_data.get("audioDuration", 0),
                "type": note_data.get("type", 0),
                "reminderType": note_data.get("reminderType", 0),
                "reminderDate": note_data.get("reminderDate", 0)
            }
            notes.insert(0, saved_note)

        if not storage.save_notes(notes):
            return {"success": False, "error": "Storage error"}
        return {"success": True, "note": saved_note}

    def delete_note(self, note_id):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n.get("id") == note_id:
                n["deleted"] = True
                n["deletedAt"] = now
                n["updatedAt"] = now
                break
        storage.save_notes(notes)
        return True

    def archive_note(self, note_id):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n.get("id") == note_id:
                n["archived"] = True
                n["updatedAt"] = now
                break
        storage.save_notes(notes)
        return True

    def unarchive_note(self, note_id):
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n.get("id") == note_id:
                n["archived"] = False
                n["updatedAt"] = now
                break
        storage.save_notes(notes)
        return True

    def get_note_blocks(self, note_id):
        notes = storage.load_notes()
        target = next((n for n in notes if n.get("id") == note_id), None)
        if not target:
            return []
        return block_model.html_to_blocks(target.get("content", ""))

    def save_note_blocks(self, note_id, blocks):
        html = block_model.blocks_to_html(blocks)
        return self.save_note({"id": note_id, "content": html})

    def format_note_markdown(self, note_id):
        notes = storage.load_notes()
        target = next((n for n in notes if n.get("id") == note_id), None)
        if not target:
            return ""
        blocks = block_model.html_to_blocks(target.get("content", ""))
        return block_model.to_markdown(blocks)

    def format_note_plain_text(self, note_id):
        notes = storage.load_notes()
        target = next((n for n in notes if n.get("id") == note_id), None)
        if not target:
            return ""
        blocks = block_model.html_to_blocks(target.get("content", ""))
        return block_model.to_plain_text(blocks)

    def batch_delete_notes(self, note_ids):
        if not note_ids:
            return True
        id_set = set(note_ids)
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n.get("id") in id_set:
                n["deleted"] = True
                n["deletedAt"] = now
                n["updatedAt"] = now
        storage.save_notes(notes)
        return True

    def batch_archive_notes(self, note_ids):
        if not note_ids:
            return True
        id_set = set(note_ids)
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n.get("id") in id_set:
                n["archived"] = True
                n["updatedAt"] = now
        storage.save_notes(notes)
        return True

    def batch_unarchive_notes(self, note_ids):
        if not note_ids:
            return True
        id_set = set(note_ids)
        notes = storage.load_notes()
        now = int(time.time() * 1000)
        for n in notes:
            if n.get("id") in id_set:
                n["archived"] = False
                n["updatedAt"] = now
        storage.save_notes(notes)
        return True

    def toggle_pin(self, note_id):
        notes = storage.load_notes()
        for n in notes:
            if n.get("id") == note_id:
                n["pinned"] = not n.get("pinned", False)
                n["updatedAt"] = int(time.time() * 1000)
                break
        storage.save_notes(notes)
        return True