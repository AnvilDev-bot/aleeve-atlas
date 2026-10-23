package dev.aleeve.atlas.api.territory;

import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.territory.SyncedTerritory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;

/// 领地下发负载（规格 §7.1：服务端 API 下发全服/视野内领地区块变更）。
///
/// 单个负载携带一个领地的完整区块集合与展示元数据；区块集合较大时按
/// {@link #MAX_CHUNKS_PER_PACKET} 拆分为多个负载依次发送。
public record TerritorySyncPayload(Action action, SyncedTerritory territory, int batchIndex,
        int batchCount) implements CustomPacketPayload {
    /// 单包区块数上限：每区块 8 字节，128 区块约 1 KiB，远低于 1 MiB 上限。
    public static final int MAX_CHUNKS_PER_PACKET = 4096;

    public static final Type<TerritorySyncPayload> TYPE =
            new Type<>(AleeveAtlas.id("territory_sync"));

    /// 同步动作。
    public enum Action {
        UPSERT,
        REMOVE
    }

    public static final StreamCodec<FriendlyByteBuf, TerritorySyncPayload> STREAM_CODEC =
            StreamCodec.of(TerritorySyncPayload::write, TerritorySyncPayload::read);

    private static void write(FriendlyByteBuf buf, TerritorySyncPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeInt(payload.batchIndex);
        buf.writeInt(payload.batchCount);
        buf.writeUtf(payload.territory.territoryId(), 128);
        buf.writeUtf(payload.territory.dimension().identifier().toString(), 256);
        buf.writeInt(payload.territory.themeColorRgb());
        buf.writeFloat(payload.territory.fillOpacity());
        buf.writeInt(payload.territory.zOrder());
        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf,
                payload.territory.displayName());
        buf.writeVarInt(payload.territory.chunks().size());
        for (ChunkPos pos : payload.territory.chunks()) {
            buf.writeInt(pos.x);
            buf.writeInt(pos.z);
        }
    }

    private static TerritorySyncPayload read(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        int batchIndex = buf.readInt();
        int batchCount = buf.readInt();
        String territoryId = buf.readUtf(128);
        Identifier dimensionId = Identifier.parse(buf.readUtf(256));
        int themeColor = buf.readInt();
        float fillOpacity = buf.readFloat();
        int zOrder = buf.readInt();
        Component displayName = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf);
        int chunkCount = buf.readVarInt();
        Set<ChunkPos> chunks = new HashSet<>(Math.max(16, chunkCount));
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(new ChunkPos(buf.readInt(), buf.readInt()));
        }
        SyncedTerritory territory = new SyncedTerritory(territoryId, dimensionId, chunks,
                displayName, themeColor, fillOpacity, zOrder);
        return new TerritorySyncPayload(action, territory, batchIndex, batchCount);
    }

    /// 把一个领地拆分为符合体积上限的负载序列。
    public static List<TerritorySyncPayload> split(Action action, SyncedTerritory territory) {
        List<ChunkPos> all = new ArrayList<>(territory.chunks());
        if (all.size() <= MAX_CHUNKS_PER_PACKET) {
            return List.of(new TerritorySyncPayload(action, territory, 0, 1));
        }
        int total = (all.size() + MAX_CHUNKS_PER_PACKET - 1) / MAX_CHUNKS_PER_PACKET;
        List<TerritorySyncPayload> packets = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            int from = index * MAX_CHUNKS_PER_PACKET;
            int to = Math.min(all.size(), from + MAX_CHUNKS_PER_PACKET);
            SyncedTerritory slice = territory.withChunks(new HashSet<>(all.subList(from, to)));
            packets.add(new TerritorySyncPayload(action, slice, index, total));
        }
        return packets;
    }

    @Override
    public Type<TerritorySyncPayload> type() {
        return TYPE;
    }
}
