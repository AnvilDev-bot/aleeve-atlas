package dev.dubhe.map.client.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 画布像素搬移的索引计算测试。
///
/// 搬移方向决定遍历顺序：顺序错误会在重叠区域产生撕裂，因此需要独立验证。
class CanvasScrollTest {
    @Test
    void shiftTowardsHigherIndexStartsAtShiftAndIteratesBackwards() {
        // 目标范围为 [2, 5)，从高往低处理避免覆盖源像素。
        Assertions.assertArrayEquals(new int[] { 4, 3, 2 }, CanvasScroll.safeOrder(5, 2));
    }

    @Test
    void shiftTowardsLowerIndexStartsAtZeroAndIteratesForwards() {
        // 目标范围为 [0, 3)，从低往高处理。
        Assertions.assertArrayEquals(new int[] { 0, 1, 2 }, CanvasScroll.safeOrder(5, -2));
    }

    @Test
    void overlapShrinksByShiftMagnitude() {
        Assertions.assertEquals(3, CanvasScroll.overlapLength(5, 2));
        Assertions.assertEquals(3, CanvasScroll.overlapLength(5, -2));
        Assertions.assertEquals(5, CanvasScroll.overlapLength(5, 0));
    }

    @Test
    void enteredStripsSitOppositeTheOriginShift() {
        int shift = 2;
        // 原点增大时，新区域出现在高索引一侧。
        Assertions.assertEquals(5 - shift, CanvasScroll.enteredStart(5, shift));
        // 原点减小时，新区域出现在低索引一侧。
        Assertions.assertEquals(0, CanvasScroll.enteredStart(5, -shift));
        Assertions.assertEquals(shift, CanvasScroll.enteredCount(shift));
        Assertions.assertEquals(shift, CanvasScroll.enteredCount(-shift));
    }
}
