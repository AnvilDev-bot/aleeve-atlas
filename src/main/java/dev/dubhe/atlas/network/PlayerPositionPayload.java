package dev.dubhe.atlas.network;

import dev.dubhe.atlas.AleeveAtlas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/// 在线玩家位置同步载荷。
///
/// 客户端周期上报自身位置，服务端转发给其他在线玩家；服务端不持久化这些位置。
public record PlayerPositionPayload(UUID playerId, String playerName, String dimensionId,
        double x, double y, double z, boolean removal) implements CustomPacketPayload {
    public static final Type<PlayerPositionPayload> TYPE =
            new Type<>(AleeveAtlas.id("player_position"));

    public static final StreamCodec<FriendlyByteBuf, PlayerPositionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.playerId);
                        buf.writeUtf(payload.playerName, 64);
                        buf.writeUtf(payload.dimensionId, 256);
                        buf.writeDouble(payload.x);
                        buf.writeDouble(payload.y);
                        buf.writeDouble(payload.z);
                        buf.writeBoolean(payload.removal);
                    },
                    buf -> new PlayerPositionPayload(buf.readUUID(), buf.readUtf(64),
                            buf.readUtf(256), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readBoolean()));

    @Override
    public Type<PlayerPositionPayload> type() {
        return TYPE;
    }
}
