package dev.dubhe.map.client.radar;

import dev.dubhe.map.config.RadarDisplayMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 玩家头像渲染判定测试。
///
/// 图标模式恒为头像；混合联动模式仅在按住玩家列表键时切换为头像；圆点模式不显示头像。
class RadarPlayerHeadTest {
    @Test
    void iconModeAlwaysDrawsHead() {
        Assertions.assertTrue(RadarRenderer.shouldDrawPlayerHead(RadarDisplayMode.ICON, false));
        Assertions.assertTrue(RadarRenderer.shouldDrawPlayerHead(RadarDisplayMode.ICON, true));
    }

    @Test
    void hybridModeDrawsHeadOnlyWhileHoldingListKey() {
        Assertions.assertFalse(RadarRenderer.shouldDrawPlayerHead(RadarDisplayMode.HYBRID, false),
                "未按住玩家列表键时混合模式应保持圆点");
        Assertions.assertTrue(RadarRenderer.shouldDrawPlayerHead(RadarDisplayMode.HYBRID, true),
                "按住玩家列表键时混合模式应切换为头像");
    }

    @Test
    void dotModeNeverDrawsHead() {
        Assertions.assertFalse(RadarRenderer.shouldDrawPlayerHead(RadarDisplayMode.DOT, false));
        Assertions.assertFalse(RadarRenderer.shouldDrawPlayerHead(RadarDisplayMode.DOT, true),
                "圆点模式即使按住玩家列表键也不切换头像");
    }
}
