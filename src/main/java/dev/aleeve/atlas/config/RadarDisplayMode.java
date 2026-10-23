package dev.aleeve.atlas.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// 实体雷达显示模式（规格 §6.1 / §9.1 `radar.displayMode`）。
public enum RadarDisplayMode implements TranslatableEnum {
    ICON,
    DOT,
    HYBRID
}
