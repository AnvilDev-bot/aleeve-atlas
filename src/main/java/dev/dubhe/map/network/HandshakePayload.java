package dev.dubhe.map.network;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.config.NonTeamVisibility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/// 握手包：服务端在玩家登录后主动下发。
///
/// 除确认协同模式外，同时下发雷达锁定项与服务端地图缓存标识符。
public record HandshakePayload(String protocolVersion, boolean syncEnabled,
        int maxRadarDistance, NonTeamVisibility nonTeamVisibility, String mapCacheId)
        implements CustomPacketPayload {
    public static final Type<HandshakePayload> TYPE = new Type<>(AleeveAtlas.id("handshake"));

    public static final StreamCodec<FriendlyByteBuf, HandshakePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.protocolVersion, 32);
                buf.writeBoolean(payload.syncEnabled);
                buf.writeVarInt(payload.maxRadarDistance);
                buf.writeEnum(payload.nonTeamVisibility);
                buf.writeUtf(payload.mapCacheId, 128);
            },
            buf -> new HandshakePayload(buf.readUtf(32), buf.readBoolean(), buf.readVarInt(),
                    buf.readEnum(NonTeamVisibility.class), buf.readUtf(128)));

    @Override
    public Type<HandshakePayload> type() {
        return TYPE;
    }
}
