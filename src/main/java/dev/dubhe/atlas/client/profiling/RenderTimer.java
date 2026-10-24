package dev.dubhe.atlas.client.profiling;

import java.util.Arrays;

/// 渲染耗时统计。
///
/// 维护固定容量的环形缓冲，记录最近 N 帧的耗时（纳秒），
/// 提供平均值、最大值与超阈次数，供诊断命令输出与自动校验使用。
public final class RenderTimer {
    private final long[] samples;
    private int count;
    private int cursor;
    private long total;
    private long max;

    public RenderTimer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.samples = new long[capacity];
    }

    /// 记录一帧耗时（纳秒）。
    public void record(long nanos) {
        if (count == samples.length) {
            // 覆盖最旧样本。
            total -= samples[cursor];
        } else {
            count++;
        }
        samples[cursor] = nanos;
        total += nanos;
        max = Math.max(max, nanos);
        cursor = (cursor + 1) % samples.length;
    }

    /// 样本数量。
    public int count() {
        return count;
    }

    /// 平均耗时（纳秒）；无样本时返回 0。
    public long averageNanos() {
        return count == 0 ? 0L : total / count;
    }

    /// 平均耗时（毫秒，保留小数）。
    public double averageMillis() {
        return averageNanos() / 1_000_000.0;
    }

    /// 历史最大耗时（纳秒）。
    public long maxNanos() {
        return max;
    }

    /// 超过阈值的样本占比（0.0 ~ 1.0）。
    public double overBudgetRatio(long budgetNanos) {
        if (count == 0) {
            return 0.0;
        }
        int over = 0;
        for (int index = 0; index < count; index++) {
            if (samples[index] > budgetNanos) {
                over++;
            }
        }
        return (double) over / count;
    }

    /// 清空样本。
    public void reset() {
        Arrays.fill(samples, 0L);
        count = 0;
        cursor = 0;
        total = 0;
        max = 0;
    }
}
