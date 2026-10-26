package dev.dubhe.atlas.api.territory;

import dev.dubhe.atlas.territory.SyncedTerritory;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 领地同步负载的分片拆分测试。
class TerritorySyncPayloadTest {
    @Test
    void smallTerritoryFitsSinglePacket() {
        SyncedTerritory territory = territory(Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0)));
        List<TerritorySyncPayload> packets = TerritorySyncPayload.split(
                TerritorySyncPayload.Action.UPSERT, territory);

        Assertions.assertEquals(1, packets.size());
        Assertions.assertEquals(1, packets.get(0).batchCount());
        Assertions.assertEquals(2, packets.get(0).territory().chunks().size());
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

        Assertions.assertEquals(2, packets.size());
        int received = 0;
        for (TerritorySyncPayload packet : packets) {
            Assertions.assertTrue(packet.territory().chunks().size()
                    <= TerritorySyncPayload.MAX_CHUNKS_PER_PACKET);
            Assertions.assertEquals(2, packet.batchCount());
            received += packet.territory().chunks().size();
        }
        Assertions.assertEquals(total, received);
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
        Assertions.assertTrue(restored.chunks().contains(new ChunkPos(0, 0)));

        SyncedTerritory full = original.withChunks(Set.of()).mergeChunks(chunks);
        Assertions.assertEquals(10, full.chunks().size());
    }

    @Test
    void syncPayloadCarriesAllMetadata() {
        SyncedTerritory territory = new SyncedTerritory("t1", Level.OVERWORLD,
                Set.of(new ChunkPos(2, 3)), Component.literal("名称"), 0x33AAFF, 0.45f,
                new BorderStyle(0xFF112233, 2.0F, true),
                List.of(Component.literal("owner"), Component.literal("details")), 7);
        List<TerritorySyncPayload> packets = TerritorySyncPayload.split(
                TerritorySyncPayload.Action.UPSERT, territory);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TerritorySyncPayload.STREAM_CODEC.encode(buf, packets.get(0));
        SyncedTerritory carried = TerritorySyncPayload.STREAM_CODEC.decode(buf).territory();

        Assertions.assertEquals("t1", carried.territoryId());
        Assertions.assertEquals(Level.OVERWORLD, carried.dimension());
        Assertions.assertEquals(0x33AAFF, carried.themeColorRgb());
        Assertions.assertEquals(0.45f, carried.fillOpacity(), 1e-6f);
        Assertions.assertEquals(7, carried.zOrder());
        Assertions.assertEquals("名称", carried.displayName().getString());
        Assertions.assertEquals(new BorderStyle(0xFF112233, 2.0F, true), carried.borderStyle());
        Assertions.assertEquals(2, carried.tooltip().size());
        Assertions.assertEquals("owner", carried.tooltip().get(0).getString());
        Assertions.assertEquals("details", carried.tooltip().get(1).getString());
    }

    private static SyncedTerritory territory(Set<ChunkPos> chunks) {
        return new SyncedTerritory("t", Level.OVERWORLD, chunks, Component.literal("t"),
                0xFFFFFF, 0.25f, BorderStyle.ofTheme(0xFFFFFF), List.of(), 0);
    }
}
