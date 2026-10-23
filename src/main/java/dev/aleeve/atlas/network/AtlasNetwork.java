package dev.aleeve.atlas.network;

import dev.aleeve.atlas.AleeveAtlas;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/// 网络负载注册（规格 §10）：统一走 NeoForge Payload 体系，协议版本经 `registrar(version)` 声明。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class AtlasNetwork {
    private AtlasNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AleeveAtlas.PROTOCOL_VERSION);
        registrar.playToClient(
                HandshakePayload.TYPE,
                HandshakePayload.STREAM_CODEC,
                ClientPayloadHandler::handleHandshake);
        registrar.optional();
    }
}
