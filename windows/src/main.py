import os
import sys
import ctypes

import logging

# Enable Windows Per-Monitor DPI Awareness (V2) to prevent blurry window scaling
try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
except (AttributeError, OSError):
    try:
        ctypes.windll.user32.SetProcessDPIAware()
    except (AttributeError, OSError):
        pass

logger = logging.getLogger("AANO.Main")

# fixmyslop-ignore[deps/phantom-import] Top-level import for pywebview package declared in requirements.txt
import webview
from api import DesktopApi

def get_base_dir():
    if getattr(sys, 'frozen', False):
        return sys._MEIPASS
    return os.path.dirname(os.path.abspath(__file__))

def main():
    base_dir = get_base_dir()
    ui_dir = os.path.join(base_dir, "ui")
    index_path = os.path.join(ui_dir, "index.html")

    api = DesktopApi()

    window = webview.create_window(
        title="AANO Notes",
        url=index_path,
        js_api=api,
        width=1280,
        height=800,
        min_size=(850, 600),
        maximized=True,
        background_color="#F8FAFC",
        text_select=True
    )

    from hotkey_daemon import HotkeyDaemon

    def on_scratchpad_hotkey():
        try:
            window.show()
            window.restore()
            window.evaluate_js("window.openQuickScratchpad && window.openQuickScratchpad()")
        except Exception as e:
            logger.debug("Failed to invoke scratchpad on hotkey: %s", e)

    hotkey = HotkeyDaemon(on_scratchpad_hotkey)
    hotkey.start()

    def on_loaded():
        if len(sys.argv) > 1:
            arg_path = sys.argv[1]
            if os.path.exists(arg_path) and (arg_path.lower().endswith(".aano") or arg_path.lower().endswith(".zip")):
                import json
                try:
                    window.evaluate_js(f"window.importAanoOnStartup && window.importAanoOnStartup({json.dumps(os.path.abspath(arg_path))});")
                except Exception as e:
                    logger.warning("Failed to invoke importAanoOnStartup: %s", e)

    window.events.loaded += on_loaded

    # Launch PyWebView with native Windows Edge WebView2 runtime
    try:
        webview.start(debug=False)
    finally:
        hotkey.stop()

if __name__ == "__main__":
    main()
