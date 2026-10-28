package dev.dubhe.map.client.map;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PlayerIndicatorRendererTest {
    @Test
    void northLockedIndicatorMatchesPlayerForwardIncludingWrappedAngles() {
        for (float yaw : new float[] { 0f, 90f, 180f, -90f, 359.5f, 360.5f, -360.5f, 720f }) {
            Matrix3x2f transform = PlayerIndicatorRenderer.transform(0, 0, yaw, 0f, 1f);
            Vector2f tip = transform.transformPosition(new Vector2f(0, -1));
            double radians = Math.toRadians(yaw);
            Assertions.assertEquals(-Math.sin(radians), tip.x, 1e-6, "yaw=" + yaw);
            Assertions.assertEquals(Math.cos(radians), tip.y, 1e-6, "yaw=" + yaw);
        }
    }

    @Test
    void rotatingMinimapKeepsIndicatorFacingUp() {
        for (float yaw : new float[] { 0f, 37.25f, 90f, 180f, -90f, 359.5f, 360.5f }) {
            float rotation = MinimapRenderer.mapRotationDegrees(yaw, false);
            Vector2f tip = PlayerIndicatorRenderer.transform(0, 0, yaw, rotation, 1f)
                    .transformPosition(new Vector2f(0, -1));
            Assertions.assertEquals(0, tip.x, 1e-6);
            Assertions.assertEquals(-1, tip.y, 1e-6);
        }
    }

    @Test
    void rotationAndScalingPreserveFractionalPlayerAnchor() {
        for (float scale : new float[] { 0.5f, 1f, 2f, 4f }) {
            for (float yaw : new float[] { 0f, 47.5f, 180f, 360.5f }) {
                Vector2f anchor = PlayerIndicatorRenderer.transform(31.25, 79.75, yaw, 0f, scale)
                        .transformPosition(new Vector2f());
                Assertions.assertEquals(31.25f, anchor.x);
                Assertions.assertEquals(79.75f, anchor.y);
            }
        }
    }

    @Test
    void minimapIndicatorFollowsPanelSizeButNotTerrainZoom() {
        for (int size : new int[] { 64, 128, 257, 512 }) {
            float expectedLength = size / (float) MinimapLayout.BASE_SIZE;
            for (double zoom : new double[] { 1, 1.5, 4 }) {
                MinimapLayout layout = new MinimapLayout(10, 20, size, 0, 0, zoom);
                float scale = PlayerIndicatorRenderer.minimapScale(layout);
                Vector2f tip = PlayerIndicatorRenderer.transform(0, 0, 25f, 0f, scale)
                        .transformPosition(new Vector2f(0, -1));
                Assertions.assertEquals(expectedLength, tip.length(), 1e-6);
            }
        }
    }

    @Test
    void worldMapPositionChangesDoNotChangeIndicatorSize() {
        for (double terrainZoom : new double[] { 0.05, 1, 4, 16 }) {
            double screenX = 300 + 24.5 * terrainZoom;
            double screenY = 200 - 18.25 * terrainZoom;
            Matrix3x2f transform = PlayerIndicatorRenderer.transform(screenX, screenY, 90f, 0f, 1f);
            Vector2f center = transform.transformPosition(new Vector2f());
            Vector2f tip = transform.transformPosition(new Vector2f(0, -8));
            Assertions.assertEquals(8, center.distance(tip), 1e-5);
        }
    }
}
