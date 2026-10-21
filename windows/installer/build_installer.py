# Script to build single-file AANO-Setup.exe installer
import os
import sys
import zipfile
import shutil
import subprocess

def zip_folder(source_dir, output_zip):
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
    payload_zip = os.path.join(base_dir, "app_payload.zip")
    assets_ico = os.path.join(project_dir, "assets", "aano.ico")

    if not os.path.exists(aano_dist):
        print(f"Error: {aano_dist} does not exist. Build AANO first.")
        sys.exit(1)

    # 1. Zip the AANO dist
    zip_folder(aano_dist, payload_zip)

    # 2. Run PyInstaller to make AANO-Setup.exe
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

    print("Compiling AANO-Setup.exe...")
    subprocess.check_call(cmd)
    print("AANO-Setup.exe build complete!")

if __name__ == "__main__":
    main()
