# Changelog

All notable changes to the **AANO Notes** project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.2.0-beta.3] - 2026-11-03

### Fixed
- **AanoDrop Socket EOF Spin (BUG-01)**: Corrected byte read while-loop condition in `AanoDropManager.kt` preventing infinite 100% CPU lock on unexpected peer disconnection.
- **Task Deletion & LWW Synchronization (BUG-02)**: Added `deleted` and `deletedAt` tombstone fields to `Task.kt` and `TaskRepository.kt`, preventing deleted tasks from resurrecting during Google Drive merges.
- **Image Editor Out-of-Memory (BUG-03)**: Implemented proportional bitmap downsampling (`inSampleSize`) targeting 1280px in `EditorActivity.kt` with intermediate recycling.
- **Audio & Microphone Lifecycle Leaks (BUG-04)**: Bound `stopVoiceRecording()` to `onPause()` and guaranteed release of `MediaPlayer` and `MediaRecorder` in `onDestroy()`.
- **Drop Manager Activity Context Leak (BUG-05)**: Cleared `onNotesReceivedListener` in `HomeActivity.onDestroy()` and bound toast notifications to `applicationContext`.
- **Windows Media Storage Orphanage (BUG-06)**: Added unlinking of orphaned images and audio attachments upon note permanent deletion and trash purges in `windows/src/api.py`.
- **Backend CORS Wildcard Fix (BUG-07)**: Enforced strict regex matching for local development origins (`localhost`, `127.0.0.1`) in Cloudflare worker.
- **Google Drive Remote Wipeout Guard (BUG-08)**: Safeguarded `sync()` in `DriveSyncManager.kt` to abort push cycles if remote files exist but pull encountered download errors.
- **Android Keystore Keygen Concurrency (BUG-09)**: Synchronized AES key generation in `NoteCipher.kt` to eliminate key overwrites during simultaneous note encryptions.
- **Orphaned Alarm Cancellations (BUG-10)**: Bound `AlarmManager.cancel()` prior to `PendingIntent.cancel()` across `TaskReminderReceiver.kt` and `NoteReminderReceiver.kt`.
- **Zip Decompression Denial-of-Service Caps (BUG-11)**: Enforced strict 15MB file / 25MB total package extraction limits in `ShareReceiverActivity.kt` and 100MB vault cap in `LocalVaultManager.kt`.
- **Cloudflare D1 Schema Memoization (BUG-12)**: Cached schema readiness flag in `backend/worker.js` preventing redundant DDL queries on every request.
- **Newsletter Unsubscribe Rate Limiting (BUG-13)**: Added IP-based hourly rate limiting to `handleNewsletterUnsubscribe` endpoint.
- **Windows Drop Concurrency Lock (BUG-14)**: Protected notes read-modify-write cycle in `aano_drop_server.py` with `_storage_lock` re-entrant lock.

## [1.2.0-beta.2] - 2026-11-02

### Added
- **Direct Offline Hotspot Mode (AANO Drop)**:
  - Smart interface subnet detection prioritizing Wi-Fi access point / hotspot interfaces (`ap0`, `wlan0`, `softap`) with automatic `192.168.43.1` fallback.
  - Cellular carrier-grade NAT (CGNAT) IP filter preventing unintended binding to mobile data interfaces.
  - Zero-configuration local transfers between devices without requiring an external Wi-Fi router.
- **High-Contrast Barcode & QR Generation**:
  - Embedded JourneyApps ZXing `BarcodeEncoder` generating crisp 512x512px QR codes with guaranteed 2–4 module Quiet Zone margins.
  - Auto-scaled display card in modal layouts ensuring fast recognition from smartphone cameras in low light and off-angle perspectives.
- **Unified Auto-Focus Camera Scanner**:
  - Standardized real-time camera scanning across Notes List, Editor Activity, and Local Fragment.
  - Fixed camera orientation and continuous auto-focus for pairing in milliseconds.
- **Security & Legal Compliance**:
  - Integrated Google Search Console verification meta tags.
  - Added dedicated, accessible public Terms of Service (`terms.html`) for Google OAuth verification audit.

### Changed & Improved
- **Zero Third-Party Trademark Footprint**:
  - Sanitized all UI labels, toasts, dialogs, and code comments to 100% original, professional technical terminology.
- **FixMySlop Hardening & Reliability**:
  - Replaced empty catch blocks in transfer and discovery daemons with structured, typed logging.
  - Synchronized release artifacts across Android Gradle, Windows Inno Setup, and Web Docs.

## [1.2.0-beta.1] - 2026-10-24

