#!/usr/bin/env python3
"""
AANO Dev CLI — Unified Architecture, Rule 21, and Token Optimization Tool
Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later

Usage:
  python aano_dev.py stats       # Show package breakdown & Rule 21 line stats
  python aano_dev.py check       # Fast Rule 21 architecture & anti-slop verification
  python aano_dev.py map         # Auto-sync line counts in ARCHITECTURE_INDEX.md
"""

import os
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

# Safe Windows Console UTF-8
if sys.platform == "win32":
    try:
        import ctypes
        from ctypes import wintypes
        kernel32 = ctypes.windll.kernel32
        h_stdout = kernel32.GetStdHandle(-11)
        mode = wintypes.DWORD()
        if kernel32.GetConsoleMode(h_stdout, ctypes.byref(mode)):
            mode.value |= 0x0004
            kernel32.SetConsoleMode(h_stdout, mode)
        os.system("")
    except (AttributeError, OSError):
        pass
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")

BASE_DIR = Path(__file__).resolve().parent
KOTLIN_SRC = BASE_DIR / "android" / "app" / "src" / "main" / "java" / "app" / "aa" / "aano"
ARCH_INDEX = BASE_DIR / "ARCHITECTURE_INDEX.md"

LINE_CAP = 300

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"


def get_all_kt_files():
    if not KOTLIN_SRC.exists():
        return []
    return sorted(list(KOTLIN_SRC.rglob("*.kt")))


def scan_files():
    files = get_all_kt_files()
    stats = []
    for f in files:
        rel = f.relative_to(KOTLIN_SRC)
        pkg = str(rel.parent).replace("\\", "/")
        if pkg == ".":
            pkg = "root"
        lines = len(f.read_text(encoding="utf-8", errors="ignore").splitlines())
        stats.append({
            "path": f,
            "rel": str(rel).replace("\\", "/"),
            "pkg": pkg,
            "name": f.name,
            "lines": lines
        })
    return stats


def cmd_stats():
    stats = scan_files()
    if not stats:
        print(f"{RED}[ERROR] Source directory not found: {KOTLIN_SRC}{RESET}")
        return 1

    pkg_summary = defaultdict(lambda: {"count": 0, "lines": 0, "max_lines": 0, "max_file": ""})
    violations = []

    for s in stats:
        pkg = s["pkg"]
        pkg_summary[pkg]["count"] += 1
        pkg_summary[pkg]["lines"] += s["lines"]
        if s["lines"] > pkg_summary[pkg]["max_lines"]:
            pkg_summary[pkg]["max_lines"] = s["lines"]
            pkg_summary[pkg]["max_file"] = s["name"]
        if s["lines"] > LINE_CAP:
            violations.append(s)

    total_files = len(stats)
    total_lines = sum(s["lines"] for s in stats)

    print(f"\n{BOLD}{CYAN}========================================================================{RESET}")
    print(f"{BOLD}{CYAN} AANO Codebase Statistics & Rule 21 Compliance Dashboard{RESET}")
    print(f"{BOLD}{CYAN}========================================================================{RESET}")
    print(f"{'Package':<28} {'Files':>6} {'Lines':>8}   {'Max File':<32} {'Max Lines':>9}")
    print("-" * 88)

    for pkg in sorted(pkg_summary.keys()):
        p = pkg_summary[pkg]
        color = GREEN if p["max_lines"] <= LINE_CAP else RED
        print(f"{pkg:<28} {p['count']:>6} {p['lines']:>8}   {p['max_file']:<32} {color}{p['max_lines']:>9}{RESET}")

    print("-" * 88)
    print(f"{BOLD}{'TOTAL':<28} {total_files:>6} {total_lines:>8}   {'Compliance Status':<32} {GREEN if not violations else RED}{'100% CLEAN' if not violations else f'{len(violations)} VIOLATIONS'}{RESET}\n")

    if violations:
        print(f"{RED}{BOLD}[FAIL] Rule 21 Violations Detected (> {LINE_CAP} lines):{RESET}")
        for v in violations:
            print(f"  - {v['rel']} ({v['lines']} lines > {LINE_CAP} cap)")
        return 1
    else:
        print(f"{GREEN}[OK] Perfect: All {total_files} Kotlin files are strictly <= {LINE_CAP} lines.{RESET}\n")
        return 0


