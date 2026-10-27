package dev.dubhe.map.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/// 群系地形阴影配色的行为测试。
class BiomeShadePaletteTest {
    @Test
    void waterBlendUsesDepthAsMixRatio() {
        int water = 0xFF204080;
        int ground = 0xFF806040;
        Assertions.assertEquals(ground, BiomeShadePalette.blendWaterColor(water, ground, 0));
        Assertions.assertEquals(water, BiomeShadePalette.blendWaterColor(water, ground, 15));
    }

    @Test
    void flatTerrainHasNoDirectionalShadow() {
        int[] heights = new int[ChunkMapData.PIXELS];
        Arrays.fill(heights, 64);
        Assertions.assertArrayEquals(new int[ChunkMapData.PIXELS],
                BiomeShadePalette.computeLighting(heights));
    }

    @Test
    void higherNorthwestTerrainCreatesDirectionalShadow() {
        int[] heights = new int[ChunkMapData.PIXELS];
        Arrays.fill(heights, 64);
        heights[0] = 80;
        int[] lighting = BiomeShadePalette.computeLighting(heights);
        Assertions.assertTrue(lighting[1] > 0);
        Assertions.assertTrue(lighting[ChunkMapData.SIZE] > 0);
        Assertions.assertTrue(lighting[0] <= 0);
    }

    @Test
    void shadingPreservesTransparentPixels() {
        ChunkMapData data = new ChunkMapData(0, 0);
        BiomeShadePalette.applyTerrainShading(data);
        Assertions.assertTrue(data.isEmpty());
    }
}
