package dev.dubhe.map.network;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 地图增量分片拆分测试。
///
/// 分片必须覆盖全部区块且不重复，批次编号连续，避免接收方漏写或重复写入。
class ChunkDeltaPayloadSplitTest {
    private static final UUID TARGET = uuid(1);

    @Test
    void emptyDeltaProducesSingleEmptyPacket() {
        List<ChunkDeltaPayload> packets = ChunkDeltaPayload.split("minecraft:overworld",
                List.of(), TARGET);
        Assertions.assertEquals(1, packets.size());
        Assertions.assertTrue(packets.get(0).chunks().isEmpty());
    }

    @Test
    void smallDeltaFitsSinglePacket() {
        List<ChunkDeltaPayload> packets = ChunkDeltaPayload.split("minecraft:overworld",
                chunks(100), TARGET);
        Assertions.assertEquals(1, packets.size());
        Assertions.assertEquals(100, packets.get(0).chunks().size());
        Assertions.assertEquals(1, packets.get(0).batchCount());
    }

    @Test
    void largeDeltaSplitsAndCoversAllChunks() {
        int total = ChunkDeltaPayload.MAX_CHUNKS_PER_PACKET * 2 + 50;
        List<ChunkDeltaPayload> packets = ChunkDeltaPayload.split("minecraft:overworld",
                chunks(total), TARGET);

        Assertions.assertEquals(3, packets.size());
        int received = 0;
        for (int index = 0; index < packets.size(); index++) {
            ChunkDeltaPayload packet = packets.get(index);
            Assertions.assertEquals(index, packet.batchIndex(), "批次编号必须连续");
            Assertions.assertEquals(3, packet.batchCount());
            Assertions.assertTrue(packet.chunks().size() <= ChunkDeltaPayload.MAX_CHUNKS_PER_PACKET);
            received += packet.chunks().size();
        }
        Assertions.assertEquals(total, received, "分片必须覆盖全部区块");
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
        Assertions.assertEquals(total, seen.size());
        Assertions.assertEquals(total, seen.stream().distinct().count(), "同一区块不得出现在多个分片");
    }

    @Test
    void splitPreservesTargetAndDimension() {
        List<ChunkDeltaPayload> packets = ChunkDeltaPayload.split("minecraft:the_nether",
                chunks(ChunkDeltaPayload.MAX_CHUNKS_PER_PACKET + 1), TARGET);
        for (ChunkDeltaPayload packet : packets) {
            Assertions.assertEquals("minecraft:the_nether", packet.dimensionId());
            Assertions.assertEquals(TARGET, packet.targetPlayer());
        }
    }

    @Test
    void uploadSplitRespectsClientByteBudget() {
        List<ChunkDeltaPayload.ChunkData> chunks = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            chunks.add(new ChunkDeltaPayload.ChunkData(i, 0, 1L, new byte[1024]));
        }
        List<ChunkDeltaPayload> packets = ChunkDeltaPayload.splitForUpload(
                "minecraft:overworld", chunks, TARGET);

        Assertions.assertTrue(packets.size() > 1);
        for (ChunkDeltaPayload packet : packets) {
            int estimated = 32 + packet.chunks().stream()
                    .mapToInt(chunk -> chunk.pixels().length + 24).sum();
            Assertions.assertTrue(estimated <= ChunkDeltaPayload.MAX_UPLOAD_BYTES);
        }
    }

    @Test
    void uploadRejectsSingleChunkBeyondBudget() {
        List<ChunkDeltaPayload.ChunkData> chunks = List.of(
                new ChunkDeltaPayload.ChunkData(0, 0, 1L,
                        new byte[ChunkDeltaPayload.MAX_UPLOAD_BYTES]));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> ChunkDeltaPayload.splitForUpload("minecraft:overworld", chunks, TARGET));
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
