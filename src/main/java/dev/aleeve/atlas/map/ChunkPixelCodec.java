package dev.aleeve.atlas.map;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/// 区块像素数据的紧凑编解码（规格 §10：地图增量必须按体积上限分片，禁止整块区域塞进单包）。
///
/// 采用 Deflate 压缩 `16 × 16` 的 ARGB 像素数组，通常可将每区块压缩到约 1 KiB。
public final class ChunkPixelCodec {
    private ChunkPixelCodec() {
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
            return new int[ChunkMapData.PIXELS];
        } finally {
            inflater.end();
        }
    }
}
