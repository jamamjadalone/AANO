<p align="center">
  <br>
  <svg viewBox="0 0 108 108" width="96" height="96">
    <rect width="108" height="108" rx="24" fill="#004D40"/>
    <circle cx="54" cy="54" r="34" fill="rgba(255,255,255,0.08)"/>
    <polygon points="44,30 29,74 37.5,74 41.2,61.5 52.8,61.5 55.5,70.5 63.5,66.8 53,30" fill="#FFFFFF"/>
    <polygon points="44.6,40.5 49.2,54.5 43.2,54.5" fill="#005548"/>
    <polygon points="64,30 51.8,53.8 56.5,67.2 66.8,61.5 70.5,74 79,74 64,30" fill="#E0F2F1"/>
    <polygon points="63.4,40.5 68.2,54.5 58.8,54.5" fill="#005F50"/>
    <polygon points="44,30 54,30 49,46" fill="rgba(255,255,255,0.7)"/>
    <polygon points="64,30 54,30 59,46" fill="rgba(178,223,219,0.7)"/>
    <polygon points="54,49 58.5,55 54,61 49.5,55" fill="#004D40"/>
  </svg>
  <h1 align="center">AANO Notes</h1>
  <p align="center"><b>Minimalist, Beautiful &amp; Private Cross-Platform Notes Suite</b></p>
  <p align="center"><i>Android • Windows Desktop • P2P Local Drop • User-Owned Google Drive Sync</i></p>
</p>

<p align="center">
  <a href="https://github.com/jamamjadalone/AANO/releases/latest"><img src="https://img.shields.io/badge/Release-v1.2.0--beta.1-00796B?style=flat-square&logo=github&logoColor=white" alt="Release: v1.2.0-beta.1"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-00796B.svg?style=flat-square" alt="License: GPL-3.0"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026--35)-004D40?style=flat-square&logo=android&logoColor=white" alt="Platform: Android">
  <img src="https://img.shields.io/badge/Windows-10%20%2F%2011%20(Installer)-00796B?style=flat-square&logo=windows&logoColor=white" alt="Platform: Windows">
  <img src="https://img.shields.io/badge/P2P-AANO%20Drop%20(Local%20LAN)-0D9488?style=flat-square&logo=airplayvideo&logoColor=white" alt="AANO Drop">
  <img src="https://img.shields.io/badge/Sync-Google%20Drive%20%7C%20Cloudflare-F59E0B?style=flat-square&logo=googledrive&logoColor=white" alt="Sync">
  <img src="https://img.shields.io/badge/Trackers-0%20(Zero%20Telemetry)-10B981?style=flat-square" alt="Zero Trackers">
</p>

<p align="center">
  🌐 <b>Live Web &amp; Documentation:</b> <a href="https://jamamjadalone.github.io/AANO/">https://jamamjadalone.github.io/AANO/</a>
</p>

---

## Overview

**AANO Notes** is an open-source, privacy-first cross-platform note-taking ecosystem built for users who demand elegant design, distraction-free productivity, and absolute ownership of their data.

- **Zero Corporate Surveillance:** No telemetry trackers, no analytics pixels, no third-party advertising SDKs.
- **Cross-Platform Freedom:** Seamlessly write on your Android device and continue on your Windows desktop workstation.
- **Offline-First Storage:** Fully functional with zero internet connectivity. Notes, folders, tags, and media are stored locally on your device.
- **Hardware-Protected Encryption:** Encrypted notes and sensitive credentials are safeguarded with AES-256-GCM cryptography.
- **P2P Local Sharing (AANO Drop):** Direct high-speed note transfers over local Wi-Fi without internet or cloud intermediaries.

---

## Key Features Across Platforms

### AANO Drop (Peer-to-Peer Local Network Sharing)
- **Local Wi-Fi Radar:** Instantly discovers active AANO devices (Android phones & Windows PCs) on your local subnet (ports 8765/8766).
- **Direct P2P Note Beam:** Transfer individual or multiple notes—including formatted text, inline images, voice recordings, and tags—in milliseconds.
- **QR Code Pairing & Web Fallback:** Display a dynamic QR code on Windows or Android; any device with a camera can scan to receive or connect.
- **100% Zero-Cloud Transfer:** Traffic stays entirely on your local home or office network with zero third-party relay servers.