### Added
- **AANO Drop (Bidirectional Wi-Fi P2P Transfer)**:
  - **Zero WAN Internet Quota**: Local subnet TCP streaming (`192.168.x.x`) routed strictly at Router Layer 2 (ARP/MAC switching). Packets never touch external internet gateways.
  - **PC Receiver Daemon**: Multi-threaded Python server on port `8765` with one-time 6-digit PIN and token authentication.
  - **Android Receiver Daemon**: Lightweight embedded server on port `8766` with explicit Wi-Fi process socket lock (`ConnectivityManager.bindProcessToNetwork`) and haptic chime on arrival.
  - **Selective & Batch Transfer**: 1-click note drop button in the note editor and bulk drop in multi-selection mode.
- **Windows Desktop Media Vault Parity**:
  - **HTML5 Canvas Cropper**: Interactive modal supporting Free, 1:1, 4:3, and 16:9 framing plus 90° clockwise rotation.
  - **Quality Toggle [HD / STD]**: Switch between ultra-lightweight WebP (~120 KB, 1280px downscale) and uncompressed raw HD captures.
  - **Image Thumbnail Bar & Full Preview**: Interactive preview modal with "Save to Disk", "Crop & Rotate", and "Remove Attachment" actions.
- **Flagship Power Utilities**:
  - **Windows Global Hotkey (`Ctrl + Alt + N`)**: Win32 native `RegisterHotKey` message pump loop to launch instant scratchpad anywhere in the OS without third-party keyboard hooks.
  - **Android Quick Settings Tile**: System tile for 1-tap note capture from the Android notification shade.
  - **Smart Web & Text Clipper**: System `Intent.ACTION_SEND` hook for clipping shared articles, web links with OpenGraph title resolution, and pictures directly into AANO.
  - **1-Click PDF & Markdown Export**: Native Android `PdfDocument` rendering (0 KB library bloat) and desktop Markdown/HTML print export.
  - **Smart To-Do Auto-Sorting**: Smooth 250ms visual transition moving completed tasks beneath active checklist items.

### Changed & Improved
- **FixMySlop Code Polish (Grade A 95+)**:
  - Typed exception handling across all Python and Kotlin modules (`FileNotFoundError`, `JSONDecodeError`, `URLError`, `BadZipFile`), replacing broad and silent try-catches with structured logging.
  - Memory-safe DOM updates via modern `replaceChildren()` across all Windows UI components.
  - Accessible semantic `<button type="button">` navigation elements in place of pseudo-links.

## [1.1.0-beta.1] - 2026-10-23

### Added
- **Interactive Image Crop & Transform Toolkit**:
  - **Precision Touch-Crop Overlay**: Crop attached images directly inside the app with interactive touch handles and corner controls.
  - **Aspect Ratio Presets**: 1-tap switching between **Freeform**, **1:1 (Square)**, **4:3 (Photo)**, and **16:9 (Widescreen)** framing.
  - **90° Lossless Rotation**: Quick rotation button to fix sideways or upside-down document and receipt captures.
  - **Ultra-Efficient WebP Compression**: Automatic optimization (max 1280px resolution, 80% WebP quality), reducing 4–8 MB smartphone photos down to **~100–150 KB** while preserving visual quality.
  - **Replace & Remove Actions**: Swap an existing note photo with a newly cropped one, or permanently delete unwanted photos with instant on-disk file purges.
- **Local Media Vault .zip Backup & Restore**:
  - **All-in-One Portable Archive**: Bundles all local on-device notes (`notes.json`) along with attached media files (`media/images/`, `media/audio/`) into a single `.zip` file in `Downloads/AANO/`.
  - **Cross-Platform Device Migration**: Easily transfer your local notes and private attachments to a new phone or PC without using cloud services.
  - **1-Click Vault Restore**: Imports and unpacks the zip archive, automatically remapping local file paths into the app storage.
  - **Quick-Access UI**: Direct *Backup (.zip)* and *Restore (.zip)* actions integrated into the `LocalFragment` header banner and `Settings`.
- **Automated Cloud CI/CD Pipeline**:
  - **Multi-Platform Release Automation**: Official GitHub Actions workflow for building Android Release APK (Ubuntu runner with JDK 17) and Windows Desktop Installer (Windows runner with Inno Setup LZMA compression).
  - **Automated Tagged Artifacts**: Push-to-release workflow with versioned asset publishing (`AANO-v1.1.0-beta.1.apk` and `AANO-Setup-v1.1.0-beta.1.exe`).

### Changed & Improved
- Standardized release artifact naming with automated tag interpolation.
- Upgraded local storage engine to immediately wipe unreferenced disk media on replace and removal.

