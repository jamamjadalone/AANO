// AANO Windows Desktop App - AANO Drop 2.0 P2P Discovery & Transfer Engine
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

async function checkDropPeerStatus() {
  const api = getApi();
  if (!api || !api.get_paired_peer) return;

  try {
    const peer = await api.get_paired_peer();
    const dot = document.getElementById("dropPeerDot");
    const text = document.getElementById("dropPeerText");
    const badge = document.getElementById("dropPeerBadge");
    const card = document.getElementById("activePeerCard");
    const peerNameEl = document.getElementById("activePeerName");
    const peerEpEl = document.getElementById("activePeerEndpoint");
    const ipInput = document.getElementById("targetPhoneIp");

    if (peer && peer.ip) {
      activePairedPeer = peer;
      if (dot) {
        dot.style.background = "#22c55e";
        dot.style.boxShadow = "0 0 8px #22c55e";
      }
      if (text) {
        text.innerText = "Drop: " + (peer.name || peer.ip);
      }
      if (badge) {
        badge.title = `Connected to ${peer.name || "Device"} (${peer.ip}:${peer.port}). Click to manage.`;
      }
      if (card) card.style.display = "block";
      if (peerNameEl) peerNameEl.innerText = peer.name || "Connected Peer";
      if (peerEpEl) peerEpEl.innerText = `${peer.ip}:${peer.port}`;
      if (ipInput && !ipInput.value) {
        ipInput.value = `${peer.ip}:${peer.port}`;
      }
    } else {
      activePairedPeer = null;
      if (dot) {
        dot.style.background = "#64748b";
        dot.style.boxShadow = "none";
      }
      if (text) {
        text.innerText = "Drop: Standby";
      }
      if (badge) {
        badge.title = "AANO Drop: Standby (Click to connect/pair)";
      }
      if (card) card.style.display = "none";
    }
  } catch (err) {
    console.warn("checkDropPeerStatus error:", err);
  }
}

async function openDropModal() {
  const modal = document.getElementById("aanoDropModal");
  if (!modal) return;
  modal.classList.remove("hidden");
  await checkDropPeerStatus();

  // Populate selective notes picker
  const pickerContainer = document.getElementById("dropNotesPickerContainer");
  const countEl = document.getElementById("dropSelectionCount");
  if (pickerContainer) {
    pickerContainer.replaceChildren();
    const activeNotes = (allNotes || []).filter(n => !n.deleted && !n.archived);

    if (activeNotes.length === 0) {
      const emptyDiv = document.createElement("div");
      emptyDiv.style.cssText = "color: #64748b; font-size: 11px; padding: 4px;";
      emptyDiv.textContent = "No notes available";
      pickerContainer.appendChild(emptyDiv);
    } else {
      activeNotes.forEach(n => {
        const item = document.createElement("label");
        item.style.cssText = "display: flex; align-items: center; gap: 8px; font-size: 12px; color: #cbd5e1; padding: 3px 0; cursor: pointer;";
        const chk = document.createElement("input");
        chk.type = "checkbox";
        chk.value = n.id;
        chk.className = "drop-note-chk";

        // Pre-check if this is current editing note OR in selectedNoteIds
        if (editingNote && editingNote.id === n.id) {
          chk.checked = true;
        } else if (selectedNoteIds && selectedNoteIds.has(n.id)) {
          chk.checked = true;
        }

        chk.addEventListener("change", updateDropSelectionCount);

        const titleSpan = document.createElement("span");
        titleSpan.innerText = n.title ? (n.title.length > 28 ? n.title.slice(0, 28) + "..." : n.title) : "Untitled Note";
        titleSpan.style.cssText = "overflow: hidden; text-overflow: ellipsis; white-space: nowrap;";

        item.appendChild(chk);
        item.appendChild(titleSpan);
        pickerContainer.appendChild(item);
      });
    }
    updateDropSelectionCount();
  }

  const api = getApi();
  if (api && api.get_drop_info) {
    try {
      const info = await api.get_drop_info();
      if (info) {
        const ipEl = document.getElementById("dropServerIp");
        const pinEl = document.getElementById("dropServerPin");
        if (ipEl) ipEl.innerText = `IP: ${info.ip || "127.0.0.1"}:${info.port || 8765}`;
        if (pinEl) pinEl.innerText = info.pin || "------";

        // Render High-Contrast Standard QR Code onto canvas (220px)
        const canvas = document.getElementById("dropQrCanvas");
        if (info.qr_data_url && canvas) {
          const ctx = canvas.getContext("2d");
          const img = new Image();
          img.onload = () => {
            canvas.width = 220;
            canvas.height = 220;
            canvas.style.width = "220px";
            canvas.style.height = "220px";
            ctx.clearRect(0, 0, 220, 220);
            ctx.drawImage(img, 0, 0, 220, 220);
          };
          img.src = info.qr_data_url;
        } else if (typeof renderQrToCanvas === "function") {
          const qrUri = info.qr_data || `aano://drop?ip=${info.ip}&port=${info.port || 8765}&pin=${info.pin}`;
          renderQrToCanvas("dropQrCanvas", qrUri, { size: 220 });
        }
      }
    } catch (e) {
      console.warn("Could not load drop info:", e);
    }
  }
}

function updateDropSelectionCount() {
  const countEl = document.getElementById("dropSelectionCount");
  const checked = document.querySelectorAll(".drop-note-chk:checked");
  if (countEl) {
    countEl.innerText = `${checked.length} selected`;
    countEl.style.color = checked.length > 0 ? "var(--brand)" : "#ef4444";
  }
}

