package dev.dubhe.map.sync;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.network.ChunkDeltaPayload;

/// 地图增量同步的发送预算。
///
/// 以「每秒字节配额」限制差异分片发送速率，避免堵塞实体与移动等实时包；
/// 配额按 tick 折算，令牌桶式补充，超限时排队等待下一次机会。
public final class SyncBudget {
    /// 每次尝试最多发出的分片数，防止单 tick 内突发。
    private static final int MAX_PACKETS_PER_TICK = 4;

    private long windowStartMillis;
    private long bytesSentInWindow;
    private int packetsThisTick;

    /// 重置窗口（切换服务器或维度时调用）。
    public void reset() {
        this.windowStartMillis = System.currentTimeMillis();
        this.bytesSentInWindow = 0;
        this.packetsThisTick = 0;
    }

    /// 每 tick 开始前调用，重置单 tick 计数。
    public void beginTick() {
        this.packetsThisTick = 0;
    }

    /// 判断当前是否允许发送指定大小的分片。
    public boolean canSend(int bytes) {
        if (this.packetsThisTick >= MAX_PACKETS_PER_TICK) {
            return false;
        }
        long now = System.currentTimeMillis();
        int budget = AleeveAtlas.CONFIG.sync.mapDeltaMaxBytesPerSecond;
        if (now - this.windowStartMillis >= 1000L) {
            long windows = (now - this.windowStartMillis) / 1000L;
            this.windowStartMillis += windows * 1000L;
            this.bytesSentInWindow = Math.max(0, this.bytesSentInWindow - windows * budget);
        }
        // 单包可能大于最低每秒预算，允许独立发送并把欠额延续到后续窗口，避免永久卡队列。
        return this.bytesSentInWindow + bytes <= budget
                || (this.bytesSentInWindow == 0 && bytes <= ChunkDeltaPayload.MAX_UPLOAD_BYTES + 320);
    }

    /// 记录已发送的字节数。
    public void record(int bytes) {
        this.bytesSentInWindow += bytes;
        this.packetsThisTick++;
    }

    /// 同步推送间隔（tick），取自服务端配置。
    public static int intervalTicks() {
        int configured = AleeveAtlas.CONFIG.sync.mapDeltaIntervalTicks;
        return Math.max(10, configured);
    }
}
