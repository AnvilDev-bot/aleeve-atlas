package dev.dubhe.atlas.api.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dubhe.atlas.territory.SyncedTerritory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/// 领地同步负载的分片拆分测试（规格 §7.1 / §10 体积约束）。
class TerritorySyncPayloadTest {
    @Test
    void smallTerritoryFitsSinglePacket() {
        SyncedTerritory territory = territory(Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0)));
        List<TerritorySyncPayload> packets = TerritorySyncPayload.split(
                TerritorySyncPayload.Action.UPSERT, territory);

        assertEquals(1, packets.size());
        assertEquals(1, packets.get(0).batchCount());
        assertEquals(2, packets.get(0).territory().chunks().size());
    }

    @Test
    void largeTerritorySplitsAndCoversAllChunks() {
        Set<ChunkPos> chunks = new HashSet<>();
        int total = TerritorySyncPayload.MAX_CHUNKS_PER_PACKET + 500;
        for (int i = 0; i < total; i++) {
            chunks.add(new ChunkPos(i, 0));
        }
        List<TerritorySyncPayload> packets = TerritorySyncPayload.split(
                TerritorySyncPayload.Action.UPSERT, territory(chunks));

        assertEquals(2, packets.size());
        int received = 0;
        for (TerritorySyncPayload packet : packets) {
            assertTrue(packet.territory().chunks().size()
                    <= TerritorySyncPayload.MAX_CHUNKS_PER_PACKET);
            assertEquals(2, packet.batchCount());
            received += packet.territory().chunks().size();
        }
        assertEquals(total, received);
    }

    @Test
    void mergedChunksRestoreOriginalSet() {
        Set<ChunkPos> chunks = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            chunks.add(new ChunkPos(i, i));
        }
        SyncedTerritory original = territory(chunks);
        SyncedTerritory restored = original.withChunks(Set.of())
                .mergeChunks(Set.of(new ChunkPos(0, 0)));
        assertTrue(restored.chunks().contains(new ChunkPos(0, 0)));

        SyncedTerritory full = original.withChunks(Set.of()).mergeChunks(chunks);
        assertEquals(10, full.chunks().size());
    }

    @Test
    void syncPayloadCarriesAllMetadata() {
        SyncedTerritory territory = new SyncedTerritory("t1", Level.OVERWORLD,
                Set.of(new ChunkPos(2, 3)), Component.literal("名称"), 0x33AAFF, 0.45f, 7);
        List<TerritorySyncPayload> packets = TerritorySyncPayload.split(
                TerritorySyncPayload.Action.UPSERT, territory);
        SyncedTerritory carried = packets.get(0).territory();

        assertEquals("t1", carried.territoryId());
        assertEquals(Level.OVERWORLD, carried.dimension());
        assertEquals(0x33AAFF, carried.themeColorRgb());
        assertEquals(0.45f, carried.fillOpacity(), 1e-6f);
        assertEquals(7, carried.zOrder());
        assertEquals("名称", carried.displayName().getString());
    }

    private static SyncedTerritory territory(Set<ChunkPos> chunks) {
        return new SyncedTerritory("t", Level.OVERWORLD, chunks, Component.literal("t"),
                0xFFFFFF, 0.25f, 0);
    }
}
