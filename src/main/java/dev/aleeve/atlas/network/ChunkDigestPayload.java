package dev.aleeve.atlas.network;

import dev.aleeve.atlas.AleeveAtlas;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/// 区块摘要上报（规格 §3.3 / §10）。
///
/// 仅上报「区块坐标 + 地图数据版本号」，不含地图数据本体；
/// 单包体积受限（客户端→服务端 < 32 KiB），超大存档由发送方分批。
public record ChunkDigestPayload(String dimensionId, List<Entry> entries,
        int batchIndex, int batchCount, java.util.UUID senderId)
        implements CustomPacketPayload {
    /// 单包条目上限：每条约 12 字节，留足余量远低于 32 KiB。
    public static final int MAX_ENTRIES_PER_PACKET = 1024;

    public static final Type<ChunkDigestPayload> TYPE =
            new Type<>(AleeveAtlas.id("chunk_digest"));

    /// 单个区块摘要条目。
    public record Entry(int chunkX, int chunkZ, long version) {
    }

    public static final StreamCodec<FriendlyByteBuf, ChunkDigestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.dimensionId, 256);
                        buf.writeBoolean(payload.senderId != null);
                        if (payload.senderId != null) {
                            buf.writeUUID(payload.senderId);
                        }
                        buf.writeVarInt(payload.batchIndex);
                        buf.writeVarInt(payload.batchCount);
                        buf.writeVarInt(payload.entries.size());
                        for (Entry entry : payload.entries) {
                            buf.writeInt(entry.chunkX);
                            buf.writeInt(entry.chunkZ);
                            buf.writeVarLong(entry.version);
                        }
                    },
                    buf -> {
                        String dimension = buf.readUtf(256);
                        java.util.UUID sender = buf.readBoolean() ? buf.readUUID() : null;
                        int batchIndex = buf.readVarInt();
                        int batchCount = buf.readVarInt();
                        int size = buf.readVarInt();
                        List<Entry> entries = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            entries.add(new Entry(buf.readInt(), buf.readInt(),
                                    buf.readVarLong()));
                        }
                        return new ChunkDigestPayload(dimension, entries, batchIndex, batchCount, sender);
                    });

    /// 把完整摘要切分为多个符合体积上限的分包。
    public static List<ChunkDigestPayload> split(String dimensionId, List<Entry> entries,
            java.util.UUID senderId) {
        List<ChunkDigestPayload> packets = new ArrayList<>();
        int total = Math.max(1, (entries.size() + MAX_ENTRIES_PER_PACKET - 1)
                / MAX_ENTRIES_PER_PACKET);
        for (int index = 0; index < total; index++) {
            int from = index * MAX_ENTRIES_PER_PACKET;
            int to = Math.min(entries.size(), from + MAX_ENTRIES_PER_PACKET);
            packets.add(new ChunkDigestPayload(dimensionId,
                    List.copyOf(entries.subList(from, to)), index, total, senderId));
        }
        return packets;
    }

    @Override
    public Type<ChunkDigestPayload> type() {
        return TYPE;
    }
}
