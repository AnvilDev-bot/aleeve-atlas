package dev.dubhe.map.ping;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 标点波纹的边界裁剪判定。
///
/// 波纹半径最大 12 像素，位于小地图边缘时需逐像素判断是否落在裁剪区内，
/// 避免绘制到 HUD 之外。
class PingRenderClipTest {
    @Test
    void pixelsInsideRectangleAreDrawn() {
        Assertions.assertTrue(PingRenderer.insideClip(10, 10, 0, 0, 100));
        Assertions.assertTrue(PingRenderer.insideClip(0, 0, 0, 0, 100));
        Assertions.assertTrue(PingRenderer.insideClip(99, 99, 0, 0, 100));
    }

    @Test
    void pixelsOutsideRectangleAreSkipped() {
        Assertions.assertFalse(PingRenderer.insideClip(-1, 10, 0, 0, 100));
        Assertions.assertFalse(PingRenderer.insideClip(10, -1, 0, 0, 100));
        Assertions.assertFalse(PingRenderer.insideClip(100, 10, 0, 0, 100));
        Assertions.assertFalse(PingRenderer.insideClip(10, 100, 0, 0, 100));
    }

    @Test
    void rectangleOffsetIsRespected() {
        // 小地图不贴屏幕原点时，裁剪区随布局偏移。
        Assertions.assertTrue(PingRenderer.insideClip(50, 50, 40, 40, 20));
        Assertions.assertFalse(PingRenderer.insideClip(39, 50, 40, 40, 20));
        Assertions.assertFalse(PingRenderer.insideClip(60, 50, 40, 40, 20));
    }
}
