package dev.dubhe.map.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// 地图缓存持久化与区域文件淘汰测试。
class MapDimensionCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void putThenGetReturnsSameData() {
        MapDimensionCache cache = new MapDimensionCache(this.tempDir);
        ChunkMapData data = new ChunkMapData(5, -3);
        data.setPixel(2, 4, 0xFF112233, 72);
        data.setVersion(99L);
        cache.put(data);

        ChunkMapData restored = cache.get(5, -3).orElseThrow();
        Assertions.assertEquals(0xFF112233, restored.colorAt(2, 4));
        Assertions.assertEquals(72, restored.heightAt(2, 4));
        Assertions.assertEquals(99L, restored.version());
    }

    @Test
    void flushThenReopenRestoresFromDisk() {
        MapDimensionCache cache = new MapDimensionCache(this.tempDir);
        ChunkMapData data = new ChunkMapData(17, 33);
        data.setPixel(0, 0, 0xFFAABBCC, 65);
        cache.put(data);
        cache.flush();

        // 新实例模拟重启后从区域文件读取。
        MapDimensionCache reopened = new MapDimensionCache(this.tempDir);
        ChunkMapData restored = reopened.get(17, 33).orElseThrow();
        Assertions.assertEquals(0xFFAABBCC, restored.colorAt(0, 0));
        Assertions.assertEquals(65, restored.heightAt(0, 0));
        Assertions.assertEquals(17, restored.chunkX());
        Assertions.assertEquals(33, restored.chunkZ());
    }

    @Test
    void truncatedRegionIsQuarantinedAndRebuilt() throws IOException {
        Path region = this.tempDir.resolve("r.0.0.nbt");
        Files.write(region, new byte[] {0x1F, (byte) 0x8B, 0x08, 0, 0, 0, 0, 0, 0, 0});

        MapDimensionCache cache = new MapDimensionCache(this.tempDir);
        Assertions.assertTrue(cache.get(0, 0).isEmpty());
        Assertions.assertTrue(Files.exists(this.tempDir.resolve("r.0.0.nbt.corrupt")));
        Assertions.assertFalse(Files.exists(region));

        ChunkMapData data = new ChunkMapData(0, 0);
        data.setPixel(3, 5, 0xFF123456, 70);
        cache.put(data);
        cache.flush();
        Assertions.assertFalse(Files.exists(this.tempDir.resolve("r.0.0.nbt.tmp")));

        MapDimensionCache reopened = new MapDimensionCache(this.tempDir);
        ChunkMapData restored = reopened.get(0, 0).orElseThrow();
        Assertions.assertEquals(0xFF123456, restored.colorAt(3, 5));
        Assertions.assertEquals(70, restored.heightAt(3, 5));
    }

    @Test
    void manyRegionsDoNotGrowUnbounded() {
        MapDimensionCache cache = new MapDimensionCache(this.tempDir);
        // 跨越大量区域写入区块，触发淘汰路径。
        for (int region = 0; region < 80; region++) {
            int chunkX = region * 32;
            ChunkMapData data = new ChunkMapData(chunkX, 0);
            data.setPixel(0, 0, 0xFF000000 | region, 64);
            cache.put(data);
        }
        cache.flush();
        // 淘汰后仍能正确读到最早与最新的区块。
        Assertions.assertTrue(cache.get(0, 0).isPresent(), "最早区域应可重新加载");
        Assertions.assertTrue(cache.get(79 * 32, 0).isPresent(), "最新区域应可读取");
        Assertions.assertEquals(0, cache.get(0, 0).orElseThrow().colorAt(0, 0) & 0xFF);
        Assertions.assertEquals(79, cache.get(79 * 32, 0).orElseThrow().colorAt(0, 0) & 0xFF);
    }

    @Test
    void invalidateDropsMemoryAndDiskEntry() {
        // 方块变更后必须同时清除内存与区域文件，否则重新采样前的读取会返回过期像素。
        MapDimensionCache cache = new MapDimensionCache(this.tempDir);
        ChunkMapData data = new ChunkMapData(3, 7);
        data.setPixel(1, 1, 0xFF445566, 60);
        cache.put(data);
        cache.flush();

        cache.invalidate(3, 7);

        Assertions.assertTrue(cache.get(3, 7).isEmpty(), "内存缓存应已清除");
        // 写盘后才反映到区域文件：invalidate 只标记脏，由 flush 批量写入。
        cache.flush();
        MapDimensionCache reopened = new MapDimensionCache(this.tempDir);
        Assertions.assertTrue(reopened.get(3, 7).isEmpty(), "flush 后区域文件条目应已清除");
    }

    @Test
    void invalidateKeepsOtherChunksIntact() {
        MapDimensionCache cache = new MapDimensionCache(this.tempDir);
        ChunkMapData keep = new ChunkMapData(1, 1);
        keep.setPixel(0, 0, 0xFF00FF00, 64);
        ChunkMapData drop = new ChunkMapData(2, 2);
        drop.setPixel(0, 0, 0xFFFF0000, 65);
        cache.put(keep);
        cache.put(drop);

        cache.invalidate(2, 2);

        Assertions.assertEquals(0xFF00FF00, cache.get(1, 1).orElseThrow().colorAt(0, 0));
        Assertions.assertTrue(cache.get(2, 2).isEmpty());
    }

    @Test
    void mixedCoordinatesSurviveRegionReload() {
        MapDimensionCache cache = new MapDimensionCache(this.tempDir);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                ChunkMapData data = new ChunkMapData(x, z);
                data.setPixel(0, 0, 0xFF000000 | ((x + 2) * 16 + z + 2), 64);
                cache.put(data);
            }
        }
        cache.flush();
        MapDimensionCache reopened = new MapDimensionCache(this.tempDir);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int expected = 0xFF000000 | ((x + 2) * 16 + z + 2);
                Assertions.assertEquals(expected, reopened.get(x, z).orElseThrow().colorAt(0, 0),
                        "区块坐标 " + x + "," + z);
            }
        }
    }
}