### Interactive Image Crop & Transform Toolkit
- **Touch-Crop Overlay:** Crop attached images directly inside the app with interactive touch handles and corner controls.
- **Aspect Ratio Presets:** 1-tap switching between **Freeform**, **1:1 (Square)**, **4:3 (Photo)**, and **16:9 (Widescreen)** framing.
- **90° Lossless Rotation:** Quick rotation button to fix sideways or upside-down document and receipt captures.
- **Ultra-Efficient WebP Compression:** Automatic optimization (max 1280px resolution, 80% WebP quality), slashing 4–8 MB smartphone photos down to **~100–150 KB** with zero visible quality loss.
- **Replace & Remove Actions:** Swap an existing photo with a newly cropped one, or permanently delete unwanted photos with instant on-disk file purges.

### Local Media Vault .zip Backup & Restore
- **All-in-One Portable Archive:** Bundles all local on-device notes (`notes.json`) along with attached media files (`media/images/`, `media/audio/`) into a single `.zip` file in `Downloads/AANO/`.
- **Zero-Cloud Device Migration:** Easily transfer your private notes and attachments to a new phone or PC without using cloud services.
- **1-Click Restore:** Seamlessly unpacks the vault archive, automatically remapping local file paths into the app storage.
- **Quick UI Access:** Direct *Backup (.zip)* and *Restore (.zip)* actions integrated into the `LocalFragment` header banner and `Settings`.

### Dual Synchronization Architecture
1. **User-Owned Google Drive Sync (Primary)**:
   - Direct peer-to-peer cloud sync between Android and Windows using your own Google Drive.
   - Minimal permissions: uses restricted `drive.file` scope (AANO can only access its own `aano-notes.json` sync file).
   - Clean OAuth 2.0 flow with local loopback listener and branded GitHub Pages redirect (`docs/oauth-success.html`).
2. **Cloudflare Edge D1 Sync & Gmail OTP (Standalone)**:
   - Distributed low-latency SQLite synchronization powered by Cloudflare Workers and Cloudflare D1.
   - Official 6-digit Gmail OTP verification bridge via Google Apps Script.

### Super-Responsiveness Engine
- **Android In-Memory Cache:** Decrypted notes are cached in RAM (`memoryCache`) for instantaneous (0ms) folder filtering, tag extraction, and search queries.
- **Zero-Stutter Mobile Typing:** Removed network sync from the typing auto-save loop; cloud sync executes cleanly upon closing the editor or app switching.
- **Windows Zero-Latency Modal Dismissal:** Editor modal dismisses immediately on Save/Close (`0ms`); synchronization runs asynchronously in background threads.
- **GPU Layer Acceleration & Search Debounce:** 120ms debounce prevents DOM thrashing during rapid typing; CSS GPU compositing (`transform: translateZ(0)`) provides smooth 60 FPS scrolling on integrated Intel graphics.

### Visual & Editor Experience
- **Emerald Luxury Palette:** Material 3 design centered on deep teal obsidian (`#004D40`) and emerald green (`#00796B`).
- **12 Aesthetic Paper Themes & Custom Color Palette:**
  - Dynamic paper background shades: Lemon, Mint, Teal, Sky, Indigo, Lavender, Rose, Peach, Coral, Sand, Slate, and Charcoal Dark.
  - Custom Color Palette (`🎨`) with live Hex input (`#RRGGBB`) and a 20-color swatch palette grid.
  - Dynamic contrast adaptation for dark paper backgrounds.
- **Rich Text Formatting Toolbar:**
  - Headings: **H1** and **H2**.
  - Styling: **Bold**, *Italic*, <u>Underline</u>, <s>Strikethrough</s>.
  - Pastel highlighters: Yellow, Mint, Pink, Blue, Peach.
  - Palette text colors: Emerald, Blue, Crimson, Purple, and Obsidian.
  - Automatic bullet lists, Undo / Redo history, and real-time word counter.

### AppLock & Privacy Shield
- Salted PBKDF2 PIN lock + Biometric authentication (Fingerprint / Face Unlock).
- Android `FLAG_SECURE` prevents screenshots and hides previews in recent apps.

---

## Download Official Releases

