package dev.dubhe.atlas.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// 地图增量分片拆分测试（规格 §3.3 / §10）。
///
/// 分片必须覆盖全部区块且不重复，批次编号连续，避免接收方漏写或重复写入。
class ChunkDeltaPayloadSplitTest {
    private static final UUID TARGET = uuid(1);

    @Test
    void emptyDeltaProducesSingleEmptyPacket() {
        List<ChunkDeltaPayload> packets = ChunkDeltaPayload.split("minecraft:overworld",
                List.of(), TARGET);
        assertEquals(1, packets.size());
        assertTrue(packets.get(0).chunks().isEmpty());
    }

    @Test
    void smallDeltaFitsSinglePacket() {
        List<ChunkDeltaPayload> packets = ChunkDeltaPayload.split("minecraft:overworld",
                chunks(100), TARGET);
        assertEquals(1, packets.size());
        assertEquals(100, packets.get(0).chunks().size());
        assertEquals(1, packets.get(0).batchCount());
    }

    @Test
    void largeDeltaSplitsAndCoversAllChunks() {
        int total = ChunkDeltaPayload.MAX_CHUNKS_PER_PACKET * 2 + 50;
        List<ChunkDeltaPayload> packets = ChunkDeltaPayload.split("minecraft:overworld",
                chunks(total), TARGET);

        assertEquals(3, packets.size());
        int received = 0;
        for (int index = 0; index < packets.size(); index++) {
            ChunkDeltaPayload packet = packets.get(index);
            assertEquals(index, packet.batchIndex(), "批次编号必须连续");
            assertEquals(3, packet.batchCount());
            assertTrue(packet.chunks().size() <= ChunkDeltaPayload.MAX_CHUNKS_PER_PACKET);
            received += packet.chunks().size();
        }
        assertEquals(total, received, "分片必须覆盖全部区块");
    }

    @Test
    void eachChunkAppearsExactlyOnce() {
        int total = ChunkDeltaPayload.MAX_CHUNKS_PER_PACKET + 10;
        List<ChunkDeltaPayload> packets = ChunkDeltaPayload.split("minecraft:overworld",
                chunks(total), TARGET);
        List<Integer> seen = new ArrayList<>();
        for (ChunkDeltaPayload packet : packets) {
            for (ChunkDeltaPayload.ChunkData chunk : packet.chunks()) {
                seen.add(chunk.chunkX());
            }
        }
        assertEquals(total, seen.size());
        assertEquals(total, seen.stream().distinct().count(), "同一区块不得出现在多个分片");
    }

    @Test
    void splitPreservesTargetAndDimension() {
        List<ChunkDeltaPayload> packets = ChunkDeltaPayload.split("minecraft:the_nether",
                chunks(ChunkDeltaPayload.MAX_CHUNKS_PER_PACKET + 1), TARGET);
        for (ChunkDeltaPayload packet : packets) {
            assertEquals("minecraft:the_nether", packet.dimensionId());
            assertEquals(TARGET, packet.targetPlayer());
        }
    }

    private static List<ChunkDeltaPayload.ChunkData> chunks(int count) {
        List<ChunkDeltaPayload.ChunkData> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new ChunkDeltaPayload.ChunkData(i, 0, 1L, new byte[] { 1 }));
        }
        return result;
    }

    private static UUID uuid(long value) {
        return new UUID(value, value);
    }
}
