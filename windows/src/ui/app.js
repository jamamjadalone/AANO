// AANO Windows Desktop App - Main Orchestrator & Bootstrapper
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

var areUIListenersAttached = false;

function attachUIListeners() {
  if (areUIListenersAttached) return;
  areUIListenersAttached = true;

  initTheme();

  // Attach UI event listeners immediately so all buttons respond from first frame
  try {
    setupEventListeners();
    setupBatchBarListeners();
    setupEditorEventListeners();
    setupMediaListeners();
    setupCropperListeners();
    setupSyncListeners();
    setupDropListeners();
    setupScannerListeners();
    setupExportListeners();
  } catch (err) {
    console.error("UI listener registration error:", err);
  }
}

async function initApp() {
  attachUIListeners();

  if (isAppInitialized) return;
  isAppInitialized = true;

  try {
    await loadCustomColorLabels();
    await refreshData();
    await checkCloudStatus();
    await checkDropPeerStatus();
  } catch (err) {
    console.error("Initial data loading error:", err);
  }

  // If user is logged in, perform immediate auto-sync on app open
  try {
    const api = getApi();
    if (api && api.get_cloud_status) {
      const st = await api.get_cloud_status();
      if (st && st.logged_in) {
        api.sync_with_cloud().then(() => refreshData()).catch(e => console.warn("Open sync error:", e));
      }
    }
  } catch (err) {
    console.debug("Initial cloud sync check skipped:", err);
  }

  // Smart background auto-sync heartbeat (15s for instant multi-device responsiveness)
  setInterval(runAutoSyncInterval, 15000);

  // Immediate sync when app window gains focus or becomes visible
  window.addEventListener("focus", () => {
    runAutoSyncInterval();
    checkDropPeerStatus();
  });
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      runAutoSyncInterval();
      checkDropPeerStatus();
    }
  });

  // Regular periodic poll for paired peer status
  setInterval(checkDropPeerStatus, 3000);
}

// Attach UI listeners as soon as DOM is ready
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", () => {
    attachUIListeners();
    waitForApiAndInit();
  });
} else {
  attachUIListeners();
  waitForApiAndInit();
}

// PyWebView bridge listener
window.addEventListener("pywebviewready", () => {
  initApp();
});

function waitForApiAndInit() {
  if (getApi()) {
    initApp();
    return;
  }
  let attempts = 0;
  const poll = setInterval(() => {
    attempts++;
    if (getApi()) {
      clearInterval(poll);
      initApp();
    } else if (attempts >= 40) {
      clearInterval(poll);
      console.warn("PyWebView API bridge timeout; running in standalone preview mode.");
    }
  }, 100);
}
