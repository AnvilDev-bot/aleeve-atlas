package dev.dubhe.map.network;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.api.territory.TerritorySyncPayload;
import dev.dubhe.map.client.territory.ClientTerritorySync;
import dev.dubhe.map.client.waypoint.ClientWaypointSync;
import dev.dubhe.map.ping.ClientPings;
import dev.dubhe.map.ping.PingBroadcastPayload;
import dev.dubhe.map.ping.PingPayload;
import dev.dubhe.map.ping.ServerPingHandler;
import dev.dubhe.map.sync.ClientMapSync;
import dev.dubhe.map.sync.ClientPlayerPositions;
import dev.dubhe.map.sync.PlayerPositionHandler;
import dev.dubhe.map.sync.ServerMapSync;
import dev.dubhe.map.waypoint.WaypointSyncHandler;
import dev.dubhe.map.waypoint.WaypointSyncPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/// 全部自定义负载注册。
///
/// 统一在此声明协议版本与每类负载的方向、编解码器与处理器，注册为 `optional()`：
/// 客户端未安装本模组时服务端不会发送，反之亦然。
///
/// 同一 payload 类型若两个方向都要注册，必须使用 `playBidirectional`：
/// NeoForge 不允许同一类型重复注册。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class AtlasPayloads {
    private AtlasPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // `optional()` 返回新副本，必须接收返回值。
        PayloadRegistrar registrar = event.registrar(AleeveAtlas.PROTOCOL_VERSION).optional();

        // 握手：服务端下发协同模式、锁定项和地图缓存标识。
        registrar.playToClient(HandshakePayload.TYPE, HandshakePayload.STREAM_CODEC,
                ClientPayloadHandler::handleHandshake);

        // 标点：上报与广播使用不同 payload 类型，避免方向冲突。
        registrar.playToServer(PingPayload.TYPE, PingPayload.STREAM_CODEC,
                ServerPingHandler::handle);
        registrar.playToClient(PingBroadcastPayload.TYPE, PingBroadcastPayload.STREAM_CODEC,
                ClientPings::handle);

        // 路径点同步：收发策略由客户端配置决定。
        registrar.playBidirectional(WaypointSyncPayload.TYPE, WaypointSyncPayload.STREAM_CODEC,
                WaypointSyncHandler::handle, ClientWaypointSync::handle);

        // 在线玩家位置：客户端周期上报，服务端转发快照与移除消息。
        registrar.playBidirectional(PlayerPositionPayload.TYPE, PlayerPositionPayload.STREAM_CODEC,
                PlayerPositionHandler::handle, ClientPlayerPositions::handle);
        registrar.playBidirectional(TeamSightingsPayload.TYPE, TeamSightingsPayload.STREAM_CODEC,
                PlayerPositionHandler::handleSightings, ClientPlayerPositions::handleSightings);

        // 地图增量同步：摘要双向转发（服务端聚合后转发）。
        registrar.playBidirectional(ChunkDigestPayload.TYPE, ChunkDigestPayload.STREAM_CODEC,
                ServerMapSync::handleDigest, ClientMapSync::handleDigest);

        // 增量分片解压属密集计算，注册为网络线程执行：
        // 客户端在处理线程完成解压后入队，由主线程的 tick 出队写缓存。
        PayloadRegistrar deltaRegistrar = event.registrar(AleeveAtlas.PROTOCOL_VERSION)
                .executesOn(HandlerThread.NETWORK)
                .optional();
        deltaRegistrar.playBidirectional(ChunkDeltaPayload.TYPE,
                ChunkDeltaPayload.STREAM_CODEC, ServerMapSync::handleDelta,
                ClientMapSync::handleDelta);

        // 领地下发：服务端 API 推送、客户端重组后写入注册表。
        registrar.playToClient(TerritorySyncPayload.TYPE, TerritorySyncPayload.STREAM_CODEC,
                ClientTerritorySync::handle);
    }
}
