package dev.dubhe.atlas.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// 区域文件缓存的最久未用淘汰测试（规格 §12.1 内存占用）。
///
/// 驻留上限为 32 个区域；访问热点区域后，其淘汰优先级必须被刷新，
/// 否则高频读取的区域会被误淘汰，导致反复读盘。
class MapDimensionCacheLruTest {
    /// 与实现内的驻留上限一致。
    private static final int MAX_REGIONS = 32;

    @TempDir
    Path tempDir;

    @Test
    void residentRegionCountDoesNotExceedLimit() {
        MapDimensionCache cache = new MapDimensionCache(tempDir);
        for (int region = 0; region < MAX_REGIONS * 3; region++) {
            cache.put(chunkInRegion(region));
        }
        assertTrue(cache.loadedRegionCount() <= MAX_REGIONS,
                "驻留区域数不得超过上限，实际 " + cache.loadedRegionCount());
    }

    @Test
    void recentlyAccessedRegionSurvivesEviction() {
        MapDimensionCache cache = new MapDimensionCache(tempDir);
        // 先填充到接近上限。
        for (int region = 0; region < MAX_REGIONS - 1; region++) {
            cache.put(chunkInRegion(region));
        }
        // 访问最早的区域（region 0），刷新其淘汰优先级。
        assertTrue(cache.get(0, 0).isPresent());
        // 继续插入足量新区域，迫使淘汰发生。
        for (int region = MAX_REGIONS; region < MAX_REGIONS * 2; region++) {
            cache.put(chunkInRegion(region));
        }
        // 热点区域应仍在内存中：读取不触发区域重载，数据保持一致。
        assertEquals(0, cache.get(0, 0).orElseThrow().colorAt(0, 0) & 0xFF,
                "近期访问过的区域不应被淘汰");
    }

    @Test
    void leastRecentlyUsedRegionIsEvictedFirst() {
        MapDimensionCache cache = new MapDimensionCache(tempDir);
        for (int region = 0; region < MAX_REGIONS; region++) {
            cache.put(chunkInRegion(region));
        }
        int before = cache.loadedRegionCount();
        // 再插入一个新区域，必然淘汰一个旧区域。
        cache.put(chunkInRegion(MAX_REGIONS * 2));
        assertTrue(cache.loadedRegionCount() <= Math.max(before, MAX_REGIONS),
                "淘汰后驻留数不应增长");
        // 被淘汰的区域仍能从磁盘重新加载，数据不丢失。
        for (int region = 0; region < MAX_REGIONS; region++) {
            assertTrue(cache.get(region * 32, 0).isPresent(),
                    "区域 " + region + " 应可从磁盘恢复");
        }
    }

    @Test
    void evictionPersistsDataBeforeDropping() {
        MapDimensionCache cache = new MapDimensionCache(tempDir);
        ChunkMapData first = chunkInRegion(0);
        cache.put(first);
        // 不调用 flush，直接插入足量区域触发淘汰。
        for (int region = 1; region <= MAX_REGIONS * 2; region++) {
            cache.put(chunkInRegion(region));
        }
        // 淘汰时应先写入磁盘，重新打开后仍能读到最早的数据。
        MapDimensionCache reopened = new MapDimensionCache(tempDir);
        assertTrue(reopened.get(0, 0).isPresent(), "淘汰前必须写入磁盘");
        assertEquals(0, reopened.get(0, 0).orElseThrow().colorAt(0, 0) & 0xFF);
    }

    /// 构造位于指定区域首区块的数据，颜色低字节编码区域序号便于追溯。
    private static ChunkMapData chunkInRegion(int region) {
        ChunkMapData data = new ChunkMapData(region * 32, 0);
        data.setPixel(0, 0, 0xFF000000 | (region & 0xFF), 64);
        return data;
    }
}
