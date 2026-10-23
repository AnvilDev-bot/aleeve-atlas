package dev.aleeve.atlas.network;

import dev.aleeve.atlas.AleeveAtlas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/// 握手包（规格 §1 / §10）：服务端在玩家登录后主动下发，客户端据此确认进入协同模式。
///
/// 服务端→客户端单向，注册为 `optional()`，客户端未安装本模组时 NeoForge 不会发送。
public record HandshakePayload(String protocolVersion, boolean syncEnabled,
        int maxRadarDistance) implements CustomPacketPayload {
    public static final Type<HandshakePayload> TYPE = new Type<>(AleeveAtlas.id("handshake"));

    public static final StreamCodec<FriendlyByteBuf, HandshakePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.protocolVersion, 32);
                buf.writeBoolean(payload.syncEnabled);
                buf.writeVarInt(payload.maxRadarDistance);
            },
            buf -> new HandshakePayload(buf.readUtf(32), buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<HandshakePayload> type() {
        return TYPE;
    }
}
