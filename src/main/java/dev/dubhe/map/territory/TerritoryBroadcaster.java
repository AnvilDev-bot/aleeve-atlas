package dev.dubhe.map.territory;

import dev.dubhe.map.api.territory.TerritoryGroup;
import dev.dubhe.map.api.territory.TerritorySyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/// 领地覆层的服务端下发。
///
/// 外部模组在服务端注册领地后调用本类将变更推送给在线玩家；
/// 区块集合过大时自动拆分为多个负载。
public final class TerritoryBroadcaster {
    private TerritoryBroadcaster() {
    }

    /// 向单个玩家下发领地全量数据。
    public static void sendTo(ServerPlayer player, TerritoryGroup territory) {
        SyncedTerritory synced = toSynced(territory);
        for (TerritorySyncPayload packet : TerritorySyncPayload.split(
                TerritorySyncPayload.Action.UPSERT, synced)) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    /// 向全体在线玩家广播领地全量数据。
    public static void broadcast(TerritoryGroup territory) {
        SyncedTerritory synced = toSynced(territory);
        List<TerritorySyncPayload> packets = TerritorySyncPayload.split(
                TerritorySyncPayload.Action.UPSERT, synced);
        for (TerritorySyncPayload packet : packets) {
            PacketDistributor.sendToAllPlayers(packet);
        }
    }

    /// 向全体在线玩家广播领地移除。
    public static void broadcastRemoval(TerritoryGroup territory) {
        PacketDistributor.sendToAllPlayers(new TerritorySyncPayload(
                TerritorySyncPayload.Action.REMOVE, toSynced(territory), 0, 1));
    }

    /// 服务端注册的领地表征为可传输对象。
    private static SyncedTerritory toSynced(TerritoryGroup territory) {
        return new SyncedTerritory(territory.territoryId(), territory.dimension(),
                territory.chunks(), territory.displayName(), territory.themeColorRgb(),
                territory.fillOpacity(), territory.borderStyle(), territory.tooltip(),
                territory.zOrder());
    }
}
