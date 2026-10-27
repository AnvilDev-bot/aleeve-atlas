package dev.dubhe.map.ping;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/// 标点广播负载。
public record PingBroadcastPayload(MapPingData ping) implements CustomPacketPayload {
    public static final Type<PingBroadcastPayload> TYPE =
            new Type<>(AleeveAtlas.id("ping_broadcast"));

    public static final StreamCodec<FriendlyByteBuf, PingBroadcastPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        PingPayload.STREAM_CODEC.encode(buf, new PingPayload(payload.ping));
                    },
                    buf -> new PingBroadcastPayload(
                            PingPayload.STREAM_CODEC.decode(buf).ping()));

    @Override
    public Type<PingBroadcastPayload> type() {
        return TYPE;
    }
}
