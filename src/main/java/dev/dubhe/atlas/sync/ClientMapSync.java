package dev.dubhe.atlas.sync;

import dev.dubhe.atlas.client.map.ClientMapManager;
import dev.dubhe.atlas.map.ChunkMapData;
import dev.dubhe.atlas.map.ChunkPixelCodec;
import dev.dubhe.atlas.map.MapDimensionCache;
import dev.dubhe.atlas.network.ChunkDeltaPayload;
import dev.dubhe.atlas.network.ChunkDigestPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 客户端地图增量同步（规格 §3.3）。
///
/// 两条路径：
/// 1. 上报本地摘要（新上线/入队追赶同步）；
/// 2. 收到队友摘要后计算「自己有而对方缺失」的区块并回传差异分片。
public final class ClientMapSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/mapsync");
    /// 单 tick 写入的区块数上限，避免接收端帧率骤降（规格 §12.1）。
    public static final int CHUNKS_PER_TICK = 8;

    private static final List<PendingWrite> WRITE_QUEUE = new ArrayList<>();
    private static final Map<String, Long> RECEIVED_BATCHES = new HashMap<>();
    /// 发送预算：按服务端配置限制带宽（§12.1）。
    private static final SyncBudget BUDGET = new SyncBudget();

    private ClientMapSync() {
    }

    /// 上报本地已探索区块摘要（规格 §3.3 摘要比对）。
    public static void reportLocalDigest() {
        ClientMapManager manager = dev.dubhe.atlas.client.map.AtlasClientMap.manager();
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
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        java.util.UUID selfId = minecraft.player == null ? null : minecraft.player.getUUID();
        for (ChunkDigestPayload packet : ChunkDigestPayload.split(dimension, entries, selfId)) {
            ClientPacketDistributor.sendToServer(packet);
        }
        LOGGER.debug("Reported digest of {} chunk(s)", entries.size());
    }

    /// 收到队友摘要：计算对方缺失的区块并回传差异分片。
    public static void handleDigest(ChunkDigestPayload payload, IPayloadContext context) {
        ClientMapManager manager = dev.dubhe.atlas.client.map.AtlasClientMap.manager();
        if (manager == null) {
            return;
        }
        MapDimensionCache cache = manager.currentCache();
        if (cache == null) {
            return;
        }
        Map<Long, Long> theirVersions = new HashMap<>();
        for (ChunkDigestPayload.Entry entry : payload.entries()) {
            theirVersions.put((long) entry.chunkX() << 32 | (entry.chunkZ() & 0xFFFFFFFFL),
                    entry.version());
        }
        List<ChunkDeltaPayload.ChunkData> deltas = new ArrayList<>();
        for (ChunkMapData data : cache.loadedChunks()) {
            long key = (long) data.chunkX() << 32 | (data.chunkZ() & 0xFFFFFFFFL);
            Long theirVersion = theirVersions.get(key);
            if (theirVersion == null || theirVersion < data.version()) {
                deltas.add(new ChunkDeltaPayload.ChunkData(data.chunkX(), data.chunkZ(),
                        data.version(),
                        ChunkPixelCodec.compress(data.colors(), data.heights())));
            }
        }
        if (deltas.isEmpty()) {
            return;
        }
        // 按服务端配置的带宽上限与间隔限流，避免堵塞实时包（§9.2 / §12.1）。
        BUDGET.beginTick();
        // 差异回传给摘要发送者（由服务端按 targetPlayer 精确路由）。
        java.util.UUID target = payload.senderId();
        for (ChunkDeltaPayload packet : ChunkDeltaPayload.split(payload.dimensionId(), deltas,
                target)) {
            int estimated = estimate(packet);
            if (!BUDGET.canSend(estimated)) {
                // 配额用尽：剩余分片留待下一轮同步，不做无界排队。
                break;
            }
            ClientPacketDistributor.sendToServer(packet);
            BUDGET.record(estimated);
        }
        LOGGER.debug("Sent {} missing chunk(s) to teammate", deltas.size());
    }

    /// 收到差异分片：入队后按 tick 分批写入本地缓存。
    public static void handleDelta(ChunkDeltaPayload payload, IPayloadContext context) {
        String batchKey = payload.dimensionId() + "#" + payload.batchIndex()
                + "/" + payload.batchCount();
        RECEIVED_BATCHES.put(batchKey, System.currentTimeMillis());
        for (ChunkDeltaPayload.ChunkData chunk : payload.chunks()) {
            WRITE_QUEUE.add(new PendingWrite(payload.dimensionId(), chunk));
        }
    }

    /// 每个客户端 tick 调用：限速写入缓存（规格 §12.1 分帧写入）。
    public static void tick() {
        if (WRITE_QUEUE.isEmpty()) {
            return;
        }
        ClientMapManager manager = dev.dubhe.atlas.client.map.AtlasClientMap.manager();
        if (manager == null) {
            return;
        }
        MapDimensionCache cache = manager.currentCache();
        if (cache == null) {
            return;
        }
        int written = 0;
        while (!WRITE_QUEUE.isEmpty() && written < CHUNKS_PER_TICK) {
            PendingWrite pending = WRITE_QUEUE.remove(0);
            ChunkMapData data = new ChunkMapData(pending.chunk().chunkX(),
                    pending.chunk().chunkZ());
            // 颜色与高度一并还原，保证接收方地图的地表高度可用（§3.3）。
            int[][] decoded = ChunkPixelCodec.decompressWithHeights(pending.chunk().pixels());
            int[] colors = decoded[0];
            int[] heights = decoded[1];
            for (int index = 0; index < colors.length && index < ChunkMapData.PIXELS; index++) {
                data.setPixel(index % ChunkMapData.SIZE, index / ChunkMapData.SIZE,
                        colors[index], heights[index]);
            }
            data.setVersion(pending.chunk().version());
            cache.put(data);
            written++;
        }
    }

    /// 清理断线状态。
    public static void clear() {
        WRITE_QUEUE.clear();
        RECEIVED_BATCHES.clear();
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
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null) {
            return "minecraft:overworld";
        }
        return minecraft.level.dimension().identifier().toString();
    }

    private record PendingWrite(String dimensionId, ChunkDeltaPayload.ChunkData chunk) {
    }
}
