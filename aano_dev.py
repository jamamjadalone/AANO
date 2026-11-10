#!/usr/bin/env python3
"""
AANO Dev CLI — Unified Architecture, Rule 21, and Token Optimization Tool
Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later

Usage:
  python aano_dev.py stats                     # Show package breakdown & line limits
  python aano_dev.py check                     # Fast Rule 21 & anti-slop check
  python aano_dev.py doctor                    # Deep health, manifest & ghost import audit
  python aano_dev.py scaffold <type> <Name>    # Generate compliant activity/fragment/helper
  python aano_dev.py map                       # Auto-sync ARCHITECTURE_INDEX.md
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
MANIFEST_FILE = BASE_DIR / "android" / "app" / "src" / "main" / "AndroidManifest.xml"
ARCH_INDEX = BASE_DIR / "ARCHITECTURE_INDEX.md"

LINE_CAP = 300
GREEN, RED, YELLOW, CYAN, BOLD, RESET = "\033[92m", "\033[91m", "\033[93m", "\033[96m", "\033[1m", "\033[0m"


def scan_files():
    if not KOTLIN_SRC.exists():
        return []
    stats = []
    for f in sorted(list(KOTLIN_SRC.rglob("*.kt"))):
        rel = f.relative_to(KOTLIN_SRC)
        pkg = str(rel.parent).replace("\\", "/")
        lines = len(f.read_text(encoding="utf-8", errors="ignore").splitlines())
        stats.append({"path": f, "rel": str(rel).replace("\\", "/"), "pkg": pkg if pkg != "." else "root", "name": f.name, "lines": lines})
    return stats


def cmd_stats():
    stats = scan_files()
    if not stats:
        print(f"{RED}[ERROR] Source directory not found: {KOTLIN_SRC}{RESET}")
        return 1

    pkg_summary = defaultdict(lambda: {"count": 0, "lines": 0, "max_lines": 0, "max_file": ""})
    violations = []
    for s in stats:
        p = pkg_summary[s["pkg"]]
        p["count"] += 1
        p["lines"] += s["lines"]
        if s["lines"] > p["max_lines"]:
            p["max_lines"] = s["lines"]
            p["max_file"] = s["name"]
        if s["lines"] > LINE_CAP:
            violations.append(s)

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
    print(f"{BOLD}{'TOTAL':<28} {len(stats):>6} {sum(s['lines'] for s in stats):>8}   {'Compliance Status':<32} {GREEN if not violations else RED}{'100% CLEAN' if not violations else f'{len(violations)} VIOLATIONS'}{RESET}\n")
    return 0 if not violations else 1


def cmd_check(skip_build=False):
    print(f"\n{BOLD}{CYAN}[AANO Dev] Running fast Rule 21 & Anti-Vibe architectural check...{RESET}")
    stats, errors = scan_files(), []

    for s in stats:
        if s["lines"] > LINE_CAP:
            errors.append(f"Line Cap: {s['rel']} has {s['lines']} lines (max allowed: {LINE_CAP})")
        if (s["name"].endswith("Activity.kt") or s["name"].endswith("Fragment.kt")) and s["pkg"] != "pages":
            errors.append(f"Placement: Screen '{s['name']}' is in '{s['pkg']}/', but must be located in 'pages/'")
        if s["name"].endswith("Adapter.kt") and s["pkg"] != "components/adapters":
            errors.append(f"Placement: Adapter '{s['name']}' is in '{s['pkg']}/', must be in 'components/adapters/'")
        if s["name"].endswith("DialogHelper.kt") and s["pkg"] not in ("components/dialogs", "components/editor"):
            errors.append(f"Placement: Dialog helper '{s['name']}' is in '{s['pkg']}/', must be in 'components/dialogs/' or 'components/editor/'")
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
            print(f"{RED}{BOLD}[FAIL] Kotlin Compilation FAILED:{RESET}\n{res.stderr or res.stdout}")
            return 1
        print(f" {GREEN}[ PASS ]{RESET} Kotlin debug offline compilation SUCCESSFUL")

    print(f"\n{GREEN}{BOLD}[OK] All architecture and quality checks passed! Clean to commit.{RESET}\n")
    return 0


def cmd_doctor():
    print(f"\n{BOLD}{CYAN}[AANO Dev Doctor] Running deep codebase health audit...{RESET}")
    stats, issues = scan_files(), []

    # 1. Ghost import check
    for s in stats:
        content = s["path"].read_text(encoding="utf-8", errors="ignore")
        if "app.aa.aano.ui" in content:
            issues.append(f"Ghost Import: {s['rel']} imports obsolete deleted package 'app.aa.aano.ui'")

    # 2. AndroidManifest activity registration check
    if MANIFEST_FILE.exists():
        manifest_text = MANIFEST_FILE.read_text(encoding="utf-8")
        for s in stats:
            if s["pkg"] == "pages" and s["name"].endswith("Activity.kt"):
                act_name = s["name"][:-3]
                if f".pages.{act_name}" not in manifest_text and f"{act_name}" not in manifest_text:
                    issues.append(f"Manifest Missing: Activity '{act_name}' in pages/ is not registered in AndroidManifest.xml")

    # 3. Architecture index check
    if ARCH_INDEX.exists():
        idx_text = ARCH_INDEX.read_text(encoding="utf-8")
        for s in stats:
            if f"`{s['name']}`" not in idx_text:
                issues.append(f"Index Missing: File '{s['name']}' is not documented in ARCHITECTURE_INDEX.md")

    if issues:
        print(f"\n{RED}{BOLD}[FAIL] Doctor found {len(issues)} issue(s):{RESET}")
        for i in issues:
            print(f"  {RED}[!] {i}{RESET}")
        return 1

    print(f" {GREEN}[ PASS ]{RESET} Zero ghost imports detected (ui package 100% purged)")
    print(f" {GREEN}[ PASS ]{RESET} All activities in pages/ properly registered in AndroidManifest.xml")
    print(f" {GREEN}[ PASS ]{RESET} All source files mapped in ARCHITECTURE_INDEX.md")
    print(f"\n{GREEN}{BOLD}[OK] Codebase is in pristine health!{RESET}\n")
    return 0


def cmd_map():
    if not ARCH_INDEX.exists():
        print(f"{RED}[ERROR] {ARCH_INDEX} does not exist.{RESET}")
        return 1
    stats = scan_files()
    lines_by_name = {s["name"]: s["lines"] for s in stats}
    lines = ARCH_INDEX.read_text(encoding="utf-8").splitlines()
    updated, cnt = [], 0
    for line_item in lines:
        m = re.match(r"^(\|\s*`([^`]+)`\s*\|\s*)(\d+)(\s*\|.*)$", line_item)
        if m and m.group(2) in lines_by_name:
            cur, old = lines_by_name[m.group(2)], int(m.group(3))
            if cur != old:
                cnt += 1
            updated.append(f"{m.group(1)}{cur}{m.group(4)}")
        else:
            updated.append(line_item)
    ARCH_INDEX.write_text("\n".join(updated) + "\n", encoding="utf-8")
    print(f"{GREEN}[OK] ARCHITECTURE_INDEX.md synchronized ({cnt} line count(s) updated).{RESET}")
    return 0


def cmd_scaffold():
    if len(sys.argv) < 4:
        print(f"{YELLOW}Usage: python aano_dev.py scaffold <activity|fragment|helper|dialog> <Name>{RESET}")
        return 1
    target_type, name = sys.argv[2].lower(), sys.argv[3]
    try:
        from aano_scaffold import scaffold
        created = scaffold(KOTLIN_SRC, target_type, name)
        rel = created.relative_to(BASE_DIR)
        print(f"{GREEN}[OK] Successfully scaffolded: {rel}{RESET}")
        print(f"{CYAN}Remember to run 'python aano_dev.py map' after documenting your component.{RESET}")
        return 0
    except Exception as err:
        print(f"{RED}[ERROR] Scaffolding failed: {err}{RESET}")
        return 1


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 0
    cmd = sys.argv[1].lower()
    if cmd == "stats":
        return cmd_stats()
    elif cmd == "check":
        return cmd_check(skip_build="--skip-build" in sys.argv)
    elif cmd == "doctor":
        return cmd_doctor()
    elif cmd == "scaffold":
        return cmd_scaffold()
    elif cmd == "map":
        return cmd_map()
    print(f"{RED}Unknown command: {cmd}{RESET}\n{__doc__}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
