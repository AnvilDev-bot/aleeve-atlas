package dev.dubhe.atlas.client.visibility;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/// 可见性判定接口（规格 §4 的 `VisibilityResolver` 抽象）。
///
/// 两种实现：多采样点射线检测（默认可用）与 GPU 遮挡查询（实验性，需合法 GL 上下文）。
public interface VisibilityResolver {
    /// 判断目标玩家当前是否对本地玩家可见。
    boolean isVisible(Level level, Player observer, Vec3 targetEye);

    /// 实现名称，用于日志与配置展示。
    String name();
}