### Fixed
- Resolved Android camera/gallery rotation orientation handling when attaching images.


## [1.0.1-beta] - 2026-10-22

### Added
- **Super-Responsiveness Engine (Zero-Lag Typing & Instant UI)**:
  - **Android In-Memory Note Cache**: Instantaneous (0ms) folder filtering, tag extraction, and search queries by caching decrypted notes in RAM and updating asynchronously on writes.
  - **Zero-Stutter Mobile Typing**: Removed network sync from the 800ms typing auto-save loop; cloud sync now executes cleanly upon closing the editor (`finish()`) or app switching (`onPause()`).
  - **Windows Instant Modal Close (0ms)**: Editor modal dismisses immediately on Save/Close with zero wait-time; network synchronization runs asynchronously in background threads.
  - **Windows Search Debouncing & GPU Layer Acceleration**: 120ms debounce prevents DOM thrashing during rapid typing; added CSS GPU compositing (`transform: translateZ(0)` and `contain: layout paint`) for smooth 60 FPS scrolling on integrated Intel HD Graphics.
- **Branded OAuth Success Screen & Clean Redirect**:
  - Replaced raw localhost callback URLs with a clean, branded GitHub Pages screen (`https://jamamjadalone.github.io/AANO/oauth-success.html`) featuring an emerald animated checkmark, English copy, and auto-closing countdown.
  - Standardized all UI copy, tooltips, dialogs, and toasts across Android and Windows to 100% clean, professional English.
- **Hardened Trash & Tombstone Sync Protocol (Zero Ghost Resurrections)**:
  - **Tombstone Sync (`purged: true`)**: Permanent deletion ("Delete Forever" or "Empty Trash") records a lightweight tombstone with wiped content/media, ensuring other synced devices permanently drop the note rather than resurrecting it back to Google Drive.
  - **Multimedia Storage Cleanup**: Permanently purging notes physically deletes all associated attached photos (`filesDir/media/images/`) and audio recordings (`filesDir/media/audio/`) from device disk storage, eliminating orphaned media leaks.
  - **Cross-Platform Auto-Purge Parity**: Android and Windows both auto-purge expired trash and tombstones older than 30 days.
  - **Windows Immediate Cloud Auto-Sync**: Permanent delete and empty trash actions immediately trigger background Google Drive sync.
  - **Serialization Integrity**: Fully verified local notes and media fields (`isLocal`, `images`, `audioUri`, `audioDuration`, `purged`) are persisted across all app restarts.
- **Custom Color Picker & 12 Aesthetic Shades ("Apni Marzi")**:
  - **Custom Color Palette Button (`🎨`)**: Pick any color freely on Android and Windows with live Hex input (`#RRGGBB`) and a 20-color swatch palette grid.
  - **Curated 12 Aesthetic Shades**: Expanded palette to 12 modern pastel and muted paper tones including Lemon, Mint, Teal, Sky, Indigo, Lavender, Rose, Peach, Coral, Sand, Slate, and Charcoal Dark.
  - **Dynamic Contrast Adaptation**: Dark paper backgrounds (e.g. Charcoal) automatically invert text and metadata to high-contrast white and soft zinc for clear text contrast.
- **Multi-Select Batch Delete & Archive (Android & Windows)**:
  - **Android**: Long-press any note card to activate contextual selection mode with live selection counter, "Select All", "Archive Selected", and "Delete Selected" (with confirmation dialog).
  - **Windows Desktop**: Topbar "Select" button toggles batch selection mode with card checkboxes and an emerald batch action bar.
- **Dedicated Archive System**:
  - Archive notes to keep active lists clean while preserving folder categorization and tags.
  - Dedicated "Archive" view on Android (`ArchiveActivity`) with styled Material cards, and Windows Desktop sidebar with live badge count.
  - 1-tap "Unarchive" action returning notes directly to active view.
  - Added "Archive Note" option in note context menu and editor top bar across both platforms.
- **Windows Productivity Shortcuts**:
  - `Ctrl + N`: Instantly open editor for a new note.
  - `Ctrl + F`: Quick-focus the search bar.
  - `Escape`: Cancel batch selection or close editor modal cleanly.
- **Cross-Platform Database Migration**:
  - Cloudflare Worker D1 schema auto-migration adding `archived INTEGER DEFAULT 0` column.
  - Full end-to-end cloud synchronization for custom 32-bit ARGB / Hex color codes and `archived` attribute.

