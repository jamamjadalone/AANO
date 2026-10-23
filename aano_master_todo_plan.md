# AANO (`app.aa.aano`) — Master Production Engineering Blueprint & Implementation Guide

> **Target AI Agents:** Google Antigravity / Cursor / Windsurf  
> **Repository:** [jamamjadalone/AANO](https://github.com/jamamjadalone/AANO) | **Application ID:** `app.aa.aano`  
> **Core Architecture:** Android Native (Kotlin) + Windows Desktop (Python + Edge WebView2)  
> **Dual Engine:** Cloud Sync (Google Drive OAuth) + **"AANO Drop" (Bidirectional Selective Wi-Fi P2P)**  
> **Rule:** **No auto-bulk dump!** Only the specific notes, links, or photos explicitly chosen by the user are transferred.  
> **Code Quality:** FixMySlop Grade A (Score 95+)

---

## 🔄 Bidirectional AANO Drop Architecture (PC ➔ Android & Android ➔ PC)

Both devices can act as a **Sender** (transmitting chosen items) and a **Receiver** (displaying QR & listening over local Wi-Fi):

```
┌────────────────────────────────────────────────────────────────────────┐
│                        DIRECTION 1: ANDROID ➔ PC                       │
│  1. On Android: Select specific note -> Tap "Send via AANO Drop"       │
│  2. On PC: Click "Receive via Wi-Fi" -> Displays QR Code on Screen     │
│  3. Phone scans PC's QR -> Streams ONLY that note at 300 Mbps          │
│  4. PC receives note -> Chime chime -> Appended to PC Local Vault      │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                        DIRECTION 2: PC ➔ ANDROID                       │
│  1. On PC: Open note/link -> Click "Drop to Phone"                     │
│  2. On Android: Tap "Receive via Wi-Fi" -> Displays QR Code on Phone   │
│  3. PC web-cam scans phone QR (or phone enters 6-digit PC PIN)         │
│  4. Android receives note -> Haptic vibration -> Saved to Local Vault  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🌐 The Science: Zero Internet Usage (Even If 100 Mbps Wi-Fi / 5G Is ON)

1. **RFC 1918 Private IP Routing (Zero WAN Bytes):**
   - Transmissions target local subnet addresses (`192.168.x.x`).
   - Router hardware switches packets strictly at Layer 2 (MAC address / ARP). Data **never leaves the local router** or touches the ISP fiber cable. 0 MB of internet quota is consumed.
2. **Android Mobile Data Bypassed (`TRANSPORT_WIFI` Lock):**
   - In `AanoDropManager.kt`, sockets are explicitly bound via `connectivityManager.bindProcessToNetwork(wifiNetwork)`.
   - Android's cellular radio (4G/5G) is physically locked out of the transfer pipeline.
3. **Zero-Drop Connection Resilience:**
   - Android acquires `WifiManager.WIFI_MODE_FULL_HIGH_PERF` and `PowerManager.PARTIAL_WAKE_LOCK` to stop battery managers from sleeping the Wi-Fi chip mid-transfer.
   - Payloads stream in **64 KB chunks** over TCP with `TCP_NODELAY = true` and running SHA-256 validation.

---

## 📋 Comprehensive Execution Checklist (Sprint by Sprint)

### SPRINT 1: FixMySlop Code Polish (Grade A 95+ — Execute First)
- [ ] **Task 1.1 — Python Exception Hardening (`drive_sync.py`, `storage.py`, `api.py`, `main.py`):**
  - Replace all bare `except Exception:` and `except Exception as e:` with `logger.warning(...)` / `logger.exception(...)` and typed catches (`FileNotFoundError`, `json.JSONDecodeError`).
- [ ] **Task 1.2 — Android Kotlin Empty Catch Logging (`EditorActivity.kt`, `NoteRepository.kt`):**
  - Replace `catch (_: Exception) {}` with `Log.w("AANO", "Suppressed: ${e.message}")`.
- [ ] **Task 1.3 — HTML `<button>` Accessibility (`windows/src/ui/index.html`):**
  - Replace all `<a href="#" class="nav-item">` and `<a href="#" data-highlight="...">` with `<button type="button" class="nav-item">`.
- [ ] **Task 1.4 — Safe DOM Clearing (`windows/src/ui/app.js`):**
  - Replace `container.innerHTML = ""` with `container.replaceChildren()`.
- [ ] **Task 1.5 — Clean Headings in Docs (`README.md`, `CHANGELOG.md`):**
  - Remove decorative emojis from markdown headings.

---

### SPRINT 2: Local Media Vault Toolkit (Crop, Replace, Remove & .zip Backup)
- [ ] **Task 2.1 — In-App Image Crop (`CropActivity.kt` & `storage.py`):**
  - Android: Freeform, 1:1, 4:3, 16:9 crop -> Downscale max dimension to `1280px` -> Save as **WebP (80% quality, ~100–150 KB)**.
  - Windows: HTML5 Canvas crop modal in `app.js` -> Python Pillow WebP converter in `storage.py`.
- [ ] **Task 2.2 — Attachment Quality Option (Standard HD vs Original RAW):**
  - Provide a 1-tap `[HD]` icon on image attachment bar. Default is optimized WebP (`~150KB`); tapping `HD` preserves 100% original uncompressed bytes.
- [ ] **Task 2.3 — Full-Screen Preview "Save to Device / Gallery" Action:**
  - Android: Export attached photo to public `/Pictures/AANO/` using Android `MediaStore` API so it shows in phone Gallery.
  - Windows: Provide `Save As...` file dialog to export image to user hard drive.
- [ ] **Task 2.4 — Image Replace & Remove with Immediate Disk Garbage Collection:**
  - Tapping Replace or Remove permanently deletes the old physical file from `filesDir/media/images/` or `%APPDATA%/AANO/images/` using `file.delete()` so dead image files never bloat storage.
- [ ] **Task 2.5 — One-Click Portable `.zip` Vault Backup:**
  - Export: Zip `local_notes.json` + `media/images/` + `media/audio/` into `aano-vault-backup.zip`.
  - Restore: Extract, populate local SQLite, and restore media files.

---

### SPRINT 3: "AANO Drop" — Bidirectional Selective Wi-Fi Transfer (PC ⇄ Android)

#### 3.1 Selective Transfer Controls (No Auto-Bulk Sync!)
- [ ] **Task 3.1.1 — Single Note Send Trigger:**
  - On Android: Inside Note Editor (or card 3-dots menu), add button **"⚡ Drop via Wi-Fi"**.
  - On Windows: Inside Note Editor toolbar, add button **"⚡ Drop to Phone"**.
- [ ] **Task 3.1.2 — Batch Select Send (Optional Multi-Note):**
  - When in multi-select mode (checkboxes checked), add top-bar action **"Drop Selected (X Notes)"** to transfer only the checked items.

#### 3.2 Dual Receiver & Sender Setup (Both Devices Can Send & Receive)
- [ ] **Task 3.2.1 — Windows Daemon (`aano_drop_server.py`):**
  - **Receiver Role:** Python background HTTP server on port `8765`.
  - Clicking *"Receive via Wi-Fi"* in Windows Local Tab displays QR code containing:
    ```json
    { "protocol": "aano-drop-v1", "role": "pc-receiver", "ip": "192.168.1.15", "port": 8765, "token": "a1b2c3d4" }
    ```
  - **Sender Role:** When user clicks *"Drop to Phone"*, prompts to connect to Phone's IP/QR and pushes the selected note payload.
- [ ] **Task 3.2.2 — Android Daemon & Scanner (`AanoDropManager.kt`):**
  - **Sender Role (Android ➔ PC):** Opens Google Code Scanner / CameraX, scans PC QR code, and pushes the selected note directly to `http://<pc-ip>:8765/aano/drop`.
  - **Receiver Role (PC ➔ Android):** When user taps *"Receive via Wi-Fi"* on Android:
    - Starts a lightweight local Android socket server on port `8766`.
    - Shows QR code on phone screen (`http://<phone-ip>:8766`) or 6-digit PIN.
    - Receives note from PC, buzzes with haptic vibration, and adds to Local Vault.
- [ ] **Task 3.3 — Android Native Share Sheet Hook:**
  - Hook Android `Intent.ACTION_SEND` so user can share links from Chrome, YouTube, or photos from Gallery directly to **"AANO Drop -> Send to PC"**.

---

### SPRINT 4: Flagship Next-Level Features (The 5 Power Utilities)

#### 4.1 Feature 1: Windows Global Hotkey Quick Scratchpad (`Ctrl + Alt + N`)
* **How It Works:** User is anywhere in Windows (gaming, coding, browsing). Pressing `Ctrl + Alt + N` instantly summons a sleek, floating glass-like notepad on top of all windows. Writing a quick thought and pressing `Esc` auto-saves and hides the scratchpad.
- [ ] **Task 4.1.1 — Windows Backend Global Hotkey Daemon (`windows/src/hotkey_daemon.py`):**
  - Run a lightweight background thread using Python's `keyboard` or native Win32 `RegisterHotKey`:
    ```python
    import keyboard

    def init_global_hotkey(webview_window):
        def on_hotkey():
            webview_window.show()
            webview_window.evaluate_js("window.openQuickScratchpad()")
        keyboard.add_hotkey("ctrl+alt+n", on_hotkey)
    ```
- [ ] **Task 4.1.2 — Windows Frontend Floating Scratchpad Modal (`windows/src/ui/app.js`):**
  - When `openQuickScratchpad()` is called, open a minimalist floating modal with auto-focused textarea.
  - Pressing `Esc` or clicking outside triggers `api.quick_save_note(title, content)` and minimizes the window.

#### 4.2 Feature 2: Android Quick Settings Notification Tile (`+ New Note`)
* **How It Works:** In the Android pull-down notification shade, a tile named **"AANO Note"** appears. Tapping it opens an instant lightweight dialog to jot down ideas without opening the full app.
- [ ] **Task 4.2.1 — Implement `AanoTileService.kt`:**
  - Create `app/aa/aano/service/AanoTileService.kt` extending `android.service.quicksettings.TileService`:
    ```kotlin
    class AanoTileService : TileService() {
        override fun onClick() {
            super.onClick()
            val intent = Intent(this, QuickNoteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivityAndCollapse(intent)
        }
    }
    ```
- [ ] **Task 4.2.2 — Declare in `AndroidManifest.xml`:**
  - Register with `android.permission.BIND_QUICK_SETTINGS_TILE`.
- [ ] **Task 4.2.3 — Implement Translucent `QuickNoteActivity.kt`:**
  - A compact floating card layout with Title, Note text, and a "Save" FAB button. Saves directly to SQLite database and finishes.

#### 4.3 Feature 3: Smart Android Web Clipper (`Intent.ACTION_SEND`)
* **How It Works:** In Chrome, YouTube, X/Twitter, or Reddit, tapping **Share -> AANO** automatically parses the link, title, and preview snippet, creating a formatted bookmark note with 1 tap.
- [ ] **Task 4.3.1 — Register Share Intent Filter in `AndroidManifest.xml`:**
  ```xml
  <activity android:name=".ui.ShareReceiverActivity" android:exported="true">
      <intent-filter>
          <action android:name="android.intent.action.SEND" />
          <category android:name="android.intent.category.DEFAULT" />
          <data android:mimeType="text/plain" />
      </intent-filter>
      <intent-filter>
          <action android:name="android.intent.action.SEND" />
          <category android:name="android.intent.category.DEFAULT" />
          <data android:mimeType="image/*" />
      </intent-filter>
  </activity>
  ```
- [ ] **Task 4.3.2 — Implement `ShareReceiverActivity.kt`:**
  - Extract `Intent.EXTRA_TEXT` and `Intent.EXTRA_SUBJECT`.
  - If text contains `http://` or `https://`, automatically format as Markdown link: `[Page Title](URL)`.
  - Save to Local Vault or Cloud Notes repository with toast: *"Saved to AANO Notes"*.

#### 4.4 Feature 4: Smart To-Do List Auto-Sort & Completed Task Animation
* **How It Works:** When a user checks a task checkbox `[x]`, the item strikes through with an animated fade and smoothly moves to the bottom under a collapsed **"Completed (X)"** section.
- [ ] **Task 4.4.1 — Windows Desktop Implementation (`windows/src/ui/app.js`):**
  - Render active tasks at the top. When an item checkbox changes, trigger CSS transition `transform: translateY(20px); opacity: 0.5`.
  - Move completed tasks to `<div id="completedTasksList" class="collapsed">` with a toggle to expand/hide completed items.
- [ ] **Task 4.4.2 — Android Implementation (`NoteEditorActivity.kt` / `TaskAdapter.kt`):**
  - Use `RecyclerView` `DefaultItemAnimator` to smoothly animate checked items to the bottom partition.

#### 4.5 Feature 5: 1-Click Note Export to PDF & Markdown (WhatsApp/Email Share)
* **How It Works:** Tapping 3-dots on any note provides **"Export as PDF"** or **"Share as Markdown"** for instant sharing on WhatsApp.
- [ ] **Task 4.5.1 — Android Native PDF Generator (`PdfExportManager.kt`):**
  - Use native Android `android.graphics.pdf.PdfDocument` (zero heavy external libraries!).
  - Draw Title (Bold 18sp), Date (12sp grey), and Markdown body text with clean page margins.
  - Share generated PDF via Android `FileProvider` (`content://...`) to WhatsApp or Gmail.
- [ ] **Task 4.5.2 — Windows Desktop PDF & Markdown Export (`windows/src/api.py`):**
  - Export Markdown: Write note content to `note-title.md`.
  - Export PDF: Call PyWebView's built-in `window.print_to_pdf(path)` to render the clean HTML note directly into a print-ready PDF file.

---

### SPRINT 5: GitHub Actions Automated Beta Release (`v1.1.0-beta.1`)
- [ ] **Task 5.1 — Verify `.github/workflows/release.yml`:**
  - Build Android APK (`ubuntu-latest`) + Windows setup (`windows-latest` via Inno Setup).
- [ ] **Task 5.2 — Publish Beta Release:**
  - Run `python bump_version.py` -> Tag `v1.1.0-beta.1` -> Push with `--tags` to trigger automated cloud deployment.
