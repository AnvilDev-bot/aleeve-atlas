package dev.aleeve.atlas;

import dev.aleeve.atlas.config.AtlasServerConfigs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(AleeveAtlas.MOD_ID)
public final class AleeveAtlas {
    public static final String MOD_ID = "aleeve_atlas";

    /// 网络协议版本，握手包据此判定双端兼容性（规格 §10）。
    public static final String PROTOCOL_VERSION = "1";

    public AleeveAtlas(IEventBus modEventBus, ModContainer modContainer) {
        AtlasServerConfigs.get();
        if (FMLEnvironment.getDist().isClient()) {
            registerClientConfig();
        }
    }

    /// 客户端专属注册通过独立方法隔离，保证专用服务端不解析客户端配置类。
    private static void registerClientConfig() {
        dev.aleeve.atlas.config.AtlasClientConfigs.register();
    }

    public static net.minecraft.resources.Identifier id(String path) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