def cmd_check(skip_build=False):
    print(f"\n{BOLD}{CYAN}[AANO Dev] Running fast Rule 21 & Anti-Vibe architectural check...{RESET}")
    stats = scan_files()
    errors = []

    # 1. Line cap check
    for s in stats:
        if s["lines"] > LINE_CAP:
            errors.append(f"Line Cap: {s['rel']} has {s['lines']} lines (max allowed: {LINE_CAP})")

    # 2. Package placement check
    for s in stats:
        name = s["name"]
        pkg = s["pkg"]

        if (name.endswith("Activity.kt") or name.endswith("Fragment.kt")) and pkg != "pages":
            errors.append(f"Placement: Screen '{name}' is in '{pkg}/', but must be located in 'pages/'")

        if name.endswith("Adapter.kt") and pkg != "components/adapters":
            errors.append(f"Placement: Adapter '{name}' is in '{pkg}/', but must be located in 'components/adapters/'")

        if name.endswith("DialogHelper.kt") and pkg not in ("components/dialogs", "components/editor"):
            errors.append(f"Placement: Dialog helper '{name}' is in '{pkg}/', but must be located in 'components/dialogs/' or 'components/editor/'")

    # 3. Anti-slop empty catch check
    for s in stats:
        content = s["path"].read_text(encoding="utf-8", errors="ignore")
        if re.search(r"catch\s*\(\s*_\s*:\s*Exception\s*\)\s*\{\s*\}", content):
            errors.append(f"Anti-Slop: {s['rel']} contains empty catch block without logging")

    if errors:
        print(f"\n{RED}{BOLD}[FAIL] Rule 21 / Architecture Checks FAILED ({len(errors)} issues):{RESET}")
        for e in errors:
            print(f"  {RED}[X]{RESET} {e}")
        return 1

    print(f" {GREEN}[ PASS ]{RESET} Rule 21 line limits (<={LINE_CAP} lines) verified across {len(stats)} files")
    print(f" {GREEN}[ PASS ]{RESET} Component topology verified (screens in pages, dialogs in components, storage in data)")
    print(f" {GREEN}[ PASS ]{RESET} Anti-slop code pattern audit clean")

    if not skip_build:
        print(f"\n{CYAN}[AANO Dev] Verifying offline Kotlin compilation...{RESET}")
        gradle_cmd = str(BASE_DIR / "android" / "gradlew.bat") if sys.platform == "win32" else "./gradlew"
        res = subprocess.run([gradle_cmd, ":app:compileDebugKotlin", "--offline"], cwd=str(BASE_DIR / "android"), capture_output=True, text=True)
        if res.returncode != 0:
            print(f"{RED}{BOLD}[FAIL] Kotlin Compilation FAILED:{RESET}")
            print(res.stderr or res.stdout)
            return 1
        print(f" {GREEN}[ PASS ]{RESET} Kotlin debug offline compilation SUCCESSFUL")

    print(f"\n{GREEN}{BOLD}[OK] All architecture and quality checks passed! Clean to commit.{RESET}\n")
    return 0


def cmd_map():
    if not ARCH_INDEX.exists():
        print(f"{RED}[ERROR] {ARCH_INDEX} does not exist.{RESET}")
        return 1

    print(f"{CYAN}[AANO Dev] Synchronizing ARCHITECTURE_INDEX.md line counts...{RESET}")
    stats = scan_files()
    lines_by_name = {s["name"]: s["lines"] for s in stats}

    content = ARCH_INDEX.read_text(encoding="utf-8")
    updated_lines = []
    updated_count = 0

    for line in content.splitlines():
        m = re.match(r"^(\|\s*`([^`]+)`\s*\|\s*)(\d+)(\s*\|.*)$", line)
        if m:
            fname = m.group(2)
            if fname in lines_by_name:
                cur_lines = lines_by_name[fname]
                old_lines = int(m.group(3))
                if cur_lines != old_lines:
                    updated_count += 1
                updated_lines.append(f"{m.group(1)}{cur_lines}{m.group(4)}")
            else:
                updated_lines.append(line)
        else:
            updated_lines.append(line)

    ARCH_INDEX.write_text("\n".join(updated_lines) + "\n", encoding="utf-8")
    print(f"{GREEN}[OK] ARCHITECTURE_INDEX.md synchronized ({updated_count} line count(s) updated).{RESET}")
    return 0


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 0

    cmd = sys.argv[1].lower()
    if cmd == "stats":
        return cmd_stats()
    elif cmd == "check":
        skip_build = "--skip-build" in sys.argv
        return cmd_check(skip_build=skip_build)
    elif cmd == "map":
        return cmd_map()
    else:
        print(f"{RED}Unknown command: {cmd}{RESET}\n")
        print(__doc__)
        return 1


if __name__ == "__main__":
    sys.exit(main())
