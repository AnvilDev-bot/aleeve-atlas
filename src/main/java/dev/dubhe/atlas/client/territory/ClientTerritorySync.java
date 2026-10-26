package dev.dubhe.atlas.client.territory;

import dev.dubhe.atlas.api.territory.AtlasTerritory;
import dev.dubhe.atlas.api.territory.TerritorySyncPayload;
import dev.dubhe.atlas.sync.BatchTracker;
import dev.dubhe.atlas.territory.SyncedTerritory;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/// 客户端领地接收与分片重组。
///
/// 服务端下发的领地可能拆成多个负载；本类按「领地 ID」累积区块，
/// 收齐全部批次后一次性写入客户端注册表，避免重复触发连通分量重算。
public final class ClientTerritorySync {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/territory");

    /// 领地 ID → 已累积的分片数据。
    private static final Map<String, Accumulator> PENDING = new HashMap<>();
    /// 累加器超时（毫秒）：分片丢失时按此时间回收。
    private static final long ACCUMULATOR_TIMEOUT_MILLIS = 60_000L;

    private ClientTerritorySync() {
    }

    public static void handle(TerritorySyncPayload payload, IPayloadContext context) {
        if (payload.action() == TerritorySyncPayload.Action.REMOVE) {
            PENDING.remove(payload.territory().territoryId());
            AtlasTerritory.client().removeTerritory(payload.territory().territoryId());
            return;
        }
        String territoryId = payload.territory().territoryId();
        long now = System.currentTimeMillis();
        // 清理超时未收齐的累加器：分片丢失时不应永久占用内存。
        PENDING.entrySet().removeIf(entry -> now - entry.getValue().lastUpdateMillis()
                > ACCUMULATOR_TIMEOUT_MILLIS);
        Accumulator accumulator = PENDING.computeIfAbsent(territoryId,
                ignored -> new Accumulator(payload.batchCount(), payload.territory()));
        accumulator.add(payload.batchIndex(), payload.territory().chunks(),
                payload.batchCount(), now);
        if (!accumulator.complete()) {
            return;
        }
        PENDING.remove(territoryId);
        SyncedTerritory merged = accumulator.merge();
        AtlasTerritory.client().upsertTerritory(merged);
        LOGGER.debug("Received territory {} with {} chunk(s)", territoryId,
                merged.chunks().size());
    }

    /// 清空累加状态（断线时调用）。
    public static void clear() {
        PENDING.clear();
    }

    /// 单个领地的分片累加器。
    ///
    /// 收齐判定委托给 {@link BatchTracker}：按分片序号去重，
    /// 避免网络重排或重传导致提前判定收齐。
    private static final class Accumulator {
        private final BatchTracker tracker;
        private final SyncedTerritory template;
        private final Set<ChunkPos> chunks = new HashSet<>();

        private Accumulator(int expectedBatches, SyncedTerritory template) {
            this.tracker = new BatchTracker(expectedBatches, System.currentTimeMillis());
            this.template = template;
        }

        private void add(int batchIndex, Set<ChunkPos> batchChunks, int batchCount, long now) {
            this.chunks.addAll(batchChunks);
            this.tracker.add(batchIndex, batchCount, now);
        }

        private long lastUpdateMillis() {
            return this.tracker.lastUpdateMillis();
        }

        private boolean complete() {
            return this.tracker.complete();
        }

        private SyncedTerritory merge() {
            return this.template.withChunks(this.chunks);
        }
    }
}
