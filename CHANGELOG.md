# Changelog

All notable changes to the **AANO Notes** project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-10-22

### 🚀 Added
- **Cross-Platform Real-Time Sync**: Instant bi-directional cloud synchronization between Android and Windows Desktop powered by Cloudflare Workers and Cloudflare D1 distributed SQLite database.
- **2.5s Foreground Sync Engine**: Active foreground polling engine on both mobile and PC that automatically synchronizes note creations, updates, and deletions within 2.5 seconds without manual refresh.
- **Hardware-Backed AES-256 Encryption**: Notes and sensitive metadata are locally encrypted using AES-256-GCM cryptography on both Android and Windows clients.
- **Official Google Mail OTP Bridge**: Enterprise-grade 6-digit OTP verification delivered directly to the user's Gmail inbox for registration and secure password recovery.
- **Rich Text Formatting Toolbar**: Integrated rich text formatting engine supporting Headings (H1, H2), Bold, Italic, Underline, Strikethrough, Bullet Lists, and color highlighters docked above the keyboard on Android and in the editor top bar on Windows.
- **9 Pastel Paper Themes**: Beautiful, distraction-free pastel note background shades (Emerald Mint, Butter Yellow, Leaf Green, Sky Blue, Lavender, Rose Pink, Peach, Coral, and Pure White).
- **Offline JSON Backup & Restore**: Cross-compatible offline backup export and import (`aano-backup.json`) enabling zero-cloud local data portability.
- **Cold-Start & Resume App Lock**: Biometric and 4-digit PIN security shield with `FLAG_SECURE` window protection preventing unauthorized screenshots and app switcher previews on Android.
- **Modern Windows Inno Setup Installer**: Crisp, lightweight installer (`AANO-Setup.exe`) with multi-resolution per-monitor DPI awareness and desktop integration.

### 🎨 Changed & Modernized
- **Complete Vector Icon Overhaul**: Replaced all legacy and emoji buttons with sleek, minimalist SVG / Material 3 vector icons (Feather & Lucide rounded stroke design) across Android and Windows.
- **Native Custom Dialogs**: Replaced browser `confirm()`, `prompt()`, and `alert()` popups on Windows with 100% native blurred backdrop modals, eliminating browser URLs (`file:///...`) from all dialogs.
- **Crisp Multi-Resolution App Icons**: Generated high-fidelity mipmap assets (`mdpi` through `xxxhdpi` on Android, 16px to 256px multi-frame `.ico` on Windows) for pristine clarity across 4K, high-DPI, and mobile displays.

### 🐛 Fixed
- **Instant Cross-Device Delete Propagation**: Fixed deletion timestamps (`updatedAt = maxOf(now, existing + 1000L)`) ensuring server-side conflict resolution permanently accepts deletions and immediately removes deleted notes across all paired devices.
- **Session Expiration & 401 Detection**: Resolved silent sync failures caused by password resets by adding automatic 401 token expiration detection and prompting the user with an Amber session warning.
- **Direct Editor Delete Action**: Added an immediate delete action (`ic_trash`) inside the Android note editor top bar with confirmation dialog to allow deleting notes without exiting to the main list.
- **Dex Merging & Daemon Contention**: Resolved Windows file-locking conflicts during automated release compilation.

### 🛡️ Security
- **Cloudflare Edge Shield**: Rate limiting on API endpoints (IP-based brute-force defense on login and OTP generation).
- **Single Database Integrity**: Verified identical D1 schema and Cloudflare Worker endpoints for mobile and desktop clients to guarantee cross-device data coherence.

---

[1.0.0]: https://github.com/jamamjadalone/AANO/releases/tag/v1.0.0
