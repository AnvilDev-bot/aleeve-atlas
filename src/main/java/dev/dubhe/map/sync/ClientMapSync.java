package dev.dubhe.map.sync;

import dev.dubhe.map.client.map.AtlasClientMap;
import dev.dubhe.map.client.map.ClientMapManager;
import dev.dubhe.map.client.map.WorldMapScreen;
import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.map.ChunkPixelCodec;
import dev.dubhe.map.map.MapDimensionCache;
import dev.dubhe.map.network.ChunkDeltaPayload;
import dev.dubhe.map.network.ChunkDigestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/// 客户端地图增量同步。
///
/// 两条路径：
/// 1. 上报本地摘要（新上线/入队追赶同步）；
/// 2. 收到队友摘要后计算「自己有而对方缺失」的区块并回传差异分片。
public final class ClientMapSync {
    /// 单 tick 写入的区块数上限，避免接收端帧率骤降。
    public static final int CHUNKS_PER_TICK = 8;

    /// 待写队列：网络线程入队、主线程出队，需线程安全。
    private static final Queue<PendingWrite> WRITE_QUEUE =
            new ConcurrentLinkedQueue<>();
    private static final Map<String, BatchRecord> RECEIVED_BATCHES = new HashMap<>();
    /// 批次记录的过期窗口（毫秒）：超过该窗口的未收齐记录视为陈旧并清理。
    private static final long BATCH_WINDOW_MILLIS = 30_000L;
    /// 发送预算：按服务端配置限制带宽。
    private static final SyncBudget BUDGET = new SyncBudget();

    private ClientMapSync() {
    }

    /// 上报本地已探索区块摘要。
    public static void reportLocalDigest() {
        dev.dubhe.map.client.structure.ClientStructures.shareNext();
        ClientMapManager manager = AtlasClientMap.manager();
        if (manager == null) {
            return;
        }
        MapDimensionCache cache = manager.currentCache();
        if (cache == null) {
            return;
        }
        List<ChunkDigestPayload.Entry> entries = new ArrayList<>();
        for (ChunkMapData data : cache.loadedChunks()) {
            entries.add(new ChunkDigestPayload.Entry(data.chunkX(), data.chunkZ(),
                    data.version()));
        }
        String dimension = currentDimensionId();
        var minecraft = Minecraft.getInstance();
        UUID selfId = minecraft.player == null ? null : minecraft.player.getUUID();
        for (ChunkDigestPayload packet : ChunkDigestPayload.split(dimension, entries, selfId)) {
            ClientPacketDistributor.sendToServer(packet);
        }
    }

    /// 收到队友摘要：计算对方缺失的区块并回传差异分片。
    public static void handleDigest(ChunkDigestPayload payload, IPayloadContext context) {
        ClientMapManager manager = AtlasClientMap.manager();
        if (manager == null) {
            return;
        }
        MapDimensionCache cache = manager.currentCache();
        if (cache == null) {
            return;
        }
        List<ChunkDeltaPayload.ChunkData> deltas = computeDeltas(payload.entries(),
                cache.loadedChunks());
        if (deltas.isEmpty()) {
            return;
        }
        // 按服务端配置的带宽上限与间隔限流，避免堵塞实时包。
        // 单 tick 计数由 `tick` 统一重置，此处不再重复重置，否则同 tick 内多批差异各自计数。
        // 差异回传给摘要发送者（由服务端按 targetPlayer 精确路由）。
        UUID target = payload.senderId();
        for (ChunkDeltaPayload packet : ChunkDeltaPayload.splitForUpload(
                payload.dimensionId(), deltas, target)) {
            int estimated = estimate(packet);
            if (!BUDGET.canSend(estimated)) {
                // 配额用尽：剩余分片留待下一轮同步，不做无界排队。
                break;
            }
            ClientPacketDistributor.sendToServer(packet);
            BUDGET.record(estimated);
        }
    }

    /// 计算「本地有而对方缺失或过期」的区块差异。
    ///
    /// 对方摘要中不存在的区块视为缺失，版本更低的视为过期，两者都需回传。
    /// 抽为纯函数便于验证版本比对规则。
    static List<ChunkDeltaPayload.ChunkData> computeDeltas(
            List<ChunkDigestPayload.Entry> theirEntries, List<ChunkMapData> localChunks) {
        Map<Long, Long> theirVersions = new HashMap<>();
        for (ChunkDigestPayload.Entry entry : theirEntries) {
            theirVersions.put(chunkKey(entry.chunkX(), entry.chunkZ()), entry.version());
        }
        List<ChunkDeltaPayload.ChunkData> deltas = new ArrayList<>();
        for (ChunkMapData data : localChunks) {
            Long theirVersion = theirVersions.get(chunkKey(data.chunkX(), data.chunkZ()));
            if (theirVersion == null || theirVersion < data.version()) {
                deltas.add(new ChunkDeltaPayload.ChunkData(data.chunkX(), data.chunkZ(),
                        data.version(),
                        ChunkPixelCodec.compressChunk(data)));
            }
        }
        return deltas;
    }

