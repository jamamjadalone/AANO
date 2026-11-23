# Beta Testing Guide - Dark Mode Toggle (v1.2.0-rc.2)

## Downloads

| Platform | Download | Notes |
|----------|----------|-------|
| Android | [AANO-v1.2.0-rc.2.apk](https://github.com/jamamjadalone/AANO/releases/download/v1.2.0-rc.2/AANO-v1.2.0-rc.2.apk) | **Includes dark mode feature** |
| Windows | [AANO-Setup-v1.2.0-rc.1.exe](https://github.com/jamamjadalone/AANO/releases/download/v1.2.0-rc.2/AANO-Setup-v1.2.0-rc.1.exe) | Dark mode code is ready, EXE rebuild pending |

## What Was Built

### Dark Mode Implementation
- **Theme preference** stored in `config.json`
- **Persistent settings** - theme survives app restarts
- **CSS variable switching** - smooth transitions between light/dark
- **Settings modal** - toggle checkbox with immediate visual feedback
- **Header toggle** - additional toggle in the main header button

### Files Modified/Created
- `windows/src/storage.py` - Added `get_theme()` / `set_theme()`
- `windows/src/api_theme.py` - New API mixin (28 lines)
- `windows/src/ui/js/app_settings.js` - Settings logic (55 lines)
- `windows/src/ui/js/app_events.js` - Event handlers
- `windows/src/ui/index.html` - Settings modal HTML
- `windows/src/ui/css/base.css` - Theme CSS variables

## How to Test

### Android Testing
1. Download the APK from the link above
2. Enable "Install unknown apps" in Settings
3. Install and open AANO
4. Create a note to see the full UI
5. Go to **Settings** → **Appearance** section
6. Toggle **Dark Mode** checkbox
7. Verify theme changes immediately
8. Close and reopen app - verify toggle stays

### Windows Testing (Source Code)
Note: The EXE build is pending. To test locally:
1. Clone the repo: `git clone https://github.com/jamamjadalone/AANO.git`
2. Navigate to `windows/src`
3. Run: `python main.py`
4. Test dark mode toggle in Settings

## Testing Checklist

- [ ] Fresh install defaults to light mode
- [ ] Toggle to dark mode persists after restart
- [ ] Toggle to light mode persists after restart
- [ ] No UI flicker or layout shifts on theme change
- [ ] All notes list colors update correctly in dark mode
- [ ] Editor background adapts to theme changes

## Feedback

Please report issues or suggestions on GitHub Issues:
https://github.com/jamamjadalone/AANO/issues