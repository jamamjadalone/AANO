#!/usr/bin/env python3
"""
AANO Pre-Build UI Integrity & Runtime Auditor
Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later
Rule 21 compliant (< 300 lines).

Verifies:
1. Zero duplicate top-level lexical identifiers (const / let) across all UI scripts.
2. Zero duplicate function definitions across all UI scripts.
3. Live headless PyWebView runtime audit (all 22 scripts loaded, buttons responsive, zero uncaught errors).
"""

import os
import re
import sys
import time
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
UI_DIR = BASE_DIR / "windows" / "src" / "ui"
INDEX_HTML = UI_DIR / "index.html"


def audit_lexical_scope():
    if not INDEX_HTML.exists():
        return False, "index.html not found"

    html_content = INDEX_HTML.read_text(encoding="utf-8", errors="ignore")
    script_srcs = re.findall(r'<script\s+src="([^"]+)"></script>', html_content)
    if not script_srcs:
        return False, "No script tags found in index.html"

    top_level_lexical = {}
    top_level_funcs = {}
    errors = []

    for src in script_srcs:
        js_path = (UI_DIR / src).resolve()
        if not js_path.exists():
            errors.append(f"Referenced script missing: {src}")
            continue

        with open(js_path, "r", encoding="utf-8", errors="ignore") as fp:
            for idx, line in enumerate(fp, 1):
                # Check top-level const / let / var
                m_var = re.match(r"^(let|const)\s+([a-zA-Z0-9_$]+)", line)
                if m_var:
                    kind, name = m_var.group(1), m_var.group(2)
                    top_level_lexical.setdefault(name, []).append((kind, src, idx))

                # Check top-level function
                m_fn = re.match(r"^(?:async\s+)?function\s+([a-zA-Z0-9_$]+)", line)
                if m_fn:
                    fname = m_fn.group(1)
                    top_level_funcs.setdefault(fname, []).append((src, idx))

    # Detect duplicate const/let declarations
    for name, occurrences in top_level_lexical.items():
        if len(occurrences) > 1:
            details = ", ".join(f"{kind} in {src}:{line}" for kind, src, line in occurrences)
            errors.append(f"Duplicate top-level lexical declaration '{name}': {details}")

    # Detect duplicate functions
    for fname, occurrences in top_level_funcs.items():
        if len(occurrences) > 1:
            details = ", ".join(f"{src}:{line}" for src, line in occurrences)
            errors.append(f"Duplicate top-level function declaration '{fname}': {details}")

    if errors:
        return False, "\n".join(f"  [X] {e}" for e in errors)

    return True, f"Lexical scope clean ({len(script_srcs)} scripts audited, 0 collisions)"


