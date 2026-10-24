package dev.dubhe.atlas.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/// 单区块像素数据的解码上限测试（规格 §10）。
///
/// 单区块未压缩为 2 KiB，压缩后通常约 1 KiB；解码端限制为 8 KiB，
/// 超出该值的载荷视为异常并拒绝，避免接受超大数组。
class ChunkPixelBytesLimitTest {
    /// 与实现内的上限保持一致。
    private static final int MAX_CHUNK_BYTES = 8 * 1024;

    @Test
    void normalChunkPayloadIsAccepted() {
        // 正常压缩后的区块数据约 1 KiB，必须能被解码。
        byte[] pixels = new byte[1024];
        ChunkDeltaPayload original = new ChunkDeltaPayload("minecraft:overworld",
                List.of(new ChunkDeltaPayload.ChunkData(0, 0, 1L, pixels)), 0, 1, null);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ChunkDeltaPayload.STREAM_CODEC.encode(buf, original);
        ChunkDeltaPayload decoded = ChunkDeltaPayload.STREAM_CODEC.decode(buf);

        assertEquals(1024, decoded.chunks().get(0).pixels().length);
    }

    @Test
    void payloadAtLimitIsAccepted() {
        // 恰好等于上限时应正常解码，确认阈值本身没有被写小。
        byte[] pixels = new byte[MAX_CHUNK_BYTES];
        ChunkDeltaPayload original = new ChunkDeltaPayload("minecraft:overworld",
                List.of(new ChunkDeltaPayload.ChunkData(0, 0, 1L, pixels)), 0, 1, null);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ChunkDeltaPayload.STREAM_CODEC.encode(buf, original);
        ChunkDeltaPayload decoded = ChunkDeltaPayload.STREAM_CODEC.decode(buf);

        assertEquals(MAX_CHUNK_BYTES, decoded.chunks().get(0).pixels().length);
    }

    @Test
    void oversizedChunkPayloadIsRejected() {
        // 手工构造声明长度超过上限的区块数据：解码必须抛 DecoderException。
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf("minecraft:overworld", 256);
        buf.writeVarInt(0);
        buf.writeVarInt(1);
        buf.writeBoolean(false);
        buf.writeVarInt(1);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeVarLong(1L);
        buf.writeVarInt(MAX_CHUNK_BYTES + 1);
        buf.writeBytes(new byte[MAX_CHUNK_BYTES + 1]);

        DecoderException error = assertThrows(DecoderException.class,
                () -> ChunkDeltaPayload.STREAM_CODEC.decode(buf));
        assertTrue(error.getMessage() != null);
    }
}
