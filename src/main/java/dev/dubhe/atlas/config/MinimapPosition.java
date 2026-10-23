package dev.dubhe.atlas.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// 小地图在屏幕上的预设位置（规格 §9.1 `minimap.position`）。
public enum MinimapPosition implements TranslatableEnum {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CUSTOM
}
