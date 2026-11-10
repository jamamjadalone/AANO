// AANO Windows Desktop App - Main Orchestrator & Bootstrapper
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

async function initApp() {
  if (isAppInitialized) return;
  isAppInitialized = true;

  initTheme();
  await loadCustomColorLabels();
  await refreshData();
  await checkCloudStatus();
  await checkDropPeerStatus();

  setupEventListeners();
  setupBatchBarListeners();
  setupEditorEventListeners();
  setupMediaListeners();
  setupCropperListeners();
  setupSyncListeners();
  setupDropListeners();
  setupScannerListeners();
  setupExportListeners();

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

// PyWebView bridge listener
window.addEventListener("pywebviewready", () => {
  initApp();
});

// Fallback if pywebviewready already fired
document.addEventListener("DOMContentLoaded", () => {
  setTimeout(() => {
    if (getApi()) initApp();
  }, 300);
});
