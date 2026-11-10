// AANO Windows Desktop App - Google Drive & Cloud Sync Engine
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

function updateAutoSyncButton(isEnabled) {
  const btn = document.getElementById("btnAutoSyncToggle");
  if (!btn) return;
  const label = document.getElementById("autoSyncLabel");
  const dot = btn.querySelector(".autosync-dot");
  if (isEnabled) {
    btn.classList.remove("off");
    btn.classList.add("active");
    if (label) label.innerText = "Auto-Sync: ON";
    if (dot) dot.innerText = "●";
  } else {
    btn.classList.add("off");
    btn.classList.remove("active");
    if (label) label.innerText = "Auto-Sync: OFF";
    if (dot) dot.innerText = "○";
  }
}

async function runAutoSyncInterval() {
  const api = getApi();
  if (!api) return;
  try {
    const status = await api.get_cloud_status();
    if (status.is_logged_in && status.auto_sync !== false) {
      const res = await api.sync_now();
      if (res && res.success) {
        await refreshData();
      }
      await checkCloudStatus();
    }
    await checkDropPeerStatus();
  } catch (err) {
    console.error("Auto-sync tick error:", err);
  }
}

// --------------------- Cloud Sync Integration ---------------------
async function checkCloudStatus() {
  const api = getApi();
  if (!api) return;

  try {
    const status = await api.get_cloud_status();
    const dot = document.querySelector(".status-dot");
    const statusText = document.getElementById("cloudStatusText");

    if (status.is_logged_in) {
      dot.classList.add("connected");
      dot.style.background = "";
      statusText.innerText = "Synced: " + (status.email || "Online");
      updateAutoSyncButton(status.auto_sync !== false);
      document.getElementById("cloudLoginForm").classList.add("hidden");
      document.getElementById("cloudActiveSession").classList.remove("hidden");
      document.getElementById("lblConnectedEmail").innerText = status.email || "";
    } else {
      dot.classList.remove("connected");
      if (status.session_expired) {
        dot.style.background = "#F59E0B";
        statusText.innerText = "Session Expired (Click to Sign In)";
      } else {
        dot.style.background = "";
        statusText.innerText = "Offline (Click to Sign In)";
      }
      document.getElementById("cloudLoginForm").classList.remove("hidden");
      document.getElementById("cloudActiveSession").classList.add("hidden");
    }
  } catch (err) {
    console.error("Cloud status error:", err);
  }
}

function openCloudModal() {
  document.getElementById("cloudError").classList.add("hidden");
  document.getElementById("cloudModal").classList.remove("hidden");
}

function closeCloudModal() {
  document.getElementById("cloudModal").classList.add("hidden");
}

async function handleCloudLogin() {
  const email = document.getElementById("cloudEmailInput").value.trim();
  const pass = document.getElementById("cloudPassInput").value.trim();
  const errDiv = document.getElementById("cloudError");

  if (!email || !pass) {
    errDiv.innerText = "Please enter email and password.";
    errDiv.classList.remove("hidden");
    return;
  }

  errDiv.innerText = "Connecting to AANO Cloud...";
  errDiv.classList.remove("hidden");

  const api = getApi();
  if (api) {
    const res = await api.login_cloud(email, pass);
    if (res.success) {
      errDiv.classList.add("hidden");
      await checkCloudStatus();
      await refreshData();
      closeCloudModal();
    } else {
      errDiv.innerText = res.error || "Failed to sign in.";
      errDiv.classList.remove("hidden");
    }
  }
}

