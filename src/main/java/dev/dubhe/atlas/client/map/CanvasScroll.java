package dev.dubhe.atlas.client.map;

/// 画布像素搬移的索引计算。
///
/// 视野平移时把重叠区域的像素按区块粒度搬到新位置，只补绘新进入的边缘条带。
/// 源与目标区域可能重叠，因此遍历顺序必须与搬移方向相反，否则会覆盖尚未读取的像素。
public final class CanvasScroll {
    private CanvasScroll() {
    }

    /// 原点变化时，重叠内容在纹理中的搬移方向与原点相反。
    public static int contentShiftForOriginShift(int originShift) {
        return -originShift;
    }

    /// 目标像素在旧画布中的来源索引。
    public static int sourceIndexForOriginShift(int targetIndex, int originShift) {
        return targetIndex + originShift;
    }

    /// 按方向生成不会自我覆盖的目标位置遍历顺序。
    ///
    /// 目标位置 `p` 的源位置为 `p - shift`：`shift > 0` 表示内容向高索引方向搬移，
    /// 因此目标范围为 `[shift, total)`；`shift < 0` 时目标范围为 `[0, total + shift)`。
    /// 遍历顺序与搬移方向相反，避免覆盖尚未读取的源像素。
    ///
    /// @param total 每边槽位数
    /// @param shift 搬移量
    /// @return 目标位置遍历序列
    public static int[] safeOrder(int total, int shift) {
        int length = overlapLength(total, shift);
        int[] order = new int[length];
        int start = shift > 0 ? shift : 0;
        for (int i = 0; i < length; i++) {
            // 向高索引搬移时从高往低处理，反之从低往高。
            order[i] = shift > 0 ? start + length - 1 - i : start + i;
        }
        return order;
    }

    /// 搬移后被重叠区域覆盖的跨度（元素数）。
    public static int overlapLength(int total, int shift) {
        return total - Math.abs(shift);
    }

    /// 平移后新进入视野的起始索引（在画布上的边界位置）。
    ///
    /// @param total 每边槽位数
    /// @param shift 原点变化的槽位数（正数表示原点增大）
    public static int enteredStart(int total, int shift) {
        return shift > 0 ? total - shift : 0;
    }

    /// 平移后新进入视野的槽位数量。
    public static int enteredCount(int shift) {
        return Math.abs(shift);
    }
}
