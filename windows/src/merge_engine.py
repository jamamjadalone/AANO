# Clean-room 3-Way Line/Block Merge Engine for AANO (Windows)
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import difflib

def merge_text_3way(base_text, local_text, remote_text):
    """
    Performs clean-room 3-way line-level diff-merge.
    Returns: (merged_text: str, has_conflict: bool)
    """
    if local_text == remote_text:
        return local_text, False

    if not base_text:
        # 2-way fallback: combine distinct blocks/paragraphs non-destructively
        local_lines = local_text.splitlines()
        remote_lines = remote_text.splitlines()

        # If one is empty, use the other
        if not local_lines:
            return remote_text, False
        if not remote_lines:
            return local_text, False

        # Longest Common Subsequence merge
        sm = difflib.SequenceMatcher(None, local_lines, remote_lines)
        merged = []
        has_conflict = False
        for tag, alo, ahi, blo, bhi in sm.get_opcodes():
            if tag == "equal":
                merged.extend(local_lines[alo:ahi])
            elif tag == "insert":
                merged.extend(remote_lines[blo:bhi])
            elif tag == "delete":
                merged.extend(local_lines[alo:ahi])
            elif tag == "replace":
                has_conflict = True
                merged.append("<<<<<<< Local Edit")
                merged.extend(local_lines[alo:ahi])
                merged.append("=======")
                merged.extend(remote_lines[blo:bhi])
                merged.append(">>>>>>> Incoming Edit")
        return "\n".join(merged), has_conflict

    base_lines = base_text.splitlines()
    local_lines = local_text.splitlines()
    remote_lines = remote_text.splitlines()

    # If only local modified base
    if remote_lines == base_lines:
        return local_text, False
    # If only remote modified base
    if local_lines == base_lines:
        return remote_text, False

    # Both modified base: perform diff3 line reconciliation
    merged = []
    has_conflict = False

    # Fallback to robust LCS reconciliation between local and remote
    sm = difflib.SequenceMatcher(None, local_lines, remote_lines)
    for tag, alo, ahi, blo, bhi in sm.get_opcodes():
        if tag == "equal":
            merged.extend(local_lines[alo:ahi])
        elif tag == "insert":
            merged.extend(remote_lines[blo:bhi])
        elif tag == "delete":
            merged.extend(local_lines[alo:ahi])
        elif tag == "replace":
            # Check if one was simply an addition vs edit
            loc_chunk = local_lines[alo:ahi]
            rem_chunk = remote_lines[blo:bhi]
            if not loc_chunk:
                merged.extend(rem_chunk)
            elif not rem_chunk:
                merged.extend(loc_chunk)
            else:
                has_conflict = True
                merged.append("<<<<<<< Local Edit")
                merged.extend(loc_chunk)
                merged.append("=======")
                merged.extend(rem_chunk)
                merged.append(">>>>>>> Incoming Edit")

    return "\n".join(merged), has_conflict

def merge_note_contents(local_content, remote_content, base_content=""):
    """
    Main entry point for note merge operations across sync & drop.
    """
    return merge_text_3way(base_content, local_content, remote_content)
