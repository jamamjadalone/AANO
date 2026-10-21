# AANO Notes 🌿

> **Minimalist, Beautiful & Private Cross-Platform Notes App**  
> Built with Android (Kotlin), Windows Desktop (Python/WebView2), and Cloudflare Edge Sync (D1 SQL).

---

## ✨ Features

- **Emerald Elegance Theme:** Luxury Material 3 emerald green branding (`#00796B`) with the signature "A.A" dual-apex origami monogram icon.
- **Rich Text Formatting:** Heading 1/2, Bold, Italic, Underline, Strikethrough, 5 Pastel Highlighters, Dynamic Font Colors, Bullet Lists, and Undo/Redo history stack.
- **Dynamic Pastel Paper:** 9 soothing pastel paper background colors (White, Mint Green, Butter Yellow, Leaf Green, Sky Blue, Lavender, Rose Pink, Peach Orange, Coral Red).
- **Folders & Instant Search:** Organized note hierarchy with real-time note count badges and live search filtering.
- **Bi-Directional Cloud Sync:** Instant sync between Android and Windows powered by Cloudflare Workers & Cloudflare D1 distributed SQL database.
- **Bulletproof AppLock:** Cold-start & background-resume PIN lock with biometric support and hardware `FLAG_SECURE` screenshot prevention.
- **Offline First & Backup:** 100% functional without internet. Export and import `aano-backup.json` freely across any device.

---

## 📁 Repository Structure

```text
AANO/
├── android/             # Android Studio Kotlin Project (Android 8.0+)
│   ├── app/             # Application source, Material 3 layouts, vector icons
│   └── gradle/          # Version catalogs and Gradle wrappers
├── windows/             # Windows Desktop Client
│   ├── src/             # Python backend bridge & HTML5/CSS3/JS UI
│   ├── assets/          # Multi-resolution aano.ico & branding assets
│   └── installer/       # Windows setup wizard & Inno Setup scripts
├── backend/             # Cloudflare Edge Worker API
│   ├── worker.js        # Hardened Cloudflare Worker (Rate limiter + OTP)
│   ├── schema.sql       # Cloudflare D1 SQL table definitions
│   └── .env.example     # Environment variable documentation
├── .gitignore           # Strict security exclusions (blocks .jks, .pfx, secrets)
├── LICENSE              # GNU General Public License v3.0
└── README.md            # Project documentation
```

---

## 🚀 Getting Started

### 1. Android App
- Open the `/android` folder in **Android Studio Hedgehog / Jellyfish**.
- Sync Gradle and run on device or build release APK:
  ```bash
  ./gradlew assembleRelease
  ```

### 2. Windows Desktop App
- Requirements: Python 3.10+ (Windows 10 / 11)
- Run directly from source:
  ```bash
  pip install pywebview requests
  python windows/src/main.py
  ```
- To build standalone `AANO.exe` and `AANO-Setup.exe`:
  ```bash
  python windows/installer/build_installer.py
  ```

### 3. Backend Cloudflare Worker
- Deploy `backend/worker.js` to Cloudflare Workers.
- Bind your Cloudflare D1 database as `DB` and run `backend/schema.sql`.
- Configure environment secrets in Cloudflare Dashboard:
  - `SECRET_APP_KEY`: Client handshake signature.
  - `MAIL_BRIDGE_URL`: Your Google Apps Script Web App URL for OTP delivery.
  - `MAIL_BRIDGE_SECRET`: Shared secret for mail bridge.

---

## 📄 License

Licensed under the [GNU General Public License v3.0](LICENSE).  
Copyright (C) 2026 Jam Amjad Rasheed.
