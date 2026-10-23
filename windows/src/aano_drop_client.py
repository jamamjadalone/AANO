# AANO Drop - Bidirectional Local Wi-Fi P2P Client
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import json
import base64
import urllib.request
import urllib.error
import logging
import storage

logger = logging.getLogger("AANO.DropClient")

def send_drop_to_device(target_ip, target_port=8766, notes=None, pin=None, token=None):
    """
    Sends selected notes and attached images directly over LAN to another AANO device.
    Zero internet egress - local RFC 1918 Layer 2 switching.
    """
    if not notes:
        return {"success": False, "error": "No notes selected to drop"}

    explicit_port = None
    clean_ip = target_ip
    if ":" in target_ip:
        parts = target_ip.split(":", 1)
        clean_ip = parts[0].strip()
        try:
            explicit_port = int(parts[1].strip())
        except ValueError:
            pass

    # Try default port (8766 Android), fallback to 8765 (PC) if port was not specified
    ports_to_try = [explicit_port] if explicit_port else [target_port, 8765 if target_port == 8766 else 8766]

    # Gather required images
    image_names = set()
    for n in notes:
        for img in n.get("images", []):
            fn = os.path.basename(img)
            if fn:
                image_names.add(fn)

    images_payload = []
    for fn in image_names:
        img_path = os.path.join(storage.IMAGES_DIR, fn)
        if os.path.exists(img_path):
            try:
                with open(img_path, "rb") as f:
                    b64 = base64.b64encode(f.read()).decode("ascii")
                    images_payload.append({
                        "name": fn,
                        "data_base64": b64
                    })
            except Exception as e:
                logger.warning("Failed to encode image %s: %s", fn, e)

    payload = {
        "protocol": "aano-drop-v1",
        "pin": pin,
        "token": token or pin,
        "notes": notes,
        "images": images_payload
    }
    data = json.dumps(payload).encode("utf-8")

    last_error = None
    for port in ports_to_try:
        target_url = f"http://{clean_ip}:{port}/aano/drop"
        logger.info("Dropping %d notes to %s", len(notes), target_url)
        try:
            req = urllib.request.Request(
                target_url,
                data=data,
                headers={
                    "Content-Type": "application/json",
                    "X-AANO-TOKEN": str(token or pin or "")
                },
                method="POST"
            )
            with urllib.request.urlopen(req, timeout=12) as resp:
                body = resp.read().decode("utf-8")
                return json.loads(body)
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="ignore")
            logger.warning("Drop HTTP error %d from %s: %s", e.code, target_url, err_body)
            return {"success": False, "error": f"HTTP {e.code}: {err_body or e.reason}"}
        except Exception as e:
            last_error = e
            logger.warning("Drop transfer attempt to %s failed: %s, trying next port...", target_url, e)

    return {"success": False, "error": str(last_error or "Connection failed")}
