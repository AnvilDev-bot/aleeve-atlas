package dev.dubhe.map.sync;

import dev.dubhe.map.network.PlayerPositionPayload;
import dev.dubhe.map.network.TeamSightingsPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/// 每位观察者独立保存快照；一个队友失去视野不影响另一个队友仍在看见的目标。
final class TeamSightingsCache {
    private static final long EXPIRY_MILLIS = 2_000L;
    private record Observation(Map<UUID, PlayerPositionPayload> targets, long receivedAt) { }
    private final Map<UUID, Observation> observers = new HashMap<>();

    void update(TeamSightingsPayload payload, String dimension, long now) {
        Map<UUID, PlayerPositionPayload> targets = new HashMap<>();
        for (var sighting : payload.sightings()) {
            if (dimension.equals(sighting.dimensionId())) {
                targets.put(sighting.playerId(), sighting);
            }
        }
        this.observers.put(payload.observerId(), new Observation(targets, now));
    }

    boolean visible(UUID target, Predicate<UUID> teammate, long now) {
        this.observers.entrySet().removeIf(entry -> now - entry.getValue().receivedAt() > EXPIRY_MILLIS
                || !teammate.test(entry.getKey()));
        return this.observers.values().stream().anyMatch(value -> value.targets().containsKey(target));
    }

    void clear() {
        this.observers.clear();
    }
}
