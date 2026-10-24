package dev.dubhe.atlas.network;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.api.territory.TerritorySyncPayload;
import dev.dubhe.atlas.client.territory.ClientTerritorySync;
import dev.dubhe.atlas.client.waypoint.ClientWaypointSync;
import dev.dubhe.atlas.ping.ClientPings;
import dev.dubhe.atlas.ping.PingBroadcastPayload;
import dev.dubhe.atlas.ping.PingPayload;
import dev.dubhe.atlas.ping.ServerPingHandler;
import dev.dubhe.atlas.sync.ClientMapSync;
import dev.dubhe.atlas.sync.ServerMapSync;
import dev.dubhe.atlas.waypoint.WaypointSyncHandler;
import dev.dubhe.atlas.waypoint.WaypointSyncPayload;
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

        // 握手：服务端在玩家登录后下发，含协同模式与可见性锁定项。
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

        // 地图增量同步：摘要双向转发（服务端聚合后转发）。
        registrar.playBidirectional(ChunkDigestPayload.TYPE, ChunkDigestPayload.STREAM_CODEC,
                ServerMapSync::handleDigest, ClientMapSync::handleDigest);

        // 增量分片解压属密集计算，注册为网络线程执行：
        // 处理器在网络线程完成解压，再用 context.enqueueWork 切回主线程写缓存。
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
