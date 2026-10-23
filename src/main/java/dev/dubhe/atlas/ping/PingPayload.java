package dev.dubhe.atlas.ping;

import dev.dubhe.atlas.AleeveAtlas;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/// 标点分发负载（规格 §10：标点分发包，C→S→C）。
///
/// 客户端把本地创建的标点上报服务端，服务端校验后广播给同队在线成员。
public record PingPayload(MapPingData ping) implements CustomPacketPayload {
    public static final Type<PingPayload> TYPE = new Type<>(AleeveAtlas.id("ping"));

    public static final StreamCodec<FriendlyByteBuf, PingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> write(buf, payload.ping),
            buf -> new PingPayload(read(buf)));

    @Override
    public Type<PingPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, MapPingData ping) {
        buf.writeUUID(ping.pingId());
        buf.writeUUID(ping.senderId());
        buf.writeUtf(ping.senderName(), 64);
        buf.writeVarInt(ping.teamColorArgb());
        buf.writeUtf(ping.dimensionId(), 128);
        buf.writeDouble(ping.position().x);
        buf.writeDouble(ping.position().y);
        buf.writeDouble(ping.position().z);
        buf.writeBoolean(ping.targetEntityId() != null);
        if (ping.targetEntityId() != null) {
            buf.writeUUID(ping.targetEntityId());
        }
        buf.writeEnum(ping.icon());
        buf.writeVarLong(ping.createdAtMillis());
        buf.writeVarLong(ping.lifetimeMillis());
    }

    private static MapPingData read(FriendlyByteBuf buf) {
        UUID pingId = buf.readUUID();
        UUID senderId = buf.readUUID();
        String senderName = buf.readUtf(64);
        int teamColor = buf.readVarInt();
        String dimension = buf.readUtf(128);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        MapPingData.PingIcon icon = buf.readEnum(MapPingData.PingIcon.class);
        long createdAt = buf.readVarLong();
        long lifetime = buf.readVarLong();
        return new MapPingData(pingId, senderId, senderName, teamColor, dimension,
                new Vec3(x, y, z), target, icon, createdAt, lifetime);
    }
}
