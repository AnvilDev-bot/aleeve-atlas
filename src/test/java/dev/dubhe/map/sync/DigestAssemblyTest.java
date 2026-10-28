package dev.dubhe.map.sync;

import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.network.ChunkDigestPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DigestAssemblyTest {
    @Test
    void multiPacketDigestDoesNotResendChunksListedInOtherPackets() {
        List<ChunkDigestPayload.Entry> entries = new ArrayList<>();
        List<ChunkMapData> local = new ArrayList<>();
        for (int x = 0; x < 2200; x++) {
            entries.add(new ChunkDigestPayload.Entry(x, 0, 100));
            var data = new ChunkMapData(x, 0);
            data.setVersion(100);
            local.add(data);
        }
        var packets = ChunkDigestPayload.split("minecraft:overworld", entries, UUID.randomUUID());
        DigestAssembly assembly = new DigestAssembly();
        assertNull(assembly.add(packets.get(1), 100));
        assertNull(assembly.add(packets.get(1), 101));
        assertNull(assembly.add(packets.get(0), 102));
        var complete = assembly.add(packets.get(2), 103);
        assertEquals(2200, complete.size());
        assertTrue(ClientMapSync.computeDeltas(complete, local).isEmpty());
    }

    @Test
    void emptyDigestCompletesAndTimedOutFragmentsAreDiscarded() {
        var empty = ChunkDigestPayload.split("minecraft:overworld", List.of(), UUID.randomUUID()).getFirst();
        assertEquals(List.of(), new DigestAssembly().add(empty, 100));
        var assembly = new DigestAssembly();
        assertNull(assembly.add(new ChunkDigestPayload("minecraft:overworld", List.of(), 0, 2, empty.senderId()), 100));
        assertTrue(assembly.expired(60_101));
        assertNull(assembly.add(new ChunkDigestPayload("minecraft:overworld", List.of(), 1, 2, empty.senderId()), 60_101));
    }
}
