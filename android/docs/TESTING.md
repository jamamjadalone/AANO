# AANO — Phase 9 Test Checklist

Manual test plan. Install `AANO-release.apk` (or the debug build) on at least two
Android devices. Every item below maps to a roadmap Phase 9 requirement.

Release artifacts (Termux):
- `~/storage/downloads/AANO-release.apk`  (signed, 7.5 MB) — install directly
- `~/storage/downloads/AANO-release.aab`  (6.8 MB) — for Play Store
- `~/storage/downloads/AANO-debug.apk`    (11 MB) — local debugging

Keystore (local only, **never commit**): `app/aano-release.jks`,
`keystore.properties`. Store files: `notes.json`, `tasks.json` in app-private
storage; prefs in `aano_prefs`.

---

## 1. CRUD testing
- [ ] **Notes** create (FAB), edit (auto-save, back button saves), trash, restore from Trash, delete forever.
- [ ] Note title/content persistence across app restarts (kill app, reopen).
- [ ] Note colors: pick each color (7), verify list swatch + item background changes and persists.
- [ ] Note folders: type folder name, save, verify folder chip appears (All notes + per-folder chips), filtering works, notes re-appear when folder cleared.
- [ ] Note tags: comma-separated tags saved, searchable.
- [ ] Pin/unpin ordering (pinned first), long-press share/trash flow.
- [ ] Search: matches title, content, folder, tags; empty state shows correctly.
- [ ] Sort (toolbar menu): updated/newest/title/pinned.
- [ ] Trash auto-purge after 7 days (optional: temporarily lower `TRASH_RETENTION_MS` to test).
- [ ] **Tasks**: create with priority L/M/H, due date, reminder (past time rejected), recurrence Daily/Weekly/Monthly, subtasks add/remove.
- [ ] Task complete → moves to completed; clear completed works.
- [ ] Recurring task: after completing, next-occurrence reminder is scheduled (verify via notification next day/week).
- [ ] Task "Hide from Focus" checkbox hides it in Focus tab; "Show hidden tasks again" restores.
- [ ] Task edit re-opens with all values (priority, date, reminder, recurrence, subtasks).

## 2. Encryption testing
- [ ] With device rooted or via `run-as app.aa.aano`, open `files/notes.json` and `files/tasks.json`: title/content/body/folder/tags must be ciphertext (`{"enc":true}`), not plaintext.
- [ ] `id`, timestamps, deleted flags may be plaintext (expected).
- [ ] Data survives app update (install debug over release and vice-versa) — same Keystore key.
- [ ] PIN hash: `aano_prefs` must not contain the raw PIN; only PBKDF2 output.
- [ ] Rotation attempt done only via UI; no plaintext keys logged anywhere (`adb logcat` scan for "title"/"content").

## 3. Google login testing
- [ ] Settings → Sign up / Sign in buttons both work (requires OAuth client set up in Cloud Console + SHA-1).
- [ ] Cancel sign-in → no crash, state stays "Not connected".
- [ ] Sign out from More tab → status flips to "Not connected", Sync now button hidden.
- [ ] Re-sign-in after sign-out works.
- [ ] Wrong/expired account flow shows a friendly error (no crash).

## 4. Backup testing
- [ ] Manual backup (Settings → Back up) writes `aano-backup.json` in `Downloads/AANO/`.
- [ ] Inspect backup: `{"version":1,"notes":"[...]","tasks":"[...]"}` — note bodies are encrypted strings.
- [ ] Import restores both notes and tasks on the same device.
- [ ] Google Drive auto-sync on resume pushes `aano-notes.json` + `aano-tasks.json` to appdata folder.

## 5. Restore on second device
- [ ] Install on device B, same Google account, sign in.
- [ ] Pull → notes/tasks appear seamlessly.
- [ ] **Resolved:** Cross-device restore now synchronizes through Google Drive's private appDataFolder enclave (`serializeNoteForCloud`), where Device B imports the records and re-encrypts them into its own local Android KeyStore automatically.
- [ ] Offline local files on device remain 100% hardware-encrypted with Android KeyStore.

## 6. Offline testing
- [ ] Airplane mode → app fully usable (no network thread freezes).
- [ ] Sign in while offline → Sync button shows error toast, no crash.
- [ ] Edit note/task while offline → marked `syncPending`, auto-push on next successful sync (reconnect, resume app).

## 7. Crash/error testing
- [ ] Rapid-tap FAB twice → no duplicate/blank note or crash.
- [ ] Empty editor (blank title+content) → closing it doesn't create a blank note.
- [ ] Delete while list is being filtered by folder/search → no crash.
- [ ] Focus timer: start, background the app, reopen → timer resumes/pauses sanely; "complete task" from notification-ish paths.
- [ ] Task reminder fires with app closed → notification appears (POST_NOTIFICATIONS granted).
- [ ] Note reminder fires → opens that specific note.
- [ ] Lock screen: wrong PIN ×3 → stays locked; right PIN → unlock; biometric fallback works; screenshot of locked screen is blank (FLAG_SECURE).
- [ ] Rotate/cutoff: none relevant (portrait lock optional).
- [ ] `adb logcat` run for 10 min during heavy use → no `FATAL EXCEPTION`, no `ANR`.

## 8. Performance testing
- [ ] Create 500 notes (or import via backup) → list scrolls at 60fps, search under ~200 ms.
- [ ] 300 tasks → Tasks list + Focus list still smooth.
- [ ] Memory: 10-min session, `adb shell dumpsys meminfo app.aa.aano` stays stable (no leak growth across open/close cycles).
- [ ] Startup cold → note list visible < 1 s.

## 9. Release build
- [ ] `gradle :app:bundleRelease :app:assembleRelease` → BUILD SUCCESSFUL (done 2026-09-23).
- [ ] APK verifies: `apksigner verify --print-certs` → CN=Jam Amjad Rasheed.
- [ ] Installer CPU-arch (arm64) installs `AANO-release.apk`; runs with no StrictMode/NetworkOnMainThread log spam.
- [ ] Optional: upload `AANO-release.aab` to Play Console internal track; use Play App Signing.
- [ ] Release keystore backed up offline somewhere safe (losing it = can't update the app).

## Known issues (intentionally deferred)
- Cross-device decryption (Keystore-bound key) — README documents it.
- Home-screen widget not yet implemented (roadmap "widget" unchecked).
- No automated unit tests yet (manual-only MVP).