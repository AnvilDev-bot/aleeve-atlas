package dev.aleeve.atlas.config;

import dev.aleeve.atlas.AleeveAtlas;
import dev.anvilcraft.lib.v2.config.ConfigManager;

/// 服务端配置实例持有者（规格 §9.2）。
///
/// AnvilLib `ConfigManager.register` 每次调用都会新建配置对象，因此启动期注册一次并缓存，之后只读缓存。
public final class AtlasServerConfigs {
    private static AtlasServerConfig instance;

    private AtlasServerConfigs() {
    }

    public static AtlasServerConfig get() {
        if (instance == null) {
            instance = ConfigManager.register(AleeveAtlas.MOD_ID, AtlasServerConfig::new);
        }
        return instance;
    }
}
