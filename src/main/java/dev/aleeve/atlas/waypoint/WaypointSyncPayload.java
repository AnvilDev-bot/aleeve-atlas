package dev.aleeve.atlas.waypoint;

import dev.aleeve.atlas.AleeveAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/// 路径点同步负载（规格 §5.2 / §5.3 / §10：C→S→C）。
///
/// 携带完整路径点字段与操作类型（创建/更新/删除），接收方按自身接收策略处理。
public record WaypointSyncPayload(Action action, Waypoint waypoint) implements CustomPacketPayload {
    public static final Type<WaypointSyncPayload> TYPE =
            new Type<>(AleeveAtlas.id("waypoint_sync"));

    /// 同步动作。
    public enum Action {
        UPSERT,
        DELETE
    }

    public static final StreamCodec<FriendlyByteBuf, WaypointSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeEnum(payload.action);
                        buf.writeUUID(payload.waypoint.id());
                        buf.writeUtf(payload.waypoint.name(), 128);
                        buf.writeLong(payload.waypoint.pos().asLong());
                        buf.writeUtf(payload.waypoint.dimension().identifier().toString(), 256);
                        buf.writeInt(payload.waypoint.colorArgb());
                        buf.writeUtf(payload.waypoint.icon(), 32);
                        buf.writeBoolean(payload.waypoint.enabled());
                        buf.writeVarInt(payload.waypoint.visibleDistance());
                        buf.writeBoolean(payload.waypoint.deathPoint());
                        buf.writeUtf(payload.waypoint.sourcePlayer(), 64);
                    },
                    buf -> {
                        Action action = buf.readEnum(Action.class);
                        var id = buf.readUUID();
                        String name = buf.readUtf(128);
                        BlockPos pos = BlockPos.of(buf.readLong());
                        Identifier dimensionId = Identifier.parse(buf.readUtf(256));
                        int color = buf.readInt();
                        String icon = buf.readUtf(32);
                        boolean enabled = buf.readBoolean();
                        int visible = buf.readVarInt();
                        boolean death = buf.readBoolean();
                        String source = buf.readUtf(64);
                        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                                dimensionId);
                        return new WaypointSyncPayload(action,
                                new Waypoint(id, name, pos, dimension, color, icon, enabled,
                                        visible, death, source));
                    });

    @Override
    public Type<WaypointSyncPayload> type() {
        return TYPE;
    }
}
