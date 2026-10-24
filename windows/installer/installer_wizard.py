# AANO Notes - Windows Setup Wizard
# Copyright (C) 2026 Jam Amjad Rasheed

import os
import sys
import zipfile
import threading
import winreg
import subprocess
import tkinter as tk
from tkinter import ttk, messagebox

APP_NAME = "AANO Notes"
APP_VERSION = "1.0.0"
APP_PUBLISHER = "Jam Amjad Rasheed"

def get_bundle_dir():
    if getattr(sys, "frozen", False):
        return sys._MEIPASS
    return os.path.dirname(os.path.abspath(__file__))

def get_install_dir():
    local_app_data = os.environ.get("LOCALAPPDATA", os.path.expanduser("~\\AppData\\Local"))
    return os.path.join(local_app_data, "Programs", APP_NAME)

def create_shortcut(target, shortcut_path, icon_path, description="AANO Notes"):
    working_dir = os.path.dirname(target)
    ps = f"""
$ws = New-Object -ComObject WScript.Shell
$s = $ws.CreateShortcut('{shortcut_path}')
$s.TargetPath = '{target}'
$s.WorkingDirectory = '{working_dir}'
$s.IconLocation = '{icon_path},0'
$s.Description = '{description}'
$s.Save()
"""
    subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", ps], creationflags=0x08000000)

def register_uninstaller(install_dir, exe_path, icon_path):
    try:
        key_path = rf"Software\Microsoft\Windows\CurrentVersion\Uninstall\{APP_NAME}"
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, key_path) as key:
            winreg.SetValueEx(key, "DisplayName", 0, winreg.REG_SZ, APP_NAME)
            winreg.SetValueEx(key, "DisplayVersion", 0, winreg.REG_SZ, APP_VERSION)
            winreg.SetValueEx(key, "Publisher", 0, winreg.REG_SZ, APP_PUBLISHER)
            winreg.SetValueEx(key, "DisplayIcon", 0, winreg.REG_SZ, f"{icon_path},0")
            winreg.SetValueEx(key, "InstallLocation", 0, winreg.REG_SZ, install_dir)
            uninstall_cmd = f'powershell -NoProfile -Command "Remove-Item -Recurse -Force \'{install_dir}\'; Remove-Item -Force \'$([Environment]::GetFolderPath(\'Desktop\'))\\AANO Notes.lnk\' -ErrorAction SilentlyContinue; Remove-Item -Recurse -Force \'$([Environment]::GetFolderPath(\'StartMenu\'))\\Programs\\AANO Notes\' -ErrorAction SilentlyContinue; Remove-Item -Path \'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\AANO Notes\' -Force"'
            winreg.SetValueEx(key, "UninstallString", 0, winreg.REG_SZ, uninstall_cmd)
    except OSError as e:
        sys.stderr.write(f"Registry registration error: {e}\n")

