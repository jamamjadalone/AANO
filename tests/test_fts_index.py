# AANO Windows Unit Tests - SQLite FTS5 Full-Text Search Engine
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "windows", "src")))
import fts_index

def test_fts_indexing_and_search():
    note1 = {
        "id": "test_note_1",
        "title": "Secret Apple Pie Recipe",
        "content": "Use cinnamon and granny smith apples",
        "folder": "Recipes",
        "tags": ["baking", "dessert"]
    }
    note2 = {
        "id": "test_note_2",
        "title": "Project Antigravity Roadmap",
        "content": "Phase 1 release checklist and security",
        "folder": "Work",
        "tags": ["dev"]
    }

    try:
        fts_index.sync_note(note1)
        fts_index.sync_note(note2)

        # 1. Search apple
        results = fts_index.search("apple")
        assert "test_note_1" in results

        # 2. Search roadmap
        results2 = fts_index.search("roadmap")
        assert "test_note_2" in results2

        # 3. Delete note1 and verify search updates
        fts_index.delete_note("test_note_1")
        results3 = fts_index.search("apple")
        assert "test_note_1" not in results3
    finally:
        fts_index.delete_note("test_note_1")
        fts_index.delete_note("test_note_2")
