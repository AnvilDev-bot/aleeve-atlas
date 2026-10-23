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
}
