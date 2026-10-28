package dev.dubhe.map.cartography;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BannerPayloadTest {
    @Test
    void optionalResponseKeepsBannersAndOriginalMapSnapshotTogether() {
        var marker = new BannerMarker(new BlockPos(-130, -32, 257), DyeColor.CYAN, "example");
        var result = result(List.of(marker));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var response = assertInstanceOf(CartographyPayloads.BannerResult.class, result.response(true));
            CartographyPayloads.BannerResult.CODEC.encode(buffer, response);
            var received = CartographyPayloads.BannerResult.CODEC.decode(buffer).result();
            assertEquals(result.requestId(), received.requestId());
            assertEquals(result.banners(), received.banners());
            assertEquals(result.snapshot().targets(), received.snapshot().targets());
            assertArrayEquals(result.snapshot().colors(), received.snapshot().colors());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void connectionWithoutBannerChannelUsesUnchangedMapProtocol() {
        var result = result(List.of(new BannerMarker(BlockPos.ZERO, DyeColor.RED, "example")));
        assertInstanceOf(CartographyPayloads.Result.class, result.response(false));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CartographyPayloads.Result.CODEC.encode(buffer, result);
            var received = CartographyPayloads.Result.CODEC.decode(buffer);
            assertTrue(received.banners().isEmpty());
            assertEquals(result.snapshot().targets(), received.snapshot().targets());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
        var error = new CartographyPayloads.Result(UUID.randomUUID(), "changed", -1, null);
        assertSame(error, error.response(true));
    }

    @Test
    void decoderRejectsInvalidCountsAndDyeColors() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int count : new int[] { -1, BannerMarker.MAX_MARKERS + 1, Integer.MAX_VALUE }) {
                buffer.clear();
                CartographyPayloads.Result.CODEC.encode(buffer, result(List.of()));
                buffer.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> CartographyPayloads.BannerResult.CODEC.decode(buffer));
            }
            buffer.clear();
            CartographyPayloads.Result.CODEC.encode(buffer, result(List.of()));
            buffer.writeVarInt(1);
            buffer.writeBlockPos(BlockPos.ZERO);
            buffer.writeVarInt(16);
            buffer.writeUtf("example");
            assertThrows(RuntimeException.class, () -> CartographyPayloads.BannerResult.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static CartographyPayloads.Result result(List<BannerMarker> banners) {
        return new CartographyPayloads.Result(UUID.randomUUID(), "imported", 33,
                MapSnapshotTest.snapshot(33, 3, new byte[MapSnapshot.PIXELS]), banners);
    }
}