function closeDropModal() {
  const modal = document.getElementById("aanoDropModal");
  if (modal) modal.classList.add("hidden");
}

// AANO Windows Desktop App - Frontend Logic
// Copyright (C) 2026 Jam Amjad Rasheed

function setupDropListeners() {
  // AANO Drop Modal Listeners
  const dropPeerBadge = document.getElementById("dropPeerBadge");
  if (dropPeerBadge) dropPeerBadge.addEventListener("click", openDropModal);

  const btnAanoDrop = document.getElementById("btnAanoDrop");
  if (btnAanoDrop) btnAanoDrop.addEventListener("click", openDropModal);
  const btnCloseAanoDrop = document.getElementById("btnCloseAanoDrop");
  if (btnCloseAanoDrop) btnCloseAanoDrop.addEventListener("click", closeDropModal);
  const btnDoneAanoDrop = document.getElementById("btnDoneAanoDrop");
  if (btnDoneAanoDrop) btnDoneAanoDrop.addEventListener("click", closeDropModal);

  const btnUnpairPeer = document.getElementById("btnUnpairPeer");
  if (btnUnpairPeer) {
    btnUnpairPeer.addEventListener("click", async () => {
      const api = getApi();
      if (api && api.unpair_peer) {
        await api.unpair_peer();
        await checkDropPeerStatus();
      }
    });
  }

  const btnScanRadar = document.getElementById("btnScanRadar");
  const radarResults = document.getElementById("radarResultsContainer");
  const radarLabel = document.getElementById("radarScanLabel");
  if (btnScanRadar) {
    btnScanRadar.addEventListener("click", async () => {
      const api = getApi();
      if (!api || !api.scan_nearby_devices) return;
      if (radarLabel) radarLabel.textContent = "Scanning Wi-Fi Radar...";
      if (radarResults) {
        radarResults.style.display = "block";
        const scanMsg = document.createElement("div");
        scanMsg.style.cssText = "color: #94a3b8; font-size: 11px; padding: 6px; text-align: center;";
        scanMsg.textContent = "Scanning subnet for active AANO devices...";
        radarResults.replaceChildren(scanMsg);
      }
      try {
        const found = await api.scan_nearby_devices();
        if (radarResults) {
          if (!found || found.length === 0) {
            const noDev = document.createElement("div");
            noDev.style.cssText = "color: #94a3b8; font-size: 11px; padding: 6px; text-align: center;";
            noDev.textContent = "No nearby AANO devices detected on Wi-Fi.";
            radarResults.replaceChildren(noDev);
          } else {
            radarResults.replaceChildren();
            found.forEach(dev => {
              const row = document.createElement("div");
              row.style.cssText = "display: flex; align-items: center; justify-content: space-between; padding: 6px 8px; border-bottom: 1px solid rgba(255,255,255,0.05);";

              const infoCol = document.createElement("div");
              const nameDiv = document.createElement("div");
              nameDiv.style.cssText = "font-weight: 600; font-size: 12px; color: #f1f5f9;";
              nameDiv.textContent = dev.name || "Device";
              const epDiv = document.createElement("div");
              epDiv.style.cssText = "font-size: 11px; color: #64748b;";
              epDiv.textContent = `${dev.ip}:${dev.port}`;
              infoCol.appendChild(nameDiv);
              infoCol.appendChild(epDiv);

              const connBtn = document.createElement("button");
              connBtn.className = "btn-connect-dev";
              connBtn.style.cssText = "background: var(--brand); color: white; border: none; border-radius: 4px; padding: 4px 10px; font-size: 11px; cursor: pointer; font-weight: 600;";
              connBtn.textContent = "Connect & Pair";
              connBtn.addEventListener("click", async () => {
                connBtn.textContent = "Pairing...";
                const res = await api.pair_with_device(dev.ip, dev.port);
                if (res && res.success) {
                  await checkDropPeerStatus();
                  closeDropModal();
                  showNativeAlert({ title: "Connected", message: `Paired successfully with ${dev.name}!` });
                } else {
                  showNativeAlert({ title: "Pairing Failed", message: res?.error || "Could not pair with device." });
                }
                connBtn.textContent = "Connect & Pair";
              });

              row.appendChild(infoCol);
              row.appendChild(connBtn);
              radarResults.appendChild(row);
            });
          }
        }
      } catch (err) {
        if (radarResults) {
          const errMsg = document.createElement("div");
          errMsg.style.cssText = "color: #ef4444; font-size: 11px; padding: 6px;";
          errMsg.textContent = `Scan error: ${String(err)}`;
          radarResults.replaceChildren(errMsg);
        }
      } finally {
        if (radarLabel) radarLabel.textContent = "Radar Scan";
      }
    });
  }

  // Copy PC Drop URI
  const btnCopyDropUri = document.getElementById("btnCopyDropUri");
  if (btnCopyDropUri) {
    btnCopyDropUri.addEventListener("click", () => {
      const ip = document.getElementById("dropServerIp")?.innerText.replace("IP: ", "").trim() || "";
      const pin = document.getElementById("dropServerPin")?.innerText.trim() || "";
      const uri = `aano://drop?ip=${ip.split(":")[0]}&port=${ip.split(":")[1] || "8765"}&pin=${pin}`;
      navigator.clipboard.writeText(uri).then(() => {
        btnCopyDropUri.innerText = "✅";
        setTimeout(() => { btnCopyDropUri.innerText = "📋"; }, 2000);
      }).catch(() => {
        prompt("Copy Drop URI:", uri);
      });
    });
  }

}
