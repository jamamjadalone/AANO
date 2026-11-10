# AANO Drop - Bidirectional Local Wi-Fi P2P Receiver Daemon
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import json
import time
import random
import socket
import logging
import threading

import storage
from drop_payload_handler import (
    ThreadedHTTPServer, AanoDropHandler, get_local_ip,
    DROP_PORT, DISCOVERY_PORT, PROTOCOL_VERSION
)

logger = logging.getLogger("AANO.DropServer")

class AanoDropServer:
    def __init__(self, port=DROP_PORT, on_receive_callback=None):
        self.port = port
        self.on_receive_callback = on_receive_callback
        self.server = None
        self.thread = None
        self.udp_sock = None
        self.udp_thread = None
        self._running = False
        self.pin = self._generate_pin()
        self.token = self._generate_token()
        self.last_receive_time = 0
        self.last_receive_info = None

    def _generate_pin(self):
        return str(random.randint(100000, 999999))

    def _generate_token(self):
        return "%06d" % random.randint(100000, 999999)

    def regenerate_pin(self):
        self.pin = self._generate_pin()
        self.token = self._generate_token()
        return self.pin

    def _start_udp_beacon(self):
        def _beacon_loop():
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                sock.bind(("0.0.0.0", DISCOVERY_PORT))
                sock.settimeout(1.0)
                self.udp_sock = sock
                while self._running:
                    try:
                        data, addr = sock.recvfrom(2048)
                        msg = json.loads(data.decode("utf-8", errors="ignore"))
                        if msg.get("action") == "ping" and msg.get("app") == "aano":
                            resp = {
                                "action": "pong",
                                "app": "aano",
                                "device": socket.gethostname(),
                                "ip": get_local_ip(),
                                "port": self.port,
                                "type": "desktop"
                            }
                            sock.sendto(json.dumps(resp).encode("utf-8"), addr)
                    except socket.timeout:
                        continue
                    except Exception as loop_err:
                        logger.debug("UDP beacon loop notice: %s", loop_err)
            except Exception as e:
                logger.warning("Could not bind UDP beacon on port %d: %s", DISCOVERY_PORT, e)
            finally:
                if self.udp_sock:
                    try:
                        self.udp_sock.close()
                    except Exception as err:
                        logger.debug("UDP close notice: %s", err)

        self.udp_thread = threading.Thread(target=_beacon_loop, daemon=True)
        self.udp_thread.start()

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
            self._start_udp_beacon()
            logger.info("AANO Drop server listening on port %d with PIN %s", self.port, self.pin)
            return True
        except Exception as e:
            logger.error("Failed to start AANO Drop server on port %d: %s", self.port, e)
            return False

    def stop(self):
        self._running = False
        if self.udp_sock:
            try:
                self.udp_sock.close()
            except Exception as err:
                logger.debug("UDP close notice: %s", err)
        if self.server:
            try:
                self.server.shutdown()
                self.server.server_close()
            except Exception as err:
                logger.debug("Server shutdown notice: %s", err)
        logger.info("AANO Drop server stopped")

    def record_received(self, count, sender_name):
        self.last_receive_time = time.time()
        self.last_receive_info = {
            "count": count,
            "sender": sender_name,
            "time": self.last_receive_time
        }
        if self.on_receive_callback:
            try:
                self.on_receive_callback(count, sender_name)
            except Exception as cb_err:
                logger.warning("Receive callback error: %s", cb_err)

    def get_status(self):
        paired_peer = self.get_paired_peer()
        return {
            "running": self._running,
            "port": self.port,
            "ip": get_local_ip(),
            "pin": self.pin,
            "token": self.token,
            "device": socket.gethostname(),
            "protocol": PROTOCOL_VERSION,
            "last_receive": self.last_receive_info,
            "paired_peer": paired_peer
        }

    def get_paired_peer(self):
        cfg = storage.load_config()
        return cfg.get("aano_drop_paired_peer")

    def set_paired_peer(self, ip, device_name):
        cfg = storage.load_config()
        cfg["aano_drop_paired_peer"] = {
            "ip": ip,
            "device": device_name,
            "timestamp": int(time.time() * 1000)
        }
        storage.save_config(cfg)
        return True

    def unpair_peer(self):
        cfg = storage.load_config()
        cfg["aano_drop_paired_peer"] = None
        storage.save_config(cfg)
        return True
