# Beta Testing Guide - Dark Mode Toggle

## Current Status

**✅ Release v1.2.0-rc.2 Building**

### Download Links
| Platform | Download | Status |
|----------|----------|--------|
| **Android APK** | Waiting for CI build | Will include dark mode |
| **Windows EXE** | Waiting for CI build | Will include dark mode |

### Alternative: Build From Source
```bash
# Android
cd android && ./gradlew :app:assembleRelease

# Windows
cd windows/src && python main.py
```

## What Was Built ✅

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

### Android Testing (Local Build)
1. Build APK locally with `./gradlew :app:assembleRelease`
2. Install and open AANO
3. Create a note to see the full UI
4. Go to **Settings** → **Appearance** section
5. Toggle **Dark Mode** checkbox
6. Verify theme changes immediately
7. Close and reopen app - verify toggle stays

### Windows Testing (Local Build)
1. Clone the repo: `git clone https://github.com/jamamjadalone/AANO.git`
2. Navigate to `windows/src`
3. Install dependencies: `pip install -r requirements.txt`
4. Run: `python main.py`
5. Test dark mode toggle in Settings

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