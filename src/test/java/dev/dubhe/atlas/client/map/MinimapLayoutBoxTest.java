package dev.dubhe.atlas.client.map;

import dev.dubhe.atlas.config.MinimapPosition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 小地图尺寸与定位的边界测试。
///
/// 极端缩放或自定义偏移下，小地图必须完整落在屏幕内，不能出现负坐标。
class MinimapLayoutBoxTest {
    private static final int SCREEN_W = 427;
    private static final int SCREEN_H = 240;

    @Test
    void sizeIsCappedByShortScreenEdge() {
        // 4 倍缩放请求 512 像素，超过屏幕短边可用空间，必须被钳制。
        int[] box = MinimapLayout.computeBox(MinimapPosition.TOP_LEFT, 4.0, 0, 0,
                SCREEN_W, SCREEN_H);
        int size = box[2];
        Assertions.assertTrue(size <= SCREEN_H - MinimapLayout.MARGIN * 2,
                "尺寸必须不超过屏幕短边可用空间，实际 " + size);
    }

    @Test
    void defaultScaleKeepsBaseSize() {
        int[] box = MinimapLayout.computeBox(MinimapPosition.TOP_LEFT, 1.0, 0, 0,
                SCREEN_W, SCREEN_H);
        Assertions.assertEquals(MinimapLayout.BASE_SIZE, box[2]);
    }

    @Test
    void allCornersStayWithinScreen() {
        for (MinimapPosition position : MinimapPosition.values()) {
            int[] box = MinimapLayout.computeBox(position, 4.0, 0, 0, SCREEN_W, SCREEN_H);
            int x = box[0];
            int y = box[1];
            int size = box[2];
            Assertions.assertTrue(x >= 0, position + " 的 x 不得为负，实际 " + x);
            Assertions.assertTrue(y >= 0, position + " 的 y 不得为负，实际 " + y);
            Assertions.assertTrue(x + size <= SCREEN_W, position + " 的右边缘不得超出屏幕");
            Assertions.assertTrue(y + size <= SCREEN_H, position + " 的下边缘不得超出屏幕");
        }
    }

    @Test
    void customOffsetIsClampedIntoView() {
        // 自定义偏移远超屏幕尺寸，必须钳制到屏幕范围内。
        int[] box = MinimapLayout.computeBox(MinimapPosition.CUSTOM, 1.0, 9999, 9999,
                SCREEN_W, SCREEN_H);
        int x = box[0];
        int y = box[1];
        int size = box[2];
        Assertions.assertTrue(x + size <= SCREEN_W, "自定义偏移的右边缘不得超出屏幕");
        Assertions.assertTrue(y + size <= SCREEN_H, "自定义偏移的下边缘不得超出屏幕");
        Assertions.assertTrue(x >= 0 && y >= 0, "自定义偏移不得为负");
    }

    @Test
    void scaleIsClampedToConfiguredBounds() {
        // 低于下限的缩放按 0.5 处理，高于上限按 4.0 处理，均不产生异常尺寸。
        int[] tooSmall = MinimapLayout.computeBox(MinimapPosition.TOP_LEFT, 0.1, 0, 0,
                SCREEN_W, SCREEN_H);
        Assertions.assertEquals(Math.round(MinimapLayout.BASE_SIZE * 0.5F), tooSmall[2]);
    }
}
