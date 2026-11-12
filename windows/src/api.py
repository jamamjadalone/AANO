# AANO Windows Desktop App - Python/JS Bridge API Facade
# Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.

import aano_drop_server
from api_notes import NotesApiMixin
from api_trash import TrashApiMixin
from api_sync import SyncApiMixin
from api_drop import DropApiMixin
from api_media import MediaApiMixin
from api_export import ExportApiMixin
from api_lock import LockApiMixin

class DesktopApi(NotesApiMixin, TrashApiMixin, SyncApiMixin, DropApiMixin, MediaApiMixin, ExportApiMixin, LockApiMixin):
    """
    Composite PyWebView JavaScript Bridge facade delegating across single-responsibility modules:
    - NotesApiMixin: Notes CRUD, blocks, markdown/plain conversion, pinning, folders.
    - TrashApiMixin: Soft-delete, restore, permanent purge, media cleanup.
    - SyncApiMixin: Google Drive sync, session status, cloud credentials.
    - DropApiMixin: AANO Drop 2.0 P2P send/receive, nearby discovery radar.
    - MediaApiMixin: Image attachment dialogs, cropping, WebP encoding.
    - ExportApiMixin: Formatted note exports (TXT, MD, PDF, ZIP, .aano package).
    - LockApiMixin: AppLock PIN protection, inactivity auto-lock, exponential backoff.
    """
    def __init__(self):
        super().__init__()
        self._last_url = ""
        self._last_open_time = 0.0
        self._purge_expired_trash()
        self._async_auto_sync()
        self._drop_server = aano_drop_server.AanoDropServer(on_receive_callback=self._on_drop_received)
        self._drop_server.start()
