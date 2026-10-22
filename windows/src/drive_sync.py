# Google Drive OAuth 2.0 loopback authentication & REST API sync for AANO Windows.
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import os
import json
import time
import socket
import webbrowser
import urllib.request
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
import threading
import sys

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
            except Exception:
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

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/callback":
            params = urllib.parse.parse_qs(parsed.query)
            if "code" in params:
                OAuthCallbackHandler.auth_code = params["code"][0]
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>AANO Notes — Connected</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; background: #0f172a; color: #f8fafc; text-align: center; }
                        .card { background: #1e293b; padding: 40px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); max-width: 420px; border: 1px solid #334155; }
                        h2 { color: #38bdf8; margin-top: 0; }
                        p { color: #94a3b8; line-height: 1.5; font-size: 15px; }
                        .badge { display: inline-block; padding: 6px 16px; background: rgba(56, 189, 248, 0.15); color: #38bdf8; border-radius: 20px; font-weight: 600; margin-bottom: 16px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="badge">Google Account Connected</div>
                        <h2>AANO Notes Sync Ready!</h2>
                        <p>Aapka Google account successfully connect ho chuka hai.<br>Aap is browser tab ko band karke AANO app par wapas ja sakte hain.</p>
                    </div>
                    <script>setTimeout(function() { window.close(); }, 3000);</script>
                </body>
                </html>
                """
                self.wfile.write(html.encode("utf-8"))
            else:
                OAuthCallbackHandler.auth_error = params.get("error", ["Unknown error"])[0]
                self.send_response(400)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(f"<h3>Login Failed: {OAuthCallbackHandler.auth_error}</h3>".encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass  # Quiet logging

class GoogleDriveSync:
    def __init__(self, config_getter, config_saver):
        self.get_config = config_getter
        self.save_config = config_saver

    def get_auth_url(self):
        params = {
            "client_id": CLIENT_ID,
            "redirect_uri": REDIRECT_URI,
            "response_type": "code",
            "scope": " ".join(SCOPES),
            "access_type": "offline",
            "prompt": "consent"
        }
        return f"{AUTH_ENDPOINT}?{urllib.parse.urlencode(params)}"

    def start_login_flow(self):
        OAuthCallbackHandler.auth_code = None
        OAuthCallbackHandler.auth_error = None

        server = None
        try:
            server = HTTPServer(("localhost", REDIRECT_PORT), OAuthCallbackHandler)
        except OSError:
            # Port might be in use or bound
            return {"success": False, "error": f"Port {REDIRECT_PORT} busy. Please try again."}

        server_thread = threading.Thread(target=self._run_server, args=(server,), daemon=True)
        server_thread.start()

        auth_url = self.get_auth_url()
        webbrowser.open(auth_url)

        # Wait up to 120 seconds for user authorization
        start_time = time.time()
        while time.time() - start_time < 120:
            if OAuthCallbackHandler.auth_code:
                break
            if OAuthCallbackHandler.auth_error:
                server.server_close()
                return {"success": False, "error": f"OAuth Error: {OAuthCallbackHandler.auth_error}"}
            time.sleep(0.5)

        try:
            server.server_close()
        except Exception:
            pass

        code = OAuthCallbackHandler.auth_code
        if not code:
            return {"success": False, "error": "Login timed out or was cancelled."}

        return self._exchange_code_for_tokens(code)

    def _run_server(self, server):
        try:
            server.handle_request()
        except Exception:
            pass

    def _exchange_code_for_tokens(self, code):
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
        except Exception as e:
            return {"success": False, "error": f"Token exchange failed: {e}"}

        access_token = token_data.get("access_token")
        refresh_token = token_data.get("refresh_token")
        expires_in = token_data.get("expires_in", 3600)

        # Fetch user profile info
        user_info = self._fetch_user_info(access_token)
        email = user_info.get("email", "")
        name = user_info.get("name", "")

        cfg = self.get_config()
        cfg["google_access_token"] = access_token
        if refresh_token:
            cfg["google_refresh_token"] = refresh_token
        cfg["google_token_expiry"] = int(time.time()) + expires_in
        cfg["google_email"] = email
        cfg["google_name"] = name
        cfg["google_connected"] = True
        self.save_config(cfg)

        return {"success": True, "email": email, "name": name}

    def _fetch_user_info(self, access_token):
        req = urllib.request.Request(USERINFO_ENDPOINT, headers={"Authorization": f"Bearer {access_token}"})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception:
            return {}

    def get_valid_access_token(self):
        cfg = self.get_config()
        access_token = cfg.get("google_access_token")
        refresh_token = cfg.get("google_refresh_token")
        expiry = cfg.get("google_token_expiry", 0)

        # If token is still valid for at least 60 seconds
        if access_token and time.time() < (expiry - 60):
            return access_token

        if not refresh_token:
            return None

        # Refresh the token
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
                self.save_config(cfg)
                return new_token
        except Exception as e:
            print(f"[AANO Drive] Token refresh failed: {e}")
            return None

    def logout(self):
        cfg = self.get_config()
        cfg["google_access_token"] = None
        cfg["google_refresh_token"] = None
        cfg["google_token_expiry"] = 0
        cfg["google_email"] = ""
        cfg["google_name"] = ""
        cfg["google_connected"] = False
        cfg["google_drive_file_id"] = None
        self.save_config(cfg)
        return {"success": True}

    def find_remote_file(self, token, filename="aano-notes.json"):
        query = f"name = '{filename}' and trashed = false"
        url = f"{DRIVE_FILES_ENDPOINT}?q={urllib.parse.quote(query)}&fields=files(id,name,modifiedTime)"
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                res = json.loads(resp.read().decode("utf-8"))
                files = res.get("files", [])
                return files[0] if files else None
        except Exception as e:
            print(f"[AANO Drive] find_remote_file error: {e}")
            return None

    def sync_notes(self, local_notes):
        token = self.get_valid_access_token()
        if not token:
            return {"success": False, "error": "Not signed in to Google Drive or token expired.", "auth_expired": True}

        filename = "aano-notes.json"
        remote_file = self.find_remote_file(token, filename)

        remote_notes = []
        if remote_file:
            file_id = remote_file["id"]
            download_url = f"{DRIVE_FILES_ENDPOINT}/{file_id}?alt=media"
            req = urllib.request.Request(download_url, headers={"Authorization": f"Bearer {token}"})
            try:
                with urllib.request.urlopen(req, timeout=20) as resp:
                    content = resp.read().decode("utf-8")
                    remote_notes = json.loads(content)
                    if isinstance(remote_notes, dict) and "notes" in remote_notes:
                        remote_notes = remote_notes["notes"]
            except Exception as e:
                print(f"[AANO Drive] Download remote notes failed: {e}")

        # Merge local and remote notes
        merged_notes = self._merge_notes(local_notes, remote_notes)

        # Upload merged notes back to Google Drive (Exclude local-only notes from cloud)
        cloud_payload = [n for n in merged_notes if not n.get("isLocal", False)]
        json_data = json.dumps(cloud_payload, ensure_ascii=False, indent=2).encode("utf-8")

        if remote_file:
            # Update existing file
            upload_url = f"{DRIVE_UPLOAD_ENDPOINT}/{remote_file['id']}?uploadType=media"
            req = urllib.request.Request(upload_url, data=json_data, headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json; charset=UTF-8"
            }, method="PATCH")
        else:
            # Create new file using simple multipart upload
            meta = json.dumps({"name": filename, "mimeType": "application/json"}).encode("utf-8")
            boundary = "-------314159265358979323846"
            delimiter = f"\r\n--{boundary}\r\n".encode("utf-8")
            close_delim = f"\r\n--{boundary}--\r\n".encode("utf-8")

            body = (
                delimiter +
                b"Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                meta +
                delimiter +
                b"Content-Type: application/json\r\n\r\n" +
                json_data +
                close_delim
            )

            upload_url = f"{DRIVE_UPLOAD_ENDPOINT}?uploadType=multipart"
            req = urllib.request.Request(upload_url, data=body, headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": f"multipart/related; boundary={boundary}"
            }, method="POST")

        try:
            with urllib.request.urlopen(req, timeout=25) as resp:
                resp.read()
        except Exception as e:
            return {"success": False, "error": f"Drive upload failed: {e}"}

        return {"success": True, "merged_notes": merged_notes, "count": len(merged_notes)}

    def _merge_notes(self, local_notes, remote_notes):
        local_map = {n["id"]: n for n in local_notes}
        for rn in remote_notes:
            rid = rn.get("id")
            if not rid:
                continue
            existing = local_map.get(rid)
            if existing and existing.get("isLocal"):
                continue
            if not existing:
                local_map[rid] = rn
            else:
                is_deleted = bool(existing.get("deleted", False) or rn.get("deleted", False))
                is_archived = bool(existing.get("archived", False) or rn.get("archived", False))
                rn_updated = rn.get("updatedAt", 0)
                ex_updated = existing.get("updatedAt", 0)

                if rn_updated >= ex_updated:
                    merged = dict(rn)
                    merged["deleted"] = is_deleted
                    merged["archived"] = is_archived
                    if is_deleted:
                        merged["deletedAt"] = max(existing.get("deletedAt", 0), rn.get("deletedAt", 0), rn_updated)
                    local_map[rid] = merged
                else:
                    existing["deleted"] = is_deleted
                    existing["archived"] = is_archived
                    if is_deleted:
                        existing["deletedAt"] = max(existing.get("deletedAt", 0), rn.get("deletedAt", 0), ex_updated)
                    local_map[rid] = existing

        return list(local_map.values())
