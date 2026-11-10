# AANO Codebase Architecture Index (Rule 21 Single Source of Truth)
> **Token Optimization Directive**: Agents and developers must consult this index *before* reading or searching multiple files. This index maps all 87 source files and their exact single responsibilities.
> **Rule 21 Cap**: Strict <= 300 lines per file. Zero monolithic files allowed.

---

## 1. Directory Topology

```
android/app/src/main/java/app/aa/aano/
├── pages/                  # All screen activities and main UI fragments
├── components/
│   ├── editor/             # Rich text editor delegates, media & format helpers
│   ├── dialogs/            # Modal dialogs (folders, colors, drop, vault, pin)
│   ├── views/              # Custom views (crop overlays, drop radar hubs)
│   └── adapters/           # RecyclerView adapters and view holders
├── data/                   # Repositories, DAOs, models, cipher, and JSON sync
├── drop/                   # P2P Wi-Fi direct transfer engine (HTTP + UDP radar)
├── net/                    # Cloud sync (Drive / Cloudflare), broadcast receivers
└── service/                # Background tile services and notification hooks
```

---

## 2. Component Map by Package

### `pages/` (Screens & Fragments — Strictly UI Orchestration)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `HomeActivity.kt` | 120 | Main landing screen, bottom navigation routing, and toolbar coordination. |
| `EditorActivity.kt` | 297 | Note editor screen orchestrating format bar, audio, checklist, and media. |
| `NotesFragment.kt` | 296 | Active notes grid/staggered feed, search filter bar, and selection toolbar. |
| `LocalFragment.kt` | 288 | Encrypted offline local vault notes screen with P2P drop pairing actions. |
| `SettingsActivity.kt` | 274 | App preferences screen (theme, font, auto-lock, cloud accounts, backup). |
| `FoldersFragment.kt` | 239 | Folder navigation grid, custom folder creation, and folder counts. |
| `SearchFragment.kt` | 114 | Fast SQLite FTS note search screen with real-time result highlighting. |
| `FocusFragment.kt` | 183 | Distraction-free zen reading and single-note contemplation mode. |
| `ArchiveActivity.kt` | 97 | Archived notes list with batch unarchive, delete, and search actions. |
| `TrashActivity.kt` | 114 | Deleted notes recycle bin with 30-day auto-purge and restore controls. |
| `LockActivity.kt` | 250 | AppLock PIN / biometric authentication prompt screen. |
| `CropActivity.kt` | 158 | Image cropping screen with custom aspect ratios and rotation controls. |
| `QuickNoteActivity.kt` | 67 | Floating/quick note creation dialog activity launched from quick settings. |
| `ShareReceiverActivity.kt` | 287 | Android system share target intent receiver (text, images, URLs). |
| `MoreFragment.kt` | 139 | Utility hub fragment (Trash, Archive, Vault, Help, About AANO). |
| `TasksFragment.kt` | 196 | To-Do checklist and task tracking screen with priority filters. |
| `CalendarFragment.kt` | 178 | Full-screen interactive month calendar grid with day sticky-note pills and monthly stats. |

---

### `components/editor/` (Editor Subsystems & Delegates)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `EditorAudioHelper.kt` | 241 | Voice memo audio recording (M4A/AAC), playback controls, timer, and visualizer. |
| `EditorChecklistHelper.kt` | 168 | Markdown interactive checklist toggling, batch checked sinking, and item menus. |
| `EditorColorPickerHelper.kt` | 225 | ARGB note background color picker, preset palettes, and custom hex builder. |
| `EditorDialogHelper.kt` | 272 | Folder selection, note deletion, reminder scheduling, and share dialogs. |
| `EditorDropHelper.kt` | 87 | Direct P2P note drop dispatcher, QR scanner launcher, and peer pairing. |
| `EditorExportHelper.kt` | 270 | Note export to TXT, MD, PDF, ZIP attachment bundles, and `.aano` packages. |
| `EditorFormatBarHelper.kt` | 297 | Rich text format toolbar (Bold, Italic, Strikethrough, Underline, Spans). |
| `EditorImageCoordinator.kt` | 96 | Image picker contracts, crop launchers, replace triggers, and image container render. |
| `EditorImageHelper.kt` | 279 | Fullscreen image lightbox preview, crop dialog, and gallery exporter. |
| `EditorMediaHelper.kt` | 86 | Image downsampling (1280px), JPEG compression, and local sandbox caching. |
| `EditorMenuHelper.kt` | 37 | Editor options menu inflation and navigation item click routing. |
| `EditorTouchHelper.kt` | 118 | Text selection touch coordinates, URL linkify navigation, and view/edit modes. |
| `EditorUndoRedoManager.kt` | 100 | Stack-based undo/redo text history buffer for editor content. |

