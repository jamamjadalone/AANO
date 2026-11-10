// AANO Windows Desktop App - Camera QR Scanner & Pairing Engine
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

function setupScannerListeners() {
  // PC Camera QR Scanner Modal Logic
  const scannerModal = document.getElementById("aanoScannerModal");
  const scannerVideo = document.getElementById("qrScannerVideo");
  const scannerStatus = document.getElementById("qrScannerStatus");
  const btnOpenQrScanner = document.getElementById("btnOpenQrScanner");
  const btnCloseScannerModal = document.getElementById("btnCloseScannerModal");
  const btnUploadQrImage = document.getElementById("btnUploadQrImage");
  const qrFileInput = document.getElementById("qrFileInput");
  const btnPasteQrCode = document.getElementById("btnPasteQrCode");

  function applyScannedDropResult(val) {
    if (!val) return;
    const parsed = window.AanoQrScanner ? window.AanoQrScanner.parseDropUri(val) : null;
    const targetIp = document.getElementById("targetPhoneIp");
    const targetPin = document.getElementById("targetPhonePin");

    if (parsed && parsed.valid) {
      if (targetIp) targetIp.value = parsed.port && parsed.port !== "8765" ? `${parsed.ip}:${parsed.port}` : parsed.ip;
      if (targetPin) targetPin.value = parsed.pin || "";
      closeScannerModal();
      showNativeAlert({
        title: "⚡ Phone Paired",
        message: `Paired successfully with device at ${parsed.ip}! Choose note(s) below and click 'Send Selected Note(s)'.`
      });
    } else {
      if (targetIp) targetIp.value = val.trim();
      closeScannerModal();
    }
  }

  function openScannerModal() {
    if (!scannerModal || !scannerVideo) return;
    scannerModal.classList.remove("hidden");
    if (scannerStatus) scannerStatus.innerText = "Point camera at phone's AANO Drop QR code";
    if (window.AanoQrScanner) {
      window.AanoQrScanner.startCamera(scannerVideo, (val) => {
        applyScannedDropResult(val);
      }, (err) => {
        if (scannerStatus) scannerStatus.innerText = "Camera not detected or permission denied. Use Screenshot or Paste below.";
      });
    }
  }

  function closeScannerModal() {
    if (window.AanoQrScanner) window.AanoQrScanner.stopCamera();
    if (scannerModal) scannerModal.classList.add("hidden");
  }

  if (btnOpenQrScanner) btnOpenQrScanner.addEventListener("click", openScannerModal);
  if (btnCloseScannerModal) btnCloseScannerModal.addEventListener("click", closeScannerModal);

  if (btnUploadQrImage && qrFileInput) {
    btnUploadQrImage.addEventListener("click", () => qrFileInput.click());
    qrFileInput.addEventListener("change", (e) => {
      const file = e.target.files && e.target.files[0];
      if (file && window.AanoQrScanner) {
        if (scannerStatus) scannerStatus.innerText = "Analyzing image...";
        window.AanoQrScanner.decodeImageFile(file, (val) => {
          applyScannedDropResult(val);
        }, (err) => {
          if (scannerStatus) scannerStatus.innerText = `Could not decode QR: ${err.message || "Unknown error"}`;
        });
      }
    });
  }

  if (btnPasteQrCode) {
    btnPasteQrCode.addEventListener("click", async () => {
      let text = "";
      try {
        text = await navigator.clipboard.readText();
      } catch (err) {
        console.debug("Clipboard read notice:", err);
      }
      if (!text) {
        text = prompt("Paste Phone's AANO Drop URI or IP:PORT (e.g. aano://drop?ip=...):", "");
      }
      if (text && text.trim()) {
        applyScannedDropResult(text.trim());
      }
    });
  }

  const btnBatchDrop = document.getElementById("btnBatchDrop");
  if (btnBatchDrop) {
    btnBatchDrop.addEventListener("click", async () => {
      const selectedIds = Array.from(selectedNoteIds);
      if (selectedIds.length === 0) {
        showNativeAlert({ title: "No Notes Selected", message: "Please select at least one note to drop." });
        return;
      }
      const api = getApi();
      if (!api) return;

      if (activePairedPeer && activePairedPeer.ip) {
        const origText = btnBatchDrop.textContent;
        btnBatchDrop.textContent = `Dropping ${selectedIds.length}...`;
        try {
          const res = await api.send_drop_to_device(activePairedPeer.ip, activePairedPeer.port || 8766, selectedIds);
          if (res && res.success) {
            showNativeAlert({ title: "Drop Complete", message: `Successfully transferred ${res.count || selectedIds.length} notes to ${activePairedPeer.name || "Phone"}!` });
            exitSelectionMode();
          } else {
            showNativeAlert({ title: "Drop Failed", message: res?.error || "Could not reach paired device." });
          }
        } catch (e) {
          showNativeAlert({ title: "Error", message: String(e) });
        } finally {
          btnBatchDrop.textContent = origText;
        }
      } else {
        openDropModal();
      }
    });
  }

  const btnSendDropToPhone = document.getElementById("btnSendDropToPhone");
  if (btnSendDropToPhone) {
    btnSendDropToPhone.addEventListener("click", async () => {
      const ipInput = document.getElementById("targetPhoneIp");
      const pinInput = document.getElementById("targetPhonePin");
      const targetIp = ipInput ? ipInput.value.trim() : "";
      const targetPin = pinInput ? pinInput.value.trim() : null;

      if (!targetIp) {
        showNativeAlert({ title: "Target IP Required", message: "Please enter target IP shown on AANO Android or PC." });
        return;
      }

      // Collect checked note IDs
      const checkedBoxes = document.querySelectorAll(".drop-note-chk:checked");
      const noteIdsToSend = Array.from(checkedBoxes).map(cb => cb.value);

      if (noteIdsToSend.length === 0) {
        showNativeAlert({ title: "No Notes Selected", message: "Please select at least one note to drop from the list." });
        return;
      }

      const api = getApi();
      if (!api || !api.send_drop_to_device) return;
      btnSendDropToPhone.innerText = `Sending ${noteIdsToSend.length} Note(s)...`;
      try {
        const res = await api.send_drop_to_device(targetIp, 8766, noteIdsToSend, targetPin);
        if (res && res.success) {
          await checkDropPeerStatus();
          showNativeAlert({ title: "Drop Complete", message: `Successfully transferred ${res.count || 0} notes to phone!` });
          closeDropModal();
        } else {
          showNativeAlert({ title: "Drop Failed", message: res?.error || "Could not reach phone on Wi-Fi." });
        }
      } catch (err) {
        showNativeAlert({ title: "Error", message: String(err) });
      } finally {
        btnSendDropToPhone.innerText = "⚡ Send Notes to Phone";
      }
    });
  }

}
