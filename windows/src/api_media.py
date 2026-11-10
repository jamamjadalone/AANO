# AANO Windows Desktop App - Media Attachments & Image Processing API Delegate
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import os
import base64
import logging
import storage

logger = logging.getLogger("AANO.API.Media")

ALLOWED_IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp", ".gif"}
ALLOWED_AUDIO_EXTS = {".m4a", ".aac", ".wav", ".mp3", ".ogg"}

class MediaApiMixin:
    def _choose_files_dialog(self, file_types):
        try:
            import ctypes
            from ctypes import wintypes
            class OPENFILENAME(ctypes.Structure):
                _fields_ = [
                    ("lStructSize", wintypes.DWORD),
                    ("hwndOwner", wintypes.HWND),
                    ("hInstance", wintypes.HINSTANCE),
                    ("lpstrFilter", wintypes.LPCWSTR),
                    ("lpstrCustomFilter", wintypes.LPWSTR),
                    ("nMaxCustFilter", wintypes.DWORD),
                    ("nFilterIndex", wintypes.DWORD),
                    ("lpstrFile", wintypes.LPWSTR),
                    ("nMaxFile", wintypes.DWORD),
                    ("lpstrFileTitle", wintypes.LPWSTR),
                    ("nMaxFileTitle", wintypes.DWORD),
                    ("lpstrInitialDir", wintypes.LPCWSTR),
                    ("lpstrTitle", wintypes.LPCWSTR),
                    ("Flags", wintypes.DWORD),
                    ("nFileOffset", wintypes.WORD),
                    ("nFileExtension", wintypes.WORD),
                    ("lpstrDefExt", wintypes.LPCWSTR),
                    ("lCustData", wintypes.LPARAM),
                    ("lpfnHook", wintypes.LPVOID),
                    ("lpTemplateName", wintypes.LPCWSTR),
                ]
            ofn = OPENFILENAME()
            ofn.lStructSize = ctypes.sizeof(OPENFILENAME)
            buffer = ctypes.create_unicode_buffer(32768)
            ofn.lpstrFile = ctypes.cast(buffer, wintypes.LPWSTR)
            ofn.nMaxFile = 32768
            filter_str = "Images (*.png;*.jpg;*.jpeg;*.webp)\0*.png;*.jpg;*.jpeg;*.webp\0All Files (*.*)\0*.*\0\0"
            ofn.lpstrFilter = filter_str
            ofn.Flags = 0x00080000 | 0x00001000 | 0x00000800 | 0x00000200
            if ctypes.windll.comdlg32.GetOpenFileNameW(ctypes.byref(ofn)):
                raw = buffer.value
                parts = buffer[:].split("\0")
                parts = [p for p in parts if p]
                if len(parts) > 1:
                    dir_path = parts[0]
                    return [os.path.join(dir_path, f) for f in parts[1:]]
                return [raw] if raw else []
            return []
        except Exception as e:
            logger.warning("Native file dialog error: %s", e)
            return []

    def _file_to_data_url(self, filepath):
        try:
            ext = os.path.splitext(filepath)[1].lower()
            mime = "image/jpeg"
            if ext == ".png":
                mime = "image/png"
            elif ext == ".webp":
                mime = "image/webp"
            elif ext == ".gif":
                mime = "image/gif"
            with open(filepath, "rb") as f:
                b64 = base64.b64encode(f.read()).decode("utf-8")
                return f"data:{mime};base64,{b64}"
        except Exception as e:
            logger.warning("Failed to encode image to data URL: %s", e)
            return None

    def attach_images(self, quality_hd=False):
        files = self._choose_files_dialog("Images (*.png;*.jpg;*.jpeg;*.webp)")
        if not files:
            return {"success": False, "cancelled": True}

        saved_images = []
        for src in files:
            res = storage.process_and_save_image(src, quality_hd=quality_hd)
            if res.get("success"):
                data_url = self._file_to_data_url(res["path"])
                saved_images.append({
                    "filename": res["filename"],
                    "dataUrl": data_url,
                    "width": res.get("width"),
                    "height": res.get("height"),
                    "size": res.get("size")
                })

        return {"success": True, "images": saved_images}

    def get_image_data_url(self, filename):
        src_path = os.path.join(storage.IMAGES_DIR, filename)
        if not os.path.exists(src_path):
            return {"success": False, "error": "Image not found"}
        url = self._file_to_data_url(src_path)
        if url:
            return {"success": True, "dataUrl": url}
        return {"success": False, "error": "Could not encode image"}

    def crop_image(self, filename, crop_box=None, rotation_deg=0, quality_hd=False):
        res = storage.crop_and_rotate_image(
            filename,
            crop_box=crop_box,
            rotation_deg=rotation_deg,
            quality_hd=quality_hd
        )
        if res.get("success"):
            data_url = self._file_to_data_url(res["path"])
            res["dataUrl"] = data_url
        return res

    def save_image_to_disk(self, filename, dest_dir=None):
        src_path = os.path.join(storage.IMAGES_DIR, filename)
        if not os.path.exists(src_path):
            return {"success": False, "error": "File not found"}
        try:
            target_dir = dest_dir or os.path.join(os.path.expanduser("~"), "Downloads")
            os.makedirs(target_dir, exist_ok=True)
            dest_path = os.path.join(target_dir, filename)
            import shutil
            shutil.copy2(src_path, dest_path)
            return {"success": True, "saved_path": dest_path}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def delete_image_file(self, filename):
        src_path = os.path.join(storage.IMAGES_DIR, filename)
        if os.path.exists(src_path):
            try:
                os.remove(src_path)
                return {"success": True}
            except OSError as e:
                return {"success": False, "error": str(e)}
        return {"success": False, "error": "File not found"}

    def _format_duration_str(self, ms):
        if not ms or ms <= 0:
            return "00:00"
        sec = int(ms // 1000) % 60
        mn = int(ms // 60000)
        return f"{mn:02d}:{sec:02d}"

    def _resolve_image_path(self, fn):
        if not fn or not isinstance(fn, str):
            return None
        if os.path.isabs(fn) and os.path.exists(fn):
            return fn
        p = os.path.join(storage.IMAGES_DIR, fn)
        return p if os.path.exists(p) else None

    def _resolve_audio_path(self, audio_uri):
        if not audio_uri or not isinstance(audio_uri, str):
            return None
        clean = audio_uri
        if clean.startswith("file://"):
            clean = clean[7:]
        if os.path.isabs(clean) and os.path.exists(clean):
            return clean
        p = os.path.join(storage.APPDATA_DIR, "audio", os.path.basename(clean))
        return p if os.path.exists(p) else None
