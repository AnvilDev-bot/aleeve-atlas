package dev.dubhe.atlas.client.visibility;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/// 可见性判定接口。
///
/// 两种实现：多采样点射线检测（默认可用）与 GPU 遮挡查询。
/// 输入保留实体身份，GPU 实现据此读取异步结果。
public interface VisibilityResolver {
    /// 判断目标实体当前是否对本地玩家可见。
    boolean isVisible(Level level, Player observer, Entity target);

    /// 实现名称，用于日志与配置展示。
    String name();
}
