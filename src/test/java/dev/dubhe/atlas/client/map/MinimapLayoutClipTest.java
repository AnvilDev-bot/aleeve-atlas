package dev.dubhe.atlas.client.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 标记中心在圆形与方形地图边界内的约束测试。
class MinimapLayoutClipTest {
    private final MinimapLayout layout = new MinimapLayout(10, 20, 100, 0.0, 0.0);

    @Test
    void circularBoundaryKeepsMarkerInsideWithMargin() {
        double[] point = this.layout.fitInside(10 + 49, 20 + 50, true, 6.0);
        double dx = point[0] - 60.0;
        double dy = point[1] - 70.0;
        Assertions.assertTrue(Math.sqrt(dx * dx + dy * dy) <= 44.0 + 1.0E-6);
    }

    @Test
    void squareBoundaryClampsBothAxes() {
        double[] point = this.layout.fitInside(-100.0, 500.0, false, 5.0);
        Assertions.assertTrue(point[0] >= 15.0 && point[0] <= 105.0);
        Assertions.assertTrue(point[1] >= 25.0 && point[1] <= 115.0);
    }

    @Test
    void squareBoundaryPreservesTargetDirection() {
        double[] point = this.layout.fitInside(60.0 + 100.0, 70.0 + 50.0, false, 6.0, 0.0);
        double dx = point[0] - 60.0;
        double dy = point[1] - 70.0;
        Assertions.assertEquals(100.0 / 50.0, dx / dy, 1.0E-6);
        Assertions.assertTrue(Math.max(Math.abs(dx), Math.abs(dy)) <= 44.0 + 1.0E-6);
    }

    @Test
    void rotatedSquareUsesPostRotationBounds() {
        double[] point = this.layout.fitInside(60.0 + 58.0, 70.0 + 58.0, false, 6.0, 45.0);
        double dx = point[0] - 60.0;
        double dy = point[1] - 70.0;
        double radians = Math.toRadians(45.0);
        double rotatedX = dx * Math.cos(radians) - dy * Math.sin(radians);
        double rotatedY = dx * Math.sin(radians) + dy * Math.cos(radians);
        Assertions.assertTrue(Math.max(Math.abs(rotatedX), Math.abs(rotatedY)) <= 44.0 + 1.0E-6);
        Assertions.assertEquals(1.0, dx / dy, 1.0E-6);
    }

    @Test
    void rotatedSquareClassifiesVisibilityWithSameRotation() {
        MinimapLayout square = new MinimapLayout(0, 0, 128, 0.0, 0.0);
        Assertions.assertTrue(square.containsRotated(64.0 + 80.0, 64.0, false, 45.0));
        Assertions.assertTrue(!square.containsRotated(64.0 + 60.0, 64.0 + 60.0, false, 45.0));
    }
}
