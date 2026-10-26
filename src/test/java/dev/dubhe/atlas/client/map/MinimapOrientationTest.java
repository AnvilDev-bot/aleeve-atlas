package dev.dubhe.atlas.client.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// 小地图旋转方向与固定北向模式测试。
class MinimapOrientationTest {
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
            assertEquals(0.0, rotatedX, 1.0E-6, "yaw=" + yaw);
            assertTrue(rotatedY < 0.0, "yaw=" + yaw + " 时玩家朝向应在屏幕上方");
        }
    }
}
