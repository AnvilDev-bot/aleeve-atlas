package dev.dubhe.map.cartography;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CartographyPayloadTest {
    @Test void frozenCraftRequestRoundTripsWithMaterialChoice() {
        byte[] colors = new byte[MapSnapshot.PIXELS]; colors[9] = 6;
        var request = new CartographyPayloads.CraftRequest(UUID.randomUUID(), InteractionHand.OFF_HAND,
                8, "minecraft:the_nether", -128, 256, true, colors);
        colors[9] = 7;
        request.colors()[9] = 8;
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CartographyPayloads.CraftRequest.CODEC.encode(buffer, request);
            var decoded = CartographyPayloads.CraftRequest.CODEC.decode(buffer);
            assertEquals(request.requestId(), decoded.requestId());
            assertEquals(request.hand(), decoded.hand());
            assertEquals(8, decoded.slot());
            assertEquals("minecraft:the_nether", decoded.dimension());
            assertEquals(-128, decoded.centerX());
            assertTrue(decoded.locked());
            assertEquals(6, decoded.colors()[9]);
        } finally { buffer.release(); }
    }

    @Test void decoderRejectsOversizedImageBeforeAllocation() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(UUID.randomUUID()); buffer.writeEnum(InteractionHand.MAIN_HAND);
            buffer.writeVarInt(0); buffer.writeUtf("minecraft:overworld"); buffer.writeInt(0); buffer.writeInt(0);
            buffer.writeBoolean(false); buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(RuntimeException.class, () -> CartographyPayloads.CraftRequest.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void importAndBothResultKindsRoundTrip() {
        var id = UUID.randomUUID();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var request = new CartographyPayloads.ImportRequest(id, InteractionHand.MAIN_HAND, 2, 33);
            CartographyPayloads.ImportRequest.CODEC.encode(buffer, request);
            assertEquals(request, CartographyPayloads.ImportRequest.CODEC.decode(buffer));
            var snapshot = MapSnapshotTest.snapshot(33, 2, new byte[MapSnapshot.PIXELS]);
            CartographyPayloads.Result.CODEC.encode(buffer, new CartographyPayloads.Result(id, "imported", 33, snapshot));
            var result = CartographyPayloads.Result.CODEC.decode(buffer);
            assertEquals(id, result.requestId()); assertEquals("imported", result.status());
            assertEquals(snapshot.targets(), result.snapshot().targets());
            var failure = new CartographyPayloads.Result(id, "glass_missing", -1, null);
            CartographyPayloads.Result.CODEC.encode(buffer, failure);
            assertEquals(failure, CartographyPayloads.Result.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
