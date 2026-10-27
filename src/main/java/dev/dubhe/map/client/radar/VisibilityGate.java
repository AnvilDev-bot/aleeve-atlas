package dev.dubhe.map.client.radar;

import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.client.visibility.RaycastVisibilityResolver;
import dev.dubhe.map.client.visibility.VisibilityResolver;
import dev.dubhe.map.config.NonTeamVisibility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/// 雷达与临时标点共用的可见性闸门。
///
/// 服务端策略为 `VISIBLE_ONLY` 时，非同队玩家必须通过可见性判定才允许绘制。
/// 默认使用射线检测；GPU 遮挡查询可用时由选择器替换为异步结果读取。
public final class VisibilityGate {
    private static VisibilityResolver resolver = new RaycastVisibilityResolver();

    private VisibilityGate() {
    }

    /// 替换可见性实现。
    public static void setResolver(VisibilityResolver newResolver) {
        if (newResolver != null) {
            resolver = newResolver;
        }
    }

    public static String resolverName() {
        return resolver.name();
    }

    /// 判定目标实体是否可见。
    public static boolean isVisible(Level level, Player observer, Entity target) {
        return resolver.isVisible(level, observer, target);
    }

    /// 计算非同队玩家标记的透明度，统一处理服务端策略、前置剔除与渐隐。
    ///
    /// 从未取得可见结果的目标返回 0；GPU 查询未完成时沿用上一有效结果。
    public static float opacity(Level level, Player observer, Entity target) {
        NonTeamVisibility policy = AtlasSession.nonTeamVisibility();
        if (policy == NonTeamVisibility.NEVER) {
            return 0.0F;
        }
        if (policy == NonTeamVisibility.ALWAYS) {
            return 1.0F;
        }
        if (VisibilityPreFilter.isHiddenByInvisibility(target)) {
            return 0.0F;
        }
        if (!VisibilityPreFilter.isInViewFrustum(target)) {
            return VisibilityFade.opacity(target);
        }
        if (resolver.isVisible(level, observer, target)) {
            VisibilityFade.markSeen(target);
            return 1.0F;
        }
        return VisibilityFade.opacity(target);
    }
}
