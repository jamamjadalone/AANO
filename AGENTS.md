# AANO Agent Playbook & Rapid Engineering Directives
> **Target Audience**: AI Agents (DeepMind, Claude, Cursor, Copilot) & Human Contributors.  
> **Core Mandate**: Minimum token waste, zero vibe-coding errors, instant task execution.

---

## 1. Golden Rules (Non-Negotiable)

1. **Rule 21 Line Cap (< 300 Lines)**:
   - **No file over 300 lines**. If a class or file approaches 280 lines, decompose it into focused sub-delegates immediately.
2. **Directory Topology**:
   - **Screens & UI Pages** ➔ `pages/` (e.g. `EditorActivity.kt`, `NotesFragment.kt`)
   - **Shared Pieces & Reusable Elements** ➔ `components/` (subdivided into `editor/`, `dialogs/`, `views/`, `adapters/`)
   - **Storage, Database & Models** ➔ `data/` (e.g. `NoteRepository.kt`, `NoteCipher.kt`)
   - **P2P Local Transfer** ➔ `drop/` (e.g. `DropRadar.kt`, `DropServer.kt`)
   - **Cloud Sync & Receivers** ➔ `net/` (e.g. `DriveSyncManager.kt`, `BootReceiver.kt`)
3. **Token-Saver Protocol**:
   - **Never grep or scan all 87 files**. Read `ARCHITECTURE_INDEX.md` first to find the exact target file.
   - Make surgical edits to that single file only.
4. **Anti-Slop Guard**:
   - Never write empty catch blocks (`catch (_: Exception) {}`). Always log (`Log.d(TAG, "Notice: ${e.message}")`).
5. **Zero Trademark Footprint (Rule 13)**:
   - Never mention third-party commercial brand names in code, layout text, or commit messages.

---

## 2. Dev CLI Workflow (`aano_dev.py`)

Always use the built-in developer CLI for rapid, deterministic operations:

```bash
# 1. Health & Architecture Audit (0.2s)
python aano_dev.py check

# 2. Deep Integrity Check (Ghost imports, Manifest registrations, Index sync)
python aano_dev.py doctor

# 3. Package Line Statistics
python aano_dev.py stats

# 4. Instant Component Scaffolding (Pre-formatted, Rule-21 compliant)
python aano_dev.py scaffold activity <ScreenName>
python aano_dev.py scaffold fragment <FragmentName>
python aano_dev.py scaffold helper <HelperName>
python aano_dev.py scaffold dialog <DialogName>

# 5. Synchronize Architecture Index
python aano_dev.py map
```

---

## 3. Standard Patterns for New Code

### Creating a Screen
- Use `python aano_dev.py scaffold activity MyNew`
- Registers into `pages/MyNewActivity.kt`
- Add to `android/app/src/main/AndroidManifest.xml`: `<activity android:name=".pages.MyNewActivity" android:exported="false" />`
- Run `python aano_dev.py doctor` to verify.

### Modifying Note Storage
- Do NOT bloat `NoteRepository.kt`.
- Add queries to `data/NoteQueryDelegate.kt`.
- Add mutations to `data/NoteCrudDelegate.kt`.
- Add serialization / sync to `data/NoteSyncTrashDelegate.kt`.
- Forward through `NoteRepository.kt` facade.

---

## 4. Pre-Commit Verification Checklist
Before committing or marking any task complete:
```bash
python aano_dev.py check
python aano_dev.py doctor
```
If both commands output `[OK]`, your code is 100% clean and ready to commit.
