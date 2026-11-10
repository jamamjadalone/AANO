# AANO Windows Desktop App - Image Processing & Cropping Subsystem
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import time
import random
import logging
from PIL import Image, ImageOps

logger = logging.getLogger("AANO.ImageProcessor")

APPDATA_DIR = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "AANO")
IMAGES_DIR = os.path.join(APPDATA_DIR, "images")
os.makedirs(IMAGES_DIR, exist_ok=True)

def process_and_save_image(src_path, quality_hd=False):
    """
    Copies or converts an image into %APPDATA%/AANO/images/
    Standard: Resized to max 1280px dimension, saved as WebP (quality 80) ~100-150KB.
    HD: Original resolution and quality preserved.
    """
    if not os.path.exists(src_path):
        return {"success": False, "error": "Source file not found"}

    try:
        ts = int(time.time() * 1000)
        rand = random.randint(100, 999)
        with Image.open(src_path) as im:
            im = ImageOps.exif_transpose(im)
            if quality_hd:
                ext = os.path.splitext(src_path)[1].lower()
                if ext not in [".jpg", ".jpeg", ".png", ".webp"]:
                    ext = ".jpg"
                dest_fn = f"img_{ts}_{rand}{ext}"
                dest_path = os.path.join(IMAGES_DIR, dest_fn)
                if ext in [".jpg", ".jpeg"]:
                    if im.mode in ("RGBA", "P"):
                        im = im.convert("RGB")
                    im.save(dest_path, "JPEG", quality=95)
                elif ext == ".png":
                    im.save(dest_path, "PNG", optimize=True)
                else:
                    im.save(dest_path, "WEBP", quality=95)
            else:
                dest_fn = f"img_{ts}_{rand}.webp"
                dest_path = os.path.join(IMAGES_DIR, dest_fn)
                max_dim = 1280
                w, h = im.size
                if w > max_dim or h > max_dim:
                    ratio = w / h
                    if w > h:
                        new_w = max_dim
                        new_h = int(max_dim / ratio)
                    else:
                        new_h = max_dim
                        new_w = int(max_dim * ratio)
                    im = im.resize((new_w, new_h), Image.Resampling.LANCZOS)
                if im.mode not in ("RGB", "RGBA"):
                    im = im.convert("RGB")
                im.save(dest_path, "WEBP", quality=80)

            return {
                "success": True,
                "filename": dest_fn,
                "path": dest_path,
                "size": os.path.getsize(dest_path),
                "width": im.width,
                "height": im.height
            }
    except Exception as e:
        logger.warning("Failed to process image %s: %s", src_path, e)
        return {"success": False, "error": str(e)}

def crop_and_rotate_image(filename, crop_box=None, rotation_deg=0, quality_hd=False):
    """
    Applies crop and/or rotation to an image in %APPDATA%/AANO/images/
    crop_box: [left, top, right, bottom] in original image pixel space
    rotation_deg: Clockwise rotation in degrees (e.g. 90, 180, 270)
    """
    src_path = os.path.join(IMAGES_DIR, filename)
    if not os.path.exists(src_path):
        return {"success": False, "error": "Target image not found"}

    try:
        ts = int(time.time() * 1000)
        rand = random.randint(100, 999)
        dest_fn = f"img_crop_{ts}_{rand}.webp"
        dest_path = os.path.join(IMAGES_DIR, dest_fn)

        with Image.open(src_path) as im:
            if rotation_deg and (rotation_deg % 360 != 0):
                im = im.rotate(-rotation_deg, expand=True)

            if crop_box and len(crop_box) == 4:
                left, top, right, bottom = crop_box
                w, h = im.size
                left = max(0, min(int(left), w - 1))
                top = max(0, min(int(top), h - 1))
                right = max(left + 1, min(int(right), w))
                bottom = max(top + 1, min(int(bottom), h))
                im = im.crop((left, top, right, bottom))

            if not quality_hd:
                max_dim = 1280
                w, h = im.size
                if w > max_dim or h > max_dim:
                    ratio = w / h
                    if w > h:
                        new_w = max_dim
                        new_h = int(max_dim / ratio)
                    else:
                        new_h = max_dim
                        new_w = int(max_dim * ratio)
                    im = im.resize((new_w, new_h), Image.Resampling.LANCZOS)

            if im.mode not in ("RGB", "RGBA"):
                im = im.convert("RGB")

            q = 95 if quality_hd else 80
            im.save(dest_path, "WEBP", quality=q)

        return {
            "success": True,
            "filename": dest_fn,
            "path": dest_path,
            "size": os.path.getsize(dest_path),
            "width": im.width,
            "height": im.height
        }
    except Exception as e:
        logger.warning("Failed to crop image %s: %s", filename, e)
        return {"success": False, "error": str(e)}
