# Contributing to AANO

Thank you for your interest in improving AANO! AANO is an open-source, privacy-first Android note-taking and task workstation licensed under **GPL-3.0-or-later**.

## Core Development Guidelines

1. **Zero Tracking Principle**:
   - Pull requests introducing third-party tracking, ad libraries, telemetry collectors, or unnecessary network calls will be immediately rejected.
2. **Offline-First Resilience**:
   - Every core feature (notes, tasks, search, focus timer) must work 100% reliably in Airplane mode.
3. **Cryptographic Integrity**:
   - All persistence routines touching note bodies or task contents must pass through `NoteCipher` using AES-256-GCM.
4. **Code Style**:
   - Idiomatic Kotlin, utilizing Kotlin Coroutines for asynchronous work.
   - Maintain AndroidX ViewBinding standards.
   - Target SDK: 35 | Minimum SDK: 24.

## Getting Started

1. **Fork and Clone**:
   ```bash
   git clone https://github.com/jamamjadalone/aano-notes.git
   cd AANO
   ```

2. **Build Debug APK**:
   ```bash
   gradle :app:assembleDebug
   ```

3. **Branching & Commits**:
   - Use descriptive commit messages following the Conventional Commits specification (e.g., `feat:`, `fix:`, `docs:`, `refactor:`).
   - Create a feature branch for your work: `git checkout -b feature/my-feature`.

4. **Testing**:
   - Follow the manual verification checklist documented in [`docs/TESTING.md`](docs/TESTING.md) before submitting a pull request.
