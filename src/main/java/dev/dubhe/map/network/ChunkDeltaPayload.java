package dev.dubhe.map.network;

import dev.dubhe.map.AleeveAtlas;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 地图增量同步分片。
///
/// 每片携带若干区块的压缩像素数据；接收端按 `batchIndex/batchCount` 逐片写入本地缓存，
/// 分帧处理以避免接收端帧率骤降。
public record ChunkDeltaPayload(String dimensionId, List<ChunkData> chunks,
        int batchIndex, int batchCount, UUID targetPlayer)
        implements CustomPacketPayload {
    /// 单包区块数上限：每区块 16×16 像素压缩后约 1 KiB，取 128 区块留足余量。
    public static final int MAX_CHUNKS_PER_PACKET = 128;
    /// 客户端上行预算，预留协议头后仍小于 32 KiB。
    public static final int MAX_UPLOAD_BYTES = 31 * 1024;
    /// 服务端下行预算，预留协议头后仍小于 1 MiB。
    private static final int MAX_DOWNLOAD_BYTES = 1024 * 1024 - 1;
    private static final int PACKET_HEADER_BYTES = 32;
    private static final int CHUNK_HEADER_BYTES = 24;
    /// 单区块压缩数据的解码上限（字节）。
    ///
    /// 未压缩为 2 KiB（颜色与高度各 1024 字节），最坏情况下 Deflate 略有膨胀，
    /// 取 8 KiB 留足余量，同时避免接受异常大的数组。
    private static final int MAX_CHUNK_BYTES = 8 * 1024;

    public static final Type<ChunkDeltaPayload> TYPE =
            new Type<>(AleeveAtlas.id("chunk_delta"));

    /// 单个区块的传输数据：坐标、版本号与像素颜色压缩串。
    public record ChunkData(int chunkX, int chunkZ, long version, byte[] pixels) {
    }

    public static final StreamCodec<FriendlyByteBuf, ChunkDeltaPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.dimensionId, 256);
                        buf.writeVarInt(payload.batchIndex);
                        buf.writeVarInt(payload.batchCount);
                        buf.writeBoolean(payload.targetPlayer != null);
                        if (payload.targetPlayer != null) {
                            buf.writeUUID(payload.targetPlayer);
                        }
                        buf.writeVarInt(payload.chunks.size());
                        for (ChunkData chunk : payload.chunks) {
                            buf.writeInt(chunk.chunkX);
                            buf.writeInt(chunk.chunkZ);
                            buf.writeVarLong(chunk.version);
                            buf.writeByteArray(chunk.pixels);
                        }
                    },
                    buf -> {
                        final String dimension = buf.readUtf(256);
                        int batchIndex = buf.readVarInt();
                        int batchCount = buf.readVarInt();
                        final UUID target = buf.readBoolean() ? buf.readUUID() : null;
                        // 批次序号参与移位运算，越界会让接收端掩码失真。
                        if (batchCount < 1 || batchIndex < 0 || batchIndex >= batchCount) {
                            throw new DecoderException(
                                    "Invalid delta batch index " + batchIndex + " of " + batchCount);
                        }
                        int size = buf.readVarInt();
                        // 上限与拆包阈值一致：超出说明数据损坏或伪造，直接拒绝避免按声明值分配内存。
                        if (size < 0 || size > MAX_CHUNKS_PER_PACKET) {
                            throw new DecoderException("Invalid delta chunk count: " + size);
                        }
                        List<ChunkData> chunks = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            chunks.add(new ChunkData(buf.readInt(), buf.readInt(),
                                    buf.readVarLong(), buf.readByteArray(MAX_CHUNK_BYTES)));
                        }
                        return new ChunkDeltaPayload(dimension, chunks, batchIndex, batchCount, target);
                    });

    /// 把区块数据切分为服务端下行分片，同时限制区块数与估算字节数。
    public static List<ChunkDeltaPayload> split(String dimensionId, List<ChunkData> chunks,
            UUID targetPlayer) {
        return splitWithBudget(dimensionId, chunks, targetPlayer, MAX_DOWNLOAD_BYTES);
    }

    /// 把区块数据切分为客户端上行分片，保证每包小于 32 KiB。
    public static List<ChunkDeltaPayload> splitForUpload(String dimensionId, List<ChunkData> chunks,
            UUID targetPlayer) {
        return splitWithBudget(dimensionId, chunks, targetPlayer, MAX_UPLOAD_BYTES);
    }

    private static List<ChunkDeltaPayload> splitWithBudget(String dimensionId, List<ChunkData> chunks,
            UUID targetPlayer, int maxBytes) {
        List<List<ChunkData>> batches = new ArrayList<>();
        List<ChunkData> current = new ArrayList<>();
        int currentBytes = PACKET_HEADER_BYTES;
        for (ChunkData chunk : chunks) {
            int chunkBytes = chunk.pixels().length + CHUNK_HEADER_BYTES;
            if (PACKET_HEADER_BYTES + chunkBytes > maxBytes) {
                throw new IllegalArgumentException("Chunk payload exceeds packet byte budget");
            }
            if (!current.isEmpty()
                    && (current.size() >= MAX_CHUNKS_PER_PACKET
                    || currentBytes + chunkBytes > maxBytes)) {
                batches.add(List.copyOf(current));
                current = new ArrayList<>();
                currentBytes = PACKET_HEADER_BYTES;
            }
            current.add(chunk);
            currentBytes += chunkBytes;
        }
        if (!current.isEmpty() || batches.isEmpty()) {
            batches.add(List.copyOf(current));
        }

        List<ChunkDeltaPayload> packets = new ArrayList<>(batches.size());
        for (int index = 0; index < batches.size(); index++) {
            packets.add(new ChunkDeltaPayload(dimensionId, batches.get(index), index,
                    batches.size(), targetPlayer));
        }
        return packets;
    }

    @Override
    public Type<ChunkDeltaPayload> type() {
        return TYPE;
    }
}
