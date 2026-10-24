package dev.dubhe.atlas.client;

import dev.dubhe.atlas.sync.ClientMapSync;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.client.map.AtlasWorldMap;
import dev.dubhe.atlas.client.waypoint.ClientWaypoints;
import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.ping.ClientPings;
import dev.dubhe.atlas.config.ConfigBounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// 客户端 tick 入口（规格 §8）：在 `ClientTickEvent.Post` 中 while 消费键位。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasClientTick {
    private AtlasClientTick() {
    }

    /// 自检只执行一次。
    private static boolean scrollSelfTestDone;
    /// 联机自检只执行一次。
    private static boolean connectSelfTestDone;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // 自动化验收：打开世界地图并连续平移，验证像素搬移路径（正常游玩不触发）。
        if (!scrollSelfTestDone && System.getenv("ALEEVE_ATLAS_SCROLL_SELFTEST") != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null
                    && minecraft.screen == null) {
                scrollSelfTestDone = true;
                AtlasWorldMap.open();
            }
        }
        // 自动化验收：直接连接指定服务器，验证双端握手与协同模式（正常游玩不触发）。
        String serverAddress = System.getenv("ALEEVE_ATLAS_CONNECT_SERVER");
        if (!connectSelfTestDone && serverAddress != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                connectSelfTestDone = true;
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                        minecraft.screen,
                        minecraft,
                        ServerAddress.parseString(serverAddress),
                        new net.minecraft.client.multiplayer.ServerData(
                                "atlas_selftest", serverAddress,
                                net.minecraft.client.multiplayer.ServerData.Type.OTHER),
                        false,
                        null);
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // 标点由外部模组经 API 注入，本模组只负责生命周期推进与渲染（规格 §3.2.2）。
        ClientPings.tick();
        // 地图增量分片按 tick 限速写入本地缓存（规格 §3.3 / §12.1）。
        ClientMapSync.tick();
        if (minecraft.screen != null) {
            return;
        }
        while (AtlasKeyMappings.OPEN_WORLD_MAP.consumeClick()) {
            AtlasWorldMap.open();
        }
        while (AtlasKeyMappings.NEW_WAYPOINT.consumeClick()) {
            var waypoint = ClientWaypoints.createHere(Component.translatable(
                    "aleeve_atlas.waypoint.default_name").getString());
            if (waypoint != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("aleeve_atlas.message.waypoint_created",
                                waypoint.pos().getX(), waypoint.pos().getY(),
                                waypoint.pos().getZ()).withStyle(ChatFormatting.GREEN),
                        true);
            }
        }
    }

    /// 供 HUD 读取的缩放倍率，已按规格 §9.1 钳制到 `0.5 ~ 4.0`。
    public static float minimapScale() {
        return (float) ConfigBounds.clamp(AtlasClientConfigs.CONFIG.minimap.scale, 0.5, 4.0);
    }
}
