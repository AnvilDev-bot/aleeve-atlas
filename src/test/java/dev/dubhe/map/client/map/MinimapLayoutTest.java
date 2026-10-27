package dev.dubhe.map.client.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 小地图布局的坐标映射测试。
class MinimapLayoutTest {
    @Test
    void fullScreenCoordinateMapping() {
        // 小地图为 128 像素、覆盖 144 方块，玩家位于世界原点。
        MinimapLayout layout = new MinimapLayout(100, 20, 128, 0.0, 0.0);
        Assertions.assertEquals(164.0, layout.worldToScreenX(0.0), 1e-6);
        Assertions.assertEquals(84.0, layout.worldToScreenZ(0.0), 1e-6);
        // 移动 144 方块应恰好移动一个完整边长。
        Assertions.assertEquals(164.0 + 128.0, layout.worldToScreenX(144.0), 1e-6);
        Assertions.assertEquals(84.0 + 128.0, layout.worldToScreenZ(144.0), 1e-6);
    }

    @Test
    void zoomScalesWorldCoordinatesAroundPlayer() {
        MinimapLayout normal = new MinimapLayout(0, 0, 128, 0.0, 0.0, 1.0);
        MinimapLayout zoomed = new MinimapLayout(0, 0, 128, 0.0, 0.0, 2.0);
        double normalOffset = normal.worldToScreenX(24.0) - normal.x() - normal.size() / 2.0;
        double zoomedOffset = zoomed.worldToScreenX(24.0) - zoomed.x() - zoomed.size() / 2.0;
        Assertions.assertEquals(normalOffset * 2.0, zoomedOffset, 1.0E-6);
        double normalZOffset = normal.worldToScreenZ(24.0) - normal.y() - normal.size() / 2.0;
        Assertions.assertEquals(normalZOffset * 2.0,
                zoomed.worldToScreenZ(24.0) - zoomed.y() - zoomed.size() / 2.0, 1.0E-6);
    }

    @Test
    void focusedLayoutFillsScreenShortSide() {
        int[] box = MinimapLayout.focusedBox(640, 360);
        Assertions.assertEquals(360, box[2]);
        Assertions.assertEquals(140, box[0]);
        Assertions.assertEquals(0, box[1]);
    }

    @Test
    void squareDrawSizeCoversEveryRotation() {
        int visibleSize = 101;
        int drawSize = MinimapLayout.squareDrawSize(visibleSize);
        for (int degrees = 0; degrees < 360; degrees += 15) {
            double radians = Math.toRadians(degrees);
            double cornerExtent = (Math.abs(Math.cos(radians)) + Math.abs(Math.sin(radians)))
                    * visibleSize / 2.0;
            Assertions.assertTrue(cornerExtent <= drawSize / 2.0,
                    "rotation=" + degrees);
        }
    }

    @Test
    void containsRespectsShape() {
        MinimapLayout layout = new MinimapLayout(0, 0, 100, 0.0, 0.0);
        // 角落像素：方形包含、圆形不包含。
        Assertions.assertTrue(layout.contains(1.0, 1.0, false));
        Assertions.assertFalse(layout.contains(1.0, 1.0, true));
        // 圆心附近：两种形态都包含。
        Assertions.assertTrue(layout.contains(50.0, 50.0, false));
        Assertions.assertTrue(layout.contains(50.0, 50.0, true));
    }
}
