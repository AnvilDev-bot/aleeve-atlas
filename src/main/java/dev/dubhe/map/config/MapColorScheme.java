package dev.dubhe.map.config;

import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;

/// 地图着色方案。
public enum MapColorScheme implements TranslatableEnum {
    VANILLA("vanilla"),
    BIOME_SHADE("biome_shade");

    private final String id;

    MapColorScheme(String id) {
        this.id = id;
    }

    /// 缓存目录后缀使用的稳定标识。
    public String id() {
        return this.id;
    }
}
