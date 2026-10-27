package dev.dubhe.map.territory;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 横向路段压缩测试。
class TerritoryRunsTest {
    @Test
    void contiguousRowBecomesSingleRun() {
        Set<ChunkPos> chunks = new HashSet<>();
        for (int x = 0; x < 5; x++) {
            chunks.add(new ChunkPos(x, 0));
        }
        List<TerritoryRuns.Run> runs = TerritoryRuns.of(chunks);
        Assertions.assertEquals(1, runs.size());
        Assertions.assertEquals(0, runs.get(0).startX());
        Assertions.assertEquals(4, runs.get(0).endX());
        Assertions.assertEquals(0, runs.get(0).z());
        Assertions.assertEquals(5, runs.get(0).length());
    }

    @Test
    void gapsSplitIntoMultipleRuns() {
        Set<ChunkPos> chunks = Set.of(
                new ChunkPos(0, 0), new ChunkPos(1, 0),
                new ChunkPos(5, 0), new ChunkPos(6, 0));
        List<TerritoryRuns.Run> runs = TerritoryRuns.of(chunks);
        Assertions.assertEquals(2, runs.size());
        Assertions.assertEquals(0, runs.get(0).startX());
        Assertions.assertEquals(1, runs.get(0).endX());
        Assertions.assertEquals(5, runs.get(1).startX());
        Assertions.assertEquals(6, runs.get(1).endX());
    }

    @Test
    void rowsAreOrderedByZ() {
        Set<ChunkPos> chunks = Set.of(
                new ChunkPos(0, 2), new ChunkPos(0, 0), new ChunkPos(0, 1));
        List<TerritoryRuns.Run> runs = TerritoryRuns.of(chunks);
        Assertions.assertEquals(3, runs.size());
        Assertions.assertEquals(0, runs.get(0).z());
        Assertions.assertEquals(1, runs.get(1).z());
        Assertions.assertEquals(2, runs.get(2).z());
    }

    @Test
    void largeRectangleCompressesWell() {
        Set<ChunkPos> chunks = new HashSet<>();
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        // 10000 区块压缩为 100 段，绘制调用减少 100 倍。
        Assertions.assertEquals(100, TerritoryRuns.of(chunks).size());
        Assertions.assertTrue(TerritoryRuns.compressionRatio(chunks) < 0.02);
    }

    @Test
    void emptySetHasNoRuns() {
        Assertions.assertTrue(TerritoryRuns.of(Set.of()).isEmpty());
    }
}
