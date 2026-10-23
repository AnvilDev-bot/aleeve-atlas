package dev.dubhe.atlas.client;

import dev.dubhe.atlas.sync.ClientMapSync;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.AtlasSession;
import dev.dubhe.atlas.client.map.AtlasWorldMap;
import dev.dubhe.atlas.client.waypoint.ClientWaypoints;
import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.ping.ClientPings;
import dev.dubhe.atlas.config.ConfigBounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
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
