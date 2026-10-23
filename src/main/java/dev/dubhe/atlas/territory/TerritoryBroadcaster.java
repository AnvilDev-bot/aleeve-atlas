package dev.dubhe.atlas.territory;

import dev.dubhe.atlas.api.territory.TerritorySyncPayload;
import dev.dubhe.atlas.api.territory.TerritoryGroup;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 领地覆层的服务端下发（规格 §7.1「通过本模组提供的服务端 API 下发全服或视野内领地区块变更包」）。
///
/// 外部模组在服务端注册领地后调用本类将变更推送给在线玩家；
/// 区块集合过大时自动拆分为多个负载。
public final class TerritoryBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/territory");

    private TerritoryBroadcaster() {
    }

    /// 向单个玩家下发领地全量数据。
    public static void sendTo(ServerPlayer player, TerritoryGroup territory) {
        SyncedTerritory synced = toSynced(territory);
        for (TerritorySyncPayload packet : TerritorySyncPayload.split(
                TerritorySyncPayload.Action.UPSERT, synced)) {
            PacketDistributor.sendToPlayer(player, packet);
        }
        LOGGER.debug("Sent territory {} ({} chunks) to {}",
                territory.territoryId(), territory.chunks().size(),
                player.getGameProfile().name());
    }

    /// 向全体在线玩家广播领地全量数据。
    public static void broadcast(TerritoryGroup territory) {
        SyncedTerritory synced = toSynced(territory);
        List<TerritorySyncPayload> packets = TerritorySyncPayload.split(
                TerritorySyncPayload.Action.UPSERT, synced);
        for (TerritorySyncPayload packet : packets) {
            PacketDistributor.sendToAllPlayers(packet);
        }
        LOGGER.debug("Broadcast territory {} ({} chunks, {} packet(s))",
                territory.territoryId(), territory.chunks().size(), packets.size());
    }

    /// 向全体在线玩家广播领地移除。
    public static void broadcastRemoval(TerritoryGroup territory) {
        PacketDistributor.sendToAllPlayers(new TerritorySyncPayload(
                TerritorySyncPayload.Action.REMOVE, toSynced(territory), 0, 1));
        LOGGER.debug("Broadcast removal of territory {}", territory.territoryId());
    }

    /// 服务端注册的领地表征为可传输对象。
    private static SyncedTerritory toSynced(TerritoryGroup territory) {
        return new SyncedTerritory(territory.territoryId(), territory.dimension(),
                territory.chunks(), territory.displayName(), territory.themeColorRgb(),
                territory.fillOpacity(), territory.zOrder());
    }
}
