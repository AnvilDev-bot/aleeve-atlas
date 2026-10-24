package dev.dubhe.atlas.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.world.level.ChunkPos;
import dev.dubhe.atlas.network.ChunkDeltaPayload;
import dev.dubhe.atlas.network.ChunkDigestPayload;
import org.junit.jupiter.api.Test;

/// 区块像素压缩与摘要/差异分片测试（规格 §10 体积约束 / §3.3）。
class ChunkPixelCodecTest {
    @Test
    void compressRoundTripPreservesPixels() {
        int[] colors = new int[ChunkMapData.PIXELS];
        Random random = new Random(42L);
        for (int i = 0; i < colors.length; i++) {
            colors[i] = 0xFF000000 | random.nextInt(0xFFFFFF);
        }
        int[] restored = ChunkPixelCodec.decompress(ChunkPixelCodec.compress(colors));
        assertArrayEquals(colors, restored);
    }

    @Test
    void flatTerrainCompressesWell() {
        int[] colors = new int[ChunkMapData.PIXELS];
        Arrays.fill(colors, 0xFF4C8B2B);
        byte[] packed = ChunkPixelCodec.compress(colors);
        // 单调地形应显著小于原始 1 KiB（16×16×4 字节）。
        assertTrue(packed.length < 256,
                "expected strong compression, got " + packed.length + " bytes");
    }

    @Test
    void compassWithHeightsRoundTripsBothArrays() {
        int[] colors = new int[ChunkMapData.PIXELS];
        int[] heights = new int[ChunkMapData.PIXELS];
        Random random = new Random(7L);
        for (int i = 0; i < colors.length; i++) {
            colors[i] = 0xFF000000 | random.nextInt(0xFFFFFF);
            heights[i] = 40 + random.nextInt(60);
        }
        int[][] decoded = ChunkPixelCodec.decompressWithHeights(
                ChunkPixelCodec.compress(colors, heights));
        assertArrayEquals(colors, decoded[0]);
        assertArrayEquals(heights, decoded[1]);
    }

    @Test
    void digestSplitRespectsPacketLimit() {
        List<ChunkDigestPayload.Entry> entries =
                new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            entries.add(new ChunkDigestPayload.Entry(i, -i, i));
        }
        List<ChunkDigestPayload> packets =
                ChunkDigestPayload.split("minecraft:overworld", entries, null);
        assertEquals(3, packets.size());
        int total = packets.stream().mapToInt(packet -> packet.entries().size()).sum();
        assertEquals(3000, total);
        for (var packet : packets) {
            assertTrue(packet.entries().size()
                    <= ChunkDigestPayload.MAX_ENTRIES_PER_PACKET);
        }
    }

    @Test
    void deltaSplitRespectsPacketLimit() {
        List<ChunkDeltaPayload.ChunkData> chunks =
                new ArrayList<>();
        byte[] payload = new byte[64];
        for (int i = 0; i < 300; i++) {
            chunks.add(new ChunkDeltaPayload.ChunkData(i, i, i, payload));
        }
        List<ChunkDeltaPayload> packets =
                ChunkDeltaPayload.split("minecraft:overworld", chunks, null);
        assertEquals(3, packets.size());
        int total = packets.stream().mapToInt(packet -> packet.chunks().size()).sum();
        assertEquals(300, total);
    }

    @Test
    void chunkPosPackingIsLossless() {
        ChunkPos pos = new ChunkPos(-1234, 5678);
        long packed = (long) pos.x << 32 | (pos.z & 0xFFFFFFFFL);
        int x = (int) (packed >> 32);
        int z = (int) packed;
        assertEquals(pos.x, x);
        assertEquals(pos.z, z);
    }
}