---

### `components/dialogs/` (Reusable Popups & Sheets)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `DropHubDialogHelper.kt` | 264 | P2P Wi-Fi transfer hub modal with QR code display and peer status. |
| `LocalDropCoordinator.kt` | 74 | Local vault P2P note transfer workflow, QR generator, and pairing dialog. |
| `LocalNoteActionDialogHelper.kt` | 120 | Long-press note action menu (Move, Export, Tag, Delete) in LocalFragment. |
| `NotesColorDialogHelper.kt` | 214 | Note color selection popup dialog for quick theme tinting. |
| `NotesFolderDialogHelper.kt` | 152 | Folder creator and folder assignment modal dialog. |
| `NotesBatchActionDialogHelper.kt` | 145 | Multi-note selection batch actions (Archive, Delete, Pin, Move). |
| `NotesExportDialogHelper.kt` | 162 | Multi-note batch export selection dialog (JSON / PDF / ZIP). |
| `NotesChipBarHelper.kt` | 204 | Filter chips bar (All, Folders, Tags, Color pills) on NotesFragment. |
| `TaskDialogHelper.kt` | 203 | Task create / edit dialog with priority, due date, and subtask editor. |
| `PasscodeDialogHelper.kt` | 104 | PIN creation and verification dialog for private folders and notes. |
| `LocalVaultDialogHelper.kt` | 81 | Local offline vault unlock and passcode prompt dialog. |
| `CustomColorPickerDialogHelper.kt` | 104 | Hex ARGB color picker dialog with RGB sliders, live preview, and recent swatches. |
| `NoteActionDialogHelper.kt` | 215 | Notes feed long-press action menu (Pin, Color, Move, Archive, Delete). |
| `NotesBatchActionHelper.kt` | 250 | Batch selection action sheet coordinator (Archive, Delete, Pin multiple notes). |
| `SettingsAuthCoordinator.kt` | 65 | Cloudflare / Google sign-in UI coordinator and status delegate. |
| `SettingsOtpDialogDelegate.kt` | 220 | Password-less email OTP verification code input dialog. |
| `CalendarDayDetailBottomSheet.kt` | 120 | Bottom sheet dialog displaying notes and reminders scheduled on a specific date. |

---

### `components/views/` (Custom Views & Visuals)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `CropOverlayView.kt` | 211 | Interactive touch-based image cropping rect with draggable corner handles. |
| `DropHubViewHelper.kt` | 108 | UI card builders for Wi-Fi P2P status, QR display, and connection badges. |
| `EditorFormatBarView.kt` | 120 | Floating horizontal styling toolbar layout builder. |
| `AudioVisualizerView.kt` | 85 | Real-time audio amplitude wave rendering view during voice memo recording. |
| `ChecklistSpanView.kt` | 92 | Custom clickable checkbox view embedded inside Spannable text. |
| `ColorPillView.kt` | 68 | Circular color swatch indicator with active selection ring. |
| `CropMathHelper.kt` | 137 | Image matrix transformations, rotation radians, and crop rect calculations. |
| `LinedNoteEditText.kt` | 166 | High-performance ruled notebook canvas with bottom viewport extension. |
| `NotesMenuHelper.kt` | 79 | NotesFragment options menu inflation and sort order selection handler. |
| `NotesSearchBarHelper.kt` | 28 | Real-time search query text watcher and clear button coordinator. |
| `CalendarMonthView.kt` | 180 | Custom 7x6 month grid view rendering colored sticky-note pills with zero allocations in onDraw. |

