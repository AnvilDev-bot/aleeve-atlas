package dev.dubhe.map.client.visibility;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/// 遮挡查询的诊断输出。
///
/// 仅用于自动化验收与日志核对，输出目标当前的查询判定与样本数。
public final class OcclusionDiagnostics {
    private OcclusionDiagnostics() {
    }

    /// 描述目标当前的可见性判定。
    public static String describe(Entity target) {
        if (!(target instanceof Player player)) {
            return "not-a-player";
        }
        GlOcclusionVisibilityResolver resolver = GlOcclusionVisibilityResolver.instance();
        String visible = resolver.isVisible(target.level(), player,
                target) ? "visible" : "hidden";
        return " uuid=" + target.getUUID()
                + " visible=" + visible
                + " samples=" + resolver.samplesOf(target.getUUID())
                + " pending=" + resolver.isPending(target.getUUID())
                + " resolver=" + resolver.name();
    }
}
