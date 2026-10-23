package dev.dubhe.atlas.config;

import dev.dubhe.atlas.AleeveAtlas;
import dev.anvilcraft.lib.v2.config.ConfigManager;

/// 客户端配置持有者（规格 §9.1）。
///
/// 只在物理客户端被引用：静态字段在类初始化时完成注册，由 `@Mod` 构造期按 Dist 触发。
public final class AtlasClientConfigs {
    /// 客户端配置实例（规格 §9.1）。
    public static final AtlasClientConfig CONFIG =
            ConfigManager.register(AleeveAtlas.MOD_ID, AtlasClientConfig::new);

    private AtlasClientConfigs() {
    }

    /// 触发本类静态初始化，从而完成配置注册。
    public static void load() {
    }
}