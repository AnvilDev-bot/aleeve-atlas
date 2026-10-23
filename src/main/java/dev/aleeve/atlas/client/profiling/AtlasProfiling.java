package dev.aleeve.atlas.client.profiling;

/// 模组内渲染性能统计入口（规格 §12.1）。
///
/// 各处渲染路径通过本类记录耗时；诊断命令读取统计结果用于验收。
public final class AtlasProfiling {
    /// 小地图 HUD 单帧预算：1 ms（规格 §12.1）。
    public static final long MINIMAP_BUDGET_NANOS = 1_000_000L;
    /// 采样窗口：最近 300 帧（约 5 秒 @60fps）。
    private static final int SAMPLE_CAPACITY = 300;

    private static final RenderTimer MINIMAP = new RenderTimer(SAMPLE_CAPACITY);

    private AtlasProfiling() {
    }

    /// 记录一次小地图渲染耗时。
    public static void recordMinimap(long nanos) {
        MINIMAP.record(nanos);
    }

    public static RenderTimer minimap() {
        return MINIMAP;
    }

    /// 小地图统计摘要（供命令输出）。
    public static String minimapSummary() {
        return "minimap: avg=%.3fms max=%.3fms over-budget=%.1f%% (n=%d, budget=1ms)"
                .formatted(MINIMAP.averageMillis(), MINIMAP.maxNanos() / 1_000_000.0,
                        MINIMAP.overBudgetRatio(MINIMAP_BUDGET_NANOS) * 100.0,
                        MINIMAP.count());
    }

    public static void reset() {
        MINIMAP.reset();
    }
}
