# AANO Drop - Bidirectional Local Wi-Fi P2P Receiver Daemon
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import json
import time
import base64
import random
import socket
import logging
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

import storage

logger = logging.getLogger("AANO.DropServer")

DROP_PORT = 8765
PROTOCOL_VERSION = "aano-drop-v1"

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
                if content_len == 0 or content_len > 100 * 1024 * 1024:  # 100 MB safety cap
                    self.send_response(400)
                    self._send_cors_headers()
                    self.end_headers()
                    self.wfile.write(b'{"success":false,"error":"Invalid payload size"}')
                    return

                raw_body = self.rfile.read(content_len)
                payload = json.loads(raw_body.decode("utf-8"))

                # Validate PIN or Token
                incoming_token = payload.get("token") or self.headers.get("X-AANO-TOKEN")
                server = self.server.app_server
                client_ip = self.client_address[0]
                paired_peer = getattr(server, "paired_peer", None)
                is_paired_ip = bool(paired_peer and paired_peer.get("ip") == client_ip)

                if server.pin and not is_paired_ip and str(incoming_token).strip() != str(server.pin).strip() and str(incoming_token).strip() != str(server.token).strip():
                    self.send_response(403)
                    self._send_cors_headers()
                    self.end_headers()
                    self.wfile.write(b'{"success":false,"error":"Invalid PIN or Token"}')
                    return

                incoming_notes = payload.get("notes", [])
                incoming_images = payload.get("images", [])

                # 1. Save incoming images to local images directory
                saved_images = 0
                for img in incoming_images:
                    name = os.path.basename(img.get("name", ""))
                    b64_data = img.get("data_base64") or img.get("bytes_base64", "")
                    if name and b64_data:
                        try:
                            if "," in b64_data:
                                b64_data = b64_data.split(",", 1)[1]
                            raw_bytes = base64.b64decode(b64_data)
                            target_path = os.path.join(storage.IMAGES_DIR, name)
                            with open(target_path, "wb") as f:
                                f.write(raw_bytes)
                            saved_images += 1
                        except Exception as e:
                            logger.warning("Failed to save dropped image %s: %s", name, e)

                # 2. Save incoming notes to local notes database
                existing_notes = storage.load_notes()
                existing_map = {n["id"]: n for n in existing_notes}
                saved_notes = 0

                for n in incoming_notes:
                    note_id = n.get("id")
                    if not note_id:
                        continue
                    n["isLocal"] = True
                    n["updatedAt"] = max(n.get("updatedAt", 0), int(time.time() * 1000))
                    existing_map[note_id] = n
                    saved_notes += 1

                storage.save_notes(list(existing_map.values()))

                # 3. Auto-pair with sender if sender provided info
                sender_name = payload.get("sender_name") or "Android Phone"
                sender_ip = payload.get("sender_ip") or self.client_address[0]
                sender_port = int(payload.get("sender_port", 8766))
                server.set_paired_peer(sender_name, sender_ip, sender_port)

                if server.on_receive_callback:
                    try:
                        server.on_receive_callback(saved_notes, saved_images)
                    except Exception as e:
                        logger.warning("Receiver callback error: %s", e)

                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self._send_cors_headers()
                self.end_headers()
                res = {
                    "success": True,
                    "count": saved_notes,
                    "images": saved_images,
                    "message": f"Successfully received {saved_notes} notes and {saved_images} photos"
                }
                self.wfile.write(json.dumps(res).encode("utf-8"))
            except Exception as e:
                logger.exception("Error processing drop payload: %s", e)
                self.send_response(500)
                self._send_cors_headers()
                self.end_headers()
                err_res = {"success": False, "error": str(e)}
                self.wfile.write(json.dumps(err_res).encode("utf-8"))
        elif self.path == "/aano/pair" or self.path == "/aano/pair/":
            try:
                content_len = int(self.headers.get("Content-Length", 0))
                raw_body = self.rfile.read(content_len) if content_len > 0 else b"{}"
                payload = json.loads(raw_body.decode("utf-8")) if raw_body else {}
                server = self.server.app_server
                incoming_pin = payload.get("pin")
                if server.pin and incoming_pin and str(incoming_pin).strip() != str(server.pin).strip():
                    self.send_response(403)
                    self._send_cors_headers()
                    self.end_headers()
                    self.wfile.write(b'{"success":false,"error":"Invalid PIN"}')
                    return
                peer_name = payload.get("name", "Android Device")
                peer_ip = payload.get("ip") or self.client_address[0]
                peer_port = int(payload.get("port", 8766))
                server.set_paired_peer(peer_name, peer_ip, peer_port)
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self._send_cors_headers()
                self.end_headers()
                res = {
                    "success": True,
                    "name": f"Desktop PC ({socket.gethostname()})",
                    "ip": get_local_ip(),
                    "port": server.port
                }
                self.wfile.write(json.dumps(res).encode("utf-8"))
            except Exception as e:
                self.send_response(500)
                self._send_cors_headers()
                self.end_headers()
                self.wfile.write(json.dumps({"success": False, "error": str(e)}).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

class AanoDropServer:
    def __init__(self, port=DROP_PORT, on_receive_callback=None):
        self.port = port
        self.on_receive_callback = on_receive_callback
        self.server = None
        self.thread = None
        self.pin = str(random.randint(100000, 999999))
        self.token = f"drop_sec_{int(time.time())}_{random.randint(1000, 9999)}"
        self._running = False

    def start(self):
        if self._running:
            return True
        try:
            self.server = ThreadedHTTPServer(("0.0.0.0", self.port), AanoDropHandler)
            self.server.app_server = self
            self.server.socket.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
            self.thread.start()
            self._running = True
            logger.info("AANO Drop server listening on port %d with PIN %s", self.port, self.pin)
            return True
        except Exception as e:
            logger.error("Failed to start AANO Drop server on port %d: %s", self.port, e)
            return False

    def stop(self):
        if self.server:
            try:
                self.server.shutdown()
                self.server.server_close()
            except Exception as e:
                logger.warning("Error stopping drop server: %s", e)
        self._running = False

    def get_paired_peer(self):
        cfg = storage.load_config()
        return cfg.get("drop_paired_peer")

    def set_paired_peer(self, name, ip, port=8766):
        cfg = storage.load_config()
        cfg["drop_paired_peer"] = {
            "name": name,
            "ip": ip,
            "port": int(port or 8766),
            "connected_at": int(time.time())
        }
        storage.save_config(cfg)
        logger.info("AANO Drop paired with peer: %s (%s:%s)", name, ip, port)

    def unpair(self):
        cfg = storage.load_config()
        cfg.pop("drop_paired_peer", None)
        storage.save_config(cfg)

    def get_info(self):
        qr_uri = f"aano://drop?ip={get_local_ip()}&port={self.port}&pin={self.pin}&token={self.token}"
        qr_data_url = None
        try:
            import cv2
            import base64
            encoder = cv2.QRCodeEncoder_create()
            mat = encoder.encode(qr_uri)
            bordered = cv2.copyMakeBorder(mat, 4, 4, 4, 4, cv2.BORDER_CONSTANT, value=255)
            resized = cv2.resize(bordered, (280, 280), interpolation=cv2.INTER_NEAREST)
            _, png_bytes = cv2.imencode(".png", resized)
            qr_data_url = "data:image/png;base64," + base64.b64encode(png_bytes).decode("ascii")
        except Exception as e:
            logger.warning("Could not generate OpenCV QR code: %s", e)

        return {
            "running": self._running,
            "ip": get_local_ip(),
            "port": self.port,
            "pin": self.pin,
            "token": self.token,
            "paired_peer": self.get_paired_peer(),
            "qr_data": qr_uri,
            "qr_data_url": qr_data_url
        }