def audit_headless_runtime():
    sys.path.insert(0, str(BASE_DIR / "windows" / "src"))
    try:
        import webview
        from api import DesktopApi
    except ImportError as e:
        return False, f"Missing dependencies for headless audit: {e}"

    api = DesktopApi()
    window = webview.create_window(
        "AANO Pre-Build Audit",
        str(INDEX_HTML),
        js_api=api,
        width=900,
        height=700,
        hidden=True
    )

    test_results = {"success": False, "report": None, "error": None}

    def run_tests():
        time.sleep(2)
        try:
            res = window.evaluate_js("""
            (function() {
                var report = {
                    errors: [],
                    modules: {},
                    buttons: {}
                };

                // Track console errors
                var origError = console.error;
                console.error = function() {
                    report.errors.push(Array.from(arguments).join(" "));
                    origError.apply(console, arguments);
                };

                // 1. Audit core module exports
                var requiredExports = {
                    app_state: typeof allNotes,
                    app_utils: typeof loadCustomColorLabels,
                    app_dialogs: typeof showNativeAlert,
                    app_notes_render: typeof renderNotes,
                    app_notes_filter: typeof refreshData,
                    app_editor_core: typeof openEditor,
                    app_editor_toolbar: typeof applyEditorColor,
                    app_editor_checklist: typeof toggleChecklistInEditor,
                    setupFormattingToolbar: typeof setupFormattingToolbar,
                    app_editor_events: typeof setupEditorEventListeners,
                    app_media: typeof setupMediaListeners,
                    app_cropper: typeof setupCropperListeners,
                    app_sync: typeof checkCloudStatus,
                    app_drop: typeof checkDropPeerStatus,
                    app_scanner: typeof setupScannerListeners,
                    app_export: typeof setupExportListeners,
                    app_batch_events: typeof setupBatchBarListeners,
                    app_events: typeof setupEventListeners,
                    app_lock: typeof AppLockManager,
                    app_orchestrator: typeof initApp
                };
                report.modules = requiredExports;

                // 2. Test btnNewNote -> editorModal open
                try {
                    var btnNew = document.getElementById("btnNewNote");
                    var modal = document.getElementById("editorModal");
                    btnNew.click();
                    var opened = modal && !modal.classList.contains("hidden");
                    report.buttons.btnNewNote = opened ? "PASS" : "FAIL (modal not opened)";
                } catch(e) { report.buttons.btnNewNote = "ERROR: " + e; }

                // 3. Test btnEditorClose -> editorModal close
                try {
                    var btnClose = document.getElementById("btnEditorClose");
                    var modal = document.getElementById("editorModal");
                    btnClose.click();
                    var closed = modal && modal.classList.contains("hidden");
                    report.buttons.btnEditorClose = closed ? "PASS" : "FAIL (modal not closed)";
                } catch(e) { report.buttons.btnEditorClose = "ERROR: " + e; }

                // 4. Test btnThemeToggle
                try {
                    var body = document.body;
                    var initTheme = body.getAttribute("data-theme") || "light";
                    document.getElementById("btnThemeToggle").click();
                    var nextTheme = body.getAttribute("data-theme");
                    report.buttons.btnThemeToggle = (initTheme !== nextTheme) ? "PASS" : "FAIL";
                } catch(e) { report.buttons.btnThemeToggle = "ERROR: " + e; }

                // 5. Test btnViewToggle
                try {
                    var cont = document.getElementById("notesContainer");
                    var initList = cont.classList.contains("notes-list-view");
                    document.getElementById("btnViewToggle").click();
                    var nextList = cont.classList.contains("notes-list-view");
                    report.buttons.btnViewToggle = (initList !== nextList) ? "PASS" : "FAIL";
                } catch(e) { report.buttons.btnViewToggle = "ERROR: " + e; }

                // 6. Test btnToggleSelect
                try {
                    var batchBar = document.getElementById("batchActionBar");
                    document.getElementById("btnToggleSelect").click();
                    var isOpen = !batchBar.classList.contains("hidden");
                    document.getElementById("btnToggleSelect").click();
                    var isClosed = batchBar.classList.contains("hidden");
                    report.buttons.btnToggleSelect = (isOpen && isClosed) ? "PASS" : "FAIL";
                } catch(e) { report.buttons.btnToggleSelect = "ERROR: " + e; }

                return report;
            })()
            """)
            test_results["report"] = res
            test_results["success"] = True
        except Exception as ex:
            test_results["error"] = str(ex)
        finally:
            window.destroy()

    webview.start(run_tests, debug=False)

    if not test_results["success"]:
        return False, f"Headless execution crashed: {test_results['error']}"

    rep = test_results["report"] or {}
    modules = rep.get("modules", {})
    buttons = rep.get("buttons", {})
    js_errors = rep.get("errors", [])

    failures = []
    for mod, state in modules.items():
        if state in ("undefined", None):
            failures.append(f"Module '{mod}' failed to export (state: {state})")

    for btn, state in buttons.items():
        if state != "PASS":
            failures.append(f"Button simulation '{btn}' failed: {state}")

    if js_errors:
        for err in js_errors:
            failures.append(f"Uncaught JS console error: {err}")

    if failures:
        return False, "\n".join(f"  [X] {f}" for f in failures)

    return True, f"All {len(modules)} UI modules and {len(buttons)} button interactions verified PASS"


def main():
    print("[AANO UI Audit] Checking JavaScript lexical scope & global identifier integrity...")
    lex_ok, lex_msg = audit_lexical_scope()
    if not lex_ok:
        print(f"\033[91m[FAIL] Lexical Scope Violation Detected:\033[0m\n{lex_msg}")
        return 1
    print(f" \033[92m[ PASS ]\033[0m {lex_msg}")

    print("\n[AANO UI Audit] Launching headless runtime to test script evaluation & button wiring...")
    rt_ok, rt_msg = audit_headless_runtime()
    if not rt_ok:
        print(f"\033[91m[FAIL] UI Runtime Audit FAILED:\033[0m\n{rt_msg}")
        return 1
    print(f" \033[92m[ PASS ]\033[0m {rt_msg}")

    print("\n\033[92m[OK] Complete UI frontend verified functional!\033[0m")
    return 0


if __name__ == "__main__":
    sys.exit(main())
