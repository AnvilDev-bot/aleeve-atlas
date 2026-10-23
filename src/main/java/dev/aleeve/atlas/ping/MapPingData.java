package dev.aleeve.atlas.ping;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/// 临时标点核心数据结构（规格 §3.2.1）。
///
/// 包含唯一 ID、发起者信息、精确三维坐标、可选跟随实体、图标类型、高亮颜色与生命周期元数据。
public record MapPingData(
        UUID pingId,
        UUID senderId,
        String senderName,
        int teamColorArgb,
        String dimensionId,
        Vec3 position,
        UUID targetEntityId,
        PingIcon icon,
        long createdAtMillis,
        long lifetimeMillis) {

    /// 标点图标类型（规格 §3.2.1：普通标记、危险警告、集合、物资）。
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("aleeve_atlas/ping");

    /// 标点图标类型（规格 §3.2.1）。
    public enum PingIcon {
        NORMAL,
        DANGER,
        RALLY,
        SUPPLY
    }

    public static final long DEFAULT_LIFETIME_MILLIS = 8000L;

    /// 是否因跟随实体而需要更新位置。
    public boolean followsEntity() {
        return targetEntityId != null;
    }

    /// 相对给定时间是否已过期。
    public boolean expired(long nowMillis) {
        return nowMillis - createdAtMillis >= lifetimeMillis;
    }

    public MapPingData withPosition(Vec3 newPosition) {
        return new MapPingData(pingId, senderId, senderName, teamColorArgb, dimensionId,
                newPosition, targetEntityId, icon, createdAtMillis, lifetimeMillis);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("pingId", pingId.toString());
        tag.putString("senderId", senderId.toString());
        tag.putString("senderName", senderName);
        tag.putInt("teamColor", teamColorArgb);
        tag.putString("dimension", dimensionId);
        tag.putDouble("x", position.x);
        tag.putDouble("y", position.y);
        tag.putDouble("z", position.z);
        if (targetEntityId != null) {
            tag.putString("target", targetEntityId.toString());
        }
        tag.putString("icon", icon.name());
        tag.putLong("createdAt", createdAtMillis);
        tag.putLong("lifetime", lifetimeMillis);
        return tag;
    }

    public static MapPingData load(CompoundTag tag) {
        String target = tag.getStringOr("target", "");
        return new MapPingData(
                UUID.fromString(tag.getStringOr("pingId", UUID.randomUUID().toString())),
                UUID.fromString(tag.getStringOr("senderId", new UUID(0L, 0L).toString())),
                tag.getStringOr("senderName", ""),
                tag.getIntOr("teamColor", 0xFFFFFFFF),
                tag.getStringOr("dimension", "minecraft:overworld"),
                new Vec3(tag.getDoubleOr("x", 0.0), tag.getDoubleOr("y", 0.0),
                        tag.getDoubleOr("z", 0.0)),
                target.isEmpty() ? null : UUID.fromString(target),
                parseIcon(tag.getStringOr("icon", PingIcon.NORMAL.name())),
                tag.getLongOr("createdAt", 0L),
                tag.getLongOr("lifetime", DEFAULT_LIFETIME_MILLIS));
    }

    private static PingIcon parseIcon(String raw) {
        try {
            return PingIcon.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return PingIcon.NORMAL;
        }
    }

    /// 维度键工具：把维度标识写入数据。
    public static String dimensionId(ResourceKey<Level> dimension) {
        return dimension.identifier().toString();
    }
}
