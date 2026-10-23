package dev.aleeve.atlas.network;

import dev.aleeve.atlas.AleeveAtlas;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/// 地图增量同步分片（规格 §3.3 / §10：服务端→客户端单包 ≤ 1 MiB）。
///
/// 每片携带若干区块的压缩像素数据；接收端按 `batchIndex/batchCount` 逐片写入本地缓存，
/// 分帧处理以避免接收端帧率骤降（规格 §12.1）。
public record ChunkDeltaPayload(String dimensionId, List<ChunkData> chunks,
        int batchIndex, int batchCount, java.util.UUID targetPlayer)
        implements CustomPacketPayload {
    /// 单包区块数上限：每区块 16×16 像素压缩后约 1 KiB，取 128 区块留足余量。
    public static final int MAX_CHUNKS_PER_PACKET = 128;

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
                        String dimension = buf.readUtf(256);
                        int batchIndex = buf.readVarInt();
                        int batchCount = buf.readVarInt();
                        java.util.UUID target = buf.readBoolean() ? buf.readUUID() : null;
                        int size = buf.readVarInt();
                        List<ChunkData> chunks = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            chunks.add(new ChunkData(buf.readInt(), buf.readInt(),
                                    buf.readVarLong(), buf.readByteArray()));
                        }
                        return new ChunkDeltaPayload(dimension, chunks, batchIndex, batchCount, target);
                    });

    /// 把区块数据切分为符合体积上限的分片。
    public static List<ChunkDeltaPayload> split(String dimensionId, List<ChunkData> chunks,
            java.util.UUID targetPlayer) {
        List<ChunkDeltaPayload> packets = new ArrayList<>();
        int total = Math.max(1, (chunks.size() + MAX_CHUNKS_PER_PACKET - 1)
                / MAX_CHUNKS_PER_PACKET);
        for (int index = 0; index < total; index++) {
            int from = index * MAX_CHUNKS_PER_PACKET;
            int to = Math.min(chunks.size(), from + MAX_CHUNKS_PER_PACKET);
            packets.add(new ChunkDeltaPayload(dimensionId,
                    List.copyOf(chunks.subList(from, to)), index, total, targetPlayer));
        }
        return packets;
    }

    @Override
    public Type<ChunkDeltaPayload> type() {
        return TYPE;
    }
}
