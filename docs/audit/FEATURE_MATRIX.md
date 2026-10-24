# Phase 3 — Complete Feature & Functionality Traceability Matrix

## 1. Feature Inventory & Implementation Mapping

| ID | Feature Name | Primary Component / Files | Status | Test / Verification Method |
|---|---|---|---|---|
| **F-01** | Note Creation & Rich Editing | `EditorActivity.kt`, `Note.kt`, `NoteRepository.kt` | **OPERATIONAL** | Verified via lifecycle tests & UI flow |
| **F-02** | 12 Aesthetic Color Themes | `res/values/colors.xml`, `res/values-night/colors.xml` | **OPERATIONAL** | Verified 1:1 Day/Night token symmetry |
| **F-03** | Local Media Vault (WebP & Disk GC) | `LocalVaultManager.kt`, `CropActivity.kt` | **OPERATIONAL** | File I/O + WebP conversion pipeline |
| **F-04** | Master PIN & Biometric Lock | `AppLock.kt`, `LockActivity.kt`, `androidx.biometric` | **OPERATIONAL** | BiometricPrompt + SHA-256 PIN hash |
| **F-05** | Folders & Category Management | `FoldersFragment.kt`, `Note.kt` (`folder` field) | **OPERATIONAL** | CRUD + folder badge filtering |
| **F-06** | Task Management & Auto-Sort | `TasksFragment.kt`, `TaskRepository.kt`, `Task.kt` | **OPERATIONAL** | Task status toggling & animated sort |
| **F-07** | Trash & Purge Lifecycle | `TrashActivity.kt`, `TrashAdapter.kt`, `NoteRepository.kt` | **OPERATIONAL** | Soft-delete, restore, tombstone sync |
| **F-08** | Archive Lifecycle | `ArchiveActivity.kt`, `ArchiveAdapter.kt` | **OPERATIONAL** | Archive flag toggling & UI isolation |
| **F-09** | Dual Sync Engine | `CloudSyncManager.kt`, `DriveSyncManager.kt` | **OPERATIONAL** | Google Drive REST API + Cloudflare D1 |
| **F-10** | AANO Drop (Wi-Fi P2P Selective Transfer) | `AanoDropManager.kt`, `QrCode.java` | **OPERATIONAL** | RFC 1918 socket transfer on port 8766 |
| **F-11** | Quick Settings Tile | `AanoTileService.kt`, `QuickNoteActivity.kt` | **OPERATIONAL** | TileService manifest registration |
| **F-12** | Share Receiver Web Clipper | `ShareReceiverActivity.kt` | **OPERATIONAL** | Intent filter ACTION_SEND text & image |
| **F-13** | Native PDF & Markdown Export | `PdfExportManager.kt`, `EditorActivity.kt` | **OPERATIONAL** | Android PdfDocument generator |
| **F-14** | One-Click Vault Backup (.zip) | `LocalVaultManager.kt` (`exportVaultZip`, `restoreVaultZip`)| **OPERATIONAL** | ZipOutputStream / ZipInputStream |

---

## 2. Granular Flow Traceability

### Flow A: Note Creation to Sync
1. User taps FAB in `NotesFragment.kt` -> launches `EditorActivity.kt`.
2. Title & body edited -> auto-saved debounced to `NoteRepository.saveNote(note)`.
3. If cloud sync enabled, `CloudSyncManager` merges metadata into sync queue.
4. If marked On-Device / Local Vault, note is stored with `isVault = true` and excluded from public cloud sync.

### Flow B: AANO Drop (Android ➔ PC & PC ➔ Android)
1. Android sends note: User selects note -> taps "Drop via Wi-Fi" -> scans PC QR code (contains IP + Port 8765 + Token).
2. Android HTTP client posts JSON payload to `http://<PC_IP>:8765/aano/drop`.
3. Android receives note: Tap "Receive via Wi-Fi" in `LocalFragment.kt` -> starts local NanoHTTPD on port 8766.
4. PC pushes note -> Phone receives, validates token, plays haptic chime, persists to local vault.

### Flow C: Trash Lifecycle & Cloud Consistency
1. Note deleted -> `note.isDeleted = true`, `note.deletedAt = timestamp`.
2. In Trash screen, user taps "Empty Trash":
   - Notes permanently purged locally.
   - Tombstone record (`is_purged = true`, `purged_at`) generated and sent to Cloudflare D1 `/api/sync` so deleted notes never resurrect from remote devices.

---

## 3. Audit Verdict
**Phase 3 Status: PASS (14/14 Features Operational & Traceable)**
