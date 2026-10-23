package dev.dubhe.atlas.client.map;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.api.territory.AtlasTerritory;
import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.config.ConfigBounds;
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
            // 释放动态纹理，避免重连时残留 GPU 纹理占用显存（规格 §12.1）。
            manager.releaseTexture();
            manager = null;
        }
        AtlasWorldMap.release();
        ticksSinceFlush = 0;
    }

    /// 配置重载时同步领地锚点权重（规格 §11 配置热重载）。
    @SubscribeEvent
    public static void onConfigReload(
            net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        applyAnchorWeight();
    }

    /// 把 `territory.anchorWeight` 应用到领地注册表（规格 §9.1）。
    private static void applyAnchorWeight() {
        float lambda = (float) ConfigBounds.clamp(AtlasClientConfigs.CONFIG.territory.anchorWeight,
                0.3, 0.5);
        AtlasTerritory.clientRegistry().setAnchorLambda(lambda);
    }

    /// 创建地图管理器并同步领地锚点权重配置（规格 §9.1 territory.anchorWeight）。
    private static void initManager() {
        manager = new ClientMapManager();
        applyAnchorWeight();
        DemoTerritory.registerIfDebugging();
    }
}
