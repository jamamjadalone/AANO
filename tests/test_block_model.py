# AANO Windows Unit Tests - Block Model & Markdown Conversion
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "windows", "src")))
from block_model import parse_blocks, blocks_to_markdown, blocks_to_plain_text

def test_parse_blocks():
    raw = "# Header 1\nSome normal paragraph text.\n[ ] Task item 1\n[x] Task item 2"
    blocks = parse_blocks(raw)
    assert len(blocks) >= 3
    assert blocks[0].get("type") == "heading"
    assert blocks[0].get("text") == "Header 1"

def test_blocks_to_markdown_roundtrip():
    blocks = [
        {"type": "heading", "level": 1, "text": "Shopping List"},
        {"type": "checkbox", "checked": False, "text": "Milk"},
        {"type": "checkbox", "checked": True, "text": "Bread"},
        {"type": "paragraph", "text": "Don't forget butter"}
    ]
    md = blocks_to_markdown(blocks)
    assert "# Shopping List" in md
    assert "[ ] Milk" in md
    assert "[x] Bread" in md
    assert "Don't forget butter" in md

def test_blocks_to_plain_text():
    blocks = [
        {"type": "heading", "text": "Title"},
        {"type": "paragraph", "text": "Paragraph content"}
    ]
    txt = blocks_to_plain_text(blocks)
    assert "Title" in txt
    assert "Paragraph content" in txt
