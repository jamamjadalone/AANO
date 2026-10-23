#!/usr/bin/env python3
"""
AANO Automated Version Manager
Synchronizes version strings across Android, Windows, Web Docs, and Changelog.
Usage:
    python bump_version.py <new_version>
    python bump_version.py --check
"""
import os
import sys
import re
import datetime

ROOT_DIR = os.path.dirname(os.path.abspath(__file__))
VERSION_FILE = os.path.join(ROOT_DIR, "VERSION")
GRADLE_FILE = os.path.join(ROOT_DIR, "android", "app", "build.gradle.kts")
ISS_FILE = os.path.join(ROOT_DIR, "windows", "installer", "aano.iss")
DOCS_HTML = os.path.join(ROOT_DIR, "docs", "index.html")
CHANGELOG_FILE = os.path.join(ROOT_DIR, "CHANGELOG.md")

def get_current_version():
    if os.path.exists(VERSION_FILE):
        with open(VERSION_FILE, "r", encoding="utf-8") as f:
            v = f.read().strip()
            if v:
                return v
    return "1.0.1-beta"

def check_status():
    current_v = get_current_version()
    print("=" * 60)
    print(f"AANO VERSION AUDIT (Single Source: {current_v})")
    print("=" * 60)
    if os.path.exists(GRADLE_FILE):
        with open(GRADLE_FILE, "r", encoding="utf-8") as f:
            txt = f.read()
            vname = re.search(r'versionName\s*=\s*"([^"]+)"', txt)
            vcode = re.search(r'versionCode\s*=\s*(\d+)', txt)
            name_val = vname.group(1) if vname else "NOT FOUND"
            code_val = vcode.group(1) if vcode else "NOT FOUND"
            status = "MATCH" if name_val == current_v else "MISMATCH"
            print(f"Android Gradle  : versionName=\"{name_val}\" (code={code_val}) -> [{status}]")
    if os.path.exists(ISS_FILE):
        with open(ISS_FILE, "r", encoding="utf-8") as f:
            txt = f.read()
            m = re.search(r'#define\s+MyAppVersion\s+"([^"]+)"', txt)
            iss_v = m.group(1) if m else "NOT FOUND"
            status = "MATCH" if iss_v == current_v else "MISMATCH"
            print(f"Windows Inno ISS: MyAppVersion=\"{iss_v}\" -> [{status}]")
    if os.path.exists(DOCS_HTML):
        with open(DOCS_HTML, "r", encoding="utf-8") as f:
            txt = f.read()
            m = re.search(r'v([0-9a-zA-Z.-]+)\s*(&bull;|•)\s*Open Source', txt)
            doc_v = m.group(1) if m else "NOT FOUND"
            status = "MATCH" if doc_v == current_v else "MISMATCH"
            print(f"Docs Landing    : v{doc_v} -> [{status}]")
    print("=" * 60)

def bump(new_version):
    if new_version.startswith("v"):
        new_version = new_version[1:]
    if not re.match(r"^\d+\.\d+\.\d+.*$", new_version):
        print(f"Error: Invalid version format \"{new_version}\". Expected SemVer (e.g. 1.1.0).")
        sys.exit(1)
    old_version = get_current_version()
    print(f"Bumping AANO: v{old_version} -> v{new_version}\n")
    with open(VERSION_FILE, "w", encoding="utf-8") as f:
        f.write(f"{new_version}\n")
    print(f"OK [VERSION] -> {new_version}")
    if os.path.exists(GRADLE_FILE):
        with open(GRADLE_FILE, "r", encoding="utf-8") as f:
            gradle_txt = f.read()
        def inc_code(match):
            old_code = int(match.group(1))
            new_code = old_code + 1
            print(f"OK [Android] versionCode: {old_code} -> {new_code}")
            return f"versionCode = {new_code}"
        gradle_txt = re.sub(r"versionCode\s*=\s*(\d+)", inc_code, gradle_txt, count=1)
        gradle_txt = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_version}"', gradle_txt, count=1)
        print(f'OK [Android] versionName -> "{new_version}"')
        with open(GRADLE_FILE, "w", encoding="utf-8") as f:
            f.write(gradle_txt)
    if os.path.exists(ISS_FILE):
        with open(ISS_FILE, "r", encoding="utf-8") as f:
            iss_txt = f.read()
        iss_txt = re.sub(r'#define\s+MyAppVersion\s+"[^"]+"', f'#define MyAppVersion "{new_version}"', iss_txt)
        iss_txt = re.sub(r"OutputBaseFilename=AANO-Setup-v[^\r\n]+", f"OutputBaseFilename=AANO-Setup-v{new_version}", iss_txt)
        with open(ISS_FILE, "w", encoding="utf-8") as f:
            f.write(iss_txt)
        print(f"OK [Windows Inno Setup] MyAppVersion & OutputBaseFilename -> v{new_version}")
    if os.path.exists(DOCS_HTML):
        with open(DOCS_HTML, "r", encoding="utf-8") as f:
            docs_txt = f.read()
        docs_txt = re.sub(r"v[0-9a-zA-Z.-]+\s*&bull;\s*Open Source", f"v{new_version} &bull; Open Source", docs_txt)
        with open(DOCS_HTML, "w", encoding="utf-8") as f:
            f.write(docs_txt)
        print(f"OK [Docs HTML] Badge -> v{new_version}")
    if os.path.exists(CHANGELOG_FILE):
        with open(CHANGELOG_FILE, "r", encoding="utf-8") as f:
            ch_txt = f.read()
        header_needle = f"## [{new_version}]"
        if header_needle not in ch_txt:
            today_str = datetime.date.today().strftime("%Y-%m-%d")
            new_section = f"## [{new_version}] - {today_str}\n\n### Added\n- \n\n### Changed\n- \n\n### Fixed\n- \n\n"
            pos = ch_txt.find("## [")
            if pos != -1:
                ch_txt = ch_txt[:pos] + new_section + ch_txt[pos:]
            else:
                ch_txt = ch_txt + "\n\n" + new_section
            with open(CHANGELOG_FILE, "w", encoding="utf-8") as f:
                f.write(ch_txt)
            print(f"OK [CHANGELOG.md] Added section for [{new_version}]")
    print("\n" + "=" * 60)
    print("ALL FILES SYNCHRONIZED SUCCESSFULLY!")
    print(f'  git commit -am "chore(release): bump version to v{new_version}"')
    print(f"  git tag v{new_version}")
    print("=" * 60)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage:")
        print("    python bump_version.py <new_version>")
        print("    python bump_version.py --check")
        sys.exit(1)
    arg = sys.argv[1].strip()
    if arg == "--check":
        check_status()
    else:
        bump(arg)
