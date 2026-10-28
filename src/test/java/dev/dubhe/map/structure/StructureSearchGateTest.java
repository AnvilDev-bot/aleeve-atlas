package dev.dubhe.map.structure;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StructureSearchGateTest {
    @Test
    void busyRejectsOtherPlayersWithoutChargingTheirCooldown() {
        var gate = new StructureSearchGate();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertNull(gate.begin(first, 100));
        assertEquals(StructurePayloads.SearchStatus.BUSY, gate.begin(second, 101));
        gate.finish();
        assertNull(gate.begin(second, 102));
        gate.finish();
        assertEquals(StructurePayloads.SearchStatus.COOLDOWN, gate.begin(first, 9999));
        assertNull(gate.begin(first, 10100));
        gate.finish();
        gate.clear(first);
        assertNull(gate.begin(first, 10101));
        gate.reset();
        assertNull(gate.begin(second, 10102));
    }
}
