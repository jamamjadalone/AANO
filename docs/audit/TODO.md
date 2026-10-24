# Audit Execution Checklist & Action Items (AANO Android)

## Phase 1: Repository Discovery & Baseline
- [x] Create docs/audit/ workspace directory.
- [x] Create docs/audit/PLAN.md.
- [x] Create docs/audit/TODO.md.
- [x] Run baseline Gradle build & diagnose any existing errors or warnings.
- [x] Document project architecture, packages, and components baseline.

## Phase 2: UI/UX & Visual Polish Audit
- [x] Audit Material 3 theming (values/colors.xml, values-night/colors.xml, themes.xml).
- [x] Inspect interactive touch target sizes (FAB, bottom navigation, toolbar icons >= 48dp).
- [x] Audit accessibility attributes (contentDescription on ImageButton and icon views).
- [x] Verify empty state UX in Notes, Folders, Tasks, Trash, and Archive.
- [x] Generate docs/audit/UI_UX_AUDIT.md.

## Phase 3: Complete Feature Audit & Matrix
- [x] Audit Note Creation, Markdown & Rich Editing flow.
- [x] Audit Media Vault: Image cropping, WebP compression, disk garbage collection.
- [x] Audit App Lock & Biometrics (Master PIN, biometric prompt).
- [x] Audit Tasks / Checklists lifecycle & auto-sort animation.
- [x] Audit Trash & Archive soft-delete, restore, and permanent purge.
- [x] Audit Dual Cloud Sync (Google Drive + Cloudflare D1 fallback).
- [x] Audit Bidirectional AANO Drop (Wi-Fi P2P selective note transfer).
- [x] Audit Quick Settings Tile & Share Sheet Clipper.
- [x] Audit Native PDF & Markdown export.
- [x] Audit Vault Backup/Restore (.zip bundle).
- [x] Generate docs/audit/FEATURE_MATRIX.md.

## Phase 4: Architecture, Lifecycle & Coroutines Audit
- [x] Audit Fragment ViewBinding lifecycle cleanup (_binding = null in onDestroyView).
- [x] Audit Coroutines cancellation safety in lifecycleScope / background tasks.
- [x] Replace deprecated Android APIs (vibrator, options menus).
- [x] Verify clean separation of concerns in data, ui, net, drop, service.

## Phase 5: Security & Privacy Audit
- [x] Audit AndroidManifest.xml exported components & intent-filters.
- [x] Audit res/xml/file_paths.xml FileProvider paths (path traversal prevention).
- [x] Audit Cryptographic implementations (NoteCipher.kt: AES-GCM, hardware AndroidKeyStore).
- [x] Audit P2P Wi-Fi AANO Drop security (token validation, subnet restriction, payload sanitization).
- [x] Generate docs/audit/SECURITY_AUDIT.md.

## Phase 6: Performance, Memory & Battery Audit
- [x] Inspect memory footprint during image capture, cropping, and rendering.
- [x] Audit background threading: Ensure zero disk/network I/O on Dispatchers.Main.
- [x] Audit battery consumption (WakeLock / Wi-Fi lock release in AanoDropManager).
- [x] Generate docs/audit/PERFORMANCE_REPORT.md.

## Phase 7: Android Platform Compatibility (API 24 - 34+)
- [x] Validate minSdk 24 to targetSdk 34 compatibility.
- [x] Verify Window Insets and Edge-to-Edge handling across system bars & cutouts.
- [x] Verify configuration changes handling (screen rotation, dark/light theme switching).

## Phase 8: Automated Build, Unit Testing & Static Analysis
- [x] Add JUnit 4 & Coroutines test dependencies to android/app/build.gradle.kts.
- [x] Implement NoteModelTest.kt (serialization roundtrip, tombstone sync, default values).
- [x] Implement TaskModelTest.kt (Task creation, subtask toggling, immutability copy).
- [x] Implement AanoDropPayloadTest.kt (P2P protocol serialization, token authentication).
- [x] Run gradlew testReleaseUnitTest and capture test results (8/8 PASSED).
- [x] Run gradlew lintRelease and triage issues (0 fatal errors).
- [x] Generate docs/audit/TEST_REPORT.md.

## Phase 9: Release APK Artifact Audit
- [x] Build clean release APK via ./gradlew assembleRelease.
- [x] Verify APK signature using keystore.properties (app/aano-release.jks).
- [x] Calculate SHA-256 hash, APK file size (7.73 MB), and DEX count (2).
- [x] Generate docs/audit/RELEASE_CHECKLIST.md.

## Phase 10: Code Hardening & Fixes
- [x] Apply all identified architectural, UI/UX, and security fixes.
- [x] Document before/after evidence in docs/audit/FIX_LOG.md.

## Phases 11-13: Comprehensive Audit Report & Acceptance Sign-off
- [x] Compile executive audit findings into docs/audit/AUDIT_REPORT.md.
- [x] Complete final acceptance verification gate.
