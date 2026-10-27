package dev.dubhe.map.network;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.sync.PlayerPositionHandler;
import dev.dubhe.map.sync.ServerMapSync;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/// 服务端负载处理：玩家登录后主动下发握手包。
///
/// 服务端未安装本模组时不会下发，客户端保持独立模式；客户端未安装时 NeoForge 跳过 optional 载荷。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class ServerPayloadHandler {
    private ServerPayloadHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var config = AleeveAtlas.CONFIG;
        String mapCacheId = MapCacheIdentity.get(player.level().getServer().overworld()).id();
        PacketDistributor.sendToPlayer(player,
                new HandshakePayload(AleeveAtlas.PROTOCOL_VERSION, config.sync.enabled,
                        config.radar.maxRadarDistance, config.radar.nonTeamPlayerVisibility,
                        mapCacheId));
        PlayerPositionHandler.sendSnapshot(player);
    }

    /// 玩家断开后清理其未收齐的摘要批次聚合，避免服务端内存残留。
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerMapSync.clear(player.getUUID());
        PlayerPositionHandler.remove(player);
    }
}
