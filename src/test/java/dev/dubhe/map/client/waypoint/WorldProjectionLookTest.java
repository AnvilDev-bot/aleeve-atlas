package dev.dubhe.map.client.waypoint;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 路径点距离文本的视线容差判定测试。
class WorldProjectionLookTest {
    @Test
    void projectedPointNearScreenCenterIsLookedAt() {
        Assertions.assertTrue(WorldProjection.isLookingAt(projected(160.0, 90.0), 320, 180));
        Assertions.assertTrue(WorldProjection.isLookingAt(projected(174.0, 90.0), 320, 180));
    }

    @Test
    void projectedPointAwayFromScreenCenterIsNotLookedAt() {
        Assertions.assertFalse(WorldProjection.isLookingAt(projected(210.0, 90.0), 320, 180));
        Assertions.assertFalse(WorldProjection.isLookingAt(projected(190.0, 120.0), 320, 180));
    }

    @Test
    void toleranceFollowsViewportCenter() {
        Assertions.assertTrue(WorldProjection.isLookingAt(projected(502.0, 274.0), 1004, 548));
        Assertions.assertFalse(WorldProjection.isLookingAt(projected(160.0, 90.0), 1004, 548));
    }

    private static WorldProjection.Projected projected(double x, double y) {
        return new WorldProjection.Projected(x, y, 20.0);
    }
}
