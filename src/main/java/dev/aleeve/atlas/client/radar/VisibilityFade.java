package dev.aleeve.atlas.client.radar;

import dev.aleeve.atlas.config.AtlasClientConfigs;
import dev.aleeve.atlas.config.ConfigBounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

/// 可见性渐隐跟踪（规格 §4.2 第 4 条）。
///
/// 目标由「可见」变为「遮挡」时不立即消失，而是在配置的渐隐期内逐步降低不透明度，
/// 避免玩家转视角或目标穿过树林时标点高频闪烁。
public final class VisibilityFade {
    /// 实体 UUID → 最近一次可见的时间戳（毫秒）。
    private static final Map<UUID, Long> LAST_SEEN = new HashMap<>();

    private VisibilityFade() {
    }

    /// 记录一次可见；由可见性判定成功后调用。
    public static void markSeen(Entity entity) {
        LAST_SEEN.put(entity.getUUID(), System.currentTimeMillis());
    }

    /// 当前应使用的不透明度（0.0 ~ 1.0）。
    ///
    /// 从未可见过的实体返回 1.0（不参与渐隐）；可见期内为 1.0；
    /// 超出可见后按渐隐期线性衰减至 0。
    public static float opacity(Entity entity) {
        Long lastSeen = LAST_SEEN.get(entity.getUUID());
        if (lastSeen == null) {
            return 1.0F;
        }
        float fadeSeconds = (float) ConfigBounds.clamp(
                AtlasClientConfigs.CONFIG.radar.fadeOutSeconds, 0.5, 1.5);
        long elapsedMillis = System.currentTimeMillis() - lastSeen;
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
