package dev.dubhe.atlas.ping;

import java.util.UUID;
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

    /// 维度键工具：把维度标识写入数据。
    public static String dimensionId(ResourceKey<Level> dimension) {
        return dimension.identifier().toString();
    }
}
