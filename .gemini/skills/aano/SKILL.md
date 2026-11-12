---
name: aano
description: Comprehensive architecture, subsystem guide, and debugging playbook for AANO (Android & Windows Notes App)
---

# AANO Core Architecture & Diagnostics Skill

<role>
You are an expert systems engineer and software architect for AANO (Cross-Platform Notes & P2P Sync Suite for Android and Windows).
You understand the complete codebase topology, modular architecture, data flows, security constraints, and debugging patterns across both platforms.
</role>

---

## 1. System Overview & Dual-Platform Architecture

AANO is a private, zero-telemetry, offline-first notes suite with cloud backup and local P2P sharing:

```
+-------------------------------------------------------------------------------+
|                                  AANO SUITE                                   |
+---------------------------------------+---------------------------------------+
|          ANDROID SUBSYSTEM            |           WINDOWS SUBSYSTEM           |
|  - Native Kotlin + Jetpack AndroidX   |  - Python 3.11 + PyWebView            |
|  - Room SQLite Database (Room FTS4)   |  - Edge Chromium WebView2 Runtime     |
|  - Material Design Components         |  - Custom CSS/JS Native Desktop Shell |
|  - BiometricPrompt + Android Keystore |  - Inno Setup Packaging (x86_64)      |
+---------------------------------------+---------------------------------------+
|                             SHARED PROTOCOLS                                  |
|  1. AANO Drop 2.0: UDP Port 52528 Discovery + HTTP Port 52529 Data Streaming  |
|  2. Cloud Sync: Google Drive restricted DRIVE_FILE scope + 3-way MergeEngine  |
|  3. Package Format: .aano encrypted/portable JSON + WebP media bundles        |
+-------------------------------------------------------------------------------+
```

---

## 2. Rule 21 Topology & Governance

- **Strict File Limit**: Every file in the entire repository MUST stay `<= 300 lines` (`No file over 300 lines`).
- **Single Source Index**: `ARCHITECTURE_INDEX.md` at repository root maps every file and its exact role.
- **Directory Roles**:
  - `pages/` (Android) / `ui/` (Windows): Screen controllers & visual interfaces.
  - `components/`: Modular reusable helpers, dialog delegates, adapters.
  - `data/`: Persistence, DAO, migrations, and local state management.
- **Verification Commands**:
  - `python aano_dev.py stats`: Real-time line-limit and package overview.
  - `python aano_dev.py check`: Fast Rule 21, Kotlin compilation, Python syntax, and JS syntax test.
  - `python aano_dev.py doctor`: Ghost import, manifest, and index mapping auditor.

---

## 3. Subsystems Deep-Dive

### A. Windows Desktop App (`windows/src/`)
- **Host Process (`main.py`)**: Boots `webview.create_window()`, initialises `HotkeyDaemon` (`user32.RegisterHotKey`) for `Ctrl+Alt+N` Quick Scratchpad, and `TrayManager` (`pystray`) for system tray minimization.
- **API Bridge (`api.py` -> `DesktopApi`)**:
  - Composite facade inheriting from mixins: `NotesApiMixin`, `TrashApiMixin`, `SyncApiMixin`, `DropApiMixin`, `MediaApiMixin`, `ExportApiMixin`, `LockApiMixin`.
  - **CRITICAL**: `DesktopApi.__init__()` MUST call `super().__init__()` so mixin constructors (like `LockApiMixin._app_lock`) are instantiated!
- **Vault & AppLock (`app_lock.py`, `lock.css`, `app_lock.js`)**:
  - Inactivity auto-lock with configurable timeout and exponential backoff lockout.
  - `#lockOverlay` MUST have `display: none;` when inactive and `display: flex;` only when `.active`. Never leave `display: flex` with `opacity: 0` because Edge Chromium's `backdrop-filter` creates an invisible click-blocking compositor quad.
- **UI Architecture (`windows/src/ui/`)**:
  - Vanilla JS modular scripts loaded in `index.html`: `app_state.js`, `app_events.js`, `app_notes_filter.js`, `app_sync.js`, `app_drop.js`, `app_lock.js`, and orchestrator `app.js`.
  - In `app.js`, UI event listeners are registered FIRST before any asynchronous data queries to guarantee instant UI responsiveness.

### B. Android Native Subsystem (`android/app/src/main/`)
- **Storage**: SQLite via Android Room with full-text search (`NoteFtsEntity`).
- **Security**: Keystore-backed PIN and `BiometricPrompt` authentication.
- **Network Security**: Strict `network_security_config.xml` allowing cleartext traffic strictly on LAN IP addresses for AANO Drop P2P transfer, while enforcing HTTPS everywhere else.

### C. AANO Drop 2.0 (Peer-to-Peer Transfer)
- **Discovery**: Zero-configuration UDP multicast broadcast on `239.255.52.52:52528`.
- **Payload Transport**: HTTP chunked binary streaming on port `52529`.
- **Pairing**: QR code exchange containing device token, IP, and dynamic AES key.

### D. Cloud Sync Engine
- **OAuth Scope**: Permanent single scope `DriveScopes.DRIVE_FILE`. Zero sensitive scopes allowed.
- **Conflict Resolution**: `MergeEngine` implements 3-way line-based merge preventing accidental note overwrites between phone and desktop.

---

## 4. Rapid Diagnostic & Bug Triage Playbook

### Trap 1: Windows UI Buttons Not Responding
1. **Check JavaScript Syntax Immediately**:
   ```powershell
   cd windows\src\ui
   Get-ChildItem -Filter "*.js" -Recurse | ForEach-Object { node -c $_.FullName }
   ```
   *Why*: A single syntax error (e.g. extra `}` or unclosed tag) in any script loaded before or inside `app.js` causes the entire event listener registration chain to halt without a visible error dialog.
2. **Check `#lockOverlay` CSS State**:
   - Verify `#lockOverlay` has `display: none;` when `.active` is absent.
3. **Check `DesktopApi` Initialisation**:
   - Verify `DesktopApi` calls `super().__init__()` in `api.py`. Run:
     ```powershell
     python -m pytest tests/test_api_wiring.py -v
     ```

### Trap 2: Microsoft SmartScreen "Windows protected your PC" Warning
- **Root Cause**: Fresh SHA-256 hash without paid EV certificate + system tray/hotkey hooks.
- **Remedy**: Click **"More info" -> "Run anyway"**. This is normal for unsigned open-source binaries.

### Trap 3: Android Release Build Fails in CI/CD
1. Check ProGuard / R8 rules in `proguard-rules.pro`.
2. Inspect remote logs using `gh run view --log-failed`.
3. Never fall back to modifying local machine Android SDK paths.

---

## 5. Centralized Version & Release Protocol

- **Single Command**: `python bump_version.py <version>`
- **Integrity Audit**: `python bump_version.py --check`
- **Release Redo Flow**:
  1. Delete local tag: `git tag -d <version>`
  2. Delete remote tag: `git push origin :refs/tags/<version>`
  3. Re-tag commit: `git tag -a <version> -m "Release <version>"`
  4. Push tag: `git push origin <version>`
- **Release Verification**: GitHub Actions automatically builds Windows Setup `.exe` and Android Release `.apk`.
