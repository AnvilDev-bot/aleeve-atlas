package dev.aleeve.atlas.sync;

import dev.aleeve.atlas.client.map.ClientMapManager;
import dev.aleeve.atlas.map.ChunkMapData;
import dev.aleeve.atlas.map.ChunkPixelCodec;
import dev.aleeve.atlas.map.MapDimensionCache;
import dev.aleeve.atlas.network.ChunkDeltaPayload;
import dev.aleeve.atlas.network.ChunkDigestPayload;

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

    private ClientMapSync() {
    }

    /// 上报本地已探索区块摘要（规格 §3.3 摘要比对）。
    public static void reportLocalDigest() {
        ClientMapManager manager = dev.aleeve.atlas.client.map.AtlasClientMap.manager();
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
        for (ChunkDigestPayload packet : ChunkDigestPayload.split(dimension, entries)) {
            ClientPacketDistributor.sendToServer(packet);
        }
        LOGGER.debug("Reported digest of {} chunk(s)", entries.size());
    }

    /// 收到队友摘要：计算对方缺失的区块并回传差异分片。
    public static void handleDigest(ChunkDigestPayload payload, IPayloadContext context) {
        ClientMapManager manager = dev.aleeve.atlas.client.map.AtlasClientMap.manager();
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
                        data.version(), ChunkPixelCodec.compress(data.colors())));
            }
        }
        if (deltas.isEmpty()) {
            return;
        }
        for (ChunkDeltaPayload packet : ChunkDeltaPayload.split(payload.dimensionId(), deltas)) {
            ClientPacketDistributor.sendToServer(packet);
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
        ClientMapManager manager = dev.aleeve.atlas.client.map.AtlasClientMap.manager();
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
            int[] colors = ChunkPixelCodec.decompress(pending.chunk().pixels());
            for (int index = 0; index < colors.length && index < ChunkMapData.PIXELS; index++) {
                data.setPixel(index % ChunkMapData.SIZE, index / ChunkMapData.SIZE,
                        colors[index], 0);
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
