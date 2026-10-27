package dev.dubhe.map.map;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 地图取色与区块数据的基础单元测试。
class MapPaletteTest {
    @Test
    void terrainBrightnessFollowsRelativeHeight() {
        Assertions.assertEquals(MapColor.Brightness.HIGH,
                MapPalette.brightnessForTerrain(80, 70, 0, 0));
        Assertions.assertEquals(MapColor.Brightness.LOW,
                MapPalette.brightnessForTerrain(70, 80, 0, 0));
        Assertions.assertEquals(MapColor.Brightness.NORMAL,
                MapPalette.brightnessForTerrain(70, 70, 0, 0));
    }

    @Test
    void waterBrightnessFollowsDepthAndCheckerOffset() {
        Assertions.assertEquals(MapColor.Brightness.HIGH,
                MapPalette.brightnessForWater(0, 0, 0));
        Assertions.assertEquals(MapColor.Brightness.NORMAL,
                MapPalette.brightnessForWater(5, 0, 0));
        Assertions.assertEquals(MapColor.Brightness.LOW,
                MapPalette.brightnessForWater(10, 0, 0));
        Assertions.assertEquals(MapColor.Brightness.NORMAL,
                MapPalette.brightnessForWater(3, 1, 0));
    }

    @Test
    void waterDarkensWithDepth() {
        int shallow = MapPalette.colorOf(MapColor.WATER,
                MapPalette.brightnessForWater(1, 0, 0));
        int deep = MapPalette.colorOf(MapColor.WATER,
                MapPalette.brightnessForWater(20, 0, 0));
        Assertions.assertTrue(luminance(shallow) > luminance(deep));
    }

    @Test
    void colorIsOpaque() {
        int argb = MapPalette.colorOf(MapColor.GRASS, MapColor.Brightness.NORMAL);
        Assertions.assertEquals(0xFF, (argb >>> 24) & 0xFF);
    }

    @Test
    void noneColorStaysTransparent() {
        Assertions.assertEquals(MapPalette.TRANSPARENT,
                MapPalette.colorOf(MapColor.NONE, MapColor.Brightness.NORMAL));
    }

    @Test
    void chunkDataSurvivesRoundTrip() {
        ChunkMapData data = new ChunkMapData(3, -7);
        data.setPixel(0, 0, ChunkMapData.PixelState.of(Blocks.STONE.defaultBlockState()), 70);
        data.setPixel(15, 15, ChunkMapData.PixelState.of(Blocks.DIRT.defaultBlockState()), 40);
        data.setVersion(12345L);

        ChunkMapData restored = ChunkMapData.load(data.save());
        Assertions.assertEquals(3, restored.chunkX());
        Assertions.assertEquals(-7, restored.chunkZ());
        Assertions.assertEquals(12345L, restored.version());
        Assertions.assertEquals(Blocks.STONE, restored.stateAt(0, 0).state().getBlock());
        Assertions.assertEquals(70, restored.heightAt(0, 0));
        Assertions.assertEquals(Blocks.DIRT, restored.stateAt(15, 15).state().getBlock());
        Assertions.assertEquals(40, restored.heightAt(15, 15));
    }

    @Test
    void identicalStatesSharePaletteEntry() {
        ChunkMapData data = new ChunkMapData(0, 0);
        ChunkMapData.PixelState stone = ChunkMapData.PixelState.of(Blocks.STONE.defaultBlockState());
        data.setPixel(0, 0, stone, 64);
        data.setPixel(1, 0, stone, 65);
        Assertions.assertEquals(2, data.paletteSize());
        Assertions.assertSame(data.stateAt(0, 0), data.stateAt(1, 0));
    }

    @Test
    void emptyChunkIsEmpty() {
        Assertions.assertTrue(new ChunkMapData(0, 0).isEmpty());
        ChunkMapData data = new ChunkMapData(0, 0);
        data.setPixel(4, 4, ChunkMapData.PixelState.of(Blocks.STONE.defaultBlockState()), 64);
        Assertions.assertFalse(data.isEmpty());
    }

    private static int luminance(int argb) {
        return ((argb >>> 16) & 0xFF) + ((argb >>> 8) & 0xFF) + (argb & 0xFF);
    }
}