    /// 以区块坐标构造摘要比对用的键，负坐标同样可区分。
    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /// 收到差异分片：在网络线程完成解压，主线程只做缓存写入。
    ///
    /// 本负载注册为 `HandlerThread.NETWORK`，处理时已在网络线程；
    /// 解压结果通过线程安全队列交给 `tick` 在逻辑线程写入缓存。
    public static void handleDelta(ChunkDeltaPayload payload, IPayloadContext context) {
        enqueueDecoded(payload);
    }

    /// 清理超过窗口仍未收齐的批次记录，避免分片丢失时记录永久残留。
    private static void pruneStaleBatches() {
        long cutoff = System.currentTimeMillis() - BATCH_WINDOW_MILLIS;
        synchronized (RECEIVED_BATCHES) {
            RECEIVED_BATCHES.values().removeIf(record -> record.firstSeenMillis < cutoff);
        }
    }

    /// 解压分片并放入待写队列（网络线程执行）。
    ///
    /// 按 `batchIndex/batchCount` 记录分片到达情况，用于确认一次差异传输是否完整。
    /// 批次键不含时间桶：一次拆分跨过窗口边界时，前后分片仍应归入同一批次。
    private static void enqueueDecoded(ChunkDeltaPayload payload) {
        String batchKey = payload.dimensionId() + '#' + payload.targetPlayer() + '#'
                + payload.batchCount();
        synchronized (RECEIVED_BATCHES) {
            BatchRecord record = RECEIVED_BATCHES.computeIfAbsent(batchKey,
                    key -> new BatchRecord(System.currentTimeMillis()));
            if (payload.batchIndex() < 64) {
                record.mask |= 1L << payload.batchIndex();
            }
            int expected = payload.batchCount();
            // 位图只能表示 64 个分片：超出时无法在此判定收齐，交由超时清理。
            if (expected <= 64) {
                long mask = (1L << expected) - 1L;
                if ((record.mask & mask) == mask) {
                    RECEIVED_BATCHES.remove(batchKey);
                }
            }
        }
        for (ChunkDeltaPayload.ChunkData chunk : payload.chunks()) {
            ChunkMapData decoded = ChunkPixelCodec.decompressChunk(chunk.chunkX(), chunk.chunkZ(),
                    chunk.pixels(), chunk.version());
            WRITE_QUEUE.add(new PendingWrite(payload.dimensionId(), chunk, decoded));
        }
    }

    /// 未收齐批次的到达位图与首次到达时刻。
    private static final class BatchRecord {
        private long mask;
        private final long firstSeenMillis;

        private BatchRecord(long firstSeenMillis) {
            this.firstSeenMillis = firstSeenMillis;
        }
    }

    /// 每个客户端 tick 调用：重置发送预算并限速写入缓存。
    public static void tick() {
        // 每 tick 重置单 tick 发包计数，避免同一 tick 内多批差异各自计数。
        BUDGET.beginTick();
        pruneStaleBatches();
        if (WRITE_QUEUE.isEmpty()) {
            return;
        }
        ClientMapManager manager = AtlasClientMap.manager();
        if (manager == null) {
            return;
        }
        MapDimensionCache cache = manager.currentCache();
        if (cache == null) {
            return;
        }
        int written = 0;
        while (!WRITE_QUEUE.isEmpty() && written < CHUNKS_PER_TICK) {
            PendingWrite pending = WRITE_QUEUE.poll();
            if (pending == null) {
                break;
            }
            // 分片所属维度与当前缓存不一致时丢弃：写入会让另一维度的地形覆盖本维度。
            String cacheDimension = currentDimensionId();
            if (cacheDimension != null && !cacheDimension.equals(pending.dimensionId())) {
                continue;
            }
            cache.put(pending.data());
            written++;
        }
        if (written > 0) {
            // 队友同步来的区块写入后刷新已打开的世界地图。
            WorldMapScreen.onMapDataChanged();
        }
    }

    /// 清理断线状态。
    public static void clear() {
        WRITE_QUEUE.clear();
        // 与网络线程的写入共用同一把锁，避免登出时并发破坏哈希表。
        synchronized (RECEIVED_BATCHES) {
            RECEIVED_BATCHES.clear();
        }
        // 带宽预算属会话级状态，切换服务器后从零开始。
        BUDGET.reset();
    }

    /// 估算一个分片的传输字节数（坐标 + 版本 + 像素串 + 包头）。
    private static int estimate(ChunkDeltaPayload packet) {
        int total = 0;
        for (ChunkDeltaPayload.ChunkData chunk : packet.chunks()) {
            total += chunk.pixels().length + 24;
        }
        return total + 32;
    }

    private static String currentDimensionId() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return "minecraft:overworld";
        }
        return minecraft.level.dimension().identifier().toString();
    }

    /// 待写入的区块：色板数据已在网络线程解压完成。
    private record PendingWrite(String dimensionId, ChunkDeltaPayload.ChunkData chunk,
            ChunkMapData data) {
    }
}
