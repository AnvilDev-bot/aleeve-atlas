package dev.dubhe.map.client.visibility;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// GPU 遮挡查询异步结果缓存的行为测试。
class VisibilityResultCacheTest {
    private final UUID target = UUID.randomUUID();

    @Test
    void unseenTargetStaysHidden() {
        VisibilityResultCache cache = new VisibilityResultCache();
        Assertions.assertFalse(cache.isVisible(this.target));
        Assertions.assertEquals(0, cache.samples(this.target));

        cache.observe(this.target, 1L);
        Assertions.assertFalse(cache.isVisible(this.target), "仅登记目标不能视为已经可见");
    }

    @Test
    void pendingQueryRetainsPreviousVisibleResult() {
        VisibilityResultCache cache = new VisibilityResultCache();
        VisibilityResultCache.Entry entry = cache.observe(this.target, 1L);
        cache.attach(entry, 3);
        cache.complete(entry, true, 1200, 100L);
        Assertions.assertTrue(cache.isVisible(this.target));
        Assertions.assertEquals(1200, cache.samples(this.target));

        cache.attach(entry, 3);
        Assertions.assertTrue(cache.isPending(this.target));
        Assertions.assertTrue(cache.isVisible(this.target), "新查询未就绪时必须沿用上一可见结果");

        cache.complete(entry, false, 0, 200L);
        Assertions.assertFalse(cache.isVisible(this.target));
        Assertions.assertFalse(cache.isPending(this.target));
    }

    @Test
    void cancelledSubmissionKeepsPreviousResultAndReleasesSlot() {
        VisibilityResultCache cache = new VisibilityResultCache();
        VisibilityResultCache.Entry entry = cache.observe(this.target, 1L);
        cache.attach(entry, 7);
        cache.complete(entry, true, 64, 100L);

        cache.attach(entry, 7);
        Assertions.assertTrue(cache.occupiedSlots().contains(7));
        cache.cancel(entry);

        Assertions.assertTrue(cache.isVisible(this.target));
        Assertions.assertFalse(cache.occupiedSlots().contains(7));
    }

    @Test
    void completedQueryReleasesSlotAndPruneSkipsPendingEntries() {
        VisibilityResultCache cache = new VisibilityResultCache();
        VisibilityResultCache.Entry entry = cache.observe(this.target, 1L);
        cache.attach(entry, 2);
        List<VisibilityResultCache.Entry> removed = new ArrayList<>();
        cache.prune(1000L, 10, removed);
        Assertions.assertTrue(removed.isEmpty(), "pending 查询不得被清理");

        cache.complete(entry, false, 0, 100L);
        Assertions.assertFalse(cache.occupiedSlots().contains(2));
        cache.prune(1000L, 10, removed);
        Assertions.assertEquals(1, removed.size());
        Assertions.assertEquals(0, cache.size());
    }
}
