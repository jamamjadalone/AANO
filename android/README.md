# AANO

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" alt="AANO Logo" width="96" height="96" />
</p>

<p align="center">
  <b>A private, offline-first notes, tasks, and productivity workstation for Android.</b><br>
  Equipped with hardware-backed AES-256-GCM encryption, biometric security, Pomodoro focus timer, and zero-knowledge Google Drive backup.
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License: GPL-3.0"></a>
  <img src="https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024--35)-brightgreen?style=flat-square" alt="Platform: Android">
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square" alt="Language: Kotlin">
  <img src="https://img.shields.io/badge/Encryption-AES--256--GCM-orange?style=flat-square" alt="Encryption: AES-256-GCM">
  <img src="https://img.shields.io/badge/Trackers-0%20(Zero%20Telemetry)-success?style=flat-square" alt="Zero Trackers">
</p>

---

## Overview

**AANO** is engineered for individuals who demand uncompromising privacy and productivity without subscriptions or corporate data harvesting. Your notes and tasks never touch external analytics servers, your keystore keys never leave your physical device hardware, and cloud backups are encrypted and saved solely into your private Google Drive appdata folder.

### Core Principles
- **Zero Telemetry / No Tracking**: No Google Analytics, no Firebase Crashlytics, no ad networks, no tracking pixels.
- **At-Rest Hardware Encryption**: Every note body and task is encrypted via authenticated **AES-256-GCM** using keys generated in the **Android Keystore**.
- **100% Offline-First**: Every feature operates flawlessly without an internet connection.
- **No Account Required**: Immediate use out of the box with zero sign-up friction.

---

## Features

### Encrypted Notes
- **Hardware-Encrypted Storage**: Plain-text notes are encrypted via AES-256-GCM before writing to flash storage.
- **Organization**: Custom folders, comma-separated searchable tags, and color-coded categorizations.
- **Smart Management**: Pinned notes, live search filtering, auto-saving while typing, and a 7-day recoverable Trash bin.
- **Export & Share**: Clean plain-text sharing and JSON backup exports directly to device storage.

### Tasks & Subtasks
- **Prioritization**: High, Medium, and Low priority classification with visual tags.
- **Due Dates & Recurrence**: One-time reminders or automated recurring intervals (Daily, Weekly, Monthly) via `AlarmManager`.
- **Subtask Breakdown**: Multi-tier subtasks with real-time completion progress tracking.
- **Focus Integration**: Option to hide specific tasks from the Focus dashboard to eliminate cognitive overload.

### Focus Timer (Pomodoro Engine)
- **Built-in Productivity Clock**: 25-minute and 50-minute interval presets with session counters.
- **Smart Task Queue**: Automatically pulls the next uncompleted task into focus.
- **Non-Intrusive Notifications**: Alerts when focus sessions complete without requiring background services when idle.

### Security & Lock Screen
- **PBKDF2-Hashed App PIN**: Secure 4-digit PIN salted per-device using `PBKDF2WithHmacSHA256`.
- **Biometric Authentication**: Official Android `BiometricPrompt` supporting fingerprint and face unlock.
- **Configurable Auto-Lock**: Automatically locks the app immediately or after 1, 5, or 15 minutes of background inactivity.
- **Screenshot Protection**: Uses Android `FLAG_SECURE` to prevent screen grabbing or task-switcher previews while locked.

### Private Google Drive Cloud Sync
- **Hidden `appdata` Scope**: Backups are written to an isolated, non-user-facing application directory (`aano-notes.json` and `aano-tasks.json`).
- **Offline Queue**: Edits made while offline queue up automatically and sync upon reconnection.

---

## Architecture & Security Model

```
AANO Application Architecture
┌────────────────────────────────────────────────────────┐
│                      UI Layer                          │
│   HomeActivity  •  EditorActivity  •  SettingsActivity │
│   NotesFragment •  TasksFragment   •  FocusFragment    │
└──────────────────────────┬─────────────────────────────┘
                           │ ViewBinding & Coroutines
┌──────────────────────────▼─────────────────────────────┐
│                   Data & Domain Layer                  │
│       NoteRepository       •       TaskRepository      │
│       AppLock (PBKDF2)     •       Prefs (Encrypted)   │
└──────────────────────────┬─────────────────────────────┘
                           │ Authenticated Cryptography
┌──────────────────────────▼─────────────────────────────┐
│                 Hardware Security Module               │
│     Android Keystore (Hardware Key Isolation)          │
│     AES-256-GCM Authenticated Encryption Engine        │
└────────────────────────────────────────────────────────┘
```

### Cryptographic Threat Model
1. **Key Generation**: 256-bit AES master keys are generated inside the Android hardware-backed Keystore (`AndroidKeyStore`), ensuring that keys cannot be extracted even on compromised or rooted platforms.
2. **Payload Protection**: Content is converted to JSON, encrypted with AES-256-GCM (12-byte random IV + 128-bit authentication tag), and saved to local storage. 
3. **PIN Security**: PIN entries are salted with a 16-byte cryptographically secure random salt and hashed through 10,000 iterations of `PBKDF2WithHmacSHA256`.

---

## Building From Source

### Prerequisites
- **JDK**: Java Development Kit 17 or higher
- **Android SDK**: `compileSdk 35`, `minSdk 24`
- **Build System**: Gradle 9.7+ with Android Gradle Plugin (AGP) 9.4+

### Clone & Build
```bash
# Clone repository
git clone https://github.com/jamamjadalone/aano-notes.git
cd AANO

# Build Debug APK
gradle :app:assembleDebug
```
The compiled debug binary will be located in:
`app/build/outputs/apk/debug/app-debug.apk`

### Google Drive Integration (Optional)
To test or enable the Google Drive sync functionality in your custom build:
1. Create an Android OAuth 2.0 Client ID in the [Google Cloud Console](https://console.cloud.google.com/).
2. Register your app package (`app.aa.aano`) along with your signing key's SHA-1 fingerprint.
3. Configure the `default_web_client_id` inside `app/src/main/res/values/strings.xml`.

---

## Project Structure

```
AANO/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/app/aa/aano/
│   │   │   ├── data/       # Repositories, models, AES-256 cipher, PIN security
│   │   │   ├── net/        # Drive sync manager, reminder receivers
│   │   │   └── ui/         # Activities, fragments, dialogs, adapters
│   │   └── res/            # Material 3 themes, layouts, vectors, strings
│   └── build.gradle.kts
├── docs/
│   ├── TESTING.md          # Manual testing checklist across 9 phases
│   └── App.txt             # Architecture & MVP implementation roadmap
├── gradle/
│   └── libs.versions.toml  # Modern version catalog
├── CONTRIBUTING.md         # Guidelines for community contributions
├── SECURITY.md             # Security policy and vulnerability reporting
├── LICENSE                 # GNU General Public License v3.0
└── README.md
```

---

## License

AANO is free and open-source software licensed under the **GNU General Public License v3.0 or later** ([GPL-3.0-or-later](LICENSE)).

Copyright (C) 2026 Jam Amjad Rasheed.
