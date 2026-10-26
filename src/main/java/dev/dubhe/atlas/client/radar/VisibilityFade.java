package dev.dubhe.atlas.client.radar;

import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.config.ConfigBounds;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// 可见性渐隐跟踪。
///
/// 目标由「可见」变为「遮挡」时不立即消失，而是在配置的渐隐期内逐步降低不透明度，
/// 避免玩家转视角或目标穿过树林时标点高频闪烁。
public final class VisibilityFade {
    /// 实体 UUID → 最近一次可见的时间戳（毫秒）。
    private static final Map<UUID, Long> LAST_SEEN = new HashMap<>();
    /// 每 N 次判定触发一次清理，避免调用方忘记维护。
    private static final int PRUNE_INTERVAL = 600;
    private static int pruneCounter;

    private VisibilityFade() {
    }

    /// 记录一次可见；由可见性判定成功后调用。
    ///
    /// 内部按固定周期清理过期条目，避免调用方遗漏导致映射无界增长。
    public static void markSeen(Entity entity) {
        markSeen(entity.getUUID());
    }

    /// 按 UUID 记录一次可见，供缓存行为测试与实体入口共用。
    static void markSeen(UUID id) {
        markSeen(id, System.currentTimeMillis());
    }

    /// 使用指定时间记录可见，供确定性测试使用。
    static void markSeen(UUID id, long timestamp) {
        LAST_SEEN.put(id, timestamp);
        if (++pruneCounter >= PRUNE_INTERVAL) {
            pruneCounter = 0;
            prune();
        }
    }

    /// 当前应使用的不透明度（0.0 ~ 1.0）。
    ///
    /// 从未取得可见结果的目标返回 0，避免未被确认的目标显示在标点层；
    /// 可见期内为 1.0；超出可见后按渐隐期线性衰减至 0。
    public static float opacity(Entity entity) {
        return opacity(entity.getUUID());
    }

    /// 按 UUID 计算透明度，避免测试依赖完整实体环境。
    static float opacity(UUID id) {
        return opacity(id, System.currentTimeMillis());
    }

    /// 使用指定时间计算透明度，供确定性测试使用。
    static float opacity(UUID id, long now) {
        Long lastSeen = LAST_SEEN.get(id);
        if (lastSeen == null) {
            return 0.0F;
        }
        float fadeSeconds = (float) ConfigBounds.clamp(
                AtlasClientConfigs.CONFIG.radar.fadeOutSeconds, 0.5, 1.5);
        long elapsedMillis = now - lastSeen;
        long fadeMillis = Math.round(fadeSeconds * 1000.0F);
        if (elapsedMillis >= fadeMillis) {
            return 0.0F;
        }
        return 1.0F - (float) elapsedMillis / fadeMillis;
    }

    /// 清理长期未见的条目，避免内存增长。
    public static void prune() {
        long now = System.currentTimeMillis();
        float fadeSeconds = (float) ConfigBounds.clamp(
                AtlasClientConfigs.CONFIG.radar.fadeOutSeconds, 0.5, 1.5);
        long limit = Math.round(fadeSeconds * 1000.0F) * 4L;
        LAST_SEEN.entrySet().removeIf(entry -> now - entry.getValue() > limit);
    }

    public static void clear() {
        LAST_SEEN.clear();
    }
}
