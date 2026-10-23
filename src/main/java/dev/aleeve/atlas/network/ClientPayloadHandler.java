package dev.aleeve.atlas.network;

import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.AtlasSession;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 客户端负载处理（规格 §1 / §10）：握手成功后进入协同模式，退出服务器回退独立模式。
///
/// 注意：本类不得有引用客户端专属类型的静态字段或静态初始化块，以保证专用服务端注册期安全。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class ClientPayloadHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/client");

    private ClientPayloadHandler() {
    }

    public static void handleHandshake(HandshakePayload payload, IPayloadContext context) {
        if (!AleeveAtlas.PROTOCOL_VERSION.equals(payload.protocolVersion())) {
            LOGGER.warn("Server protocol version {} differs from client {}",
                    payload.protocolVersion(), AleeveAtlas.PROTOCOL_VERSION);
            return;
        }
        AtlasSession.onServerHandshake(payload.maxRadarDistance());
        LOGGER.info("Entered synchronized mode (sync enabled: {})", payload.syncEnabled());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AtlasSession.reset();
    }
}
