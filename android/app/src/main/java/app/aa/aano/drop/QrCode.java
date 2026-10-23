/*
 * Fast, self-contained QR Code generator for AANO Drop (Java 7+)
 * Compatible with Android offline Gradle build. Zero external dependencies.
 * Based on Project Nayuki's QR Code library (MIT License).
 * Copyright (c) 2026 Jam Amjad Rasheed / Project Nayuki.
 */

package app.aa.aano.drop;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public final class QrCode {

    public static final int MIN_VERSION = 1;
    public static final int MAX_VERSION = 10;

    public enum Ecc {
        LOW(1), MEDIUM(0), QUARTILE(3), HIGH(2);
        final int formatBits;
        Ecc(int fb) { formatBits = fb; }
    }

    public final int version;
    public final int size;
    public final Ecc errorCorrectionLevel;
    public final int mask;
    private final boolean[][] modules;

    public static QrCode encodeText(String text, Ecc ecl) {
        Objects.requireNonNull(text);
        Objects.requireNonNull(ecl);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return encodeBinary(bytes, ecl);
    }

    public static QrCode encodeBinary(byte[] data, Ecc ecl) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(ecl);

        // Find smallest version that fits data
        int ver = 1;
        int dataBits = 4 + 8 + data.length * 8; // mode(4) + count(8 for ver 1-9) + data
        for (; ver <= MAX_VERSION; ver++) {
            int capacity = getNumDataCodewords(ver, ecl) * 8;
            int countBits = (ver < 10) ? 8 : 16;
            dataBits = 4 + countBits + data.length * 8;
            if (dataBits <= capacity) break;
        }
        if (ver > MAX_VERSION) {
            throw new IllegalArgumentException("Data too long for compact QR code");
        }

        BitBuffer bb = new BitBuffer();
        bb.appendBits(4, 4); // Byte mode indicator (0100)
        int countBits = (ver < 10) ? 8 : 16;
        bb.appendBits(data.length, countBits);
        for (byte b : data) {
            bb.appendBits(b & 0xFF, 8);
        }

        // Terminator and padding
        int capacityBits = getNumDataCodewords(ver, ecl) * 8;
        bb.appendBits(0, Math.min(4, capacityBits - bb.len));
        bb.appendBits(0, (8 - bb.len % 8) % 8);
        for (int pad = 0xEC; bb.len < capacityBits; pad ^= 0xEC ^ 0x11) {
            bb.appendBits(pad, 8);
        }

        // Codewords and error correction
        byte[] dataCodewords = new byte[bb.len / 8];
        for (int i = 0; i < bb.len; i++) {
            dataCodewords[i >>> 3] |= (bb.getBit(i) ? 1 : 0) << (7 - (i & 7));
        }

        byte[] allCodewords = addErrorCorrection(dataCodewords, ver, ecl);
        return new QrCode(ver, ecl, allCodewords);
    }

    private QrCode(int ver, Ecc ecl, byte[] codewords) {
        this.version = ver;
        this.size = ver * 4 + 17;
        this.errorCorrectionLevel = ecl;
        this.modules = new boolean[size][size];

        boolean[][] isFunction = new boolean[size][size];
        drawFunctionPatterns(isFunction);

        // Best mask selection (evaluating penalty for masks 0..7)
        int bestMask = 0;
        int minPenalty = Integer.MAX_VALUE;
        boolean[][] bestGrid = null;

        for (int m = 0; m < 8; m++) {
            drawCodewords(codewords, isFunction, m);
            drawFormatBits(ecl, m);
            int penalty = getPenaltyScore();
            if (penalty < minPenalty) {
                minPenalty = penalty;
                bestMask = m;
                bestGrid = copyGrid(modules);
            }
        }
        this.mask = bestMask;
        for (int y = 0; y < size; y++) {
            System.arraycopy(bestGrid[y], 0, modules[y], 0, size);
        }
    }

    public boolean getModule(int x, int y) {
        return (x >= 0 && x < size && y >= 0 && y < size) && modules[y][x];
    }

    public Bitmap toBitmap(int pixelSize) {
        Bitmap bmp = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.ARGB_8888);
        int scale = pixelSize / size;
        int offset = (pixelSize - (scale * size)) / 2;

        int[] pixels = new int[pixelSize * pixelSize];
        Arrays.fill(pixels, Color.WHITE);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (modules[y][x]) {
                    int startX = offset + x * scale;
                    int startY = offset + y * scale;
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            int px = startX + dx;
                            int py = startY + dy;
                            if (px < pixelSize && py < pixelSize) {
                                pixels[py * pixelSize + px] = Color.BLACK;
                            }
                        }
                    }
                }
            }
        }
        bmp.setPixels(pixels, 0, pixelSize, 0, 0, pixelSize, pixelSize);
        return bmp;
    }

    private void drawFunctionPatterns(boolean[][] isFunction) {
        // Finder patterns
        drawFinderPattern(3, 3, isFunction);
        drawFinderPattern(size - 4, 3, isFunction);
        drawFinderPattern(3, size - 4, isFunction);

        // Alignment patterns
        int[] alignPos = getAlignmentPatternPositions(version);
        for (int y : alignPos) {
            for (int x : alignPos) {
                if (!((x <= 8 && y <= 8) || (x >= size - 9 && y <= 8) || (x <= 8 && y >= size - 9))) {
                    drawAlignmentPattern(x, y, isFunction);
                }
            }
        }

        // Timing patterns
        for (int i = 0; i < size; i++) {
            setFunctionModule(6, i, i % 2 == 0, isFunction);
            setFunctionModule(i, 6, i % 2 == 0, isFunction);
        }

        // Dark module & reserve format bits
        setFunctionModule(8, size - 8, true, isFunction);
        for (int i = 0; i < 9; i++) {
            if (i != 6) {
                setFunctionModule(i, 8, false, isFunction);
                setFunctionModule(8, i, false, isFunction);
            }
        }
        for (int i = 0; i < 8; i++) {
            setFunctionModule(size - 1 - i, 8, false, isFunction);
            setFunctionModule(8, size - 1 - i, false, isFunction);
        }
    }

    private void drawFinderPattern(int cx, int cy, boolean[][] isFunction) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) {
                    setFunctionModule(x, y, dist != 2 && dist != 4, isFunction);
                }
            }
        }
    }

    private void drawAlignmentPattern(int cx, int cy, boolean[][] isFunction) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunctionModule(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1, isFunction);
            }
        }
    }

    private void setFunctionModule(int x, int y, boolean isDark, boolean[][] isFunction) {
        modules[y][x] = isDark;
        isFunction[y][x] = true;
    }

    private void drawFormatBits(Ecc ecl, int m) {
        int data = (ecl.formatBits << 3) | m;
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = ((data << 10) | rem) ^ 0x5412;

        for (int i = 0; i <= 5; i++) setModule(8, i, getBit(bits, i));
        setModule(8, 7, getBit(bits, 6));
        setModule(8, 8, getBit(bits, 7));
        setModule(7, 8, getBit(bits, 8));
        for (int i = 9; i < 15; i++) setModule(14 - i, 8, getBit(bits, i));

        for (int i = 0; i < 8; i++) setModule(size - 1 - i, 8, getBit(bits, i));
        for (int i = 8; i < 15; i++) setModule(8, size - 15 + i, getBit(bits, i));
        setModule(8, size - 8, true);
    }

    private static boolean getBit(int val, int i) {
        return ((val >>> i) & 1) != 0;
    }

    private void setModule(int x, int y, boolean isDark) {
        modules[y][x] = isDark;
    }

    private void drawCodewords(byte[] codewords, boolean[][] isFunction, int m) {
        int bitIndex = 0;
        int right = size - 1;
        while (right > 0) {
            if (right == 6) right--; // Skip vertical timing column
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x]) {
                        boolean bit = false;
                        if (bitIndex < codewords.length * 8) {
                            bit = getBit(codewords[bitIndex >>> 3], 7 - (bitIndex & 7));
                            bitIndex++;
                        }
                        boolean maskCondition = evaluateMask(m, x, y);
                        modules[y][x] = bit ^ maskCondition;
                    }
                }
            }
            right -= 2;
        }
    }

    private static boolean evaluateMask(int m, int x, int y) {
        switch (m) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (x / 3 + y / 2) % 2 == 0;
            case 5: return ((x * y) % 2 + (x * y) % 3) == 0;
            case 6: return (((x * y) % 2) + (x * y) % 3) % 2 == 0;
            case 7: return (((x + y) % 2) + (x * y) % 3) % 2 == 0;
            default: return false;
        }
    }

    private int getPenaltyScore() {
        int penalty = 0;
        // Horizontal runs
        for (int y = 0; y < size; y++) {
            boolean dark = modules[y][0];
            int count = 1;
            for (int x = 1; x < size; x++) {
                if (modules[y][x] == dark) {
                    count++;
                } else {
                    if (count >= 5) penalty += 3 + (count - 5);
                    dark = modules[y][x];
                    count = 1;
                }
            }
            if (count >= 5) penalty += 3 + (count - 5);
        }
        // Vertical runs
        for (int x = 0; x < size; x++) {
            boolean dark = modules[0][x];
            int count = 1;
            for (int y = 1; y < size; y++) {
                if (modules[y][x] == dark) {
                    count++;
                } else {
                    if (count >= 5) penalty += 3 + (count - 5);
                    dark = modules[y][x];
                    count = 1;
                }
            }
            if (count >= 5) penalty += 3 + (count - 5);
        }
        return penalty;
    }

    private static boolean[][] copyGrid(boolean[][] src) {
        boolean[][] dest = new boolean[src.length][src[0].length];
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, dest[i], 0, src[i].length);
        }
        return dest;
    }

    // Reed-Solomon Code generator and ECC tables
    private static byte[] addErrorCorrection(byte[] data, int ver, Ecc ecl) {
        int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal()][ver];
        int totalEcCodewords = ECC_CODEWORDS_PER_BLOCK[ecl.ordinal()][ver] * numBlocks;
        int totalData = getNumDataCodewords(ver, ecl);

        int shortBlockLen = totalData / numBlocks;
        int numShortBlocks = numBlocks - totalData % numBlocks;
        byte[][] dataBlocks = new byte[numBlocks][];
        byte[][] ecBlocks = new byte[numBlocks][];
        byte[] rsGen = reedSolomonGenerator(ECC_CODEWORDS_PER_BLOCK[ecl.ordinal()][ver]);

        for (int i = 0, k = 0; i < numBlocks; i++) {
            int blockLen = shortBlockLen + (i < numShortBlocks ? 0 : 1);
            dataBlocks[i] = Arrays.copyOfRange(data, k, k + blockLen);
            ecBlocks[i] = reedSolomonComputeDivisor(dataBlocks[i], rsGen);
            k += blockLen;
        }

        byte[] result = new byte[totalData + totalEcCodewords];
        int idx = 0;
        // Interleave data
        int maxDataLen = shortBlockLen + (numShortBlocks < numBlocks ? 1 : 0);
        for (int i = 0; i < maxDataLen; i++) {
            for (int b = 0; b < numBlocks; b++) {
                if (i < dataBlocks[b].length) {
                    result[idx++] = dataBlocks[b][i];
                }
            }
        }
        // Interleave error correction
        int ecLen = rsGen.length;
        for (int i = 0; i < ecLen; i++) {
            for (int b = 0; b < numBlocks; b++) {
                result[idx++] = ecBlocks[b][i];
            }
        }
        return result;
    }

    private static byte[] reedSolomonGenerator(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) multiplyGF((result[j] & 0xFF), root);
                if (j + 1 < degree) {
                    result[j] ^= result[j + 1];
                }
            }
            root = multiplyGF(root, 0x02);
        }
        return result;
    }

    private static byte[] reedSolomonComputeDivisor(byte[] data, byte[] gen) {
        byte[] result = new byte[gen.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < gen.length; i++) {
                result[i] ^= multiplyGF(gen[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int multiplyGF(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private static int getNumDataCodewords(int ver, Ecc ecl) {
        return TOTAL_CODEWORDS[ver] - ECC_CODEWORDS_PER_BLOCK[ecl.ordinal()][ver] * NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal()][ver];
    }

    private static int[] getAlignmentPatternPositions(int ver) {
        if (ver == 1) return new int[]{};
        int num = ver / 7 + 2;
        int step = (ver == 32) ? 26 : (ver * 4 + num * 2 + 1) / (num * 2 - 2) * 2;
        int[] result = new int[num];
        result[0] = 6;
        for (int i = num - 1, pos = ver * 4 + 10; i >= 1; i--, pos -= step) {
            result[i] = pos;
        }
        return result;
    }

    private static final int[] TOTAL_CODEWORDS = {
        0, 26, 44, 70, 100, 134, 172, 196, 242, 292, 346
    };

    private static final int[][] ECC_CODEWORDS_PER_BLOCK = {
        // Version: 0, 1,  2,  3,  4,  5,  6,  7,  8,  9, 10
        { 0,  7, 10, 15, 20, 26, 18, 20, 24, 30, 18 },  // Low
        { 0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26 },  // Medium
        { 0, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24 },  // Quartile
        { 0, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28 }   // High
    };

    private static final int[][] NUM_ERROR_CORRECTION_BLOCKS = {
        { 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4 },
        { 0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5 },
        { 0, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8 },
        { 0, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8 }
    };

    private static final class BitBuffer {
        int[] data = new int[64];
        int len = 0;

        void appendBits(int val, int count) {
            for (int i = count - 1; i >= 0; i--) {
                int bit = (val >>> i) & 1;
                if (len >= data.length * 32) {
                    data = Arrays.copyOf(data, data.length * 2);
                }
                data[len >>> 5] |= bit << (31 - (len & 31));
                len++;
            }
        }

        boolean getBit(int index) {
            return ((data[index >>> 5] >>> (31 - (index & 31))) & 1) != 0;
        }
    }
}
