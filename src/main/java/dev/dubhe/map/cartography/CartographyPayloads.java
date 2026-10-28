package dev.dubhe.map.cartography;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.cartography.ClientCartography;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.UUID;

/// 独立可选协议，旧客户端和旧服务端不需要协商新版本的队伍协议。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class CartographyPayloads {
    private CartographyPayloads() { }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("cartography-1").optional();
        registrar.playToServer(ImportRequest.TYPE, ImportRequest.CODEC, ServerCartography::importMap);
        registrar.playToServer(CraftRequest.TYPE, CraftRequest.CODEC, ServerCartography::craftMap);
        registrar.playToClient(Result.TYPE, Result.CODEC, ClientCartography::handle);
    }

    public record ImportRequest(UUID requestId, InteractionHand hand, int slot, int mapId)
            implements CustomPacketPayload {
        public static final Type<ImportRequest> TYPE = new Type<>(AleeveAtlas.id("map_import_v1"));
        public static final StreamCodec<FriendlyByteBuf, ImportRequest> CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.requestId); b.writeEnum(p.hand); b.writeVarInt(p.slot); b.writeVarInt(p.mapId); },
                b -> new ImportRequest(b.readUUID(), b.readEnum(InteractionHand.class), b.readVarInt(), b.readVarInt()));
        @Override public Type<ImportRequest> type() { return TYPE; }
    }

    public record CraftRequest(UUID requestId, InteractionHand hand, int slot, String dimension,
            int centerX, int centerZ, boolean locked, byte[] colors) implements CustomPacketPayload {
        public static final Type<CraftRequest> TYPE = new Type<>(AleeveAtlas.id("map_craft_v1"));
        public static final StreamCodec<FriendlyByteBuf, CraftRequest> CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeUUID(p.requestId); b.writeEnum(p.hand); b.writeVarInt(p.slot);
                    b.writeUtf(p.dimension, 256); b.writeInt(p.centerX); b.writeInt(p.centerZ);
                    b.writeBoolean(p.locked); b.writeByteArray(p.colors);
                }, b -> new CraftRequest(b.readUUID(), b.readEnum(InteractionHand.class), b.readVarInt(),
                        b.readUtf(256), b.readInt(), b.readInt(), b.readBoolean(), b.readByteArray(MapSnapshot.PIXELS)));
        public CraftRequest { colors = colors.clone(); }
        @Override public byte[] colors() { return this.colors.clone(); }
        @Override public Type<CraftRequest> type() { return TYPE; }
    }

    /// 成功导入时附带快照；制图成功仅返回地图 ID。错误只传递本地化键后缀。
    public record Result(UUID requestId, String status, int mapId, MapSnapshot snapshot)
            implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(AleeveAtlas.id("map_result_v1"));
        public static final StreamCodec<FriendlyByteBuf, Result> CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeUUID(p.requestId); b.writeUtf(p.status, 64); b.writeVarInt(p.mapId);
                    b.writeBoolean(p.snapshot != null); if (p.snapshot != null) p.snapshot.write(b);
                }, b -> new Result(b.readUUID(), b.readUtf(64), b.readVarInt(), b.readBoolean() ? MapSnapshot.read(b) : null));
        @Override public Type<Result> type() { return TYPE; }
    }
}
