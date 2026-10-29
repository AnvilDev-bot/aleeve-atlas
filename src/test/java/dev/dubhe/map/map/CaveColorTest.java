package dev.dubhe.map.map;

import dev.dubhe.map.config.AtlasClientConfigs;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaveColorTest {
    @Test void depthShadingPreservesSurfaceFullCavesAndEmissiveBlocks() {
        boolean previous = AtlasClientConfigs.CONFIG.caves.depthShading;
        try {
            AtlasClientConfigs.CONFIG.caves.depthShading = true;
            var data = new ChunkMapData(0, 0);
            data.setPixel(0, 0, ChunkMapData.PixelState.of(Blocks.STONE.defaultBlockState()), 50);
            int surface = data.colorAt(0, 0);
            data.setSlice(MapSlice.FULL_CAVE);
            assertEquals(surface, data.colorAt(0, 0));
            data.setSlice(MapSlice.layered(60, 30));
            int shallow = data.colorAt(0, 0);
            data.setSlice(MapSlice.layered(75, 30));
            int deep = data.colorAt(0, 0);
            assertTrue((deep & 255) < (shallow & 255));
            assertTrue((shallow & 255) < (surface & 255));
            assertEquals(surface >>> 24, deep >>> 24);
            AtlasClientConfigs.CONFIG.caves.depthShading = false;
            assertEquals(surface, data.colorAt(0, 0));
            AtlasClientConfigs.CONFIG.caves.depthShading = true;
            data.setPixel(0, 0, ChunkMapData.PixelState.of(Blocks.GLOWSTONE.defaultBlockState()), 50);
            int glowing = data.colorAt(0, 0);
            data.setSlice(MapSlice.SURFACE);
            assertEquals(glowing, data.colorAt(0, 0));
        } finally {
            AtlasClientConfigs.CONFIG.caves.depthShading = previous;
        }
    }
}
