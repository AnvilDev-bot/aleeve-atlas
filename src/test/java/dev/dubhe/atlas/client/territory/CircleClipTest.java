package dev.dubhe.atlas.client.territory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/// 圆形裁剪的填充段计算测试。
///
/// 圆形小地图下，领地填充必须被圆边界裁掉，不得溢出到圆外。
class CircleClipTest {
    private static final double CENTER = 50.0;
    private static final double RADIUS = 50.0;

    @Test
    void fullyInsideRectProducesSingleBand() {
        // 小矩形完全落在圆心附近，各行的裁剪区间一致，应合并为一段。
        List<CircleClip.Band> bands = CircleClip.bands(45, 45, 10, 10,
                CENTER, CENTER, RADIUS);
        Assertions.assertEquals(1, bands.size());
        CircleClip.Band band = bands.get(0);
        Assertions.assertEquals(45, band.left());
        Assertions.assertEquals(45, band.top());
        Assertions.assertEquals(55, band.right());
        Assertions.assertEquals(55, band.bottom());
    }

    @Test
    void rectangleOutsideCircleProducesNoBands() {
        // 矩形距圆心 100，圆半径 50，无交集。
        Assertions.assertTrue(CircleClip.bands(200, 200, 10, 10, CENTER, CENTER, RADIUS).isEmpty());
    }

    @Test
    void bandsStayWithinCircleWidth() {
        // 大矩形覆盖整个圆：每段必须落在圆的水平范围内。
        List<CircleClip.Band> bands = CircleClip.bands(0, 0, 100, 100,
                CENTER, CENTER, RADIUS);
        for (CircleClip.Band band : bands) {
            Assertions.assertTrue(band.left() >= 0 && band.right() <= 100, "横向越界: " + band);
            Assertions.assertTrue(band.left() < band.right(), "空段不应产生: " + band);
        }
    }

    @Test
    void rowsOutsideCircleAreSkipped() {
        // 圆心 (50,50)、半径 30：矩形上下各有一片圆外区域。
        List<CircleClip.Band> bands = CircleClip.bands(0, 0, 100, 100, CENTER, CENTER, 30.0);
        Assertions.assertTrue(bands.get(0).top() > 0, "圆顶外的行不应产生填充，实际首段 top="
                + bands.get(0).top());
        Assertions.assertTrue(bands.get(bands.size() - 1).bottom() < 100, "圆底外的行不应产生填充，实际"
                + "末段 bottom=" + bands.get(bands.size() - 1).bottom());
        for (CircleClip.Band band : bands) {
            Assertions.assertTrue(band.top() >= 0 && band.bottom() <= 100, "纵向越界: " + band);
        }
    }

    @Test
    void bandCountGrowsLinearlyWithRadius() {
        // 合并后的段数应远小于行数，否则性能优化失效。
        List<CircleClip.Band> bands = CircleClip.bands(0, 0, 100, 100,
                CENTER, CENTER, RADIUS);
        Assertions.assertTrue(bands.size() <= 100, "段数不应超过行数，实际 " + bands.size());
        Assertions.assertTrue(bands.size() < 60, "合并后段数应明显少于行数，实际 " + bands.size());
    }

    @Test
    void degenerateRadiusProducesNoBands() {
        Assertions.assertTrue(CircleClip.bands(0, 0, 10, 10, CENTER, CENTER, 0.0).isEmpty());
    }
}
