package dev.dubhe.map.client.territory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/// 虚线边框分段计算测试。
class DashSegmentsTest {
    @Test
    void offsetsFollowFixedPeriod() {
        Assertions.assertEquals(List.of(0, 4, 8), DashSegments.offsets(12));
    }

    @Test
    void offsetsCoverFullLength() {
        // 每条边框的最后一个实线段起点必须小于总长度。
        List<Integer> offsets = DashSegments.offsets(10);
        Assertions.assertEquals(3, offsets.size());
        Assertions.assertTrue(offsets.get(offsets.size() - 1) < 10);
    }

    @Test
    void trailingSegmentIsTruncated() {
        // 总长 5：起点 4 处的实线段只剩 1 像素。
        Assertions.assertEquals(2, DashSegments.lengthAt(5, 0));
        Assertions.assertEquals(1, DashSegments.lengthAt(5, 4));
    }

    @Test
    void outOfRangeOffsetHasNoLength() {
        Assertions.assertEquals(0, DashSegments.lengthAt(10, 10));
        Assertions.assertEquals(0, DashSegments.lengthAt(10, -1));
    }

    @Test
    void zeroLengthProducesNoSegments() {
        Assertions.assertTrue(DashSegments.offsets(0).isEmpty());
        Assertions.assertTrue(DashSegments.offsets(-5).isEmpty());
    }

    @Test
    void segmentsNeverExceedTotalLength() {
        for (int total = 1; total <= 40; total++) {
            for (int offset : DashSegments.offsets(total)) {
                int length = DashSegments.lengthAt(total, offset);
                Assertions.assertTrue(offset + length <= total,
                        "段 " + offset + "+" + length + " 超出总长 " + total);
            }
        }
    }
}
