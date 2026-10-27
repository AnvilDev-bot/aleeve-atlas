package dev.dubhe.map.sync;

import java.util.HashSet;
import java.util.Set;

/// 分片收齐判定。
///
/// 按分片序号去重记录：网络重排或重传会使同一分片多次到达，
/// 若按到达次数计数会提前判定收齐，导致领地数据缺块即被写入。
/// 期望片数随每个分片更新，兼容同一对象先后两次不同规模的拆分。
public final class BatchTracker {
    private int expectedBatches;
    private long lastUpdateMillis;
    private final Set<Integer> receivedIndices = new HashSet<>();

    /// @param expectedBatches 初始期望片数，小于 1 时按 1 处理
    /// @param now             当前时间戳（毫秒），用于超时回收
    public BatchTracker(int expectedBatches, long now) {
        this.expectedBatches = Math.max(1, expectedBatches);
        this.lastUpdateMillis = now;
    }

    /// 记录一个分片到达。
    ///
    /// 期望片数只增不减：一片声明 `batchCount=1` 的迟到分片若覆盖已有期望值，
    /// 会让残缺的集合立即被判定收齐。
    public void add(int batchIndex, int batchCount, long now) {
        this.receivedIndices.add(batchIndex);
        this.expectedBatches = Math.max(this.expectedBatches, Math.max(1, batchCount));
        this.lastUpdateMillis = now;
    }

    /// 是否已收齐全部期望分片。
    public boolean complete() {
        return this.receivedIndices.size() >= this.expectedBatches;
    }

    /// 最近一次收到分片的时间戳。
    public long lastUpdateMillis() {
        return this.lastUpdateMillis;
    }
}
