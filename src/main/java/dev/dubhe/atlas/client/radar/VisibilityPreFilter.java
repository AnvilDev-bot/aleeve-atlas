package dev.dubhe.atlas.client.radar;

import dev.dubhe.atlas.client.waypoint.WorldProjection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/// 可见性判定的 CPU 前置剔除（规格 §4.2 第 1 条）。
///
/// 在发起射线检测或 GPU 查询前，先按状态与视锥剔除目标，降低判定开销。
public final class VisibilityPreFilter {
    private VisibilityPreFilter() {
    }

    /// 目标是否应被前置剔除。
    ///
    /// 命中任一条件即判定不可见：
    /// 1. 隐身且未穿戴任何护甲；
    /// 2. 碰撞箱中心不在当前摄像机视锥体内。
    public static boolean shouldCull(Player observer, Entity target) {
        return isHiddenByInvisibility(target) || !isInViewFrustum(target);
    }

    /// 状态剔除：隐身且未穿戴护甲的目标不可见。
    static boolean isHiddenByInvisibility(Entity target) {
        if (!target.isInvisible()) {
            return false;
        }
        if (!(target instanceof LivingEntity living)) {
            return true;
        }
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (!living.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /// 视锥体剔除：目标包围盒中心必须落在摄像机能看到的范围内。
    ///
    /// 复用 {@link WorldProjection} 的透视投影判定，目标在背后或屏幕外时返回 false。
    static boolean isInViewFrustum(Entity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        return WorldProjection.project(center).isPresent();
    }
}
