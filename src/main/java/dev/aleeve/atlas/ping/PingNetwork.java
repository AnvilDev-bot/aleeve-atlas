package dev.aleeve.atlas.ping;

import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.network.HandshakePayload;
import dev.aleeve.atlas.network.ClientPayloadHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/// 标点与握手负载注册（规格 §10）。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class PingNetwork {
    private PingNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AleeveAtlas.PROTOCOL_VERSION);
        registrar.playToClient(HandshakePayload.TYPE, HandshakePayload.STREAM_CODEC,
                ClientPayloadHandler::handleHandshake);
        registrar.playToServer(PingPayload.TYPE, PingPayload.STREAM_CODEC,
                ServerPingHandler::handle);
        registrar.playToClient(PingBroadcastPayload.TYPE, PingBroadcastPayload.STREAM_CODEC,
                ClientPings::handle);
        registrar.optional();
    }
}