class SetupApp:
    def __init__(self, root):
        self.root = root
        self.root.title("AANO Notes Setup")
        self.root.geometry("520x380")
        self.root.resizable(False, False)
        self.root.configure(bg="#F8FAFC")

        bundle_dir = get_bundle_dir()
        icon_file = os.path.join(bundle_dir, "aano.ico")
        if os.path.exists(icon_file):
            try:
                self.root.iconbitmap(icon_file)
            except tk.TclError as e:
                sys.stderr.write(f"Notice: Icon could not be set: {e}\n")

        self.install_dir = get_install_dir()

        self.build_ui()

    def build_ui(self):
        # Header banner
        header = tk.Frame(self.root, bg="#00796B", height=80)
        header.pack(fill="x")
        header.pack_propagate(False)

        title_lbl = tk.Label(header, text="AANO Notes Setup", font=("Segoe UI", 16, "bold"), fg="#FFFFFF", bg="#00796B")
        title_lbl.pack(anchor="w", padx=24, pady=(16, 2))

        sub_lbl = tk.Label(header, text="Emerald Elegance • Instant Sync • Private & Secure", font=("Segoe UI", 9), fg="#B2DFDB", bg="#00796B")
        sub_lbl.pack(anchor="w", padx=24)

        # Body
        body = tk.Frame(self.root, bg="#F8FAFC", padx=24, pady=20)
        body.pack(fill="both", expand=True)

        info_lbl = tk.Label(body, text="Setup will install AANO Notes to the following location:", font=("Segoe UI", 10), fg="#334155", bg="#F8FAFC")
        info_lbl.pack(anchor="w", pady=(0, 6))

        dir_frame = tk.Frame(body, bg="#FFFFFF", relief="solid", bd=1)
        dir_frame.pack(fill="x", pady=(0, 16))
        self.dir_entry = tk.Entry(dir_frame, font=("Segoe UI", 9), relief="flat", bg="#FFFFFF", fg="#0F172A")
        self.dir_entry.insert(0, self.install_dir)
        self.dir_entry.config(state="readonly")
        self.dir_entry.pack(fill="x", padx=8, pady=6)

        self.var_desktop = tk.BooleanVar(value=True)
        cb_desktop = tk.Checkbutton(body, text="Create a Desktop Shortcut", variable=self.var_desktop, font=("Segoe UI", 10), bg="#F8FAFC", activebackground="#F8FAFC", fg="#1E293B")
        cb_desktop.pack(anchor="w", pady=2)

        self.var_startmenu = tk.BooleanVar(value=True)
        cb_sm = tk.Checkbutton(body, text="Create a Start Menu Shortcut", variable=self.var_startmenu, font=("Segoe UI", 10), bg="#F8FAFC", activebackground="#F8FAFC", fg="#1E293B")
        cb_sm.pack(anchor="w", pady=2)

        self.var_launch = tk.BooleanVar(value=True)
        cb_launch = tk.Checkbutton(body, text="Launch AANO Notes after installation", variable=self.var_launch, font=("Segoe UI", 10), bg="#F8FAFC", activebackground="#F8FAFC", fg="#1E293B")
        cb_launch.pack(anchor="w", pady=2)

        self.progress = ttk.Progressbar(body, orient="horizontal", mode="indeterminate")
        self.status_lbl = tk.Label(body, text="Ready to install.", font=("Segoe UI", 9), fg="#64748B", bg="#F8FAFC")

        # Footer Buttons
        footer = tk.Frame(self.root, bg="#F1F5F9", height=54, padx=20)
        footer.pack(fill="x", side="bottom")

        self.btn_cancel = tk.Button(footer, text="Cancel", font=("Segoe UI", 9), width=10, command=self.root.destroy, bg="#E2E8F0", relief="flat")
        self.btn_cancel.pack(side="right", pady=12, padx=(6, 0))

        self.btn_install = tk.Button(footer, text="Install", font=("Segoe UI", 9, "bold"), width=12, bg="#00796B", fg="#FFFFFF", relief="flat", command=self.start_install)
        self.btn_install.pack(side="right", pady=12)

    def start_install(self):
        self.btn_install.config(state="disabled")
        self.btn_cancel.config(state="disabled")
        self.progress.pack(fill="x", pady=(10, 4))
        self.progress.start(10)
        self.status_lbl.pack(anchor="w")
        self.status_lbl.config(text="Extracting application files...")

        t = threading.Thread(target=self.do_install, daemon=True)
        t.start()

    def do_install(self):
        try:
            bundle_dir = get_bundle_dir()
            payload_zip = os.path.join(bundle_dir, "app_payload.zip")

            os.makedirs(self.install_dir, exist_ok=True)

            if os.path.exists(payload_zip):
                with zipfile.ZipFile(payload_zip, "r") as zf:
                    zf.extractall(self.install_dir)

            exe_path = os.path.join(self.install_dir, "AANO.exe")
            icon_path = os.path.join(self.install_dir, "aano.ico")

            # Copy icon if missing
            if not os.path.exists(icon_path):
                src_icon = os.path.join(bundle_dir, "aano.ico")
                if os.path.exists(src_icon):
                    import shutil
                    shutil.copy2(src_icon, icon_path)

            # Create Desktop shortcut
            if self.var_desktop.get():
                desktop = os.path.join(os.path.expanduser("~"), "Desktop")
                shortcut = os.path.join(desktop, f"{APP_NAME}.lnk")
                create_shortcut(exe_path, shortcut, icon_path, "AANO Notes")

            # Create Start Menu shortcut
            if self.var_startmenu.get():
                appdata = os.environ.get("APPDATA", "")
                sm_dir = os.path.join(appdata, "Microsoft", "Windows", "Start Menu", "Programs", APP_NAME)
                os.makedirs(sm_dir, exist_ok=True)
                sm_shortcut = os.path.join(sm_dir, f"{APP_NAME}.lnk")
                create_shortcut(exe_path, sm_shortcut, icon_path, "AANO Notes")

            # Registry uninstaller
            register_uninstaller(self.install_dir, exe_path, icon_path)

            self.root.after(0, self.finish_install, exe_path)

        except Exception as e:
            self.root.after(0, self.error_install, str(e))

    def finish_install(self, exe_path):
        self.progress.stop()
        self.progress.pack_forget()
        self.status_lbl.config(text="Installation completed successfully!", fg="#00796B")

        if self.var_launch.get() and os.path.exists(exe_path):
            try:
                subprocess.Popen([exe_path], cwd=os.path.dirname(exe_path))
            except OSError as e:
                messagebox.showwarning("Launch Notice", f"Installed successfully, but could not automatically launch:\n{e}")

        messagebox.showinfo("Success", "AANO Notes has been successfully installed!")
        self.root.destroy()

    def error_install(self, msg):
        self.progress.stop()
        self.progress.pack_forget()
        self.status_lbl.config(text="Installation failed.", fg="#DC2626")
        messagebox.showerror("Error", f"Failed to install AANO Notes:\n{msg}")
        self.btn_cancel.config(state="normal")

if __name__ == "__main__":
    root = tk.Tk()
    app = SetupApp(root)
    root.mainloop()
