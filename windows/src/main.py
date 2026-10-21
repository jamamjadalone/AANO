import os
import sys
import ctypes

# Enable Windows Per-Monitor DPI Awareness (V2) to prevent blurry window scaling
try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
except Exception:
    try:
        ctypes.windll.user32.SetProcessDPIAware()
    except Exception:
        pass

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

    # Launch PyWebView with native Windows Edge WebView2 runtime
    webview.start(debug=False)

if __name__ == "__main__":
    main()
