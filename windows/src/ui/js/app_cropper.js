// AANO Windows Desktop App - Interactive Image Cropper & Canvas Engine
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).


function openCropModal() {
  if (currentPreviewIndex < 0 || currentPreviewIndex >= currentAttachedImages.length) return;
  const item = currentAttachedImages[currentPreviewIndex];
  closeImagePreview();

  const modal = document.getElementById("imageCropModal");
  const canvas = document.getElementById("cropCanvas");
  if (!modal || !canvas) return;

  cropRotation = 0;
  cropAspectRatio = "free";
  document.querySelectorAll(".btn-ratio").forEach(b => {
    b.classList.toggle("active", b.dataset.ratio === "free");
  });

  cropImageObj = new Image();
  cropImageObj.onload = () => {
    initCropCanvas();
    modal.classList.remove("hidden");
  };
  cropImageObj.src = item.dataUrl;
}

function closeCropModal() {
  const modal = document.getElementById("imageCropModal");
  if (modal) modal.classList.add("hidden");
  cropImageObj = null;
}

function initCropCanvas() {
  const canvas = document.getElementById("cropCanvas");
  if (!canvas || !cropImageObj) return;

  const isRotated90or270 = (cropRotation % 180 !== 0);
  const srcW = isRotated90or270 ? cropImageObj.height : cropImageObj.width;
  const srcH = isRotated90or270 ? cropImageObj.width : cropImageObj.height;

  const maxW = Math.min(window.innerWidth * 0.7, 700);
  const maxH = Math.min(window.innerHeight * 0.55, 480);
  let scale = 1;
  if (srcW > maxW || srcH > maxH) {
    scale = Math.min(maxW / srcW, maxH / srcH);
  }
  canvas.width = Math.max(100, Math.round(srcW * scale));
  canvas.height = Math.max(100, Math.round(srcH * scale));

  const insetX = canvas.width * 0.08;
  const insetY = canvas.height * 0.08;
  cropBox = {
    x: insetX,
    y: insetY,
    w: canvas.width - (insetX * 2),
    h: canvas.height - (insetY * 2)
  };
  applyAspectRatioToCropBox();
  drawCropCanvas();
}

function applyAspectRatioToCropBox() {
  if (!cropBox || cropAspectRatio === "free") return;
  const canvas = document.getElementById("cropCanvas");
  if (!canvas) return;

  let targetRatio = 1;
  if (cropAspectRatio === "1:1") targetRatio = 1;
  else if (cropAspectRatio === "4:3") targetRatio = 4 / 3;
  else if (cropAspectRatio === "16:9") targetRatio = 16 / 9;

  let newH = cropBox.w / targetRatio;
  if (newH > canvas.height - cropBox.y) {
    newH = canvas.height - cropBox.y;
    cropBox.w = newH * targetRatio;
  }
  cropBox.h = newH;
}

function drawCropCanvas() {
  const canvas = document.getElementById("cropCanvas");
  if (!canvas || !cropImageObj || !cropBox) return;
  const ctx = canvas.getContext("2d");

  ctx.clearRect(0, 0, canvas.width, canvas.height);

  ctx.save();
  ctx.translate(canvas.width / 2, canvas.height / 2);
  ctx.rotate((cropRotation * Math.PI) / 180);
  const isRotated90or270 = (cropRotation % 180 !== 0);
  const drawW = isRotated90or270 ? canvas.height : canvas.width;
  const drawH = isRotated90or270 ? canvas.width : canvas.height;
  ctx.drawImage(cropImageObj, -drawW / 2, -drawH / 2, drawW, drawH);
  ctx.restore();

  ctx.fillStyle = "rgba(0, 0, 0, 0.55)";
  ctx.fillRect(0, 0, canvas.width, cropBox.y);
  ctx.fillRect(0, cropBox.y + cropBox.h, canvas.width, canvas.height - (cropBox.y + cropBox.h));
  ctx.fillRect(0, cropBox.y, cropBox.x, cropBox.h);
  ctx.fillRect(cropBox.x + cropBox.w, cropBox.y, canvas.width - (cropBox.x + cropBox.w), cropBox.h);

  ctx.strokeStyle = "#00897B";
  ctx.lineWidth = 2;
  ctx.strokeRect(cropBox.x, cropBox.y, cropBox.w, cropBox.h);

  ctx.strokeStyle = "rgba(255, 255, 255, 0.35)";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(cropBox.x + cropBox.w / 3, cropBox.y);
  ctx.lineTo(cropBox.x + cropBox.w / 3, cropBox.y + cropBox.h);
  ctx.moveTo(cropBox.x + (cropBox.w * 2) / 3, cropBox.y);
  ctx.lineTo(cropBox.x + (cropBox.w * 2) / 3, cropBox.y + cropBox.h);
  ctx.moveTo(cropBox.x, cropBox.y + cropBox.h / 3);
  ctx.lineTo(cropBox.x + cropBox.w, cropBox.y + cropBox.h / 3);
  ctx.moveTo(cropBox.x, cropBox.y + (cropBox.h * 2) / 3);
  ctx.lineTo(cropBox.x + cropBox.w, cropBox.y + (cropBox.h * 2) / 3);
  ctx.stroke();

  const handleSize = 8;
  ctx.fillStyle = "#FFFFFF";
  ctx.fillRect(cropBox.x - handleSize / 2, cropBox.y - handleSize / 2, handleSize, handleSize);
  ctx.fillRect(cropBox.x + cropBox.w - handleSize / 2, cropBox.y - handleSize / 2, handleSize, handleSize);
  ctx.fillRect(cropBox.x - handleSize / 2, cropBox.y + cropBox.h - handleSize / 2, handleSize, handleSize);
  ctx.fillRect(cropBox.x + cropBox.w - handleSize / 2, cropBox.y + cropBox.h - handleSize / 2, handleSize, handleSize);
}

