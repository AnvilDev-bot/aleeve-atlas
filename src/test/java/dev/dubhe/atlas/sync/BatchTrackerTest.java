package dev.dubhe.atlas.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// 分片收齐判定测试。
///
/// 关键行为：同一分片重复到达不得使计数虚高，否则会在缺片时提前判定收齐。
class BatchTrackerTest {
    @Test
    void completesWhenAllBatchesArrive() {
        BatchTracker tracker = new BatchTracker(3, 0L);
        tracker.add(0, 3, 10L);
        assertFalse(tracker.complete());
        tracker.add(1, 3, 20L);
        assertFalse(tracker.complete());
        tracker.add(2, 3, 30L);
        assertTrue(tracker.complete());
    }

    @Test
    void duplicateBatchDoesNotCompleteEarly() {
        // 同一分片重复到达时，其余分片缺失不应判定收齐。
        BatchTracker tracker = new BatchTracker(3, 0L);
        tracker.add(0, 3, 10L);
        tracker.add(0, 3, 20L);
        tracker.add(0, 3, 30L);
        assertFalse(tracker.complete(), "重复分片不得使计数虚高");
    }

    @Test
    void expectedCountDoesNotShrinkOnLaterBatch() {
        // 迟到分片声明更小的批量时，期望值不得被覆盖，否则残缺集合会被判为收齐。
        BatchTracker tracker = new BatchTracker(3, 0L);
        tracker.add(0, 3, 10L);
        tracker.add(1, 1, 20L);
        assertFalse(tracker.complete(), "期望片数不得被更小的声明覆盖");
        tracker.add(2, 3, 30L);
        assertTrue(tracker.complete());
    }

    @Test
    void zeroExpectedIsTreatedAsOne() {
        // 防御性处理：声明 0 片的空负载按 1 片处理，收到一片后即完成。
        BatchTracker tracker = new BatchTracker(0, 0L);
        assertFalse(tracker.complete());
        tracker.add(0, 1, 10L);
        assertTrue(tracker.complete());
    }

    @Test
    void tracksLatestUpdateTime() {
        BatchTracker tracker = new BatchTracker(2, 100L);
        assertEquals(100L, tracker.lastUpdateMillis());
        tracker.add(0, 2, 500L);
        assertEquals(500L, tracker.lastUpdateMillis(), "每次到达都应刷新时间戳");
    }
}