---

### `components/adapters/` (List & Grid Bindings)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `NotesAdapter.kt` | 259 | Main notes grid adapter with staggered cards, colors, and badge tags. |
| `ArchiveAdapter.kt` | 76 | Archived notes list adapter with unarchive action callbacks. |
| `TrashAdapter.kt` | 59 | Recycled notes list adapter with restore and permanent purge callbacks. |
| `TasksAdapter.kt` | 126 | Checklist task items adapter with strike-through completion and priority dots. |
| `FoldersAdapter.kt` | 85 | Folder grid card adapter with note count badges. |
| `FocusAdapter.kt` | 52 | FocusFragment reading cards list adapter. |

---

### `data/` (Storage, Models, Keystore Cipher, & Repositories)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `NoteRepository.kt` | 202 | Clean facade exposing all 39 note operations to activities (0 breaking changes). |
| `NoteStorageDelegate.kt` | 217 | Low-level JSON read/write, Keystore AES-256 cipher, and memory cache. |
| `NoteQueryDelegate.kt` | 110 | SQLite FTS search, sorting algorithms, visibility filters, and aggregations. |
| `NoteCrudDelegate.kt` | 294 | Note mutations (create, update, pin, reminder, delete, archive, folders). |
| `NoteSyncTrashDelegate.kt` | 218 | Multi-device 3-way merge, trash purge lifecycle, and cloud JSON export/import. |
| `Note.kt` | 110 | Core Note data model, JSON serialization, and immutable copy methods. |
| `NoteCipher.kt` | 97 | Hardware-backed Android Keystore AES-256-GCM cipher wrapper. |
| `NoteFtsIndex.kt` | 139 | SQLite Full-Text Search (FTS4) index database for sub-millisecond search. |
| `NoteMergeEngine.kt` | 70 | 3-way conflict-free block and line merging engine for notes. |
| `LocalVaultManager.kt` | 276 | Offline-only encrypted note storage vault with separate Keystore key. |
| `TaskRepository.kt` | 275 | To-Do task JSON persistence repository and priority sorting. |
| `Task.kt` | 56 | Task, SubTask, Priority, and Recurrence data models. |
| `TaskSerializer.kt` | 106 | Task JSON serializer/deserializer with encryption spans. |
| `TaskMergeEngine.kt` | 46 | Conflict-free sync merge engine for tasks. |
| `Prefs.kt` | 112 | SharedPreferences wrapper for user settings, themes, sort order, and accounts. |
| `AppLock.kt` | 54 | App PIN / Biometric auto-lock timer and authentication validator. |
| `DocumentIngestionReader.kt` | 89 | Plaintext, Markdown, and HTML file ingestion into new notes. |
| `PdfExportManager.kt` | 245 | Android `PdfDocument` rendering engine for generating clean note PDFs. |
| `NotePackageUnpacker.kt` | 181 | `.aano` ZIP archive parser for restoring notes and media bundles. |
| `SettingsBackupDelegate.kt` | 71 | Encrypted app settings JSON backup and restore delegate. |
| `NoteBlock.kt` | 124 | Block-based rich text model (headers, quotes, code, lists, images). |
| `EditorPersistenceDelegate.kt` | 106 | Auto-save draft persistence and HTML span serialization delegate for editor. |

---

### `drop/` (P2P Wi-Fi Direct Transfer Subsystems)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `AanoDropManager.kt` | 112 | Singleton coordinator facade for local Wi-Fi peer discovery and transfer. |
| `DropNetworkHelper.kt` | 166 | Network state (Wi-Fi/Hotspot/Offline), Wi-Fi lock, QR generator, settings intents. |
| `DropRadar.kt` | 242 | Fast UDP discovery beacon (port 8767), subnet radar sweep, and peer pairing. |
| `DropClient.kt` | 163 | Outbound HTTP client, payload packaging, AES-GCM encryption, port fallback. |
| `DropPayloadProcessor.kt` | 142 | Payload decryption, image whitelist verification, note 3-way merge, haptic chime. |
| `DropServer.kt` | 268 | Embedded HTTP server (port 8766) with `/aano/info`, `/aano/pair`, `/aano/drop` routes. |

