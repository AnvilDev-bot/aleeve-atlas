package dev.aleeve.atlas.client.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// 渲染耗时统计测试（规格 §12.1 性能指标）。
class RenderTimerTest {
    @Test
    void averageAndMaxAreTracked() {
        RenderTimer timer = new RenderTimer(4);
        timer.record(1_000_000L);
        timer.record(3_000_000L);
        assertEquals(2, timer.count());
        assertEquals(2_000_000L, timer.averageNanos());
        assertEquals(3_000_000L, timer.maxNanos());
    }

    @Test
    void ringBufferOverwritesOldest() {
        RenderTimer timer = new RenderTimer(2);
        timer.record(10_000_000L);
        timer.record(10_000_000L);
        timer.record(2_000_000L);
        // 容量 2：仅保留最后两个样本。
        assertEquals(2, timer.count());
        assertEquals(6_000_000L, timer.averageNanos());
    }

    @Test
    void overBudgetRatioCountsExceedingSamples() {
        RenderTimer timer = new RenderTimer(4);
        timer.record(500_000L);
        timer.record(1_500_000L);
        timer.record(900_000L);
        timer.record(2_000_000L);

        double ratio = timer.overBudgetRatio(1_000_000L);
        assertEquals(0.5, ratio, 1e-9);
    }

    @Test
    void emptyTimerReportsZero() {
        RenderTimer timer = new RenderTimer(8);
        assertEquals(0, timer.count());
        assertEquals(0L, timer.averageNanos());
        assertEquals(0.0, timer.averageMillis(), 1e-9);
        assertEquals(0.0, timer.overBudgetRatio(1_000_000L), 1e-9);
    }

    @Test
    void resetClearsAllSamples() {
        RenderTimer timer = new RenderTimer(4);
        timer.record(5_000_000L);
        timer.reset();
        assertEquals(0, timer.count());
        assertEquals(0L, timer.maxNanos());
    }

    @Test
    void minimapBudgetIsOneMillisecond() {
        assertEquals(1_000_000L, AtlasProfiling.MINIMAP_BUDGET_NANOS);
        AtlasProfiling.reset();
        AtlasProfiling.recordMinimap(500_000L);
        AtlasProfiling.recordMinimap(1_500_000L);
        assertTrue(AtlasProfiling.minimap().count() >= 2);
        String summary = AtlasProfiling.minimapSummary();
        assertTrue(summary.contains("minimap:"), summary);
        assertTrue(summary.contains("budget=1ms"), summary);
    }
}
