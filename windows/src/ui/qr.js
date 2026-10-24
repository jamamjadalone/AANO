// Lightweight, pure-JS QR Code generator for HTML5 Canvas
// Zero external dependencies. Designed for AANO Drop.
// Based on Kazuhiko Arase's QR Code generator (MIT License).
(function(global) {
  function QR8bitByte(data) {
    this.mode = 4; // 8bit byte
    this.data = data;
  }
  QR8bitByte.prototype = {
    getLength: function() { return this.data.length; },
    write: function(buffer) {
      for (var i = 0; i < this.data.length; i++) {
        buffer.put(this.data.charCodeAt(i), 8);
      }
    }
  };

  function QRCode(typeNumber, errorCorrectLevel) {
    this.typeNumber = typeNumber || 4;
    this.errorCorrectLevel = errorCorrectLevel || 0; // M
    this.modules = null;
    this.moduleCount = 0;
    this.dataList = [];
  }
  QRCode.prototype = {
    addData: function(data) {
      this.dataList.push(new QR8bitByte(data));
    },
    isDark: function(row, col) {
      return this.modules[row][col];
    },
    getModuleCount: function() {
      return this.moduleCount;
    },
    make: function() {
      // Auto select typeNumber from 3 to 10
      for (var t = 3; t <= 10; t++) {
        try {
          this.typeNumber = t;
          this.makeImpl(false, 0);
          return;
        } catch (e) {
          if (t === 10) throw e;
        }
      }
    },
    makeImpl: function(test, maskPattern) {
      this.moduleCount = this.typeNumber * 4 + 17;
      this.modules = new Array(this.moduleCount);
      for (var row = 0; row < this.moduleCount; row++) {
        this.modules[row] = new Array(this.moduleCount);
        for (var col = 0; col < this.moduleCount; col++) {
          this.modules[row][col] = null;
        }
      }
      this.setupPositionProbePattern(0, 0);
      this.setupPositionProbePattern(this.moduleCount - 7, 0);
      this.setupPositionProbePattern(0, this.moduleCount - 7);
      this.setupTimingPattern();
      this.setupTypeInfo(test, maskPattern);
      this.mapData(this.createData(), maskPattern);
    },
    setupPositionProbePattern: function(row, col) {
      for (var r = -1; r <= 7; r++) {
        if (row + r <= -1 || this.moduleCount <= row + r) continue;
        for (var c = -1; c <= 7; c++) {
          if (col + c <= -1 || this.moduleCount <= col + c) continue;
          if ((0 <= r && r <= 6 && (c == 0 || c == 6)) ||
              (0 <= c && c <= 6 && (r == 0 || r == 6)) ||
              (2 <= r && r <= 4 && 2 <= c && c <= 4)) {
            this.modules[row + r][col + c] = true;
          } else {
            this.modules[row + r][col + c] = false;
          }
        }
      }
    },
    setupTimingPattern: function() {
      for (var r = 8; r < this.moduleCount - 8; r++) {
        if (this.modules[r][6] !== null) continue;
        this.modules[r][6] = (r % 2 == 0);
      }
      for (var c = 8; c < this.moduleCount - 8; c++) {
        if (this.modules[6][c] !== null) continue;
        this.modules[6][c] = (c % 2 == 0);
      }
    },
    setupTypeInfo: function(test, maskPattern) {
      var data = (0 << 3) | maskPattern; // Error level M
      var d = data;
      for (var i = 0; i < 10; i++) {
        d = (d << 1) ^ ((d >>> 9) * 0x537);
      }
      var bits = ((data << 10) | d) ^ 0x5412;
      for (var i = 0; i < 15; i++) {
        var mod = !test && ((bits >> i) & 1) == 1;
        if (i < 6) this.modules[i][8] = mod;
        else if (i < 8) this.modules[i + 1][8] = mod;
        else this.modules[this.moduleCount - 15 + i][8] = mod;

        if (i < 8) this.modules[8][this.moduleCount - i - 1] = mod;
        else if (i < 9) this.modules[8][15 - i - 1 + 1] = mod;
        else this.modules[8][15 - i - 1] = mod;
      }
      this.modules[this.moduleCount - 8][8] = !test;
    },
    mapData: function(data, maskPattern) {
      var inc = -1;
      var row = this.moduleCount - 1;
      var bitIndex = 7;
      var byteIndex = 0;
      for (var col = this.moduleCount - 1; col > 0; col -= 2) {
        if (col == 6) col--;
        while (true) {
          for (var c = 0; c < 2; c++) {
            if (this.modules[row][col - c] === null) {
              var dark = false;
              if (byteIndex < data.length) {
                dark = (((data[byteIndex] >>> bitIndex) & 1) == 1);
              }
              var mask = (row + (col - c)) % 2 == 0;
              if (mask) dark = !dark;
              this.modules[row][col - c] = dark;
              bitIndex--;
              if (bitIndex == -1) {
                byteIndex++;
                bitIndex = 7;
              }
            }
          }
          row += inc;
          if (row < 0 || this.moduleCount <= row) {
            row -= inc;
            inc = -inc;
            break;
          }
        }
      }
    },
    createData: function() {
      var buffer = new QRBitBuffer();
      for (var i = 0; i < this.dataList.length; i++) {
        var data = this.dataList[i];
        buffer.put(data.mode, 4);
        buffer.put(data.getLength(), this.typeNumber < 10 ? 8 : 16);
        data.write(buffer);
      }
      var totalDataCount = this.getTotalDataCodewords();
      if (buffer.getLengthInBits() > totalDataCount * 8) {
        throw new Error("Data overflow: " + buffer.getLengthInBits() + " > " + (totalDataCount * 8));
      }
      buffer.put(0, Math.min(4, totalDataCount * 8 - buffer.getLengthInBits()));
      while (buffer.getLengthInBits() % 8 != 0) {
        buffer.putBit(false);
      }
      while (buffer.getLengthInBits() < totalDataCount * 8) {
        buffer.put(0xec, 8);
        if (buffer.getLengthInBits() < totalDataCount * 8) buffer.put(0x11, 8);
      }
      return this.createBytes(buffer);
    },
    getTotalDataCodewords: function() {
      // Medium ECC capacities for versions 3..10
      var caps = [0, 16, 28, 44, 64, 86, 108, 124, 154, 182, 216];
      return caps[this.typeNumber] || 44;
    },
    createBytes: function(buffer) {
      var offset = 0;
      var dataBytes = new Array(buffer.buffer.length);
      for (var i = 0; i < buffer.buffer.length; i++) dataBytes[i] = buffer.buffer[i];
      // Generate standard RS ECC
      var ecCount = [0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26][this.typeNumber] || 26;
      var rsPoly = this.getErrorCorrectPolynomial(ecCount);
      var rawPoly = [];
      for (var i = 0; i < dataBytes.length; i++) rawPoly.push(dataBytes[i]);
      for (var i = 0; i < ecCount; i++) rawPoly.push(0);
      
      var modPoly = this.polyMod(rawPoly, rsPoly);
      var result = [];
      for (var i = 0; i < dataBytes.length; i++) result.push(dataBytes[i]);
      for (var i = 0; i < ecCount; i++) result.push(modPoly[i] || 0);
      return result;
    },
    getErrorCorrectPolynomial: function(ecCount) {
      var poly = [1];
      for (var i = 0; i < ecCount; i++) {
        poly = this.polyMultiply(poly, [1, this.gexp(i)]);
      }
      return poly;
    },
    polyMultiply: function(p1, p2) {
      var num = new Array(p1.length + p2.length - 1);
      for (var i = 0; i < num.length; i++) num[i] = 0;
      for (var i = 0; i < p1.length; i++) {
        for (var j = 0; j < p2.length; j++) {
          num[i + j] ^= this.gmult(p1[i], p2[j]);
        }
      }
      return num;
    },
    polyMod: function(p1, p2) {
      var num = p1.slice();
      while (num.length >= p2.length) {
        var lead = num[0];
        if (lead !== 0) {
          for (var i = 0; i < p2.length; i++) {
            num[i] ^= this.gmult(p2[i], lead);
          }
        }
        num.shift();
      }
      return num;
    },
    gexp: function(n) {
      var EXP_TABLE = new Array(256);
      var LOG_TABLE = new Array(256);
      for (var i = 0; i < 8; i++) EXP_TABLE[i] = 1 << i;
      for (var i = 8; i < 256; i++) EXP_TABLE[i] = EXP_TABLE[i - 4] ^ EXP_TABLE[i - 5] ^ EXP_TABLE[i - 6] ^ EXP_TABLE[i - 8];
      return EXP_TABLE[n % 255];
    },
    gmult: function(x, y) {
      if (x === 0 || y === 0) return 0;
      var EXP_TABLE = new Array(512);
      var LOG_TABLE = new Array(256);
      for (var i = 0; i < 8; i++) EXP_TABLE[i] = 1 << i;
      for (var i = 8; i < 512; i++) EXP_TABLE[i] = EXP_TABLE[i - 4] ^ EXP_TABLE[i - 5] ^ EXP_TABLE[i - 6] ^ EXP_TABLE[i - 8];
      for (var i = 0; i < 255; i++) LOG_TABLE[EXP_TABLE[i]] = i;
      return EXP_TABLE[LOG_TABLE[x] + LOG_TABLE[y]];
    }
  };

  function QRBitBuffer() {
    this.buffer = [];
    this.length = 0;
  }
  QRBitBuffer.prototype = {
    get: function(index) {
      var bufIndex = Math.floor(index / 8);
      return ((this.buffer[bufIndex] >>> (7 - index % 8)) & 1) == 1;
    },
    put: function(num, length) {
      for (var i = 0; i < length; i++) {
        this.putBit(((num >>> (length - i - 1)) & 1) == 1);
      }
    },
    getLengthInBits: function() {
      return this.length;
    },
    putBit: function(bit) {
      var bufIndex = Math.floor(this.length / 8);
      if (this.buffer.length <= bufIndex) this.buffer.push(0);
      if (bit) this.buffer[bufIndex] |= (0x80 >>> (this.length % 8));
      this.length++;
    }
  };

  // Helper function to draw rounded rectangles
  function drawRoundedRect(ctx, x, y, w, h, r) {
    if (w < 2 * r) r = w / 2;
    if (h < 2 * r) r = h / 2;
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  // Draw modern positioning finder pattern (7x7 module eye)
  function drawModernFinderPattern(ctx, x, y, cellSize, darkColor, lightColor) {
    var size = 7 * cellSize;
    var outerRadius = cellSize * 1.8;
    var innerRadius = cellSize * 1.2;
    var eyeRadius = cellSize * 0.9;

    // Outer 7x7 rounded box
    ctx.fillStyle = darkColor;
    drawRoundedRect(ctx, x, y, size, size, outerRadius);
    ctx.fill();

    // 5x5 cutout
    ctx.fillStyle = lightColor;
    drawRoundedRect(ctx, x + cellSize, y + cellSize, 5 * cellSize, 5 * cellSize, innerRadius);
    ctx.fill();

    // 3x3 inner eye
    ctx.fillStyle = darkColor;
    drawRoundedRect(ctx, x + 2 * cellSize, y + 2 * cellSize, 3 * cellSize, 3 * cellSize, eyeRadius);
    ctx.fill();
  }

  // Modern Retina-Ready QR Code Renderer with Rounded Modules & Center Lightning Emblem
  global.renderQrToCanvas = function(canvasId, text, opts) {
    opts = opts || {};
    var canvas = typeof canvasId === "string" ? document.getElementById(canvasId) : canvasId;
    if (!canvas) return;

    var displaySize = opts.size || parseInt(canvas.getAttribute("width"), 10) || 220;

    // 1. Prioritize official standard QRCode.js if available in page
    if (typeof global.QRCode !== "undefined") {
      try {
        var tempDiv = document.createElement("div");
        new global.QRCode(tempDiv, {
          text: text,
          width: displaySize,
          height: displaySize,
          correctLevel: global.QRCode.CorrectLevel.M
        });
        var srcCanvas = tempDiv.querySelector("canvas");
        if (srcCanvas) {
          canvas.width = displaySize;
          canvas.height = displaySize;
          canvas.style.width = displaySize + "px";
          canvas.style.height = displaySize + "px";
          var ctx = canvas.getContext("2d");
          ctx.drawImage(srcCanvas, 0, 0, displaySize, displaySize);
          return;
        }
      } catch (e) {
        console.warn("Standard QRCode.js render error:", e);
      }
    }

    try {
      var qr = new QRCode(4, 0); // Error level M (15% redundancy)
      qr.addData(text);
      qr.make();
      var count = qr.getModuleCount();
      var ctx = canvas.getContext("2d");

      // High-DPI Display Support (2x Super-Sampling)
      var displaySize = opts.size || parseInt(canvas.getAttribute("width"), 10) || 160;
      var dpr = 2; // Fixed 2x retina crispness
      canvas.width = displaySize * dpr;
      canvas.height = displaySize * dpr;
      canvas.style.width = displaySize + "px";
      canvas.style.height = displaySize + "px";

      ctx.save();
      ctx.scale(dpr, dpr);

      var size = displaySize;
      var paddingCells = 2;
      var cellSize = Math.floor(size / (count + paddingCells * 2));
      var offset = Math.floor((size - (cellSize * count)) / 2);

      // Colors
      var darkColor = opts.darkColor || "#0f172a";      // Deep Obsidian
      var accentColor = opts.accentColor || "#0284c7";  // Vibrant AANO Cyan/Sky
      var bgColor = opts.bgColor || "#ffffff";          // Pure Crisp White

      // 1. Draw rounded canvas background
      ctx.fillStyle = bgColor;
      drawRoundedRect(ctx, 0, 0, size, size, 12);
      ctx.fill();

      // 2. Identify finder patterns
      var isFinder = function(r, c) {
        if (r < 7 && c < 7) return true;                     // Top-Left
        if (r < 7 && c >= count - 7) return true;             // Top-Right
        if (r >= count - 7 && c < 7) return true;             // Bottom-Left
        return false;
      };

      // 3. Draw the 3 modern finder patterns
      drawModernFinderPattern(ctx, offset, offset, cellSize, darkColor, bgColor);
      drawModernFinderPattern(ctx, offset + (count - 7) * cellSize, offset, cellSize, darkColor, bgColor);
      drawModernFinderPattern(ctx, offset, offset + (count - 7) * cellSize, cellSize, darkColor, bgColor);

      // 4. Center badge exclusion zone (Radius in module units)
      var centerRow = count / 2;
      var centerCol = count / 2;
      var badgeModuleRadius = 2.9;

      // 5. Draw data modules as sleek rounded dots
      ctx.fillStyle = darkColor;
      for (var r = 0; r < count; r++) {
        for (var c = 0; c < count; c++) {
          if (isFinder(r, c)) continue;

          // Check if inside center badge zone
          var dist = Math.hypot(r - centerRow + 0.5, c - centerCol + 0.5);
          if (dist < badgeModuleRadius) continue;

          if (qr.isDark(r, c)) {
            var cx = offset + c * cellSize + cellSize / 2;
            var cy = offset + r * cellSize + cellSize / 2;
            var dotRadius = cellSize * 0.44;

            ctx.beginPath();
            ctx.arc(cx, cy, dotRadius, 0, Math.PI * 2);
            ctx.fill();
          }
        }
      }

      // 6. Draw Modern Center AANO Lightning Emblem
      var centerX = size / 2;
      var centerY = size / 2;
      var badgePixelRadius = (badgeModuleRadius - 0.2) * cellSize;

      // Outer badge circle
      ctx.beginPath();
      ctx.arc(centerX, centerY, badgePixelRadius, 0, Math.PI * 2);
      ctx.fillStyle = bgColor;
      ctx.shadowColor = "rgba(0, 0, 0, 0.16)";
      ctx.shadowBlur = 6;
      ctx.shadowOffsetX = 0;
      ctx.shadowOffsetY = 2;
      ctx.fill();

      // Reset shadow for crisp border
      ctx.shadowColor = "transparent";
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = "rgba(2, 132, 199, 0.35)"; // Subtle cyan rim
      ctx.stroke();

      // Draw AANO ⚡ Lightning Bolt inside badge
      var boltScale = badgePixelRadius * 0.72;
      ctx.beginPath();
      ctx.moveTo(centerX + boltScale * 0.18, centerY - boltScale * 0.95);
      ctx.lineTo(centerX - boltScale * 0.62, centerY + boltScale * 0.05);
      ctx.lineTo(centerX - boltScale * 0.04, centerY + boltScale * 0.05);
      ctx.lineTo(centerX - boltScale * 0.18, centerY + boltScale * 0.95);
      ctx.lineTo(centerX + boltScale * 0.62, centerY - boltScale * 0.05);
      ctx.lineTo(centerX + boltScale * 0.04, centerY - boltScale * 0.05);
      ctx.closePath();

      var gradient = ctx.createLinearGradient(centerX - boltScale, centerY - boltScale, centerX + boltScale, centerY + boltScale);
      gradient.addColorStop(0, "#0284c7");
      gradient.addColorStop(1, "#06b6d4");
      ctx.fillStyle = gradient;
      ctx.fill();

      ctx.restore();
    } catch (e) {
      console.warn("Modern QR render error:", e);
    }
  };

  // -------------------------------------------------------------
  // AANO Drop Real-Time Scanner Engine (Camera, Image & Code Decoder)
  // -------------------------------------------------------------
  global.AanoQrScanner = {
    _stream: null,
    _animId: null,
    _detector: null,

    // Check if system supports hardware barcode scanning
    hasBarcodeDetector: function() {
      return "BarcodeDetector" in window;
    },

    // Get detector instance
    getDetector: function() {
      if (!this._detector && this.hasBarcodeDetector()) {
        try {
          this._detector = new window.BarcodeDetector({ formats: ["qr_code"] });
        } catch (e) {
          console.warn("BarcodeDetector init failed:", e);
        }
      }
      return this._detector;
    },

    // Parse aano://drop or raw URI
    parseDropUri: function(text) {
      if (!text || typeof text !== "string") return null;
      var clean = text.trim();
      var ip = "", port = "8765", pin = "";

      if (clean.startsWith("aano://drop")) {
        try {
          var url = new URL(clean);
          ip = url.searchParams.get("ip") || "";
          port = url.searchParams.get("port") || "8765";
          pin = url.searchParams.get("pin") || "";
        } catch (e) {
          // Fallback regex parsing
          var ipMatch = clean.match(/ip=([0-9a-zA-Z.:]+)/);
          var portMatch = clean.match(/port=([0-9]+)/);
          var pinMatch = clean.match(/pin=([0-9a-zA-Z]+)/);
          if (ipMatch) ip = ipMatch[1];
          if (portMatch) port = portMatch[1];
          if (pinMatch) pin = pinMatch[1];
        }
      } else if (clean.includes(":") && clean.split(":").length >= 2) {
        var parts = clean.split(":");
        ip = parts[0];
        port = parts[1];
        if (parts.length > 2) pin = parts[2];
      }

      if (ip) {
        return { valid: true, ip: ip, port: port, pin: pin, raw: clean };
      }
      return null;
    },

    // Start live webcam scanner
    startCamera: async function(videoEl, onDetected, onError) {
      this.stopCamera();
      var self = this;

      try {
        var constraints = {
          video: {
            facingMode: { ideal: "environment" },
            width: { ideal: 640 },
            height: { ideal: 480 }
          },
          audio: false
        };

        this._stream = await navigator.mediaDevices.getUserMedia(constraints);
        videoEl.srcObject = this._stream;
        await videoEl.play();

        var detector = this.getDetector();
        var canvas = document.createElement("canvas");
        var ctx = canvas.getContext("2d", { willReadFrequently: true });

        var scanFrame = async function() {
          if (!self._stream || videoEl.paused || videoEl.ended) return;

          try {
            if (videoEl.videoWidth > 0 && videoEl.videoHeight > 0) {
              // 1. Primary: Use standalone pure-JS jsQR decoder (Universal, zero browser flag requirement)
              if (window.jsQR) {
                if (canvas.width !== videoEl.videoWidth || canvas.height !== videoEl.videoHeight) {
                  canvas.width = videoEl.videoWidth;
                  canvas.height = videoEl.videoHeight;
                }
                ctx.drawImage(videoEl, 0, 0, canvas.width, canvas.height);
                var imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);
                var code = window.jsQR(imgData.data, imgData.width, imgData.height, {
                  inversionAttempts: "dontInvert"
                });
                if (code && code.data) {
                  self.stopCamera();
                  if (onDetected) onDetected(code.data);
                  return;
                }
              }

              // 2. Secondary fallback: Hardware BarcodeDetector
              if (detector) {
                var barcodes = await detector.detect(videoEl);
                if (barcodes && barcodes.length > 0) {
                  for (var i = 0; i < barcodes.length; i++) {
                    var val = barcodes[i].rawValue;
                    if (val) {
                      self.stopCamera();
                      if (onDetected) onDetected(val);
                      return;
                    }
                  }
                }
              }
            }
          } catch (detErr) {
            // Frame detection skipped
          }

          if (self._stream) {
            self._animId = setTimeout(scanFrame, 150); // ~7 FPS scanning loop
          }
        };

        this._animId = setTimeout(scanFrame, 200);
      } catch (err) {
        console.error("Camera access error:", err);
        if (onError) onError(err);
      }
    },

    // Stop camera and release hardware
    stopCamera: function() {
      if (this._animId) {
        clearTimeout(this._animId);
        this._animId = null;
      }
      if (this._stream) {
        this._stream.getTracks().forEach(function(t) {
          try {
            t.stop();
          } catch (e) {
            console.debug("Camera track stop notice:", e);
          }
        });
        this._stream = null;
      }
    },

    // Decode QR code from an image file / screenshot
    decodeImageFile: function(file, onDetected, onError) {
      try {
        var img = new Image();
        img.onload = function() {
          try {
            var canvas = document.createElement("canvas");
            canvas.width = img.width;
            canvas.height = img.height;
            var ctx = canvas.getContext("2d");
            ctx.drawImage(img, 0, 0);
            var imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);

            if (window.jsQR) {
              var code = window.jsQR(imgData.data, imgData.width, imgData.height);
              if (code && code.data) {
                if (onDetected) onDetected(code.data);
                return;
              }
            }
            throw new Error("No QR code found in selected image.");
          } catch (err) {
            if (onError) onError(err);
          }
        };
        img.onerror = function() {
          if (onError) onError(new Error("Failed to load image file."));
        };
        img.src = URL.createObjectURL(file);
      } catch (e) {
        if (onError) onError(e);
      }
    }
  };
})(window);
