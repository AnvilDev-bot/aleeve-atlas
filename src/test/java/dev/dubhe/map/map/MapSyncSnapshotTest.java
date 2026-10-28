package dev.dubhe.map.map;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MapSyncSnapshotTest {
    @TempDir Path directory;

    @Test
    void includesOldRegionsWithoutLoadingThemIntoRenderCache() throws Exception {
        MapDimensionCache original = new MapDimensionCache(directory);
        for (int region = 0; region < 40; region++) {
            original.put(chunk(region * 32, 10));
        }
        original.flush();
        MapDimensionCache reopened = new MapDimensionCache(directory);
        assertTrue(reopened.loadedChunks().isEmpty());
        var snapshot = reopened.snapshotForSync().readAll();
        assertEquals(40, snapshot.size());
        assertEquals(0, reopened.loadedRegionCount());
        assertTrue(reopened.loadedChunks().isEmpty());
        assertEquals(Blocks.STONE, snapshot.getFirst().stateAt(0, 0).state().getBlock());
    }

    @Test
    void inMemoryChangesOverrideDiskWithoutResurrectingInvalidatedChunks() throws Exception {
        MapDimensionCache cache = new MapDimensionCache(directory);
        cache.put(chunk(0, 10));
        cache.put(chunk(1, 10));
        cache.flush();
        cache.put(chunk(0, 20));
        cache.invalidate(1, 0);
        var snapshot = cache.snapshotForSync();
        cache.put(chunk(0, 30));
        var chunks = snapshot.readAll();
        assertEquals(1, chunks.size());
        assertEquals(20, chunks.getFirst().version());
    }

    private static ChunkMapData chunk(int x, long version) {
        var data = new ChunkMapData(x, 0);
        data.setPixel(0, 0, ChunkMapData.PixelState.of(Blocks.STONE.defaultBlockState()), 64);
        data.setVersion(version);
        return data;
    }
}
