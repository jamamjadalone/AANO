# Clean AST Block Model for AANO Notes (Windows)
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import re

def parse_blocks(content):
    """
    Parses note content into structured AST blocks:
    - heading: {type: 'heading', level: 1..3, text: ...}
    - checkbox: {type: 'checkbox', checked: bool, text: ...}
    - bullet: {type: 'bullet', text: ...}
    - quote: {type: 'quote', text: ...}
    - code: {type: 'code', lang: ..., text: ...}
    - image: {type: 'image', alt: ..., url: ...}
    - paragraph: {type: 'paragraph', text: ...}
    """
    if not content:
        return []

    lines = content.splitlines()
    blocks = []
    in_code = False
    code_lang = ""
    code_lines = []

    for line in lines:
        trimmed = line.strip()
        if trimmed.startswith("```"):
            if in_code:
                blocks.append({
                    "type": "code",
                    "lang": code_lang,
                    "text": "\n".join(code_lines)
                })
                code_lines = []
                code_lang = ""
                in_code = False
            else:
                in_code = True
                code_lang = trimmed[3:].strip()
            continue

        if in_code:
            code_lines.append(line)
            continue

        if trimmed.startswith("# "):
            blocks.append({"type": "heading", "level": 1, "text": trimmed[2:].strip()})
        elif trimmed.startswith("## "):
            blocks.append({"type": "heading", "level": 2, "text": trimmed[3:].strip()})
        elif trimmed.startswith("### "):
            blocks.append({"type": "heading", "level": 3, "text": trimmed[4:].strip()})
        elif re.match(r"^(\[x\]|\[X\]|☑|✔)\s*", trimmed):
            text = re.sub(r"^(\[x\]|\[X\]|☑|✔)\s*", "", trimmed)
            blocks.append({"type": "checkbox", "checked": True, "text": text})
        elif re.match(r"^(\[ \]|☐)\s*", trimmed):
            text = re.sub(r"^(\[ \]|☐)\s*", "", trimmed)
            blocks.append({"type": "checkbox", "checked": False, "text": text})
        elif re.match(r"^([-*•])\s+", trimmed):
            text = re.sub(r"^([-*•])\s+", "", trimmed)
            blocks.append({"type": "bullet", "text": text})
        elif trimmed.startswith("> "):
            blocks.append({"type": "quote", "text": trimmed[2:].strip()})
        elif trimmed.startswith("![") and "](" in trimmed and trimmed.endswith(")"):
            alt = trimmed[2:trimmed.index("](")]
            url = trimmed[trimmed.index("](") + 2:-1]
            blocks.append({"type": "image", "alt": alt, "url": url})
        else:
            blocks.append({"type": "paragraph", "text": line})

    if in_code and code_lines:
        blocks.append({
            "type": "code",
            "lang": code_lang,
            "text": "\n".join(code_lines)
        })

    return blocks

def blocks_to_markdown(blocks):
    """Serializes AST blocks back into standard Markdown string."""
    out = []
    for b in blocks:
        b_type = b.get("type", "paragraph")
        if b_type == "heading":
            level = max(1, min(6, b.get("level", 1)))
            out.append(f"{'#' * level} {b.get('text', '')}")
        elif b_type == "checkbox":
            box = "[x]" if b.get("checked") else "[ ]"
            out.append(f"{box} {b.get('text', '')}")
        elif b_type == "bullet":
            out.append(f"- {b.get('text', '')}")
        elif b_type == "quote":
            out.append(f"> {b.get('text', '')}")
        elif b_type == "code":
            lang = b.get("lang", "")
            out.append(f"```{lang}\n{b.get('text', '')}\n```")
        elif b_type == "image":
            out.append(f"![{b.get('alt', '')}]({b.get('url', '')})")
        else:
            out.append(b.get("text", ""))
    return "\n".join(out)

def blocks_to_plain_text(blocks):
    """Strips Markdown syntax for clean plain text export."""
    out = []
    for b in blocks:
        b_type = b.get("type", "paragraph")
        if b_type == "heading":
            out.append(b.get("text", ""))
        elif b_type == "checkbox":
            box = "[x]" if b.get("checked") else "[ ]"
            out.append(f"{box} {b.get('text', '')}")
        elif b_type == "bullet":
            out.append(f"• {b.get('text', '')}")
        elif b_type == "quote":
            out.append(b.get("text", ""))
        elif b_type == "code":
            out.append(b.get("text", ""))
        elif b_type == "image":
            out.append(f"[Photo: {b.get('alt', '')}]")
        else:
            out.append(b.get("text", ""))
    return "\n".join(out)

def reorder_block(blocks, from_index, to_index):
    """Re-orders a block from from_index to to_index safely."""
    if not (0 <= from_index < len(blocks)) or not (0 <= to_index < len(blocks)):
        return blocks
    cloned = list(blocks)
    item = cloned.pop(from_index)
    cloned.insert(to_index, item)
    return cloned
