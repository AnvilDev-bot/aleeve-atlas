package dev.dubhe.map.cartography;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ImportedMapStoreTest {
    @TempDir Path temp;

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
