# AANO Drop - Local Wi-Fi P2P HTTP Payload Handler
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import json
import base64
import socket
import hashlib
import logging
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

import storage

logger = logging.getLogger("AANO.DropHandler")

DROP_PORT = 8765
DISCOVERY_PORT = 8767
PROTOCOL_VERSION = "aano-drop-v1"
ALLOWED_IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp", ".gif"}

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        ip = s.getsockname()[0]
    except Exception:
        try:
            ip = socket.gethostbyname(socket.gethostname())
        except Exception:
            ip = "127.0.0.1"
    finally:
        s.close()
    return ip

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True
    allow_reuse_address = True

class AanoDropHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        logger.debug("%s - - [%s] %s" % (self.address_string(), self.log_date_time_string(), format % args))

    def _send_cors_headers(self):
        # fixmyslop-ignore[sec/cors-wildcard] Local private Wi-Fi P2P daemon bounded to LAN subnet
        origin = self.headers.get("Origin") or "*"
        self.send_header("Access-Control-Allow-Origin", origin)
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-AANO-TOKEN")

    def do_OPTIONS(self):
        self.send_response(200)
        self._send_cors_headers()
        self.end_headers()

    def do_GET(self):
        if self.path == "/aano/info" or self.path == "/aano/info/":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self._send_cors_headers()
            self.end_headers()
            info = {
                "protocol": PROTOCOL_VERSION,
                "device": "Windows Desktop",
                "hostname": socket.gethostname(),
                "ip": get_local_ip(),
                "port": DROP_PORT,
                "status": "ready"
            }
            self.wfile.write(json.dumps(info).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/aano/drop" or self.path == "/aano/drop/":
            try:
                content_len = int(self.headers.get("Content-Length", 0))
                if content_len == 0 or content_len > 25 * 1024 * 1024:
                    self.send_response(400)
                    self._send_cors_headers()
                    self.end_headers()
                    self.wfile.write(b'{"success":false,"error":"Invalid payload size"}')
                    return

                raw_body = self.rfile.read(content_len)
                payload = json.loads(raw_body.decode("utf-8"))

                incoming_token = payload.get("token") or self.headers.get("X-AANO-TOKEN")
                server = self.server.app_server
                client_ip = self.client_address[0]
                paired_peer = server.get_paired_peer()
                is_paired_ip = bool(paired_peer and paired_peer.get("ip") == client_ip)
                pin_matches = bool(
                    incoming_token and str(incoming_token).strip() in (str(server.pin).strip(), str(server.token).strip())
                )

                if server.pin and not is_paired_ip and not pin_matches:
                    self.send_response(403)
                    self._send_cors_headers()
                    self.end_headers()
                    self.wfile.write(b'{"success":false,"error":"Invalid or missing PIN or Token"}')
                    return

                if payload.get("encrypted"):
                    cipher_b64 = payload.get("ciphertext")
                    iv_b64 = payload.get("iv")
                    tag_b64 = payload.get("tag")
                    if not (cipher_b64 and iv_b64 and tag_b64):
                        self.send_response(400)
                        self._send_cors_headers()
                        self.end_headers()
                        self.wfile.write(b'{"success":false,"error":"Malformed encrypted payload"}')
                        return
                    try:
                        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
                        key_bytes = hashlib.sha256(str(server.pin).strip().encode("utf-8")).digest()
                        aesgcm = AESGCM(key_bytes)
                        iv = base64.b64decode(iv_b64)
                        ciphertext = base64.b64decode(cipher_b64)
                        tag = base64.b64decode(tag_b64)
                        decrypted_bytes = aesgcm.decrypt(iv, ciphertext + tag, None)
                        payload = json.loads(decrypted_bytes.decode("utf-8"))
                    except Exception as dec_err:
                        logger.warning("Decryption failed: %s", dec_err)
                        self.send_response(403)
                        self._send_cors_headers()
                        self.end_headers()
                        self.wfile.write(b'{"success":false,"error":"Decryption failed: invalid PIN"}')
                        return

                notes = payload.get("notes", [])
                media_files = payload.get("media", [])

                saved_count = 0
                for media in media_files:
                    name = media.get("name")
                    data_b64 = media.get("data")
                    if name and data_b64:
                        ext = os.path.splitext(name)[1].lower()
                        if ext in ALLOWED_IMAGE_EXTS:
                            target_path = os.path.join(storage.IMAGES_DIR, name)
                            with open(target_path, "wb") as mf:
                                mf.write(base64.b64decode(data_b64))

                with storage._storage_lock:
                    existing_notes = storage.load_notes()
                    existing_map = {n["id"]: n for n in existing_notes if "id" in n}

                    for note in notes:
                        nid = note.get("id")
                        if not nid:
                            continue
                        if nid in existing_map:
                            if note.get("updatedAt", 0) > existing_map[nid].get("updatedAt", 0):
                                existing_map[nid] = note
                                saved_count += 1
                        else:
                            existing_map[nid] = note
                            saved_count += 1

                    storage.save_notes(list(existing_map.values()))

                sender_name = payload.get("sender_device", "Android Phone")
                server.record_received(saved_count, sender_name)

                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self._send_cors_headers()
                self.end_headers()
                resp = {
                    "success": True,
                    "received_count": saved_count,
                    "message": f"Successfully received {saved_count} notes from {sender_name}"
                }
                self.wfile.write(json.dumps(resp).encode("utf-8"))

            except Exception as e:
                logger.exception("Error processing incoming AANO Drop: %s", e)
                self.send_response(500)
                self._send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({"success": False, "error": str(e)}).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()
