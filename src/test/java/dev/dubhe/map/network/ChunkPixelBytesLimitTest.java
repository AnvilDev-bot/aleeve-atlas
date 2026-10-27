package dev.dubhe.map.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/// 单区块像素数据的解码上限测试。
///
/// 单区块色板数据使用与上行拆包一致的收发上限。
class ChunkPixelBytesLimitTest {
    private static final int MAX_CHUNK_BYTES = ChunkDeltaPayload.MAX_CHUNK_BYTES;

    @Test
    void normalChunkPayloadIsAccepted() {
        // 正常压缩后的区块数据约 1 KiB，必须能被解码。
        byte[] pixels = new byte[1024];
        ChunkDeltaPayload original = new ChunkDeltaPayload("minecraft:overworld",
                List.of(new ChunkDeltaPayload.ChunkData(0, 0, 1L, pixels)), 0, 1, null);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ChunkDeltaPayload.STREAM_CODEC.encode(buf, original);
        ChunkDeltaPayload decoded = ChunkDeltaPayload.STREAM_CODEC.decode(buf);

        Assertions.assertEquals(1024, decoded.chunks().get(0).pixels().length);
    }

    @Test
    void payloadAtLimitIsAccepted() {
        // 恰好等于上限时应正常拆包并解码，确认收发阈值一致。
        byte[] pixels = new byte[MAX_CHUNK_BYTES];
        ChunkDeltaPayload original = ChunkDeltaPayload.splitForUpload("minecraft:overworld",
                List.of(new ChunkDeltaPayload.ChunkData(0, 0, 1L, pixels)), null).get(0);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ChunkDeltaPayload.STREAM_CODEC.encode(buf, original);
        ChunkDeltaPayload decoded = ChunkDeltaPayload.STREAM_CODEC.decode(buf);

        Assertions.assertEquals(MAX_CHUNK_BYTES, decoded.chunks().get(0).pixels().length);
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

        DecoderException error = Assertions.assertThrows(DecoderException.class,
                () -> ChunkDeltaPayload.STREAM_CODEC.decode(buf));
        Assertions.assertTrue(error.getMessage() != null);
    }
}
