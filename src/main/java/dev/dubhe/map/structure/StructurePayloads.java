package dev.dubhe.map.structure;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.structure.ClientStructures;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/// 结构数据使用独立可选通道，批次上限限制网络和主线程处理成本。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class StructurePayloads {
    public static final int MAX_RECORDS = 64;

    private StructurePayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("structures-1").optional();
        registrar.playToClient(Discovery.TYPE, Discovery.CODEC, ClientStructures::receiveDiscovery);
        registrar.playBidirectional(Share.TYPE, Share.CODEC, (payload, context) -> {
            if (context.flow().isServerbound()) {
                ServerStructures.share(payload, context);
            } else {
                ClientStructures.receiveShare(payload, context);
            }
        });
    }

    private static List<StructureRecord> readRecords(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_RECORDS) {
            throw new IllegalArgumentException("Invalid structure batch length " + size);
        }
        List<StructureRecord> records = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            records.add(StructureRecord.read(buffer));
        }
        return records;
    }

    private static void writeRecords(FriendlyByteBuf buffer, List<StructureRecord> records) {
        buffer.writeVarInt(records.size());
        for (StructureRecord record : records) {
            record.write(buffer);
        }
    }

    private static List<StructureRecord> validate(List<StructureRecord> records) {
        if (records.size() > MAX_RECORDS) {
            throw new IllegalArgumentException("Too many structure records");
        }
        return List.copyOf(records);
    }

    public record Discovery(int chunkX, int chunkZ, List<StructureRecord> records) implements CustomPacketPayload {
        public static final Type<Discovery> TYPE = new Type<>(AleeveAtlas.id("structure_discovery"));
        public static final StreamCodec<FriendlyByteBuf, Discovery> CODEC = StreamCodec.of((b, p) -> {
            b.writeInt(p.chunkX);
            b.writeInt(p.chunkZ);
            writeRecords(b, p.records);
        }, b -> new Discovery(b.readInt(), b.readInt(), readRecords(b)));

        public Discovery {
            records = validate(records);
            if (records.stream().anyMatch(r -> !r.intersectsChunk(chunkX, chunkZ))) {
                throw new IllegalArgumentException("Structure does not intersect sampled chunk");
            }
        }

        @Override
        public Type<Discovery> type() {
            return TYPE;
        }
    }

    public record Share(List<StructureRecord> records) implements CustomPacketPayload {
        public static final Type<Share> TYPE = new Type<>(AleeveAtlas.id("structure_share"));
        public static final StreamCodec<FriendlyByteBuf, Share> CODEC = StreamCodec.of(
                (b, p) -> writeRecords(b, p.records), b -> new Share(readRecords(b)));

        public Share {
            records = validate(records);
        }

        @Override
        public Type<Share> type() {
            return TYPE;
        }
    }
}
