package dev.dubhe.map.structure;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructurePayloadTest {
    @Test
    void discoveryMatchesIntersectingChunkRatherThanCenter() {
        var record = StructureStoreTest.record(-3, StructureRecord.Source.DISCOVERED);
        assertTrue(record.intersectsChunk(-1, 0));
        assertFalse(record.intersectsChunk(0, 0));
        var packet = new StructurePayloads.Discovery(-1, 0, List.of(record));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            StructurePayloads.Discovery.CODEC.encode(buffer, packet);
            assertEquals(packet, StructurePayloads.Discovery.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
        assertThrows(IllegalArgumentException.class, () -> new StructurePayloads.Discovery(0, 0, List.of(record)));
    }

    @Test
    void rejectsUnboundedNetworkBatchesBeforeAllocation() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> StructurePayloads.Share.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
        assertThrows(IllegalArgumentException.class, () -> new StructurePayloads.Share(Collections.nCopies(
                StructurePayloads.MAX_RECORDS + 1, StructureStoreTest.record(0, StructureRecord.Source.SHARED))));
    }
}