function setupCropCanvasEvents() {
  const canvas = document.getElementById("cropCanvas");
  if (!canvas) return;

  canvas.addEventListener("mousedown", (e) => {
    if (!cropBox) return;
    const rect = canvas.getBoundingClientRect();
    const mx = e.clientX - rect.left;
    const my = e.clientY - rect.top;

    isDraggingCrop = true;
    cropDragStart = { x: mx, y: my };

    if (mx >= cropBox.x && mx <= cropBox.x + cropBox.w && my >= cropBox.y && my <= cropBox.y + cropBox.h) {
      cropDragMode = "move";
    } else {
      cropDragMode = "new";
      cropBox.x = mx;
      cropBox.y = my;
      cropBox.w = 10;
      cropBox.h = 10;
    }
  });

  window.addEventListener("mousemove", (e) => {
    if (!isDraggingCrop || !cropBox) return;
    const canvas = document.getElementById("cropCanvas");
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const mx = Math.max(0, Math.min(canvas.width, e.clientX - rect.left));
    const my = Math.max(0, Math.min(canvas.height, e.clientY - rect.top));

    const dx = mx - cropDragStart.x;
    const dy = my - cropDragStart.y;
    cropDragStart = { x: mx, y: my };

    if (cropDragMode === "move") {
      cropBox.x = Math.max(0, Math.min(canvas.width - cropBox.w, cropBox.x + dx));
      cropBox.y = Math.max(0, Math.min(canvas.height - cropBox.h, cropBox.y + dy));
    } else {
      cropBox.w = Math.max(30, mx - cropBox.x);
      cropBox.h = Math.max(30, my - cropBox.y);
      applyAspectRatioToCropBox();
    }
    drawCropCanvas();
  });

  window.addEventListener("mouseup", () => {
    isDraggingCrop = false;
  });
}

async function handleApplyCrop() {
  if (currentPreviewIndex < 0 || !cropImageObj || !cropBox) return;
  const canvas = document.getElementById("cropCanvas");
  if (!canvas) return;

  const isRotated90or270 = (cropRotation % 180 !== 0);
  const srcW = isRotated90or270 ? cropImageObj.height : cropImageObj.width;
  const srcH = isRotated90or270 ? cropImageObj.width : cropImageObj.height;

  const scaleX = srcW / canvas.width;
  const scaleY = srcH / canvas.height;

  const cropX = Math.round(cropBox.x * scaleX);
  const cropY = Math.round(cropBox.y * scaleY);
  const cropW = Math.round(cropBox.w * scaleX);
  const cropH = Math.round(cropBox.h * scaleY);

  const api = getApi();
  if (!api || !api.crop_image) return;

  const item = currentAttachedImages[currentPreviewIndex];
  try {
    const res = await api.crop_image(item.filename, [cropX, cropY, cropX + cropW, cropY + cropH], cropRotation, isHdAttachmentQuality);
    if (res && res.success) {
      currentAttachedImages[currentPreviewIndex] = {
        filename: res.filename,
        dataUrl: res.dataUrl
      };
      renderAttachedImages();
      closeCropModal();
      openImagePreview(currentPreviewIndex);
    }
  } catch (e) {
    console.warn("Apply crop failed:", e);
  }
}

function setupCropperListeners() {
  // Image Crop Modal Listeners
  const btnCropClose = document.getElementById("btnCropClose");
  if (btnCropClose) btnCropClose.addEventListener("click", closeCropModal);
  const btnCropCancel = document.getElementById("btnCropCancel");
  if (btnCropCancel) btnCropCancel.addEventListener("click", () => {
    closeCropModal();
    if (currentPreviewIndex >= 0) openImagePreview(currentPreviewIndex);
  });
  const btnCropRotate = document.getElementById("btnCropRotate");
  if (btnCropRotate) {
    btnCropRotate.addEventListener("click", () => {
      cropRotation = (cropRotation + 90) % 360;
      initCropCanvas();
    });
  }
  const btnCropApply = document.getElementById("btnCropApply");
  if (btnCropApply) btnCropApply.addEventListener("click", handleApplyCrop);

  document.querySelectorAll(".btn-ratio").forEach(btn => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".btn-ratio").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      cropAspectRatio = btn.dataset.ratio || "free";
      applyAspectRatioToCropBox();
      drawCropCanvas();
    });
  });

  setupCropCanvasEvents();


}
