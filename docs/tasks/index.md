# Tasks: Dark Mode Toggle for Windows AANO App

## Summary

This file tracks tasks for adding a dark mode toggle to the Windows desktop app settings.

---

## Index

| ID | Title | Milestone | Type | Depends on | Status |
|----|-------|-----------|------|------------|--------|
| T00 | Setup: Create project structure for lwh tasks | Setup | setup | | done |
| T01 | Add theme preference to storage.py | Feature | build | T00 | done |
| T02 | Add getTheme/setTheme API endpoints | Feature | build | T01 | done |
| T03 | Create Settings toggle UI component | Feature | build | | done |
| T04 | Wire toggle event to persist and apply theme | Feature | build | T02, T03 | done |
| T05 | Add CSS class toggle on root element | Feature | build | T04 | done |
| T06 | Add theme preference to backup/restore | Feature | build | T02 | done |
| T07 | Verify dark mode toggle works end-to-end | Test | review | T05 | done |

---

## T00: Setup blocks

**Done when:**
- docs/tasks/index.md exists with this task list
- CLAUDE.md has "Rules every change follows" section with Rule 21

**How to test:**
- Read docs/tasks/index.md and verify structure

---

## T01: Add theme preference to storage.py

**Design:** Modify `storage.py` to add a `theme` preference field.

**Done when:**
- Theme preference ('light', 'dark', 'system') added to settings JSON structure
- Prefs class has `get_theme()` and `set_theme()` methods
- Default theme is 'light' (user choice respected)

**How to test:**
```python
python3 -c "from storage import Prefs; p = Prefs(); print(p.get_theme())"
# Should output: light
```

---

## T02: Add getTheme/setTheme API endpoints

**Dependencies:** T01

**Design:** Add to `api.py` mixin pattern (like SyncApiMixin).

**Done when:**
- `get_theme()` PyWebView bridge method exists
- `set_theme(theme: str)` PyWebView bridge method exists
- Methods properly call storage.py Prefs

**How to test:**
```python
from api import DesktopApi
api = DesktopApi()
theme = api.get_theme()
api.set_theme("dark")
api.set_theme(theme)  # Restore
```

---

## T03: Create Settings toggle UI component

**Design:** Add toggle component in settings UI (JS/HTML).

**Done when:**
- Toggle control exists in settings modal
- Shows current theme state (checked = dark)
- Styled appropriately for AANO design system

**How to test:**
- Open Settings → Appearance section
- Verify toggle is visible and styled correctly

---

## T04: Wire toggle event to persist and apply theme

**Dependencies:** T02, T03

**Design:** Event listener on toggle that:
1. Calls API set_theme()
2. Updates CSS [data-theme] attribute
3. Saves to localStorage for immediate effect

**Done when:**
- Clicking toggle changes theme immediately
- Preference is persisted to disk
- User sees visual change

**How to test:**
- Toggle dark mode
- Verify sidebar-bg color changes from #FFFFFF to #090D13
- Close and reopen app
- Verify toggle stayed in dark position

---

## T05: Add CSS class toggle on root element

**Dependencies:** T04

**Design:** JavaScript to toggle document.documentElement.dataset.theme.

**Done when:**
- CSS variables in base.css properly switch
- Colors update correctly
- No flickering or layout shifts

**How to test:**
- Toggle dark/light
- Verify colors change per ARCHITECTURE_INDEX.md palettes

---

## T06: Add theme preference to backup/restore

**Dependencies:** T02

**Design:** Include theme in settings JSON backup.

**Done when:**
- backup_app_settings() includes theme
- restore_app_settings() applies theme
- Theme survives Windows reinstall via backup

**How to test:**
- Take backup
- Change theme
- Restore backup
- Verify theme restored

---

## T07: Verify dark mode toggle works end-to-end

**Dependencies:** T05

**Design:** Full integration test.

**Done when:**
- Fresh app install defaults to light mode
- Toggle to dark mode persists after restart
- Toggle to light mode persists after restart
- System theme detection works (if implemented)

**How to test:**
1. Install fresh AANO (if possible) or clear settings
2. Launch app → verify light mode
3. Toggle dark → verify immediate change
4. Close app → reopen → verify dark mode persisted
5. Toggle light → close → reopen → verify light mode persisted

---

## Review Block

**When milestone complete, check:**
- [x] All tasks marked done or skipped
- [x] No skipped tasks blocking progress
- Review checklist passed:
  - [x] Code follows Rule 21 (<300 lines)
  - [x] No anti-slop patterns
  - [x] Theme stored securely
  - [x] Works on Windows 10/11