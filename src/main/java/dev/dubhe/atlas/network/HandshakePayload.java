package dev.dubhe.atlas.network;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.config.NonTeamVisibility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/// 握手包（规格 §1 / §4.1 / §10）：服务端在玩家登录后主动下发。
///
/// 除确认协同模式外，同时下发非同队玩家可见性策略与雷达最大距离等锁定项。
public record HandshakePayload(String protocolVersion, boolean syncEnabled,
        int maxRadarDistance, NonTeamVisibility nonTeamVisibility)
        implements CustomPacketPayload {
    public static final Type<HandshakePayload> TYPE = new Type<>(AleeveAtlas.id("handshake"));

    public static final StreamCodec<FriendlyByteBuf, HandshakePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.protocolVersion, 32);
                buf.writeBoolean(payload.syncEnabled);
                buf.writeVarInt(payload.maxRadarDistance);
                buf.writeEnum(payload.nonTeamVisibility);
            },
            buf -> new HandshakePayload(buf.readUtf(32), buf.readBoolean(), buf.readVarInt(),
                    buf.readEnum(NonTeamVisibility.class)));

    @Override
    public Type<HandshakePayload> type() {
        return TYPE;
    }
}
