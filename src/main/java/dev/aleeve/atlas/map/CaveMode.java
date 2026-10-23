package dev.aleeve.atlas.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/// 洞穴分层模式判定（规格 §2.1：进入地下或有顶棚遮挡时自动切换）。
///
/// 判定顺序：
/// 1. 玩家位于海平面以下且头顶被遮挡 → 洞穴模式；
/// 2. 玩家处于水下 → 洞穴模式；
/// 3. 否则为地表模式。
public final class CaveMode {
    /// 判断顶棚遮挡时向上搜索的最大高度。
    private static final int CEILING_SCAN_HEIGHT = 48;
    private static final int SEA_LEVEL = 63;

    private CaveMode() {
    }

    /// 玩家当前是否处于洞穴/地下环境。
    public static boolean isUnderground(Level level, BlockPos playerPos) {
        if (level == null) {
            return false;
        }
        // 水下直接视为洞穴模式。
        if (!level.getFluidState(playerPos).isEmpty()) {
            return true;
        }
        // 海平面以上不判定为地下，避免高山地形被误判。
        if (playerPos.getY() > SEA_LEVEL) {
            return false;
        }
        return hasCeiling(level, playerPos);
    }

    /// 玩家上方是否存在遮蔽顶棚（连续不透明方块构成）。
    public static boolean hasCeiling(Level level, BlockPos playerPos) {
        int maxY = Math.min(level.getMaxY() - 1, playerPos.getY() + CEILING_SCAN_HEIGHT);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = playerPos.getY() + 2; y <= maxY; y++) {
            cursor.set(playerPos.getX(), y, playerPos.getZ());
            BlockState state = level.getBlockState(cursor);
            // 忽略流体与空气，只有能遮挡视线的实心方块才算顶棚。
            if (!state.isAir() && state.getFluidState().isEmpty() && state.canOcclude()) {
                return true;
            }
        }
        return false;
    }
}
