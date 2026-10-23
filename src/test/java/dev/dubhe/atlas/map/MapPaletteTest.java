package dev.dubhe.atlas.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;

/// 地图取色与区块数据的基础单元测试（规格 §2.1）。
class MapPaletteTest {
    @Test
    void brightnessRisesWithHeight() {
        // 档位边界：delta <= -32 最低档，(-32, 0] 低档，(0, 32] 正常档，> 32 高档。
        assertTrue(rank(MapPalette.brightnessForHeight(20, 63))
                < rank(MapPalette.brightnessForHeight(64, 63)));
        assertTrue(rank(MapPalette.brightnessForHeight(64, 63))
                < rank(MapPalette.brightnessForHeight(96, 63)));
        // 更低的海拔必须更暗（40 高于 20）。
        assertTrue(rank(MapPalette.brightnessForHeight(20, 63))
                < rank(MapPalette.brightnessForHeight(40, 63)));
    }

    @Test
    void colorIsOpaque() {
        int argb = MapPalette.colorOf(MapColor.GRASS, MapColor.Brightness.NORMAL);
        assertEquals(0xFF, (argb >>> 24) & 0xFF);
    }

    @Test
    void waterDarkensWithDepth() {
        int shallow = MapPalette.waterColor(MapColor.WATER, 1);
        int deep = MapPalette.waterColor(MapColor.WATER, 20);
        assertTrue(luminance(shallow) > luminance(deep));
    }

    @Test
    void chunkDataSurvivesRoundTrip() {
        ChunkMapData data = new ChunkMapData(3, -7);
        data.setPixel(0, 0, 0xFF112233, 70);
        data.setPixel(15, 15, 0xFF445566, 40);
        data.setVersion(12345L);

        ChunkMapData restored = ChunkMapData.load(data.save());
        assertEquals(3, restored.chunkX());
        assertEquals(-7, restored.chunkZ());
        assertEquals(12345L, restored.version());
        assertEquals(0xFF112233, restored.colorAt(0, 0));
        assertEquals(70, restored.heightAt(0, 0));
        assertEquals(0xFF445566, restored.colorAt(15, 15));
        assertEquals(40, restored.heightAt(15, 15));
    }

    @Test
    void emptyChunkIsEmpty() {
        assertTrue(new ChunkMapData(0, 0).isEmpty());
        ChunkMapData data = new ChunkMapData(0, 0);
        data.setPixel(4, 4, 0xFF000000, 64);
        assertFalse(data.isEmpty());
    }

    private static int rank(MapColor.Brightness brightness) {
        // Brightness 枚举声明顺序为 LOW/NORMAL/HIGH/LOWEST，不能按 ordinal 排序；modifier 才是实际明暗系数。
        return brightness.modifier;
    }

    private static int luminance(int argb) {
        return ((argb >>> 16) & 0xFF) + ((argb >>> 8) & 0xFF) + (argb & 0xFF);
    }
}
