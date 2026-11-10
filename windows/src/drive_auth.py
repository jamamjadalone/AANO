# Google Drive OAuth 2.0 loopback authentication for AANO Windows.
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import json
import time
import webbrowser
import urllib.request
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
import sys
import logging

logger = logging.getLogger("AANO.DriveAuth")

def _load_credentials():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    candidates = [
        os.path.join(base_dir, "google_credentials.json"),
        os.path.join(base_dir, "..", "google_credentials.json")
    ]
    if hasattr(sys, "_MEIPASS"):
        candidates.append(os.path.join(sys._MEIPASS, "google_credentials.json"))
        candidates.append(os.path.join(sys._MEIPASS, "src", "google_credentials.json"))
    for p in candidates:
        if p and os.path.exists(p):
            try:
                with open(p, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    return data.get("client_id", ""), data.get("client_secret", "")
            except (json.JSONDecodeError, OSError):
                pass
    return "", ""

CLIENT_ID, CLIENT_SECRET = _load_credentials()
REDIRECT_PORT = 8085
REDIRECT_URI = f"http://localhost:{REDIRECT_PORT}/callback"

AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"

SCOPES = [
    "https://www.googleapis.com/auth/drive.file",
    "https://www.googleapis.com/auth/userinfo.email",
    "https://www.googleapis.com/auth/userinfo.profile",
    "openid"
]

class OAuthCallbackHandler(BaseHTTPRequestHandler):
    auth_code = None
    auth_error = None
    expected_state = None

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/callback":
            params = urllib.parse.parse_qs(parsed.query)
            incoming_state = params.get("state", [""])[0]
            if OAuthCallbackHandler.expected_state and incoming_state != OAuthCallbackHandler.expected_state:
                OAuthCallbackHandler.auth_error = "Invalid OAuth state parameter (CSRF detected)"
                self.send_response(400)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(b"<h3>Authentication Failed: Invalid OAuth state parameter</h3>")
                return
            if "code" in params:
                OAuthCallbackHandler.auth_code = params["code"][0]
                redirect_target = "https://jamamjadalone.github.io/AANO/oauth-success.html"
                self.send_response(302)
                self.send_header("Location", redirect_target)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                fallback_html = f"""<!DOCTYPE html>
<html><head>
<meta http-equiv="refresh" content="0; url={redirect_target}">
<title>Redirecting...</title>
</head><body>
<p>Authentication successful. <a href="{redirect_target}">Click here to continue</a>.</p>
<script>window.location.href = "{redirect_target}";</script>
</body></html>"""
                self.wfile.write(fallback_html.encode("utf-8"))
            else:
                OAuthCallbackHandler.auth_error = params.get("error", ["Unknown error"])[0]
                self.send_response(400)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(f"<h3>Authentication Failed: {OAuthCallbackHandler.auth_error}</h3><p>Please close this tab and try again from the AANO app.</p>".encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass

class DriveAuthManager:
    @staticmethod
    def get_auth_url(state=None):
        params = {
            "client_id": CLIENT_ID,
            "redirect_uri": REDIRECT_URI,
            "response_type": "code",
            "scope": " ".join(SCOPES),
            "access_type": "offline",
            "prompt": "consent"
        }
        if state:
            params["state"] = state
        return f"{AUTH_ENDPOINT}?{urllib.parse.urlencode(params)}"

    @staticmethod
    def start_login_flow(get_config, save_config):
        import secrets
        state_token = secrets.token_urlsafe(32)
        OAuthCallbackHandler.auth_code = None
        OAuthCallbackHandler.auth_error = None
        OAuthCallbackHandler.expected_state = state_token

        server = None
        try:
            server = HTTPServer(("127.0.0.1", REDIRECT_PORT), OAuthCallbackHandler)
            server.timeout = 120
        except OSError as e:
            logger.warning("Could not bind to port %s: %s", REDIRECT_PORT, e)
            return {"success": False, "error": f"Port {REDIRECT_PORT} is in use. Please close any conflicting app."}

        auth_url = DriveAuthManager.get_auth_url(state=state_token)
        webbrowser.open(auth_url)


        try:
            start_time = time.time()
            while OAuthCallbackHandler.auth_code is None and OAuthCallbackHandler.auth_error is None:
                if time.time() - start_time > 120:
                    return {"success": False, "error": "Authentication timed out (2 minutes)."}
                server.handle_request()
        finally:
            server.server_close()

        if OAuthCallbackHandler.auth_error:
            return {"success": False, "error": f"Authentication rejected: {OAuthCallbackHandler.auth_error}"}

        code = OAuthCallbackHandler.auth_code
        if not code:
            return {"success": False, "error": "No authorization code received."}

        data = urllib.parse.urlencode({
            "code": code,
            "client_id": CLIENT_ID,
            "client_secret": CLIENT_SECRET,
            "redirect_uri": REDIRECT_URI,
            "grant_type": "authorization_code"
        }).encode("utf-8")

        req = urllib.request.Request(TOKEN_ENDPOINT, data=data, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                token_data = json.loads(resp.read().decode("utf-8"))
        except (urllib.error.URLError, json.JSONDecodeError, TimeoutError) as e:
            logger.warning("Token exchange failed: %s", e)
            return {"success": False, "error": f"Failed to exchange token: {e}"}

        access_token = token_data.get("access_token")
        refresh_token = token_data.get("refresh_token")
        expires_in = token_data.get("expires_in", 3600)

        if not access_token:
            return {"success": False, "error": "Google did not return an access token."}

        user_info = DriveAuthManager.fetch_user_info(access_token)
        email = user_info.get("email", "")
        name = user_info.get("name", "")

        cfg = get_config()
        cfg["google_access_token"] = access_token
        if refresh_token:
            cfg["google_refresh_token"] = refresh_token
        cfg["google_token_expiry"] = int(time.time()) + expires_in
        cfg["google_email"] = email
        cfg["google_name"] = name
        cfg["google_connected"] = True
        save_config(cfg)

        DriveAuthManager.bring_app_to_front()
        return {"success": True, "email": email, "name": name}

    @staticmethod
    def bring_app_to_front():
        try:
            import ctypes
            user32 = ctypes.windll.user32
            hwnd = user32.FindWindowW(None, "AANO Notes")
            if not hwnd:
                hwnd = user32.FindWindowW(None, "AANO")
            if hwnd:
                user32.ShowWindow(hwnd, 9)
                user32.SetForegroundWindow(hwnd)
                user32.BringWindowToTop(hwnd)
        except Exception as e:
            logger.debug("Bring window to front: %s", e)

    @staticmethod
    def fetch_user_info(access_token):
        req = urllib.request.Request(USERINFO_ENDPOINT, headers={"Authorization": f"Bearer {access_token}"})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except (urllib.error.URLError, json.JSONDecodeError, TimeoutError) as e:
            logger.warning("Failed to fetch user info: %s", e)
            return {}

    @staticmethod
    def get_valid_access_token(get_config, save_config):
        cfg = get_config()
        access_token = cfg.get("google_access_token")
        refresh_token = cfg.get("google_refresh_token")
        expiry = cfg.get("google_token_expiry", 0)

        if access_token and time.time() < (expiry - 60):
            return access_token

        if not refresh_token:
            return None

        data = urllib.parse.urlencode({
            "client_id": CLIENT_ID,
            "client_secret": CLIENT_SECRET,
            "refresh_token": refresh_token,
            "grant_type": "refresh_token"
        }).encode("utf-8")

        req = urllib.request.Request(TOKEN_ENDPOINT, data=data, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                token_data = json.loads(resp.read().decode("utf-8"))
                new_token = token_data.get("access_token")
                expires_in = token_data.get("expires_in", 3600)
                cfg["google_access_token"] = new_token
                cfg["google_token_expiry"] = int(time.time()) + expires_in
                save_config(cfg)
                return new_token
        except (urllib.error.URLError, json.JSONDecodeError, TimeoutError) as e:
            logger.warning("Token refresh failed: %s", e)
            return None

    @staticmethod
    def logout(get_config, save_config):
        cfg = get_config()
        cfg["google_access_token"] = None
        cfg["google_refresh_token"] = None
        cfg["google_token_expiry"] = 0
        cfg["google_email"] = ""
        cfg["google_name"] = ""
        cfg["google_connected"] = False
        cfg["google_drive_file_id"] = None
        save_config(cfg)
        return {"success": True}
