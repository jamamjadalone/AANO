"""SQLite FTS5 Full-Text Search indexing engine for AANO Windows Desktop.

Provides sub-millisecond search retrieval across note title, content, folder,
and tags with prefix matching and Porter stemming.

Copyright (C) 2026 Jam Amjad Rasheed
Licensed under GPL-3.0-or-later.
"""

import os
import sqlite3
import re
import logging
from pathlib import Path

logger = logging.getLogger("aano.fts")

APP_DIR = Path(os.environ.get("APPDATA", str(Path.home()))) / "AANO"
APP_DIR.mkdir(parents=True, exist_ok=True)
FTS_DB_PATH = APP_DIR / "fts_index.db"

_conn = None


def get_db():
    global _conn
    if _conn is None:
        try:
            _conn = sqlite3.connect(str(FTS_DB_PATH), check_same_thread=False)
            _conn.row_factory = sqlite3.Row
            _init_schema(_conn)
        except Exception as e:
            logger.error("Failed to initialize SQLite FTS5 database: %s", e)
            return None
    return _conn


def _init_schema(conn):
    with conn:
        conn.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(
                id,
                title,
                content,
                folder,
                tags,
                tokenize='porter unicode61'
            );
        """)


def sync_note(note):
    conn = get_db()
    if not conn or not note:
        return
    note_id = note.get("id")
    if not note_id:
        return

    try:
        with conn:
            conn.execute("DELETE FROM notes_fts WHERE id = ?", (note_id,))
            if not note.get("deleted") and not note.get("archived") and not note.get("purged"):
                tags_str = " ".join(note.get("tags", [])) if isinstance(note.get("tags"), list) else ""
                conn.execute(
                    "INSERT INTO notes_fts(id, title, content, folder, tags) VALUES (?, ?, ?, ?, ?)",
                    (
                        note_id,
                        note.get("title", "") or "",
                        note.get("content", "") or "",
                        note.get("folder", "") or "",
                        tags_str
                    )
                )
    except Exception as e:
        logger.warning("FTS sync_note error for %s: %s", note_id, e)


def delete_note(note_id):
    conn = get_db()
    if not conn or not note_id:
        return
    try:
        with conn:
            conn.execute("DELETE FROM notes_fts WHERE id = ?", (note_id,))
    except Exception as e:
        logger.warning("FTS delete_note error for %s: %s", note_id, e)


def sync_all(notes):
    conn = get_db()
    if not conn:
        return
    try:
        with conn:
            conn.execute("DELETE FROM notes_fts")
            items = []
            for note in notes:
                if not note.get("deleted") and not note.get("archived") and not note.get("purged"):
                    tags_str = " ".join(note.get("tags", [])) if isinstance(note.get("tags"), list) else ""
                    items.append((
                        note.get("id", ""),
                        note.get("title", "") or "",
                        note.get("content", "") or "",
                        note.get("folder", "") or "",
                        tags_str
                    ))
            if items:
                conn.executemany(
                    "INSERT INTO notes_fts(id, title, content, folder, tags) VALUES (?, ?, ?, ?, ?)",
                    items
                )
    except Exception as e:
        logger.warning("FTS sync_all error: %s", e)


def search(query):
    conn = get_db()
    if not conn:
        return []

    clean = (query or "").strip()
    if not clean:
        return []

    # Sanitize query tokens: remove punctuation and special FTS chars
    raw_tokens = re.findall(r"\w+", clean, re.UNICODE)
    if not raw_tokens:
        return []

    # Prefix match token*
    fts_expr = " ".join(f"{tok}*" for tok in raw_tokens)

    try:
        cur = conn.execute(
            "SELECT id FROM notes_fts WHERE notes_fts MATCH ? ORDER BY rank",
            (fts_expr,)
        )
        return [row["id"] for row in cur.fetchall()]
    except Exception as e:
        logger.debug("FTS search query error '%s': %s", fts_expr, e)
        return []
