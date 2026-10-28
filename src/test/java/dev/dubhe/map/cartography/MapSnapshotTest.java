package dev.dubhe.map.cartography;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MapSnapshotTest {
    static MapSnapshot snapshot(int id, int scale, byte[] colors) {
        return new MapSnapshot(id, "minecraft:overworld", -128, 256, scale, 63, colors,
                List.of(new MapSnapshot.Target("minecraft:red_x", -119, 265)), 123L);
    }

    @Test void gridMatchesVanillaAtPositiveAndNegativeBoundaries() {
        for (double value : new double[]{-300000, -193, -192, -64.001, -64, -1, 0, 63.999, 64, 192, 300000}) {
            var vanilla = MapItemSavedData.createFresh(value, value, (byte) 0, true, false, Level.OVERWORLD);
            assertEquals(vanilla.centerX, Cartography.center(value));
            assertEquals(vanilla.centerZ, Cartography.center(value));
        }
    }

    @Test void everyScaleMapsPixelsToCorrectBlocksWithoutLeakingEdges() {
        for (int scale = 0; scale <= 4; scale++) {
            byte[] colors = new byte[MapSnapshot.PIXELS]; colors[0] = 5; colors[16383] = 6;
            var map = snapshot(1, scale, colors);
            assertEquals(5, map.pixelAt(map.minX(), map.minZ()));
            assertEquals(5, map.pixelAt(map.minX() + (1 << scale) - 1, map.minZ()));
            assertEquals(6, map.pixelAt(map.minX() + map.size() - 1, map.minZ() + map.size() - 1));
            assertEquals(0, map.pixelAt(map.minX() - 1, map.minZ()));
            assertEquals(0, map.pixelAt(map.minX() + map.size(), map.minZ()));
        }
    }

    @Test void snapshotDefensivelyFreezesPreviewAndRoundTrips() {
        byte[] colors = new byte[MapSnapshot.PIXELS]; colors[1] = 7;
        var map = snapshot(7, 4, colors);
        colors[1] = 9; map.colors()[1] = 10;
        assertEquals(7, map.colors()[1]);
        var persisted = MapSnapshot.load(map.save());
        assertArrayEquals(map.colors(), persisted.colors());
        assertEquals(map.targets(), persisted.targets());
        assertEquals(map.importedAt(), persisted.importedAt());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            map.write(buffer);
            var received = MapSnapshot.read(buffer);
            assertEquals(map.dimension(), received.dimension());
            assertEquals(map.referenceHeight(), received.referenceHeight());
            assertEquals(map.targets(), received.targets());
            assertArrayEquals(map.colors(), received.colors());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void invalidSnapshotsAreRejectedAndTransparentBrightnessIsStillUnknown() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 5, new byte[MapSnapshot.PIXELS]));
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 0, new byte[1]));
        byte[] colors = new byte[MapSnapshot.PIXELS]; colors[0] = (byte) 255;
        assertThrows(IllegalArgumentException.class, () -> snapshot(1, 0, colors));
        colors[0] = 3;
        assertFalse(Cartography.hasContent(colors));
        colors[0] = MapColor.WATER.getPackedId(MapColor.Brightness.HIGH);
        assertTrue(Cartography.hasContent(colors));
    }
}
