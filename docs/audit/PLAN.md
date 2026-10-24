# Master Audit & Engineering Overhaul Plan (AANO Android)

## Project Identification
- **App Name**: AANO
- **Package ID**: pp.aa.aano
- **Current Version**: 1.2.0-beta.1 (Version Code: 4)
- **Compile SDK**: 34 (Android 14)
- **Target SDK**: 34
- **Min SDK**: 24 (Android 7.0)
- **Architecture**: Single-Activity & Modular Fragments + Native Services + Local Encrypted Storage + Bidirectional P2P Drop Server + Dual Cloud Sync Engine.

---

## 13-Phase Audit Scope

| Phase | Title | Target Deliverable | Status |
|---|---|---|---|
| **Phase 1** | Repository Discovery & Baseline | PLAN.md, TODO.md, Baseline Build | In Progress |
| **Phase 2** | UI/UX & Visual Polish Audit | UI_UX_AUDIT.md, Theme & A11y Polish | Pending |
| **Phase 3** | Complete Feature Traceability Matrix | FEATURE_MATRIX.md | Pending |
| **Phase 4** | Architecture, Lifecycle & Coroutine Safety | Architecture Review & Leak Prevention | Pending |
| **Phase 5** | Security & Privacy Deep Dive | SECURITY_AUDIT.md, FileProvider & Crypto Review | Pending |
| **Phase 6** | Performance, Memory & Battery Audit | PERFORMANCE_REPORT.md | Pending |
| **Phase 7** | Android OS Compatibility (API 24–34+) | Insets & Cutout Verification | Pending |
| **Phase 8** | Unit Testing Suite & Static Analysis | TEST_REPORT.md, JUnit 4 Suite | Pending |
| **Phase 9** | Release APK Integrity & Signing Audit | RELEASE_CHECKLIST.md, apksigner Audit | Pending |
| **Phase 10** | Code Hardening & Regressions Fixes | FIX_LOG.md | Pending |
| **Phase 11** | Audit Documentation Consolidation | Full Report Compilation | Pending |
| **Phase 12** | Acceptance Criteria & QA Sign-off | Verification Gate Check | Pending |
| **Phase 13** | Executive Summary & Handover | AUDIT_REPORT.md Final Delivery | Pending |

---

## Tooling & Verification Commands
- Gradle Wrapper: ndroid/gradlew.bat
- Build Commands:
  - Unit Tests: gradlew.bat testReleaseUnitTest
  - Lint: gradlew.bat lintRelease
  - Release APK: gradlew.bat assembleRelease
- Verification CLI:
  - Git status & diff
  - Android Lint XML/HTML report parser
  - APK Analyzer / apksigner