Download pre-built, production-ready binaries directly from [GitHub Releases](https://github.com/jamamjadalone/AANO/releases/latest):

| Platform | Download | System Requirements |
| :--- | :--- | :--- |
| **Windows** | [`AANO-Setup-v1.2.0-beta.1.exe`](https://github.com/jamamjadalone/AANO/releases/latest) | Windows 10 & 11 (64-bit PC / Laptop) |
| **Android** | [`AANO-v1.2.0-beta.1.apk`](https://github.com/jamamjadalone/AANO/releases/latest) | Android 8.0+ (Phones & Tablets) |

### First-Time Installation Guide

Because AANO is a free, community-driven open-source project without expensive corporate enterprise certificates, Windows and Android may show a standard safety prompt on the very first install. AANO is 100% clean, open source (GPL-3.0), and contains zero trackers or advertisements.

#### Windows PC / Laptop

1. Download and double-click `AANO-Setup-v1.2.0-beta.1.exe`.
2. When the blue **"Windows protected your PC"** screen appears:
   - Click **"More info"** (link under the text).
   - Click the **"Run anyway"** button at the bottom.
3. Follow the setup wizard. Once installed, AANO opens directly from your Desktop or Start Menu with no further prompts.

#### Android Phone / Tablet

1. Download `AANO-v1.2.0-beta.1.apk` on your device and tap to open it.
2. If prompted *"For your security, your phone is not allowed to install unknown apps"*:
   - Tap **Settings** and enable **"Allow from this source"**.
3. If Google Play Protect displays *"Unrecognized app"*:
   - Tap **"More details"** and then select **"Install anyway"**.
4. Launch AANO to start writing private, offline-first notes.

---

## Offline-First Quality System

AANO comes equipped with a unified, **100% offline-first coding quality system** powered by Rust tools and offline rule engines:

```bash
# Run all quality checks across Python, Web, Security, and Android
quality.bat --check

# Run fast pre-commit check (<3 seconds)
quality.bat --quick

# Check system specs and installed tooling
quality.bat --audit
```

Tools integrated:
- **Python**: `ruff` (<15ms linter, formatter, security) + `pytest`
- **Web**: `@biomejs/biome` (<80ms JS/CSS/JSON static analysis)
- **Security / Anti-Slop**: `fixmyslop` (Grade A quality guardrail)
- **Android**: `gradlew.bat` offline unit tests & compilation verification

---

## Repository Architecture

```text
AANO/
├── .github/workflows/   # Automated CI/CD (Android APK + Windows Setup installer)
├── android/             # Android Studio Kotlin Project (Android 8.0+)
│   ├── app/             # Application source, Material 3 layouts, vector drawables
│   └── gradle/          # Gradle version catalog and official wrapper
├── windows/             # Windows Desktop Application
│   ├── src/             # Python-Edge WebView2 bridge, AANO Drop server/client, Web UI
│   ├── assets/          # Multi-resolution aano.ico & branding assets
│   ├── installer/       # Inno Setup scripts (aano.iss) & build_installer.py
│   └── AANO.spec        # PyInstaller specification
├── backend/             # Cloudflare Edge Worker API (D1 SQLite + Mail OTP Bridge)
│   ├── worker.js        # Hardened Cloudflare Worker (Rate limiter + OTP)
│   └── schema.sql       # Cloudflare D1 SQL table definitions
├── docs/                # GitHub Pages Documentation & OAuth Landing
│   ├── index.html       # Landing page (jamamjadalone.github.io/AANO)
│   └── oauth-success.html # Branded Google OAuth redirect screen
├── scripts/             # Unified offline quality system runner
│   └── quality.py       # Python quality runner script
├── quality.bat          # 1-click Windows launcher for quality checks
├── bump_version.py      # Unified cross-platform version management script
├── CHANGELOG.md         # Keep a Changelog documentation
├── CONTRIBUTING.md      # Beginner-friendly guide for new contributors
├── VERSION              # Single source of truth version file (1.2.0-beta.1)
├── LICENSE              # GNU General Public License v3.0
└── README.md            # Master repository documentation
```

---

## Building & Running From Source

### 1. Android App
- Requirements: **Android Studio** (Hedgehog or newer), JDK 17+.
- Build Release APK:
  ```bash
  cd android
  ./gradlew :app:assembleRelease --offline
  ```
  Output APK: `android/app/build/outputs/apk/release/app-release.apk`

### 2. Windows Desktop Client
- Requirements: Python 3.11+, Inno Setup 6+.
- Install build dependencies:
  ```bash
  pip install -r requirements.txt
  ```
- Run directly from source:
  ```bash
  python windows/src/main.py
  ```
- Build standalone setup installer:
  ```bash
  python build_local.py
  ```
  Output Installer: `windows/dist/Installer/AANO-Setup-v1.2.0-beta.1.exe`

---

## Contributing

We warmly welcome contributions from everyone! Whether you want to fix a bug, suggest an idea, design a theme, or write code:
- Please read our [Contributing Guide](CONTRIBUTING.md) for a simple, step-by-step walkthrough.
- Check out existing issues or open a new one on [GitHub Issues](https://github.com/jamamjadalone/AANO/issues).

---

## License

AANO is free and open-source software licensed under the **GNU General Public License v3.0** ([GPL-3.0](LICENSE)).  
Copyright (C) 2026 Jam Amjad Rasheed.
