package dev.dubhe.map.client.map;

import dev.dubhe.map.map.CaveMode;
import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.map.MapSlice;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaveRescanTest {
    @Test void slowVerticalMovementChangesExactSamplingHeightAndEventuallyChangesLayer() {
        var tracker = new CaveMode.Tracker();
        var cached = new ChunkMapData(0, 0);
        cached.setSlice(MapSlice.layered(40, 30));
        for (int y = 41; y <= 49; y++) {
            MapSlice view = tracker.update(MapSlice.layered(y, 30), y);
            assertNotEquals(cached.slice(), view);
            assertEquals(y < 48 ? "layer_2" : "layer_3", view.cacheLayer());
        }
    }

    @Test void caveEntranceDoesNotFlickerAndWorldSwitchResetsDelay() {
        var tracker = new CaveMode.Tracker();
        assertEquals(MapSlice.SURFACE, tracker.update(MapSlice.SURFACE, 0));
        assertEquals(MapSlice.SURFACE, tracker.update(MapSlice.layered(50, 30), 1));
        assertTrue(tracker.update(MapSlice.layered(50, 30), 20).cave());
        assertTrue(tracker.update(MapSlice.SURFACE, 21).cave());
        assertEquals(MapSlice.SURFACE, tracker.update(MapSlice.SURFACE, 40));
        tracker.reset();
        assertEquals(MapSlice.FULL_CAVE, tracker.update(MapSlice.FULL_CAVE, 41));
    }

    @Test void negativeHeightLayersAndDepthRemainBounded() {
        assertEquals("layer_-1", MapSlice.layered(-1, 30).cacheLayer());
        assertEquals("layer_-2", MapSlice.layered(-17, 30).cacheLayer());
        assertEquals(-64, MapSlice.layered(-50, 30).scanBottom(-64));
        assertEquals(71, MapSlice.layered(100, 30).scanBottom(-64));
        assertEquals(319, MapSlice.FULL_CAVE.scanTop(320));
        assertEquals(-64, MapSlice.FULL_CAVE.scanBottom(-64));
        assertEquals(1, MapSlice.layered(0, -10).depth());
        assertEquals(64, MapSlice.layered(0, 100).depth());
    }

    @Test void preciseSliceSurvivesDiskCodecAndSurfaceRemainsSeparate() {
        var data = new ChunkMapData(3, -4);
        data.setSlice(MapSlice.layered(-12, 30));
        assertEquals(data.slice(), ChunkMapData.load(data.save()).slice());
        data.setSlice(MapSlice.FULL_CAVE);
        assertEquals(MapSlice.FULL_CAVE, ChunkMapData.load(data.save()).slice());
        assertEquals(MapSlice.SURFACE, ChunkMapData.load(new ChunkMapData(0, 0).save()).slice());
    }
}