async function handleGoogleSignIn() {
  const btn = document.getElementById("btnGoogleSignIn");
  const errDiv = document.getElementById("cloudError");
  if (errDiv) errDiv.classList.add("hidden");

  if (btn) {
    btn.disabled = true;
    btn.innerText = "Opening browser for Google login...";
  }

  const api = getApi();
  if (api && api.google_login) {
    try {
      const res = await api.google_login();
      if (res && res.success) {
        await checkCloudStatus();
        await refreshData();
        closeCloudModal();
      } else {
        if (errDiv) {
          errDiv.innerText = (res && res.error) ? res.error : "Google login failed or was cancelled.";
          errDiv.classList.remove("hidden");
        }
      }
    } catch (e) {
      if (errDiv) {
        errDiv.innerText = "Error: " + e;
        errDiv.classList.remove("hidden");
      }
    }
  }

  if (btn) {
    btn.disabled = false;
    // fixmyslop-ignore[sec/raw-html] Static SVG logo template without dynamic inputs
    btn.innerHTML = `
      <svg viewBox="0 0 24 24" width="20" height="20" style="margin-right: 12px;">
        <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
        <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
        <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
        <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
      </svg>
      Sign in with Google
    `;
  }
}

async function handleCloudLogout() {
  const api = getApi();
  if (api) {
    if (api.google_logout) await api.google_logout();
    await api.logout_cloud();
    await checkCloudStatus();
    closeCloudModal();
  }
}


async function triggerManualSync() {
  const btn = document.getElementById("btnManualSync");
  const icon = btn.querySelector(".sync-icon");
  if (icon) {
    icon.style.display = "inline-block";
    icon.style.animation = "spin 1s linear infinite";
  }

  const api = getApi();
  if (api) {
    try {
      const res = await api.sync_now();
      if (!res.success && (res.auth_expired || res.error?.includes("Unauthorized") || res.error?.includes("Session expired") || res.error?.includes("Not logged in"))) {
        openCloudModal();
        const errDiv = document.getElementById("cloudError");
        if (errDiv) {
          errDiv.innerText = res.error || "Please sign in with your Gmail password to connect.";
          errDiv.classList.remove("hidden");
        }
      }
      await refreshData();
      await checkCloudStatus();
    } catch (e) {
      console.error("Sync error:", e);
    }
  }

  if (icon) {
    setTimeout(() => {
      icon.style.animation = "";
    }, 1000);
  }
}

// --------------------- Backup & Restore ---------------------
async function handleExportBackup() {
  const api = getApi();
  if (!api) return;

  const jsonStr = await api.export_backup_json();
  const blob = new Blob([jsonStr], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `aano-backup-${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

async function handleImportBackup(e) {
  const file = e.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = async (evt) => {
    const content = evt.target.result;
    const api = getApi();
    if (api) {
      const res = await api.import_backup_json(content);
      if (res.success) {
        await showNativeAlert({
          title: "Backup Restored",
          message: `Successfully imported ${res.count} notes into AANO.`
        });
        document.getElementById("backupModal").classList.add("hidden");
        await refreshData();
      } else {
        await showNativeAlert({
          title: "Import Failed",
          message: "Failed to import: " + (res.error || "Unknown error")
        });
      }
    }
  };
  reader.readAsText(file);
}

function setupSyncListeners() {
  // Cloud Sync
  document.getElementById("btnCloudSync").addEventListener("click", openCloudModal);
  document.getElementById("btnManualSync").addEventListener("click", triggerManualSync);
  document.getElementById("btnCancelCloud").addEventListener("click", closeCloudModal);
  document.getElementById("btnDoneCloud").addEventListener("click", closeCloudModal);
  
  const btnGoogleSignIn = document.getElementById("btnGoogleSignIn");
  if (btnGoogleSignIn) {
    btnGoogleSignIn.addEventListener("click", handleGoogleSignIn);
  }

  const btnSyncNowAccount = document.getElementById("btnSyncNowAccount");
  if (btnSyncNowAccount) {
    btnSyncNowAccount.addEventListener("click", async () => {
      await triggerManualSync();
    });
  }

  const btnLoginCloud = document.getElementById("btnLoginCloud");
  if (btnLoginCloud) {
    btnLoginCloud.addEventListener("click", handleCloudLogin);
  }
  document.getElementById("btnLogoutCloud").addEventListener("click", handleCloudLogout);

}
