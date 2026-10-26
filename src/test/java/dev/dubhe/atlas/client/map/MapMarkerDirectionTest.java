package dev.dubhe.atlas.client.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/// 地图边缘方向箭头的四象限测试。
class MapMarkerDirectionTest {
    @Test
    void arrowRotationPointsAtTargetDirection() {
        assertDirection(1.0, 0.0, 1.0, 0.0);
        assertDirection(0.0, 1.0, 0.0, 1.0);
        assertDirection(-1.0, 0.0, -1.0, 0.0);
        assertDirection(0.0, -1.0, 0.0, -1.0);
    }

    private static void assertDirection(double dx, double dz, double expectedX, double expectedY) {
        double angle = MapMarkerRenderer.directionAngle(dx, dz);
        // 基础箭头尖端朝上 (0,-1)，旋转后应与目标方向一致。
        double actualX = Math.sin(angle);
        double actualY = -Math.cos(angle);
        assertEquals(expectedX, actualX, 1.0E-6);
        assertEquals(expectedY, actualY, 1.0E-6);
    }
}
