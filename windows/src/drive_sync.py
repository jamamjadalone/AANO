# Google Drive REST API note synchronization for AANO Windows.
# Copyright (C) 2026 Jam Amjad Rasheed
# Licensed under GPL-3.0-or-later.

import json
import urllib.request
import urllib.parse
import logging
import merge_engine
from drive_auth import DriveAuthManager, DRIVE_FILES_ENDPOINT, DRIVE_UPLOAD_ENDPOINT

logger = logging.getLogger("AANO.DriveSync")

class GoogleDriveSync:
    def __init__(self, config_getter, config_saver):
        self.get_config = config_getter
        self.save_config = config_saver

    def start_login_flow(self):
        return DriveAuthManager.start_login_flow(self.get_config, self.save_config)

    def get_valid_access_token(self):
        return DriveAuthManager.get_valid_access_token(self.get_config, self.save_config)

    def logout(self):
        return DriveAuthManager.logout(self.get_config, self.save_config)

    def find_remote_file(self, token, filename="aano-notes.json"):
        query = f"name = '{filename}' and trashed = false"
        url = f"{DRIVE_FILES_ENDPOINT}?q={urllib.parse.quote(query)}&fields=files(id,name,modifiedTime)"
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                res = json.loads(resp.read().decode("utf-8"))
                files = res.get("files", [])
                return files[0] if files else None
        except (urllib.error.URLError, json.JSONDecodeError, TimeoutError) as e:
            logger.warning("find_remote_file error: %s", e)
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
            except (urllib.error.URLError, json.JSONDecodeError, TimeoutError) as e:
                logger.warning("Download remote notes failed: %s", e)

        merged_notes = self._merge_notes(local_notes, remote_notes)
        cloud_payload = [n for n in merged_notes if not n.get("isLocal", False)]
        json_data = json.dumps(cloud_payload, ensure_ascii=False, indent=2).encode("utf-8")

        if remote_file:
            upload_url = f"{DRIVE_UPLOAD_ENDPOINT}/{remote_file['id']}?uploadType=media"
            req = urllib.request.Request(upload_url, data=json_data, headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json; charset=UTF-8"
            }, method="PATCH")
        else:
            meta = json.dumps({"name": filename, "mimeType": "application/json"}).encode("utf-8")
            boundary = "-------314159265358979323846"
            delimiter = f"\r\n--{boundary}\r\n".encode("utf-8")
            close_delim = f"\r\n--{boundary}--\r\n".encode("utf-8")

            body = (
                delimiter +
                b"Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                meta +
                delimiter +
                b"Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                json_data +
                close_delim
            )
            upload_url = f"{DRIVE_UPLOAD_ENDPOINT}?uploadType=multipart"
            req = urllib.request.Request(upload_url, data=body, headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": f"multipart/related; boundary={boundary}"
            }, method="POST")

        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                upload_res = json.loads(resp.read().decode("utf-8"))
                new_file_id = upload_res.get("id")
                if new_file_id:
                    cfg = self.get_config()
                    cfg["google_drive_file_id"] = new_file_id
                    self.save_config(cfg)
                return {"success": True, "count": len(merged_notes), "merged_notes": merged_notes}
        except (urllib.error.URLError, json.JSONDecodeError, TimeoutError) as e:
            logger.warning("Upload merged notes failed: %s", e)
            return {"success": False, "error": f"Failed to upload to Google Drive: {e}"}

    def _merge_notes(self, local_notes, remote_notes):
        local_map = {n["id"]: n for n in local_notes if "id" in n}
        remote_map = {n["id"]: n for n in remote_notes if "id" in n}

        all_ids = set(local_map.keys()).union(set(remote_map.keys()))
        merged = []

        for nid in all_ids:
            loc = local_map.get(nid)
            rem = remote_map.get(nid)

            if loc and not rem:
                merged.append(loc)
            elif rem and not loc:
                merged.append(rem)
            else:
                loc_del = loc.get("deleted", False)
                rem_del = rem.get("deleted", False)
                if loc_del or rem_del:
                    winner = loc if loc.get("updatedAt", 0) >= rem.get("updatedAt", 0) else rem
                    merged.append(winner)
                    continue

                loc_updated = loc.get("updatedAt", 0)
                rem_updated = rem.get("updatedAt", 0)

                if loc_updated == rem_updated:
                    merged.append(loc)
                else:
                    try:
                        merged_content = merge_engine.merge_note_content(
                            loc.get("content", ""),
                            rem.get("content", "")
                        )
                        winner = dict(loc if loc_updated > rem_updated else rem)
                        winner["content"] = merged_content
                        winner["updatedAt"] = max(loc_updated, rem_updated)
                        merged.append(winner)
                    except Exception as me:
                        logger.warning("3-way merge fallback for note %s: %s", nid, me)
                        winner = loc if loc_updated > rem_updated else rem
                        merged.append(winner)

        return merged
