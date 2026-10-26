package dev.dubhe.atlas.network;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.sync.PlayerPositionHandler;
import dev.dubhe.atlas.sync.ServerMapSync;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 服务端负载处理：玩家登录后主动下发握手包。
///
/// 服务端未安装本模组时不会下发，客户端保持独立模式；客户端未安装时 NeoForge 跳过 optional 载荷。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class ServerPayloadHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/server");

    private ServerPayloadHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var config = AleeveAtlas.CONFIG;
        PacketDistributor.sendToPlayer(player,
                new HandshakePayload(AleeveAtlas.PROTOCOL_VERSION, config.sync.enabled,
                        config.radar.maxRadarDistance, config.radar.nonTeamPlayerVisibility));
        LOGGER.debug("Sent handshake to {}", player.getGameProfile().name());
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
        LOGGER.debug("Cleared pending map digests for {}",
                player.getGameProfile().name());
    }
}
