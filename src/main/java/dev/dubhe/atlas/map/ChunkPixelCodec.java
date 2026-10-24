package dev.dubhe.atlas.map;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 区块像素数据的紧凑编解码。
///
/// 采用 Deflate 压缩 `16 × 16` 的 ARGB 像素数组，通常可将每区块压缩到约 1 KiB。
public final class ChunkPixelCodec {
    private static final Logger LOGGER =
            LoggerFactory.getLogger("aleeve_atlas/map/codec");
    private ChunkPixelCodec() {
    }

    /// 颜色与高度一起打包压缩（同步传输需要保留地表高度）。
    public static byte[] compress(int[] colors, int[] heights) {
        byte[] colorBytes = compress(colors);
        byte[] heightBytes = compress(heights);
        byte[] combined = new byte[4 + colorBytes.length + heightBytes.length];
        writeInt(combined, 0, colorBytes.length);
        System.arraycopy(colorBytes, 0, combined, 4, colorBytes.length);
        System.arraycopy(heightBytes, 0, combined, 4 + colorBytes.length, heightBytes.length);
        return combined;
    }

    /// 解包颜色与高度；格式不符时返回全透明/零高度。
    public static int[][] decompressWithHeights(byte[] packed) {
        int[] colors = new int[ChunkMapData.PIXELS];
        int[] heights = new int[ChunkMapData.PIXELS];
        if (packed.length < 4) {
            LOGGER.warn("Chunk pixel payload too short ({} bytes), expected at least 4",
                    packed.length);
            return new int[][] { colors, heights };
        }
        int colorLength = readInt(packed, 0);
        if (colorLength < 0 || 4 + colorLength > packed.length) {
            LOGGER.warn("Chunk pixel payload has invalid color length {} for {} bytes",
                    colorLength, packed.length);
            return new int[][] { colors, heights };
        }
        byte[] colorBytes = Arrays.copyOfRange(packed, 4, 4 + colorLength);
        byte[] heightBytes = Arrays.copyOfRange(packed, 4 + colorLength, packed.length);
        colors = decompress(colorBytes);
        int[] decodedHeights = decompress(heightBytes);
        System.arraycopy(decodedHeights, 0, heights, 0,
                Math.min(decodedHeights.length, heights.length));
        return new int[][] { colors, heights };
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static int readInt(byte[] source, int offset) {
        return ((source[offset] & 0xFF) << 24) | ((source[offset + 1] & 0xFF) << 16)
                | ((source[offset + 2] & 0xFF) << 8) | (source[offset + 3] & 0xFF);
    }

    /// 像素数组 → 压缩字节串。
    public static byte[] compress(int[] colors) {
        byte[] raw = new byte[colors.length * 4];
        for (int i = 0; i < colors.length; i++) {
            int color = colors[i];
            raw[i * 4] = (byte) (color >>> 24);
            raw[i * 4 + 1] = (byte) (color >>> 16);
            raw[i * 4 + 2] = (byte) (color >>> 8);
            raw[i * 4 + 3] = (byte) color;
        }
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length);
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /// 压缩字节串 → 像素数组；失败时返回全透明像素。
    public static int[] decompress(byte[] packed) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length * 4);
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) {
                    break;
                }
                out.write(buffer, 0, count);
            }
            byte[] raw = out.toByteArray();
            int[] colors = new int[ChunkMapData.PIXELS];
            for (int i = 0; i < colors.length && (i + 1) * 4 <= raw.length; i++) {
                colors[i] = ((raw[i * 4] & 0xFF) << 24) | ((raw[i * 4 + 1] & 0xFF) << 16)
                        | ((raw[i * 4 + 2] & 0xFF) << 8) | (raw[i * 4 + 3] & 0xFF);
            }
            return colors;
        } catch (DataFormatException e) {
            LOGGER.warn("Failed to decompress chunk pixels, returning blank chunk", e);
            return new int[ChunkMapData.PIXELS];
        } finally {
            inflater.end();
        }
    }
}
