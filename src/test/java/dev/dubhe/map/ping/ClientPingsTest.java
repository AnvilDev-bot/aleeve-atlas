package dev.dubhe.map.ping;

import dev.dubhe.map.api.ping.MapPingCreatedEvent;
import dev.dubhe.map.api.ping.MinimapPingAPI;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPingsTest {
    @AfterEach
    void clearPings() {
        ClientPings.clear();
    }

    @Test
    void entityRegistrationRetainsInitialPositionAndExposesImmutableSnapshot() {
        UUID target = UUID.randomUUID();
        Vec3 position = new Vec3(24, 70, -32);
        MapPingData ping = MinimapPingAPI.registerEntityPing(target, position, "minecraft:overworld",
                MapPingData.PingIcon.GENERAL, UUID.randomUUID(), "foo", 0xFFFFFFFF);
        List<MapPingData> snapshot = MinimapPingAPI.activePings();
        assertEquals(position, snapshot.getFirst().position());
        assertEquals(target, snapshot.getFirst().targetEntityId());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        MinimapPingAPI.removePing(ping.pingId());
        assertTrue(MinimapPingAPI.activePings().isEmpty());
        assertEquals(1, snapshot.size());
    }

    @Test
    void echoedPingUpdatesDataWithoutRepeatingCreatedEvent() {
        List<MapPingData> events = new ArrayList<>();
        Consumer<MapPingCreatedEvent> listener = event -> events.add(event.ping());
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            MapPingData ping = entityPing("minecraft:overworld");
            ClientPings.upsert(ping);
            MapPingData moved = ping.withPosition(new Vec3(40, 80, 60));
            ClientPings.upsert(moved);
            assertEquals(List.of(ping), events);
            assertEquals(List.of(moved), ClientPings.active());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }

    @Test
    void entityMovementPreservesLifetimeAndMissingEntityRetainsLastPosition() {
        MapPingData ping = entityPing("minecraft:overworld");
        ClientPings.upsert(ping);
        Vec3 moved = new Vec3(80, 70, -60);
        ClientPings.tick(1100, "minecraft:overworld", target -> moved);
        assertEquals(ping.withPosition(moved), ClientPings.active().getFirst());
        ClientPings.tick(1200, "minecraft:overworld", target -> null);
        assertEquals(moved, ClientPings.active().getFirst().position());
        ClientPings.tick(2000, "minecraft:overworld", target -> moved);
        assertTrue(ClientPings.active().isEmpty());
    }

    @Test
    void entityInAnotherDimensionDoesNotUpdatePingPosition() {
        MapPingData ping = entityPing("minecraft:the_nether");
        ClientPings.upsert(ping);
        ClientPings.tick(1100, "minecraft:overworld", target -> {
            throw new AssertionError("不应在其他维度查询实体");
        });
        assertEquals(List.of(ping), ClientPings.active());
    }

    private static MapPingData entityPing(String dimension) {
        return new MapPingData(UUID.randomUUID(), UUID.randomUUID(), "foo", 0xFFFFFFFF,
                dimension, new Vec3(24, 70, -32), UUID.randomUUID(),
                MapPingData.PingIcon.GENERAL, 1000, 1000);
    }
}
