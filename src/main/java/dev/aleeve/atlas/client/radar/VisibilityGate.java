package dev.aleeve.atlas.client.radar;

import dev.aleeve.atlas.client.visibility.RaycastVisibilityResolver;
import dev.aleeve.atlas.client.visibility.VisibilityResolver;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/// 雷达可见性闸门（规格 §4.2）。
///
/// 服务端策略为 `VISIBLE_ONLY` 时，非同队玩家必须通过可见性判定才允许绘制标点；
/// 默认使用多采样点射线检测，GPU 遮挡查询作为可选实现（需合法 GL 上下文）。
public final class VisibilityGate {
    private static VisibilityResolver resolver = new RaycastVisibilityResolver();

    private VisibilityGate() {
    }

    /// 替换可见性实现（实验性 GPU 遮挡查询可在取得 GL 上下文后注入）。
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
        Vec3 targetEye = target.getEyePosition();
        return resolver.isVisible(level, observer, targetEye);
    }
}
