package dev.dubhe.map.client.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 小地图旋转方向与固定北向模式测试。
class MinimapOrientationTest {
    @Test
    void squareCompassLabelsStayOnRotatedSquareEdge() {
        double center = 64.0;
        double size = 128.0;
        double labelExtent = 6.0;
        double edge = size / 2.0 - labelExtent;
        for (double rotation : new double[] { 0.0, 30.0, 45.0, 90.0, 135.0 }) {
            for (int direction = 0; direction < 4; direction++) {
                double[] point = CompassRenderer.minimapLabelPoint(center, center, size,
                        direction, rotation, false, labelExtent, edge);
                Assertions.assertEquals(edge,
                        Math.max(Math.abs(point[0] - center), Math.abs(point[1] - center)),
                        1.0E-6);
            }
        }
    }

    @Test
    void circularCompassLabelsStayOnCircle() {
        double[] point = CompassRenderer.minimapLabelPoint(50.0, 50.0, 100.0,
                0, 37.0, true, 5.0, 42.0);
        Assertions.assertEquals(42.0,
                Math.hypot(point[0] - 50.0, point[1] - 50.0), 1.0E-6);
    }

    @Test
    void rotatingMapAlignsPlayerForwardWithScreenUp() {
        // Minecraft 前向量为 (-sin(yaw), cos(yaw))，旋转后必须指向屏幕上方。
        for (float yaw : new float[] { 0.0F, 90.0F, 180.0F, -90.0F }) {
            double radians = Math.toRadians(yaw);
            double forwardX = -Math.sin(radians);
            double forwardZ = Math.cos(radians);
            double rotation = Math.toRadians(MinimapRenderer.mapRotationDegrees(yaw, false));
            double rotatedX = forwardX * Math.cos(rotation) - forwardZ * Math.sin(rotation);
            double rotatedY = forwardX * Math.sin(rotation) + forwardZ * Math.cos(rotation);
            Assertions.assertEquals(0.0, rotatedX, 1.0E-6, "yaw=" + yaw);
            Assertions.assertTrue(rotatedY < 0.0, "yaw=" + yaw + " 时玩家朝向应在屏幕上方");
        }
    }

    @Test
    void markerPositionsFollowMapRotation() {
        MinimapLayout layout = new MinimapLayout(0, 0, 128, 0.0, 0.0);
        double[] locked = layout.rotatedScreenPoint(0.0, -24.0, 0.0);
        double[] rotating = layout.rotatedScreenPoint(0.0, -24.0,
                MinimapRenderer.mapRotationDegrees(0.0F, false));
        Assertions.assertTrue(locked[1] < layout.y() + layout.size() / 2.0);
        Assertions.assertTrue(rotating[1] > layout.y() + layout.size() / 2.0);
        Assertions.assertEquals(locked[0], rotating[0], 1.0E-6);
    }
}
