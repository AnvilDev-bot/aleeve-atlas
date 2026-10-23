package dev.aleeve.atlas.config;

import dev.aleeve.atlas.AleeveAtlas;
import dev.anvilcraft.lib.v2.config.ConfigManager;

/// 客户端配置实例持有者（规格 §9.1）。
///
/// 本类只在物理客户端被引用；`ConfigManager.register` 每次调用都会新建对象，故注册一次并缓存。
public final class AtlasClientConfigs {
    private static AtlasClientConfig instance;

    private AtlasClientConfigs() {
    }

    public static void register() {
        if (instance == null) {
            instance = ConfigManager.register(AleeveAtlas.MOD_ID, AtlasClientConfig::new);
        }
    }

    public static AtlasClientConfig get() {
        return instance;
    }
}
