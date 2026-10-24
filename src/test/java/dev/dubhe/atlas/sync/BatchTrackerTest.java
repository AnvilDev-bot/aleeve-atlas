package dev.dubhe.atlas.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// 分片收齐判定测试（规格 §3.3）。
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
        assertEquals(3, tracker.receivedCount());
    }

    @Test
    void duplicateBatchDoesNotCompleteEarly() {
        // 同一分片到达三次，收到数量仍为 1，不得判定收齐。
        BatchTracker tracker = new BatchTracker(3, 0L);
        tracker.add(0, 3, 10L);
        tracker.add(0, 3, 20L);
        tracker.add(0, 3, 30L);
        assertEquals(1, tracker.receivedCount());
        assertFalse(tracker.complete(), "重复分片不得使计数虚高");
    }

    @Test
    void expectedCountFollowsLatestBatchCount() {
        // 首次声明 3 片、后续声明 2 片：期望值应随之更新，不得沿用旧值。
        BatchTracker tracker = new BatchTracker(3, 0L);
        tracker.add(0, 2, 10L);
        tracker.add(1, 2, 20L);
        assertTrue(tracker.complete(), "期望片数应随最新分片更新为 2");
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
