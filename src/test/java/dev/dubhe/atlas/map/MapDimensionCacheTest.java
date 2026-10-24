package dev.dubhe.atlas.map;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// 地图缓存持久化与区域文件淘汰测试。
class MapDimensionCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void putThenGetReturnsSameData() {
        MapDimensionCache cache = new MapDimensionCache(tempDir);
        ChunkMapData data = new ChunkMapData(5, -3);
        data.setPixel(2, 4, 0xFF112233, 72);
        data.setVersion(99L);
        cache.put(data);

        ChunkMapData restored = cache.get(5, -3).orElseThrow();
        assertEquals(0xFF112233, restored.colorAt(2, 4));
        assertEquals(72, restored.heightAt(2, 4));
        assertEquals(99L, restored.version());
    }

    @Test
    void flushThenReopenRestoresFromDisk() {
        MapDimensionCache cache = new MapDimensionCache(tempDir);
        ChunkMapData data = new ChunkMapData(17, 33);
        data.setPixel(0, 0, 0xFFAABBCC, 65);
        cache.put(data);
        cache.flush();

        // 新实例模拟重启后从区域文件读取。
        MapDimensionCache reopened = new MapDimensionCache(tempDir);
        ChunkMapData restored = reopened.get(17, 33).orElseThrow();
        assertEquals(0xFFAABBCC, restored.colorAt(0, 0));
        assertEquals(65, restored.heightAt(0, 0));
        assertEquals(17, restored.chunkX());
        assertEquals(33, restored.chunkZ());
    }

    @Test
    void manyRegionsDoNotGrowUnbounded() {
        MapDimensionCache cache = new MapDimensionCache(tempDir);
        // 跨越大量区域写入区块，触发淘汰路径。
        for (int region = 0; region < 80; region++) {
            int chunkX = region * 32;
            ChunkMapData data = new ChunkMapData(chunkX, 0);
            data.setPixel(0, 0, 0xFF000000 | region, 64);
            cache.put(data);
        }
        cache.flush();
        // 淘汰后仍能正确读到最早与最新的区块。
        assertTrue(cache.get(0, 0).isPresent(), "最早区域应可重新加载");
        assertTrue(cache.get(79 * 32, 0).isPresent(), "最新区域应可读取");
        assertEquals(0, cache.get(0, 0).orElseThrow().colorAt(0, 0) & 0xFF);
        assertEquals(79, cache.get(79 * 32, 0).orElseThrow().colorAt(0, 0) & 0xFF);
    }
}
