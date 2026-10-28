package dev.dubhe.map.client.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapCanvasResolutionTest {
    @Test void coversViewportEvenAtMinimumZoomAndUltrawideSizes() {
        for (int width : new int[] {427, 1280, 3840, 7680}) {
            for (float zoom : new float[] {0.05F, 0.1F, 0.5F, 0.79F, 0.8F, 1.0F, 12.0F}) {
                int stride = MapCanvas.resolutionFor(width, 2160, zoom);
                assertTrue((MapCanvas.PIXELS - 64) * stride * (double) zoom >= Math.max(width, 2160));
                assertEquals(0, stride & (stride - 1));
            }
        }
        assertEquals(1, MapCanvas.resolutionFor(640, 360, 1.0F));
    }
}