---

### `net/` (Cloud Sync & Broadcast Receivers)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `CloudSyncManager.kt` | 213 | Google Drive AppData sync coordinator, upload/download triggers, conflict resolve. |
| `DriveSyncManager.kt` | 168 | Google Drive REST API HTTP client (`https://www.googleapis.com/auth/drive.file`). |
| `CloudflareSyncManager.kt` | 178 | Standalone Cloudflare Worker backup client (password-less sync bridge). |
| `NoteReminderReceiver.kt` | 162 | AlarmManager broadcast receiver for scheduled note notifications. |
| `TaskReminderReceiver.kt` | 107 | AlarmManager broadcast receiver for scheduled task reminders. |
| `BootReceiver.kt` | 71 | AlarmManager notification scheduler re-registration on device boot. |
| `NetworkStateReceiver.kt` | 55 | Connectivity change listener to auto-trigger pending cloud syncs on Wi-Fi. |
| `NotesSyncCoordinator.kt` | 76 | Periodic and Wi-Fi auto-sync trigger coordinator for notes. |
| `SettingsAuthHelper.kt` | 152 | Standalone Cloudflare email auth and token storage helper. |

---

### `root` & `service/`
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `AanoApp.kt` | 67 | Application class: dynamic color themes, notification channels, unhandled crash log. |
| `AanoTileService.kt` | 35 | Android Quick Settings Tile to open QuickNoteActivity with single tap. |
| `NoteWidgetProvider.kt` | 78 | Android Home Screen AppWidget provider with dynamic list view and quick capture shortcut. |


---

## 3. Windows Desktop Architecture (Python & Web UI)

### `windows/src/` (Python Backend Architecture — PyWebView Bridge & Storage)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `main.py` | 93 | Desktop app entrypoint, PyWebView window setup, native window state, and hotkey daemon boot. |
| `api.py` | 28 | Composite JS bridge API facade inheriting mixins (`NotesApiMixin`, `TrashApiMixin`, `SyncApiMixin`, etc.). |
| `api_notes.py` | 247 | Notes CRUD, search integration, color resolution, folder management, block model conversion. |
| `api_trash.py` | 97 | Soft-delete, trash recycle bin, note restoration, permanent deletion, and media purge. |
| `api_sync.py` | 71 | Google Drive sync trigger, cloud login/status delegation, and auto-sync worker. |
| `api_drop.py` | 53 | AANO Drop 2.0 P2P transfer bridge, radar scan triggers, and peer pairing. |
| `api_media.py` | 169 | Image attachment dialogs, cropping, WebP encoding, and media path resolution. |
| `api_export.py` | 209 | Note export to TXT, MD, PDF, ZIP bundles, `.aano` package unpacker, and URL handler. |
| `storage.py` | 256 | Notes persistence, config management, color labels, DPAPI encryption, and sync lock. |
| `storage_vault.py` | 42 | Windows DPAPI encryption/decryption primitives (`CryptProtectData`/`CryptUnprotectData`). |
| `image_processor.py` | 133 | Media vault image compressor, thumbnail generator, aspect-ratio cropper, and rotation. |
| `drive_auth.py` | 245 | Google Drive OAuth 2.0 loopback server (port 8085), token refresh, and credentials parser. |
| `drive_sync.py` | 150 | Google Drive REST API file manager, appData folder lookup, and upload/download coordinator. |
| `drop_payload_handler.py` | 187 | AANO Drop HTTP request handler (`/aano/info`, `/aano/pair`, `/aano/drop`), chunk verification. |
| `aano_drop_server.py` | 160 | Embedded HTTP server (port 8766) and UDP discovery beacon loop (port 8767). |
| `aano_drop_client.py` | 230 | Outbound P2P payload transmitter, network scanner, and paired peer manager. |
| `block_model.py` | 134 | Rich text block model, HTML-to-blocks parser, and Markdown/plain-text converters. |
| `fts_index.py` | 142 | SQLite Full-Text Search (FTS5) local indexer and search query parser. |
| `hotkey_daemon.py` | 70 | Global Windows keyboard hook daemon (Win+Alt+N quick note launcher). |
| `merge_engine.py` | 92 | 3-way note reconciliation and conflict resolution engine for cloud and drop sync. |