### Changed & Improved
- **Friendly Relative Timestamps**: Note cards now display conversational dates ("Today, 2:30 PM", "Yesterday, 11:15 AM", "22 Oct, 1:00 PM") across Android and Windows.
- **Instant Title Auto-Focus**: Opening a new note automatically focuses the title input and triggers the keyboard.
- Note cards dynamically reflect selected status with emerald border highlight and checkbox indicators.
- Added protective modal confirmation dialogs for batch deletion.
- Bumped Android `versionCode` to 2 and `versionName` to `1.0.1-beta`.

### Fixed
- **Android Hardware Back Button in Selection Mode**: Fixed bug where pressing hardware/gesture back in selection mode would close the app; it now cleanly clears selection first.
- **Folder Count Inflation**: Fixed active folder badges counting archived notes in `NoteRepository.getFolderCounts()` and Windows desktop API.
- **Polished Archive Cards**: Upgraded Android `item_archive` from bare LinearLayout to rounded `MaterialCardView` with folder badges and dynamic pastel backgrounds.

---

## [1.0.0] - 2026-10-22

### Added
- **Cross-Platform Real-Time Sync**: Instant bi-directional cloud synchronization between Android and Windows Desktop powered by Cloudflare Workers and Cloudflare D1 distributed SQLite database.
- **2.5s Foreground Sync Engine**: Active foreground polling engine on both mobile and PC that automatically synchronizes note creations, updates, and deletions within 2.5 seconds without manual refresh.
- **Hardware-Backed AES-256 Encryption**: Notes and sensitive metadata are locally encrypted using AES-256-GCM cryptography on both Android and Windows clients.
- **Official Google Mail OTP Bridge**: 6-digit OTP verification delivered directly to the user's Gmail inbox for registration and secure password recovery.
- **Rich Text Formatting Toolbar**: Integrated rich text formatting engine supporting Headings (H1, H2), Bold, Italic, Underline, Strikethrough, Bullet Lists, and color highlighters docked above the keyboard on Android and in the editor top bar on Windows.
- **9 Pastel Paper Themes**: Distraction-free pastel note background shades (Emerald Mint, Butter Yellow, Leaf Green, Sky Blue, Lavender, Rose Pink, Peach, Coral, and Pure White).
- **Offline JSON Backup & Restore**: Cross-compatible offline backup export and import (`aano-backup.json`) enabling zero-cloud local data portability.
- **Cold-Start & Resume App Lock**: Biometric and 4-digit PIN security shield with `FLAG_SECURE` window protection preventing unauthorized screenshots and app switcher previews on Android.
- **Modern Windows Inno Setup Installer**: Crisp, lightweight installer (`AANO-Setup.exe`) with multi-resolution per-monitor DPI awareness and desktop integration.

### Changed & Modernized
- **Complete Vector Icon Overhaul**: Replaced all legacy and emoji buttons with sleek, minimalist SVG / Material 3 vector icons (Feather & Lucide rounded stroke design) across Android and Windows.
- **Native Custom Dialogs**: Replaced browser `confirm()`, `prompt()`, and `alert()` popups on Windows with 100% native blurred backdrop modals, eliminating browser URLs (`file:///...`) from all dialogs.
- **Crisp Multi-Resolution App Icons**: Generated mipmap assets (`mdpi` through `xxxhdpi` on Android, 16px to 256px multi-frame `.ico` on Windows) for sharp display across high-DPI and mobile displays.

### Fixed
- **Instant Cross-Device Delete Propagation**: Fixed deletion timestamps (`updatedAt = maxOf(now, existing + 1000L)`) ensuring server-side conflict resolution permanently accepts deletions and immediately removes deleted notes across all paired devices.
- **Session Expiration & 401 Detection**: Resolved silent sync failures caused by password resets by adding automatic 401 token expiration detection and prompting the user with an Amber session warning.
- **Direct Editor Delete Action**: Added an immediate delete action (`ic_trash`) inside the Android note editor top bar with confirmation dialog to allow deleting notes without exiting to the main list.
- **Dex Merging & Daemon Contention**: Resolved Windows file-locking conflicts during automated release compilation.

### Security
- **Cloudflare Edge Shield**: Rate limiting on API endpoints (IP-based brute-force defense on login and OTP generation).
- **Single Database Integrity**: Verified identical D1 schema and Cloudflare Worker endpoints for mobile and desktop clients to guarantee cross-device data coherence.

---

[1.2.0-beta.2]: https://github.com/jamamjadalone/AANO/releases/tag/v1.2.0-beta.2
[1.2.0-beta.1]: https://github.com/jamamjadalone/AANO/releases/tag/v1.2.0-beta.1
[1.0.1-beta]: https://github.com/jamamjadalone/AANO/releases/tag/v1.0.1-beta
[1.0.0]: https://github.com/jamamjadalone/AANO/releases/tag/v1.0.0
