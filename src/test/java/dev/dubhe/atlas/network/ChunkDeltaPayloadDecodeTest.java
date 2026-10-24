package dev.dubhe.atlas.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/// 地图增量载荷解码的边界校验测试。
///
/// 解码端必须拒绝超出分片阈值的条目数，避免按伪造的声明值分配内存。
class ChunkDeltaPayloadDecodeTest {
    @Test
    void roundTripPreservesChunks() {
        List<ChunkDeltaPayload.ChunkData> chunks = new ArrayList<>();
        chunks.add(new ChunkDeltaPayload.ChunkData(3, -7, 42L, new byte[] { 1, 2, 3 }));
        ChunkDeltaPayload original =
                new ChunkDeltaPayload("minecraft:overworld", chunks, 0, 1, null);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ChunkDeltaPayload.STREAM_CODEC.encode(buf, original);
        ChunkDeltaPayload decoded = ChunkDeltaPayload.STREAM_CODEC.decode(buf);

        assertEquals("minecraft:overworld", decoded.dimensionId());
        assertEquals(1, decoded.chunks().size());
        assertEquals(3, decoded.chunks().get(0).chunkX());
        assertEquals(-7, decoded.chunks().get(0).chunkZ());
        assertEquals(42L, decoded.chunks().get(0).version());
    }

    @Test
    void oversizedChunkCountIsRejected() {
        // 声明超过拆包阈值的区块数：解码必须拒绝而非按该值分配内存。
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf("minecraft:overworld", 256);
        buf.writeVarInt(0);
        buf.writeVarInt(1);
        buf.writeBoolean(false);
        buf.writeVarInt(ChunkDeltaPayload.MAX_CHUNKS_PER_PACKET + 1);

        assertThrows(DecoderException.class,
                () -> ChunkDeltaPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void negativeChunkCountIsRejected() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf("minecraft:overworld", 256);
        buf.writeVarInt(0);
        buf.writeVarInt(1);
        buf.writeBoolean(false);
        buf.writeVarInt(-1);

        assertThrows(DecoderException.class,
                () -> ChunkDeltaPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void chunkCountAtLimitIsAccepted() {
        // 边界值本身合法：等于阈值时应正常解码。
        List<ChunkDeltaPayload.ChunkData> chunks = new ArrayList<>();
        for (int i = 0; i < ChunkDeltaPayload.MAX_CHUNKS_PER_PACKET; i++) {
            chunks.add(new ChunkDeltaPayload.ChunkData(i, 0, 1L, new byte[] { 0 }));
        }
        ChunkDeltaPayload original =
                new ChunkDeltaPayload("minecraft:overworld", chunks, 0, 1, null);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ChunkDeltaPayload.STREAM_CODEC.encode(buf, original);
        ChunkDeltaPayload decoded = ChunkDeltaPayload.STREAM_CODEC.decode(buf);

        assertEquals(ChunkDeltaPayload.MAX_CHUNKS_PER_PACKET, decoded.chunks().size());
        assertTrue(decoded.chunks().size() > 0);
    }
}
