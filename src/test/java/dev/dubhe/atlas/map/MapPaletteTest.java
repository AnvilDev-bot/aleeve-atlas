package dev.dubhe.atlas.map;

import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 地图取色与区块数据的基础单元测试。
class MapPaletteTest {
    @Test
    void brightnessRisesWithHeight() {
        // 档位边界：delta <= -32 最低档，(-32, 0] 低档，(0, 32] 正常档，> 32 高档。
        Assertions.assertTrue(rank(MapPalette.brightnessForHeight(20, 63))
                < rank(MapPalette.brightnessForHeight(64, 63)));
        Assertions.assertTrue(rank(MapPalette.brightnessForHeight(64, 63))
                < rank(MapPalette.brightnessForHeight(96, 63)));
        // 更低的海拔必须更暗（40 高于 20）。
        Assertions.assertTrue(rank(MapPalette.brightnessForHeight(20, 63))
                < rank(MapPalette.brightnessForHeight(40, 63)));
    }

    @Test
    void colorIsOpaque() {
        int argb = MapPalette.colorOf(MapColor.GRASS, MapColor.Brightness.NORMAL);
        Assertions.assertEquals(0xFF, (argb >>> 24) & 0xFF);
    }

    @Test
    void waterDarkensWithDepth() {
        int shallow = MapPalette.waterColor(MapColor.WATER, 1);
        int deep = MapPalette.waterColor(MapColor.WATER, 20);
        Assertions.assertTrue(luminance(shallow) > luminance(deep));
    }

    @Test
    void chunkDataSurvivesRoundTrip() {
        ChunkMapData data = new ChunkMapData(3, -7);
        data.setPixel(0, 0, 0xFF112233, 70);
        data.setPixel(15, 15, 0xFF445566, 40);
        data.setVersion(12345L);

        ChunkMapData restored = ChunkMapData.load(data.save());
        Assertions.assertEquals(3, restored.chunkX());
        Assertions.assertEquals(-7, restored.chunkZ());
        Assertions.assertEquals(12345L, restored.version());
        Assertions.assertEquals(0xFF112233, restored.colorAt(0, 0));
        Assertions.assertEquals(70, restored.heightAt(0, 0));
        Assertions.assertEquals(0xFF445566, restored.colorAt(15, 15));
        Assertions.assertEquals(40, restored.heightAt(15, 15));
    }

    @Test
    void emptyChunkIsEmpty() {
        Assertions.assertTrue(new ChunkMapData(0, 0).isEmpty());
        ChunkMapData data = new ChunkMapData(0, 0);
        data.setPixel(4, 4, 0xFF000000, 64);
        Assertions.assertFalse(data.isEmpty());
    }

    private static int rank(MapColor.Brightness brightness) {
        // Brightness 枚举声明顺序为 LOW/NORMAL/HIGH/LOWEST，不能按 ordinal 排序；modifier 才是实际明暗系数。
        return brightness.modifier;
    }

    private static int luminance(int argb) {
        return ((argb >>> 16) & 0xFF) + ((argb >>> 8) & 0xFF) + (argb & 0xFF);
    }

    @Test
    void lightingClampsToValidRange() {
        // 超出 0~15 的光照值不应产生越界百分比，极端值必须与边界一致。
        int base = 0xFF808080;
        Assertions.assertEquals(MapPalette.applyLighting(base, 0), MapPalette.applyLighting(base, -5));
        Assertions.assertEquals(MapPalette.applyLighting(base, 15), MapPalette.applyLighting(base, 99));
    }

    @Test
    void lightingKeepsColorOpaque() {
        int lit = MapPalette.applyLighting(0xFF808080, 0);
        Assertions.assertEquals(0xFF, (lit >>> 24) & 0xFF, "光照压暗不得改变不透明度");
    }

    @Test
    void blendClampsRatioToUnitRange() {
        int base = 0xFF204060;
        int tint = 0xFFA0C0E0;
        Assertions.assertEquals(base & 0xFFFFFF, MapPalette.blend(base, tint, 0.0F) & 0xFFFFFF);
        Assertions.assertEquals(tint & 0xFFFFFF, MapPalette.blend(base, tint, 1.0F) & 0xFFFFFF);
        // 越界比例按边界处理，不得插值出越界通道值。
        Assertions.assertEquals(MapPalette.blend(base, tint, 0.0F), MapPalette.blend(base, tint, -2.0F));
        Assertions.assertEquals(MapPalette.blend(base, tint, 1.0F), MapPalette.blend(base, tint, 5.0F));
    }

    @Test
    void blendResultIsOpaque() {
        Assertions.assertEquals(0xFF, (MapPalette.blend(0x00102030, 0x00A0B0C0, 0.5F) >>> 24) & 0xFF);
    }
}
