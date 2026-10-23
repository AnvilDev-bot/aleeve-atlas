package dev.aleeve.atlas.client.map;

import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.api.territory.AtlasTerritory;
import dev.aleeve.atlas.config.AtlasClientConfigs;
import dev.aleeve.atlas.config.ConfigBounds;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// 客户端地图管理器的生命周期与调度（规格 §2.1）。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasClientMap {
    /// 定期落盘间隔（tick），避免异常退出丢失已探索数据。
    private static final int FLUSH_INTERVAL_TICKS = 200;

    private static ClientMapManager manager;
    private static int ticksSinceFlush;

    private AtlasClientMap() {
    }

    public static ClientMapManager manager() {
        if (manager == null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null && minecraft.player != null) {
                initManager();
            }
        }
        return manager;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (manager == null) {
            initManager();
        }
        manager.tick(minecraft.level);
        if (++ticksSinceFlush >= FLUSH_INTERVAL_TICKS) {
            ticksSinceFlush = 0;
            manager.flush();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (manager != null) {
            manager.flush();
            manager.clear();
            manager = null;
        }
        ticksSinceFlush = 0;
    }

    /// 创建地图管理器并同步领地锚点权重配置（规格 §9.1 territory.anchorWeight）。
    private static void initManager() {
        manager = new ClientMapManager();
        float lambda = (float) ConfigBounds.clamp(AtlasClientConfigs.CONFIG.territory.anchorWeight,
                0.3, 0.5);
        AtlasTerritory.clientRegistry().setAnchorLambda(lambda);
        DemoTerritory.registerIfDebugging();
    }
}
