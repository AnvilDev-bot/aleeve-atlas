package dev.dubhe.map.sync;

import dev.dubhe.map.network.PlayerPositionPayload;
import dev.dubhe.map.network.TeamSightingsPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamSightingsCacheTest {
    private final UUID observer = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    private TeamSightingsPayload sighting(UUID source, String dimension) {
        return new TeamSightingsPayload(source, List.of(new PlayerPositionPayload(target, "Enemy", dimension,
                100_000, 64, -100_000, false)));
    }

    @Test
    void distantTargetStaysVisibleUntilObserverReplacesSnapshot() {
        var cache = new TeamSightingsCache();
        cache.update(sighting(observer, "minecraft:overworld"), "minecraft:overworld", 100);
        assertTrue(cache.visible(target, observer::equals, 200));
        cache.update(new TeamSightingsPayload(observer, List.of()), "minecraft:overworld", 300);
        assertFalse(cache.visible(target, observer::equals, 300));
    }

    @Test
    void anotherObserverKeepsTargetVisible() {
        var cache = new TeamSightingsCache();
        UUID second = UUID.randomUUID();
        cache.update(sighting(observer, "minecraft:overworld"), "minecraft:overworld", 100);
        cache.update(sighting(second, "minecraft:overworld"), "minecraft:overworld", 100);
        cache.update(new TeamSightingsPayload(observer, List.of()), "minecraft:overworld", 200);
        assertTrue(cache.visible(target, id -> true, 200));
        assertFalse(cache.visible(target, id -> true, 2101));
    }

    @Test
    void leavingTeamOrChangingDimensionInvalidatesSightings() {
        var cache = new TeamSightingsCache();
        cache.update(sighting(observer, "minecraft:overworld"), "minecraft:overworld", 100);
        assertFalse(cache.visible(target, id -> false, 200));
        cache.update(sighting(observer, "minecraft:the_nether"), "minecraft:overworld", 300);
        assertFalse(cache.visible(target, id -> true, 300));
        cache.update(sighting(observer, "minecraft:overworld"), "minecraft:overworld", 400);
        cache.clear();
        assertFalse(cache.visible(target, id -> true, 400));
    }
}
