package dev.dubhe.atlas.client.territory;

import java.util.HashSet;

import dev.dubhe.atlas.api.territory.AtlasTerritory;
import dev.dubhe.atlas.api.territory.TerritorySyncPayload;
import dev.dubhe.atlas.territory.SyncedTerritory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 客户端领地接收与分片重组（规格 §7.1）。
///
/// 服务端下发的领地可能拆成多个负载；本类按「领地 ID」累积区块，
/// 收齐全部批次后一次性写入客户端注册表，避免重复触发连通分量重算。
public final class ClientTerritorySync {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/territory");

    /// 领地 ID → 已累积的分片数据。
    private static final Map<String, Accumulator> PENDING = new HashMap<>();

    private ClientTerritorySync() {
    }

    public static void handle(TerritorySyncPayload payload, IPayloadContext context) {
        if (payload.action() == TerritorySyncPayload.Action.REMOVE) {
            PENDING.remove(payload.territory().territoryId());
            AtlasTerritory.client().removeTerritory(payload.territory().territoryId());
            return;
        }
        String territoryId = payload.territory().territoryId();
        Accumulator accumulator = PENDING.computeIfAbsent(territoryId,
                ignored -> new Accumulator(payload.batchCount(), payload.territory()));
        accumulator.add(payload.batchIndex(), payload.territory().chunks());
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
    private static final class Accumulator {
        private final int expectedBatches;
        private final SyncedTerritory template;
        private final Set<ChunkPos> chunks = new HashSet<>();
        private int received;

        private Accumulator(int expectedBatches, SyncedTerritory template) {
            this.expectedBatches = Math.max(1, expectedBatches);
            this.template = template;
        }

        private void add(int batchIndex, Set<ChunkPos> batchChunks) {
            chunks.addAll(batchChunks);
            received++;
        }

        private boolean complete() {
            return received >= expectedBatches;
        }

        private SyncedTerritory merge() {
            return template.withChunks(chunks);
        }
    }
}
