package dev.dubhe.map.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamSightingsPayloadTest {
    @Test
    void roundTripFitsUploadLimitEvenWithMaximumUtf8Names() {
        var sighting = new PlayerPositionPayload(UUID.randomUUID(), "队".repeat(64),
                "维".repeat(256), 123456, 64, -654321, false);
        var payload = new TeamSightingsPayload(UUID.randomUUID(), Collections.nCopies(64, sighting));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TeamSightingsPayload.STREAM_CODEC.encode(buffer, payload);
            assertTrue(buffer.readableBytes() < 32768);
            assertEquals(payload, TeamSightingsPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsExcessiveCountBeforeAllocation() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeUtf("minecraft:overworld");
            buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(DecoderException.class, () -> TeamSightingsPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void emptySnapshotRoundTrips() {
        var payload = new TeamSightingsPayload(UUID.randomUUID(), List.of());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TeamSightingsPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, TeamSightingsPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
