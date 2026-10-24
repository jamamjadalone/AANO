# AANO Unified Local Build & Workspace Cleaner
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import sys
import shutil
import subprocess
import argparse

# Ensure safe output on Windows CMD / PowerShell
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = os.path.abspath(os.path.dirname(__file__))
VERSION_FILE = os.path.join(REPO_ROOT, "VERSION")

def get_version():
    if os.path.exists(VERSION_FILE):
        with open(VERSION_FILE, "r", encoding="utf-8") as f:
            v = f.read().strip()
            if v:
                return v
    return "1.2.0-beta.1"

def print_banner(version):
    print("=" * 65)
    print(f"  [AANO] UNIFIED LOCAL BUILD & CLEANER - v{version}")
    print("  Zero WAN Data | Pure Local Compilation | Auto Workspace Purge")
    print("=" * 65)

def clean_intermediates():
    print("\n[*] Cleaning intermediate build caches...")
    cleanup_dirs = [
        os.path.join(REPO_ROOT, "windows", "build"),
        os.path.join(REPO_ROOT, "windows", "dist"),
        os.path.join(REPO_ROOT, "windows", "build_installer"),
        os.path.join(REPO_ROOT, "android", "app", "build", "intermediates"),
        os.path.join(REPO_ROOT, "android", "app", "build", "tmp"),
        os.path.join(REPO_ROOT, "android", "app", "build", "kotlin"),
    ]
    cleaned_count = 0
    for d in cleanup_dirs:
        if os.path.exists(d):
            try:
                shutil.rmtree(d, ignore_errors=True)
                cleaned_count += 1
                print(f"   [CLEANED] {os.path.relpath(d, REPO_ROOT)}")
            except OSError as e:
                sys.stderr.write(f"   [SKIP] Could not purge {os.path.relpath(d, REPO_ROOT)}: {e}\n")
    print(f"[OK] Purged {cleaned_count} temporary build directories. Workspace is pristine.")

def build_windows(version, releases_dir):
    print("\n" + "-" * 50)
    print(f"[*] STEP: Building Windows Desktop Setup (Inno Setup / PyInstaller)...")
    print("-" * 50)

    win_dir = os.path.join(REPO_ROOT, "windows")
    spec_file = os.path.join(win_dir, "AANO.spec")
    installer_script = os.path.join(win_dir, "installer", "build_installer.py")

    # 1. PyInstaller standalone collection
    print(">> Running PyInstaller compilation...")
    pyi_cmd = [sys.executable, "-m", "PyInstaller", "--clean", "-y", spec_file]
    res = subprocess.run(pyi_cmd, cwd=win_dir)
    if res.returncode != 0:
        print("[ERROR] PyInstaller build failed.")
        return False

    # 2. Inno Setup packaging
    print(">> Compiling Inno Setup LZMA compressed installer...")
    inn_cmd = [sys.executable, installer_script]
    res = subprocess.run(inn_cmd, cwd=win_dir)
    if res.returncode != 0:
        print("[ERROR] Inno Setup compilation failed.")
        return False

    # Verify output in releases_dir
    expected_exe = os.path.join(releases_dir, f"AANO-Setup-v{version}.exe")
    if os.path.exists(expected_exe):
        size_mb = os.path.getsize(expected_exe) / (1024 * 1024)
        print(f"[OK] Windows Installer READY: {expected_exe} ({size_mb:.2f} MB)")
        return True
    else:
        dist_exe = os.path.join(win_dir, "dist", "Installer", f"AANO-Setup-v{version}.exe")
        if os.path.exists(dist_exe):
            shutil.copy2(dist_exe, expected_exe)
            size_mb = os.path.getsize(expected_exe) / (1024 * 1024)
            print(f"[OK] Windows Installer READY: {expected_exe} ({size_mb:.2f} MB)")
            return True
        print(f"[ERROR] Could not locate compiled Windows installer.")
        return False

def build_android(version, releases_dir):
    print("\n" + "-" * 50)
    print(f"[*] STEP: Building Android Release APK (Signed Offline Gradle)...")
    print("-" * 50)

    android_dir = os.path.join(REPO_ROOT, "android")
    gradlew = os.path.join(android_dir, "gradlew.bat" if os.name == "nt" else "gradlew")

    cmd = [gradlew, ":app:assembleRelease", "--offline"]
    print(">> Running Gradle assembleRelease...")
    res = subprocess.run(cmd, cwd=android_dir)
    if res.returncode != 0:
        print("[ERROR] Android APK compilation failed.")
        return False

    # Copy output to releases_dir
    apk_out = os.path.join(android_dir, "app", "build", "outputs", "apk", "release", "app-release.apk")
    if not os.path.exists(apk_out):
        rel_dir = os.path.join(android_dir, "app", "build", "outputs", "apk", "release")
        if os.path.exists(rel_dir):
            apks = [os.path.join(rel_dir, f) for f in os.listdir(rel_dir) if f.endswith(".apk")]
            if apks:
                apk_out = apks[0]

    if os.path.exists(apk_out):
        dest_apk = os.path.join(releases_dir, f"AANO-v{version}.apk")
        shutil.copy2(apk_out, dest_apk)
        size_mb = os.path.getsize(dest_apk) / (1024 * 1024)
        print(f"[OK] Android APK READY: {dest_apk} ({size_mb:.2f} MB)")
        return True
    else:
        print(f"[ERROR] Output APK not found at {apk_out}")
        return False

def main():
    parser = argparse.ArgumentParser(description="AANO Local Build & Clean Tool")
    parser.add_argument("--windows-only", action="store_true", help="Build only Windows EXE installer")
    parser.add_argument("--android-only", action="store_true", help="Build only Android APK")
    parser.add_argument("--clean-only", action="store_true", help="Purge intermediate build files only")
    parser.add_argument("--no-clean", action="store_true", help="Skip post-build cache cleanup")
    args = parser.parse_args()

    version = get_version()
    print_banner(version)

    if args.clean_only:
        clean_intermediates()
        return

    # 1. Version Parity Check
    bump_script = os.path.join(REPO_ROOT, "bump_version.py")
    if os.path.exists(bump_script):
        print(">> Running version parity audit...")
        res = subprocess.run([sys.executable, bump_script, "--check"], cwd=REPO_ROOT)
        if res.returncode != 0:
            print("[WARN] Version mismatch detected. Run 'python bump_version.py <version>' first.")

    releases_dir = os.path.join(REPO_ROOT, "releases", f"v{version}")
    os.makedirs(releases_dir, exist_ok=True)

    win_ok = True
    apk_ok = True

    if not args.windows_only:
        apk_ok = build_android(version, releases_dir)

    if not args.android_only:
        win_ok = build_windows(version, releases_dir)

    # 2. Workspace Cleanup
    if not args.no_clean:
        clean_intermediates()

    print("\n" + "=" * 65)
    print("  [SUMMARY] LOCAL BUILD RESULTS")
    print("=" * 65)
    print(f"  Release Output Folder: releases\\v{version}\\")
    for f in os.listdir(releases_dir):
        fp = os.path.join(releases_dir, f)
        if os.path.isfile(fp):
            sz = os.path.getsize(fp) / (1024 * 1024)
            print(f"   • {f} ({sz:.2f} MB)")
    print("=" * 65)

    if win_ok and apk_ok:
        print("\n[SUCCESS] All requested builds SUCCEEDED and are ready for local testing!")
    else:
        print("\n[WARN] One or more builds encountered issues. Check logs above.")

if __name__ == "__main__":
    main()
