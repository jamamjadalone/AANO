// AANO Windows Desktop App - Media Vault & Attached Images Engine
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

// --------------------- Media Vault & Image Processing ---------------------
async function loadAttachedImages(imageFilenames) {
  currentAttachedImages = [];
  const api = getApi();
  if (!api || !imageFilenames || !imageFilenames.length) {
    renderAttachedImages();
    return;
  }
  for (const fn of imageFilenames) {
    try {
      const cleanFn = fn.split("/").pop().split("\\").pop();
      const res = await api.get_image_data_url(cleanFn);
      if (res && res.success && res.dataUrl) {
        currentAttachedImages.push({ filename: cleanFn, dataUrl: res.dataUrl });
      }
    } catch (e) {
      console.warn("Failed to load image data URL:", fn, e);
    }
  }
  renderAttachedImages();
}

function renderAttachedImages() {
  const container = document.getElementById("editorImagesContainer");
  if (!container) return;
  if (!currentAttachedImages || currentAttachedImages.length === 0) {
    container.classList.add("hidden");
    container.replaceChildren();
    return;
  }
  container.classList.remove("hidden");
  container.replaceChildren();

  currentAttachedImages.forEach((img, idx) => {
    const thumb = document.createElement("div");
    thumb.className = "editor-image-thumb";
    thumb.title = img.filename || "Attached Image";

    const imgEl = document.createElement("img");
    imgEl.src = img.dataUrl;
    imgEl.alt = "Attachment";

    const delBtn = document.createElement("button");
    delBtn.type = "button";
    delBtn.className = "thumb-del-btn";
    delBtn.title = "Remove";
    delBtn.innerText = "✕";
    delBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      currentAttachedImages.splice(idx, 1);
      renderAttachedImages();
    });

    thumb.appendChild(imgEl);
    thumb.appendChild(delBtn);
    thumb.addEventListener("click", () => {
      openImagePreview(idx);
    });
    container.appendChild(thumb);
  });
}

async function handleAttachImage() {
  const api = getApi();
  if (!api || !api.attach_images) return;
  try {
    const res = await api.attach_images(isHdAttachmentQuality);
    if (res && res.success && res.images && res.images.length) {
      for (const item of res.images) {
        currentAttachedImages.push({
          filename: item.filename,
          dataUrl: item.dataUrl
        });
      }
      renderAttachedImages();
    }
  } catch (err) {
    console.warn("Attach image failed:", err);
  }
}

function openImagePreview(index) {
  if (index < 0 || index >= currentAttachedImages.length) return;
  currentPreviewIndex = index;
  const modal = document.getElementById("imagePreviewModal");
  const imgEl = document.getElementById("previewModalImg");
  const nameEl = document.getElementById("previewImageName");
  if (!modal || !imgEl) return;

  const item = currentAttachedImages[index];
  imgEl.src = item.dataUrl;
  if (nameEl) nameEl.innerText = item.filename || "Image Preview";
  modal.classList.remove("hidden");
}

function closeImagePreview() {
  const modal = document.getElementById("imagePreviewModal");
  if (modal) modal.classList.add("hidden");
  currentPreviewIndex = -1;
}

function setupMediaListeners() {
  // Image Attachment & Quality Toggle
  const btnToggleQuality = document.getElementById("btnToggleQualityDesktop");
  if (btnToggleQuality) {
    btnToggleQuality.addEventListener("click", () => {
      isHdAttachmentQuality = !isHdAttachmentQuality;
      if (isHdAttachmentQuality) {
        btnToggleQuality.classList.add("hd-mode");
        btnToggleQuality.innerText = "HD";
      } else {
        btnToggleQuality.classList.remove("hd-mode");
        btnToggleQuality.innerText = "STD";
      }
    });
  }

  const btnAttachImage = document.getElementById("btnAttachImage");
  if (btnAttachImage) {
    btnAttachImage.addEventListener("click", handleAttachImage);
  }

  // Image Preview Modal Listeners
  const btnPreviewClose = document.getElementById("btnPreviewClose");
  if (btnPreviewClose) btnPreviewClose.addEventListener("click", closeImagePreview);

  const btnPreviewSave = document.getElementById("btnPreviewSave");
  if (btnPreviewSave) {
    btnPreviewSave.addEventListener("click", async () => {
      if (currentPreviewIndex >= 0 && currentPreviewIndex < currentAttachedImages.length) {
        const item = currentAttachedImages[currentPreviewIndex];
        const api = getApi();
        if (api && api.save_image_to_disk) {
          const res = await api.save_image_to_disk(item.filename);
          if (res && res.success && res.path) {
            showNativeAlert({ title: "Saved", message: `Image exported to: ${res.path}` });
          }
        }
      }
    });
  }

  const btnPreviewDelete = document.getElementById("btnPreviewDelete");
  if (btnPreviewDelete) {
    btnPreviewDelete.addEventListener("click", async () => {
      if (currentPreviewIndex >= 0 && currentPreviewIndex < currentAttachedImages.length) {
        const item = currentAttachedImages[currentPreviewIndex];
        currentAttachedImages.splice(currentPreviewIndex, 1);
        renderAttachedImages();
        closeImagePreview();
        const api = getApi();
        if (api && api.delete_image_file) {
          api.delete_image_file(item.filename).catch(err => console.debug("Image file deletion notice:", err));
        }
      }
    });
  }

  const btnPreviewCrop = document.getElementById("btnPreviewCrop");
  if (btnPreviewCrop) {
    btnPreviewCrop.addEventListener("click", openCropModal);
  }

}
