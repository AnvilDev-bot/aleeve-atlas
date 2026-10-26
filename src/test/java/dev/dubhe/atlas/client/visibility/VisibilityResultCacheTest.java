package dev.dubhe.atlas.client.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// GPU 遮挡查询异步结果缓存的行为测试。
class VisibilityResultCacheTest {
    private final UUID target = UUID.randomUUID();

    @Test
    void unseenTargetStaysHidden() {
        VisibilityResultCache cache = new VisibilityResultCache();
        assertFalse(cache.isVisible(target));
        assertEquals(0, cache.samples(target));

        cache.observe(target, 1L);
        assertFalse(cache.isVisible(target), "仅登记目标不能视为已经可见");
    }

    @Test
    void pendingQueryRetainsPreviousVisibleResult() {
        VisibilityResultCache cache = new VisibilityResultCache();
        VisibilityResultCache.Entry entry = cache.observe(target, 1L);
        cache.attach(entry, 3);
        cache.complete(entry, true, 1200, 100L);
        assertTrue(cache.isVisible(target));
        assertEquals(1200, cache.samples(target));

        cache.attach(entry, 3);
        assertTrue(cache.isPending(target));
        assertTrue(cache.isVisible(target), "新查询未就绪时必须沿用上一可见结果");

        cache.complete(entry, false, 0, 200L);
        assertFalse(cache.isVisible(target));
        assertFalse(cache.isPending(target));
    }

    @Test
    void cancelledSubmissionKeepsPreviousResultAndReleasesSlot() {
        VisibilityResultCache cache = new VisibilityResultCache();
        VisibilityResultCache.Entry entry = cache.observe(target, 1L);
        cache.attach(entry, 7);
        cache.complete(entry, true, 64, 100L);

        cache.attach(entry, 7);
        assertTrue(cache.occupiedSlots().contains(7));
        cache.cancel(entry);

        assertTrue(cache.isVisible(target));
        assertFalse(cache.occupiedSlots().contains(7));
    }

    @Test
    void completedQueryReleasesSlotAndPruneSkipsPendingEntries() {
        VisibilityResultCache cache = new VisibilityResultCache();
        VisibilityResultCache.Entry entry = cache.observe(target, 1L);
        cache.attach(entry, 2);
        List<VisibilityResultCache.Entry> removed = new ArrayList<>();
        cache.prune(1000L, 10, removed);
        assertTrue(removed.isEmpty(), "pending 查询不得被清理");

        cache.complete(entry, false, 0, 100L);
        assertFalse(cache.occupiedSlots().contains(2));
        cache.prune(1000L, 10, removed);
        assertEquals(1, removed.size());
        assertEquals(0, cache.size());
    }
}
