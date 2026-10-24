package dev.dubhe.atlas.client.territory;

import java.util.ArrayList;
import java.util.List;

/// 矩形与圆形求交后的填充行段计算。
///
/// 逐行计算矩形与圆的交集区间，并把裁剪区间一致的相邻行合并为一段，
/// 减少 HUD 绘制调用；线段数量与圆半径成线性关系而非与面积成平方关系。
public final class CircleClip {
    private CircleClip() {
    }

    /// 一段待填充的区域：左闭右开。
    ///
    /// @param left  左边界（含）
    /// @param top   上边界（含）
    /// @param right 右边界（不含）
    /// @param bottom 下边界（不含）
    public record Band(int left, int top, int right, int bottom) {
    }

    /// 计算矩形与圆的交集填充段。
    ///
    /// @param x       矩形左边界
    /// @param y       矩形上边界
    /// @param width   矩形宽度
    /// @param height  矩形高度
    /// @param centerX 圆心 X
    /// @param centerZ 圆心 Y
    /// @param radius  圆半径
    /// @return 覆盖交集的填充段，按行序排列
    public static List<Band> bands(int x, int y, int width, int height,
            double centerX, double centerZ, double radius) {
        List<Band> result = new ArrayList<>();
        int runStartRow = -1;
        int runLeft = 0;
        int runRight = 0;
        for (int row = y; row <= y + height; row++) {
            int left = 0;
            int right = 0;
            boolean visible = false;
            if (row < y + height) {
                double dy = row + 0.5 - centerZ;
                double spanSq = radius * radius - dy * dy;
                if (spanSq > 0.0) {
                    double span = Math.sqrt(spanSq);
                    left = Math.max(x, (int) Math.floor(centerX - span));
                    right = Math.min(x + width, (int) Math.ceil(centerX + span));
                    visible = right > left;
                }
            }
            // 与上一行裁剪区间一致则继续累积，否则冲刷上一段。
            if (visible && runStartRow >= 0 && left == runLeft && right == runRight) {
                continue;
            }
            if (runStartRow >= 0) {
                result.add(new Band(runLeft, runStartRow, runRight, row));
                runStartRow = -1;
            }
            if (visible) {
                runStartRow = row;
                runLeft = left;
                runRight = right;
            }
        }
        return result;
    }
}
