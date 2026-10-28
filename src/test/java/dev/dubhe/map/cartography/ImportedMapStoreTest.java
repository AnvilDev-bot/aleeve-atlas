package dev.dubhe.map.cartography;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ImportedMapStoreTest {
    @TempDir Path temp;

    @Test void removesOnlyFullyReplacedColoredAreaAndPersistsRemoval() throws Exception {
        var store = new ImportedMapStore(temp.resolve("imports"), "minecraft:overworld");
        byte[] colors = new byte[MapSnapshot.PIXELS];
        colors[0] = 5;
        // 4×4 个方块的粗像素，位于负坐标且跨区块边界。
        store.put(new MapSnapshot(9, "minecraft:overworld", 255, 255, 2, 64, colors, java.util.List.of(), 0));
        var cache = new dev.dubhe.map.map.MapDimensionCache(temp.resolve("terrain"));
        assertEquals(0, store.pruneExplored(cache));
        for (int cz = -1; cz <= 0; cz++) for (int cx = -1; cx <= 0; cx++) {
            var chunk = new dev.dubhe.map.map.ChunkMapData(cx, cz);
            for (int z = -1; z < 3; z++) for (int x = -1; x < 3; x++) {
                if (Math.floorDiv(x, 16) != cx || Math.floorDiv(z, 16) != cz || (x == 2 && z == 2)) continue;
                chunk.setPixel(x & 15, z & 15, dev.dubhe.map.map.ChunkMapData.PixelState.of(
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), 70);
            }
            cache.put(chunk);
        }
        assertEquals(0, store.pruneExplored(cache), "部分探索的粗像素仍需保留");
        var last = cache.get(0, 0).orElseThrow();
        last.setPixel(2, 2, dev.dubhe.map.map.ChunkMapData.PixelState.of(
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), 70);
        cache.put(last);
        cache.flush();
        assertEquals(1, store.pruneExplored(cache));
        assertEquals(2, store.revision());
        assertFalse(Files.exists(temp.resolve("imports/9.nbt")));
        assertTrue(new ImportedMapStore(temp.resolve("imports"), "minecraft:overworld")
                .intersecting(-1000, -1000, 1000, 1000).isEmpty());
    }

    @Test void persistsReimportsAndSkipsCorruptFiles() throws Exception {
        var store = new ImportedMapStore(temp, "minecraft:overworld");
        byte[] colors = new byte[MapSnapshot.PIXELS]; colors[0] = 5;
        store.put(MapSnapshotTest.snapshot(1, 0, colors));
        colors[0] = 6;
        store.put(MapSnapshotTest.snapshot(1, 0, colors));
        Files.writeString(temp.resolve("corrupt.nbt"), "broken");
        var reopened = new ImportedMapStore(temp, "minecraft:overworld");
        var maps = reopened.intersecting(-192, 192, -65, 319);
        assertEquals(1, maps.size());
        assertEquals(6, ImportedMapStore.pixelAt(maps, -192, 192));
        assertEquals(2, store.revision());
        assertTrue(new ImportedMapStore(temp, "minecraft:the_nether").intersecting(-500, -500, 500, 500).isEmpty());
        assertTrue(new ImportedMapStore(temp.resolve("other-server"), "minecraft:overworld").intersecting(-500, -500, 500, 500).isEmpty());
    }

    @Test void resolutionThenRecencyAndTransparencyDecideOverlap() throws Exception {
        var store = new ImportedMapStore(temp, "minecraft:overworld");
        byte[] fine = new byte[MapSnapshot.PIXELS]; fine[0] = 5;
        byte[] coarse = new byte[MapSnapshot.PIXELS]; java.util.Arrays.fill(coarse, (byte) 6);
        store.put(MapSnapshotTest.snapshot(1, 0, fine));
        store.put(MapSnapshotTest.snapshot(2, 1, coarse));
        var maps = store.intersecting(-192, 192, -65, 319);
        assertEquals(5, ImportedMapStore.pixelAt(maps, -192, 192));
        assertEquals(6, ImportedMapStore.pixelAt(maps, -191, 192));
        fine[0] = 7;
        store.put(MapSnapshotTest.snapshot(3, 0, fine));
        maps = store.intersecting(-192, 192, -65, 319);
        assertEquals(7, ImportedMapStore.pixelAt(maps, -192, 192));
        assertEquals(6, ImportedMapStore.pixelAt(maps, -191, 192));
        assertEquals(0, ImportedMapStore.pixelAt(maps, 10000, 10000));
        var wrong = new MapSnapshot(4, "minecraft:the_nether", 0, 0, 0, 32, fine, java.util.List.of(), 0);
        assertThrows(IllegalArgumentException.class, () -> store.put(wrong));
    }
}
