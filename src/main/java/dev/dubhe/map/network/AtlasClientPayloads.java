package dev.dubhe.map.network;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.api.territory.TerritorySyncPayload;
import dev.dubhe.map.client.territory.ClientTerritorySync;
import dev.dubhe.map.client.waypoint.ClientWaypointSync;
import dev.dubhe.map.ping.ClientPings;
import dev.dubhe.map.ping.PingBroadcastPayload;
import dev.dubhe.map.sync.ClientMapSync;
import dev.dubhe.map.sync.ClientPlayerPositions;
import dev.dubhe.map.waypoint.WaypointSyncPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/// 将客户端处理器限制在物理客户端，避免专用服务器解析远端玩家等客户端类型。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasClientPayloads {
    private AtlasClientPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(HandshakePayload.TYPE, ClientPayloadHandler::handleHandshake);
        event.register(PingBroadcastPayload.TYPE, ClientPings::handle);
        event.register(WaypointSyncPayload.TYPE, ClientWaypointSync::handle);
        event.register(PlayerPositionPayload.TYPE, ClientPlayerPositions::handle);
        event.register(TeamSightingsPayload.TYPE, ClientPlayerPositions::handleSightings);
        event.register(ChunkDigestPayload.TYPE, ClientMapSync::handleDigest);
        event.register(ChunkDeltaPayload.TYPE, HandlerThread.NETWORK, ClientMapSync::handleDelta);
        event.register(TerritorySyncPayload.TYPE, ClientTerritorySync::handle);
    }
}
