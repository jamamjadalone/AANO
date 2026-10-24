from PyInstaller.utils.hooks import collect_all

webview_datas, webview_binaries, webview_hiddenimports = collect_all('webview')

a = Analysis(
    ['src/main.py'],
    pathex=[],
    binaries=webview_binaries,
    datas=[('src/ui', 'ui'), ('assets', 'assets'), ('src/google_credentials.json', '.')] + webview_datas,
    hiddenimports=webview_hiddenimports + [
        'clr',
        'pythonnet',
        'webview.platforms.winforms',
        'webview.platforms.edgechromium',
        'aano_drop_server',
        'aano_drop_client',
        'drive_sync',
        'hotkey_daemon',
        'storage',
        'api'
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[
        'tkinter',
        '_tkinter',
        'tcl',
        'tk',
        'numpy',
        'scipy',
        'matplotlib',
        'pandas',
        'IPython',
        'jupyter',
        'PIL.ImageTk',
        'PIL.SpiderImagePlugin',
        'unittest',
        'email.test',
        'test',
        'xmlrpc',
        'pydoc',
        'distutils',
        'sqlite3',
    ],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='AANO',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=['assets/aano.ico'],
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='AANO',
)
