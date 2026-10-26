package dev.dubhe.atlas.client.map;

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
