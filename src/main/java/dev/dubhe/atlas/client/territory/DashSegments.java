package dev.dubhe.atlas.client.territory;

import java.util.ArrayList;
import java.util.List;

/// 虚线边框的分段计算。
///
/// 把一条边框按固定节距切分为实线段，供渲染端逐段填充。
/// 抽为纯函数以便在无渲染环境验证分段边界。
public final class DashSegments {
    /// 虚线节距（像素）：实线段长度与间隔之和。
    public static final int PERIOD = 4;
    /// 实线段长度（像素）。
    public static final int LENGTH = 2;

    private DashSegments() {
    }

    /// 计算边界长度内各实线段的起始偏移（相对起点）。
    ///
    /// 末尾残段按实际剩余长度截断，不会越过 `totalLength`。
    public static List<Integer> offsets(int totalLength) {
        List<Integer> result = new ArrayList<>();
        if (totalLength <= 0) {
            return result;
        }
        for (int offset = 0; offset < totalLength; offset += PERIOD) {
            result.add(offset);
        }
        return result;
    }

    /// 指定起始偏移处的实线段长度；超出边界时按剩余长度截断。
    public static int lengthAt(int totalLength, int offset) {
        if (offset < 0 || offset >= totalLength) {
            return 0;
        }
        return Math.min(LENGTH, totalLength - offset);
    }
}
