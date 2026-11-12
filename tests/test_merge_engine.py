# AANO Windows Unit Tests - 3-Way Note Conflict Reconciliation Engine
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "windows", "src")))
from merge_engine import merge_note_contents

def test_merge_non_conflicting_local_change():
    base = "Line 1\nLine 2\nLine 3"
    local = "Line 1 modified\nLine 2\nLine 3"
    remote = "Line 1\nLine 2\nLine 3"
    res, has_conflict = merge_note_contents(local, remote, base)
    assert not has_conflict
    assert "Line 1 modified" in res

def test_merge_non_conflicting_remote_change():
    base = "Line 1\nLine 2\nLine 3"
    local = "Line 1\nLine 2\nLine 3"
    remote = "Line 1\nLine 2\nLine 3 remote"
    res, has_conflict = merge_note_contents(local, remote, base)
    assert not has_conflict
    assert "Line 3 remote" in res
