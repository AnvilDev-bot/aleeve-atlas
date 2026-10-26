package dev.dubhe.atlas.client.visibility;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/// 多采样点射线检测可见性判定。
///
/// 在目标玩家碰撞箱上取多个采样点（头部、胸口、脚部、左右肩），
/// 任一采样点与观察者眼睛之间无方块遮挡即判定为可见，避免单点采样被半砖遮挡误判。
public final class RaycastVisibilityResolver implements VisibilityResolver {
    /// 采样点数量上限，控制每帧射线开销。
    private static final int MAX_SAMPLES = 5;

    @Override
    public boolean isVisible(Level level, Player observer, Entity target) {
        Vec3 from = observer.getEyePosition();
        AABB box = target.getBoundingBox();
        Vec3[] samples = samples(box);
        for (int i = 0; i < samples.length && i < MAX_SAMPLES; i++) {
            if (isLineClear(level, from, samples[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String name() {
        return "raycast";
    }

    private static Vec3[] samples(AABB box) {
        double centerX = (box.minX + box.maxX) / 2.0;
        double centerZ = (box.minZ + box.maxZ) / 2.0;
        return new Vec3[] {
                new Vec3(centerX, box.maxY - 0.1, centerZ),           // 头部
                new Vec3(centerX, box.minY + (box.maxY - box.minY) * 0.75, centerZ), // 胸口
                new Vec3(centerX, box.minY + (box.maxY - box.minY) * 0.4, centerZ),  // 腰部
                new Vec3(box.minX + 0.1, box.minY + 0.6, centerZ),     // 左侧
                new Vec3(box.maxX - 0.1, box.minY + 0.6, centerZ)      // 右侧
        };
    }

    private static boolean isLineClear(Level level, Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, (Entity) null));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(to) < 0.01;
    }
}
