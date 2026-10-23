package dev.dubhe.atlas.client.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/// 画布像素搬移的索引计算测试（规格 §12.1）。
///
/// 搬移方向决定遍历顺序：顺序错误会在重叠区域产生撕裂，因此需要独立验证。
class CanvasScrollTest {
    @Test
    void shiftTowardsHigherIndexStartsAtShiftAndIteratesBackwards() {
        // 目标范围为 [2, 5)，从高往低处理避免覆盖源像素。
        assertArrayEquals(new int[] { 4, 3, 2 }, CanvasScroll.safeOrder(5, 2));
    }

    @Test
    void shiftTowardsLowerIndexStartsAtZeroAndIteratesForwards() {
        // 目标范围为 [0, 3)，从低往高处理。
        assertArrayEquals(new int[] { 0, 1, 2 }, CanvasScroll.safeOrder(5, -2));
    }

    @Test
    void overlapShrinksByShiftMagnitude() {
        assertEquals(3, CanvasScroll.overlapLength(5, 2));
        assertEquals(3, CanvasScroll.overlapLength(5, -2));
        assertEquals(5, CanvasScroll.overlapLength(5, 0));
    }

    @Test
    void enteredStripsSitOnOppositeSideOfShift() {
        // 原点增大时，新区域出现在高索引一侧。
        assertEquals(3, CanvasScroll.enteredStart(5, 2));
        // 原点减小时，新区域出现在低索引一侧。
        assertEquals(0, CanvasScroll.enteredStart(5, -2));
        assertEquals(2, CanvasScroll.enteredCount(2));
        assertEquals(2, CanvasScroll.enteredCount(-2));
    }
}
