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
  <p align="center"><i>Android • Windows Desktop • Cloudflare Edge Sync</i></p>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-00796B.svg?style=flat-square" alt="License: GPL-3.0"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026--35)-004D40?style=flat-square&logo=android&logoColor=white" alt="Platform: Android">
  <img src="https://img.shields.io/badge/Windows-10%20%2F%2011%20(Edge%20WebView2)-00796B?style=flat-square&logo=windows&logoColor=white" alt="Platform: Windows">
  <img src="https://img.shields.io/badge/Backend-Cloudflare%20D1%20SQL-orange?style=flat-square&logo=cloudflare&logoColor=white" alt="Backend: Cloudflare">
  <img src="https://img.shields.io/badge/Trackers-0%20(Zero%20Telemetry)-10B981?style=flat-square" alt="Zero Trackers">
</p>

---

## 🌟 Overview

**AANO Notes** is an open-source, privacy-first cross-platform note-taking ecosystem built for users who demand elegant design, distraction-free productivity, and complete ownership of their data.

- **Zero Corporate Surveillance:** No telemetry trackers, no analytics pixels, no third-party advertising SDKs.
- **Cross-Platform Freedom:** Seamlessly write on your Android phone and continue on your Windows PC.
- **Dual-Apex Monogram Identity:** Featuring the custom luxury "A.A" origami monogram vector icon across Android adaptive launchers and Windows desktop executables.
- **Offline First:** Fully functional without internet connectivity. Export and import offline JSON backups at will.

---

## ✨ Features Across Platforms

### 🎨 Visual & Editor Experience
- **Emerald Luxury Palette:** Material 3 design centered on deep teal obsidian (`#004D40`) and emerald green (`#00796B`).
- **9 Pastel Paper Shades:** Dynamic editor background tinting (White, Emerald Mint, Butter Yellow, Leaf Green, Sky Blue, Lavender, Rose Pink, Peach Orange, Coral Red).
- **Rich Text Formatting Toolbar:**
  - Headings: **H1** (1.4x) and **H2** (1.2x).
  - Styling: **Bold**, *Italic*, <u>Underline</u>, <s>Strikethrough</s>.
  - 🖍️ **Highlighters:** 5 pastel highlighter colors (Yellow, Mint, Pink, Blue, Peach).
  - 🔤 **Text Colors:** Emerald, Blue, Crimson, Purple, and Obsidian.
  - Lists: Line-level automatic bullet points.
  - History: Full Undo / Redo history stack.
  - Live Statistics: Real-time word and character counter.

### 📱 Android Application (`/android`)
- **Native Kotlin Architecture:** Built with Coroutines, StateFlow, Room / Encrypted preferences, and ViewBinding.
- **Hardware-Protected AppLock:** Salted PBKDF2 PIN lock + Biometric authentication (Fingerprint / Face Unlock).
- **Privacy Shield:** Android `FLAG_SECURE` prevents screenshots and hides sensitive previews in recent apps.
- **Docked Soft-Keyboard Toolbar:** Formatting tools remain permanently anchored directly above the keyboard.

### 💻 Windows Desktop Client (`/windows`)
- **Ultra-Lightweight Engine:** Built on native Windows Edge WebView2 runtime (runs under **30MB RAM** on dual-core CPUs).
- **Flexible Workspace:** Masonry Grid view and List view toggles with real-time title, content, and folder search.
- **Single-File Setup Installer:** Professional setup wizard (`AANO-Setup.exe`) that installs per-user into `AppData\Local\Programs\AANO Notes`, creates desktop and Start Menu shortcuts with the A.A icon, and registers a clean Windows uninstaller.

### ☁️ Cloud Sync & Security (`/backend`)
- **Cloudflare Edge Sync:** Low-latency worldwide note synchronization powered by Cloudflare Workers and Cloudflare D1 SQL.
- **Official Gmail OTP Verification:** Registration protected by 6-digit cryptographic verification codes sent via native Google Apps Script bridge from official Gmail.
- **Anti-Abuse & DDoS Shields:**
  - Canonical Gmail address sanitization (blocks `+` alias tricks, strips dots, eliminates deceptive unicode homoglyphs).
  - IP-based rate limiting (Max 5 OTP requests per 15 mins; 10 failed login limit).
  - Anti-DDoS payload memory cap (50KB max per note).

---

## 🏗️ Repository Architecture

```text
AANO/
├── android/             # Android Studio Kotlin Project (Android 8.0+)
│   ├── app/             # Application source, Material 3 layouts, vector icons
│   └── gradle/          # Gradle version catalog and wrappers
├── windows/             # Windows Desktop Application
│   ├── src/             # Python-Edge WebView2 bridge & modern HTML5/CSS3/JS UI
│   ├── assets/          # Multi-resolution aano.ico & branding assets
│   └── installer/       # Windows setup wizard & Inno Setup scripts
├── backend/             # Cloudflare Edge Worker API
│   ├── worker.js        # Hardened Cloudflare Worker (Rate limiter + OTP)
│   ├── schema.sql       # Cloudflare D1 SQL table definitions
│   └── .env.example     # Environment variable documentation
├── .gitignore           # Strict security exclusions (blocks .jks, .pfx, secrets)
├── LICENSE              # GNU General Public License v3.0
└── README.md            # Master documentation
```

---

## 🚀 Building & Running From Source

### 1. Android App
- Open `/android` in **Android Studio** (Hedgehog or newer).
- JDK 17+, `compileSdk 35`, `minSdk 26`.
- Build release APK:
  ```bash
  cd android
  ./gradlew assembleRelease
  ```

### 2. Windows Desktop Client
- Requirements: Python 3.10+ (Windows 10 / 11)
- Run directly from source:
  ```bash
  pip install pywebview requests
  python windows/src/main.py
  ```
- To bundle standalone executable and setup installer:
  ```bash
  python windows/installer/build_installer.py
  ```
  Output installer: `windows/dist/AANO-Setup.exe`.

### 3. Backend Cloudflare Worker
- Deploy `backend/worker.js` to [Cloudflare Workers](https://workers.cloudflare.com/).
- Bind your D1 database as `DB` and execute `backend/schema.sql` in the D1 console.
- Add encrypted environment variables in Cloudflare Dashboard:
  - `SECRET_APP_KEY`: Client handshake signature.
  - `MAIL_BRIDGE_URL`: Your Google Apps Script Web App URL for OTP delivery.
  - `MAIL_BRIDGE_SECRET`: Shared secret for mail bridge.

---

## 📄 License

AANO is free and open-source software licensed under the **GNU General Public License v3.0** ([GPL-3.0](LICENSE)).  
Copyright (C) 2026 Jam Amjad Rasheed.
