package dev.aleeve.atlas.map;

import net.minecraft.world.level.material.MapColor;

/// 地图像素取色（规格 §2.1：地形渲染要素）。
///
/// 基于原版 `MapColor` 调色板，并按相对高度调整明暗，形成可辨识的地形起伏。
public final class MapPalette {
    /// 透明像素（未探索/无数据）。
    public static final int TRANSPARENT = 0;

    private MapPalette() {
    }

    /// 按高度相对海平面的偏移选择明暗档位：越高越亮，越低越暗。
    public static MapColor.Brightness brightnessForHeight(int y, int seaLevel) {
        int delta = y - seaLevel;
        if (delta <= -32) {
            return MapColor.Brightness.LOWEST;
        }
        if (delta <= 0) {
            return MapColor.Brightness.LOW;
        }
        if (delta <= 32) {
            return MapColor.Brightness.NORMAL;
        }
        return MapColor.Brightness.HIGH;
    }

    /// 把方块的地图色按明暗档位转换为不透明 ARGB。
    public static int colorOf(MapColor mapColor, MapColor.Brightness brightness) {
        return mapColor.calculateARGBColor(brightness) | 0xFF000000;
    }

    /// 水体颜色：取水的 map 色并按深度压暗，用于水深表现。
    public static int waterColor(MapColor mapColor, int depth) {
        int bright = mapColor.calculateARGBColor(MapColor.Brightness.LOW) | 0xFF000000;
        int factor = Math.max(45, 100 - Math.min(depth, 24) * 2);
        return scale(bright, factor);
    }

    /// 按百分比缩放 RGB 通道，保留 alpha。
    public static int scale(int argb, int percent) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = r * percent / 100;
        g = g * percent / 100;
        b = b * percent / 100;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /// 植被类方块按生物群系染色（规格 §2.1 地形渲染要素）。
    ///
    /// 仅对草地、树叶等植被方块生效；其它方块原样返回。
    public static int tintWithBiome(int argb, net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos,
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        var color = state.getBlock().defaultMapColor();
        if (color != net.minecraft.world.level.material.MapColor.GRASS
                && color != net.minecraft.world.level.material.MapColor.PLANT
                && color != net.minecraft.world.level.material.MapColor.COLOR_GREEN) {
            return argb;
        }
        int tint = biome.value().getGrassColor(pos.getX(), pos.getZ());
        return blend(argb, tint, 0.45F);
    }

    /// 光照阴影：按方块光照等级调整明暗（规格 §2.1）。
    ///
    /// 光照 15 保持原色，光照 0 压暗到 55%，避免全亮导致地形缺乏层次。
    public static int applyLighting(int argb, int lightLevel) {
        int clamped = Math.max(0, Math.min(15, lightLevel));
        int percent = 55 + clamped * 3;
        return scale(argb, percent);
    }

    /// 按比例混合基色与染色，`ratio` 为染色占比（0~1）。
    public static int blend(int baseArgb, int tintRgb, float ratio) {
        float t = Math.max(0.0F, Math.min(1.0F, ratio));
        int br = (baseArgb >>> 16) & 0xFF;
        int bg = (baseArgb >>> 8) & 0xFF;
        int bb = baseArgb & 0xFF;
        int tr = (tintRgb >>> 16) & 0xFF;
        int tg = (tintRgb >>> 8) & 0xFF;
        int tb = tintRgb & 0xFF;
        int r = Math.round(br * (1.0F - t) + tr * t);
        int g = Math.round(bg * (1.0F - t) + tg * t);
        int b = Math.round(bb * (1.0F - t) + tb * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