---

### `windows/src/ui/css/` (Modular Desktop Stylesheets)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `styles.css` | 12 | Main CSS aggregator importing modular style sheets under Rule 21. |
| `base.css` | 263 | Design tokens, CSS variables, dark/light themes, reset, scrollbars, and main frame layout. |
| `sidebar.css` | 298 | Sidebar navigation, search bar container, folders list, tag filters, and sync status footer. |
| `cards.css` | 262 | Notes grid, masonry card layout, card themes, contrast adaptation, selection checkboxes. |
| `toolbar.css` | 260 | Live color palette filter bar, multi-select batch action bar, and export dropdown menu. |
| `editor.css` | 237 | Note editor modal, title header, lined notebook paper canvas, and formatting toolbar. |
| `modals.css` | 220 | Settings dialog, confirmation prompts, folder modal, and Google sign-in button. |
| `media_drop.css` | 234 | Attached image preview modal, interactive image cropper, and PC Drop QR scanner card. |

---

### `windows/src/ui/js/` (Modular Desktop JavaScript Frontend)
| File | Lines | Responsibility |
| :--- | :--- | :--- |
| `app.js` | 67 | Desktop frontend entrypoint, boot sequence, periodic sync/peer polling, and bridge listeners. |
| `app_state.js` | 94 | Global reactive state variables, color palettes, PyWebView bridge accessor, and regex patterns. |
| `app_utils.js` | 193 | HTML escaping, date formatting, color math, and theme switcher (Dark/Light). |
| `app_dialogs.js` | 119 | Custom native dialogs (`showNativeConfirm`, `showNativePrompt`, `showNativeAlert`). |
| `app_notes_render.js` | 261 | Notes grid/masonry card renderer, preview content generator, and link auto-linkify. |
| `app_notes_filter.js` | 221 | Note list filtering, search queries, folders renderer, and palette filter bar. |
| `app_editor_core.js` | 168 | Note editor modal lifecycle (open, save, discard, pin, word count, view/edit mode). |
| `app_editor_toolbar.js` | 203 | Rich text formatting controls, heading blocks, block reordering, and color palette. |
| `app_editor_checklist.js` | 125 | Interactive Markdown checklist toggle, strikethrough sinking, and checklist actions. |
| `app_editor_events.js` | 155 | Note editor event listeners (save, pin, delete, archive, folder select, view switch). |
| `app_media.js` | 168 | Image attachment loading, thumbnails strip rendering, and full-screen image preview. |
| `app_cropper.js` | 250 | Interactive HTML5 canvas image cropper with aspect-ratio locking and rotation. |
| `app_sync.js` | 274 | Google Drive OAuth modal, manual/auto-sync heartbeat, and backup export/import. |
| `app_drop.js` | 270 | AANO Drop 2.0 P2P discovery modal, paired peer status, and standard QR generation. |
| `app_scanner.js` | 164 | Web camera QR code scanner, pairing URI paste, and pairing handshake. |
| `app_export.js` | 147 | Unified export dropdown menu actions (TXT, MD, PDF, ZIP, `.aano` package). |
| `app_batch_events.js` | 87 | Multi-select batch action bar event listeners (batch delete, archive, move, color). |
| `app_events.js` | 231 | Global keyboard shortcuts, search input, view toggle, and modal backdrop listeners. |

