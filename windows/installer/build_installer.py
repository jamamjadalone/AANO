# Script to build official compressed AANO-Setup installer (Default: Inno Setup)
import os
import sys
import shutil
import subprocess

def find_iscc():
    candidates = [
        shutil.which("ISCC.exe"),
        shutil.which("iscc"),
        os.path.expandvars(r"%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"),
        r"C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
        r"C:\Program Files\Inno Setup 6\ISCC.exe",
    ]
    for c in candidates:
        if c and os.path.isfile(c):
            return c
    return None

def get_app_version(project_dir):
    repo_root = os.path.dirname(project_dir)
    version_file = os.path.join(repo_root, "VERSION")
    if os.path.exists(version_file):
        with open(version_file, "r", encoding="utf-8") as f:
            v = f.read().strip()
            if v:
                return v
    iss_file = os.path.join(project_dir, "installer", "aano.iss")
    if os.path.exists(iss_file):
        with open(iss_file, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip().startswith("#define MyAppVersion"):
                    return line.split('"')[1]
    return "1.0.1-beta"

def build_with_inno(iscc_path, iss_path, project_dir):
    version = get_app_version(project_dir)
    output_filename = f"AANO-Setup-v{version}.exe"
    print(f"[Inno Setup] Using compiler: {iscc_path}")
    print(f"[Inno Setup] Compiling {iss_path} for version {version} with LZMA ultra compression...")
    subprocess.check_call([iscc_path, iss_path])
    
    installer_dir = os.path.join(project_dir, "dist", "Installer")
    output_exe = os.path.join(installer_dir, output_filename)
    if os.path.exists(output_exe):
        size_mb = os.path.getsize(output_exe) / (1024 * 1024)
        print(f"[Inno Setup] Build SUCCESS! Output: {output_exe} ({size_mb:.2f} MB)")
        
        # Mirror to releases/v<version>/
        repo_root = os.path.dirname(project_dir)
        releases_dir = os.path.join(repo_root, "releases", f"v{version}")
        os.makedirs(releases_dir, exist_ok=True)
        dest_exe = os.path.join(releases_dir, output_filename)
        shutil.copy2(output_exe, dest_exe)
        print(f"[Inno Setup] Synced to releases: {dest_exe}")
        return True
    return False

def zip_folder(source_dir, output_zip):
    import zipfile
    print(f"Creating payload archive: {output_zip}...")
    with zipfile.ZipFile(output_zip, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(source_dir):
            for file in files:
                full_path = os.path.join(root, file)
                rel_path = os.path.relpath(full_path, source_dir)
                zf.write(full_path, rel_path)
    print(f"Payload created ({os.path.getsize(output_zip) / (1024*1024):.2f} MB)")

def main():
    base_dir = os.path.abspath(os.path.dirname(__file__))
    project_dir = os.path.dirname(base_dir)
    aano_dist = os.path.join(project_dir, "dist", "AANO")
    iss_file = os.path.join(base_dir, "aano.iss")

    if not os.path.exists(aano_dist):
        print(f"Error: {aano_dist} does not exist. Build AANO first.")
        sys.exit(1)

    # 1. Prefer Inno Setup (Default Official Compiler - 16 MB ultra LZMA)
    iscc = find_iscc()
    if iscc and os.path.exists(iss_file):
        if build_with_inno(iscc, iss_file, project_dir):
            return

    # 2. Fallback only if Inno Setup is not installed
    print("[WARNING] Inno Setup not found. Falling back to legacy PyInstaller wrapper...")
    payload_zip = os.path.join(base_dir, "app_payload.zip")
    assets_ico = os.path.join(project_dir, "assets", "aano.ico")
    zip_folder(aano_dist, payload_zip)
    cmd = [
        sys.executable, "-m", "PyInstaller",
        "--name", "AANO-Setup",
        "--onefile",
        "--windowed",
        "--icon", assets_ico,
        "--add-data", f"{payload_zip};.",
        "--add-data", f"{assets_ico};.",
        "--distpath", os.path.join(project_dir, "dist"),
        "--workpath", os.path.join(project_dir, "build_installer"),
        "--clean",
        "-y",
        os.path.join(base_dir, "installer_wizard.py")
    ]
    subprocess.check_call(cmd)
    if os.path.exists(payload_zip):
        os.remove(payload_zip)

if __name__ == "__main__":
    main()
