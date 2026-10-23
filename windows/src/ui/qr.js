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

  // Helper to draw QR directly to canvas
  global.renderQrToCanvas = function(canvasId, text) {
    var canvas = typeof canvasId === "string" ? document.getElementById(canvasId) : canvasId;
    if (!canvas) return;
    try {
      var qr = new QRCode(4, 0); // Type 4+, Medium ECC
      qr.addData(text);
      qr.make();
      var count = qr.getModuleCount();
      var ctx = canvas.getContext("2d");
      var size = canvas.width;
      var cellSize = Math.floor(size / (count + 2));
      var offset = Math.floor((size - (cellSize * count)) / 2);

      ctx.fillStyle = "#ffffff";
      ctx.fillRect(0, 0, size, size);
      ctx.fillStyle = "#000000";

      for (var r = 0; r < count; r++) {
        for (var c = 0; c < count; c++) {
          if (qr.isDark(r, c)) {
            ctx.fillRect(offset + c * cellSize, offset + r * cellSize, cellSize, cellSize);
          }
        }
      }
    } catch (e) {
      console.warn("QR render error:", e);
    }
  };
})(window);
