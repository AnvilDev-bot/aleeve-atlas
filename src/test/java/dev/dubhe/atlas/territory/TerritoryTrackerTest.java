package dev.dubhe.atlas.territory;

import dev.dubhe.atlas.api.territory.BorderStyle;
import dev.dubhe.atlas.api.territory.TerritoryGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/// 领地进出跟踪测试。
class TerritoryTrackerTest {
    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;

    @Test
    void enteringTerritoryReportsHighestZOrder() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Set<ChunkPos> shared = Set.of(new ChunkPos(0, 0));
        registry.upsertTerritory(withZOrder("low", shared, 1));
        registry.upsertTerritory(withZOrder("high", shared, 9));

        TerritoryGroup resolved = TerritoryTracker.resolveAt(registry, OVERWORLD, 0, 0);
        Assertions.assertEquals("high", resolved.territoryId(), "重叠时仅播报最高 zOrder");
    }

    @Test
    void stateTransitionsProduceEnterThenLeave() {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.upsertTerritory(withZOrder("t", Set.of(new ChunkPos(0, 0)), 0));
        TerritoryTracker tracker = new TerritoryTracker();

        // 首次进入。
        TerritoryTracker.Change enter = tracker.update(registry, OVERWORLD, 0, 0);
        Assertions.assertTrue(enter != null && enter.entered());
        Assertions.assertEquals("t", enter.territory().territoryId());
        Assertions.assertEquals("t", tracker.currentTerritoryId());

        // 原地不动：无变更。
        Assertions.assertNull(tracker.update(registry, OVERWORLD, 0, 0));

        // 走出领地：产生离开事件并清空状态。
        TerritoryTracker.Change leave = tracker.update(registry, OVERWORLD, 50, 50);
        Assertions.assertTrue(leave != null);
        Assertions.assertFalse(leave.entered());
        Assertions.assertEquals("t", leave.previous().territoryId());
        Assertions.assertNull(tracker.currentTerritoryId());
    }

    @Test
    void movingBetweenTerritoriesReportsEnterOnly() {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.upsertTerritory(withZOrder("a", Set.of(new ChunkPos(0, 0)), 0));
        registry.upsertTerritory(withZOrder("b", Set.of(new ChunkPos(1, 0)), 0));
        TerritoryTracker tracker = new TerritoryTracker();

        tracker.update(registry, OVERWORLD, 0, 0);
        TerritoryTracker.Change change = tracker.update(registry, OVERWORLD, 1, 0);
        Assertions.assertTrue(change != null && change.entered());
        Assertions.assertEquals("b", change.territory().territoryId());
    }

    @Test
    void resetClearsState() {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.upsertTerritory(withZOrder("t", Set.of(new ChunkPos(0, 0)), 0));
        TerritoryTracker tracker = new TerritoryTracker();

        tracker.update(registry, OVERWORLD, 0, 0);
        tracker.reset();
        Assertions.assertNull(tracker.currentTerritoryId());
        // 重置后再次进入应重新触发进入事件（跨维度场景）。
        TerritoryTracker.Change again = tracker.update(registry, OVERWORLD, 0, 0);
        Assertions.assertTrue(again != null && again.entered());
    }

    @Test
    void emptyRegistryNeverReports() {
        TerritoryRegistry registry = new TerritoryRegistry();
        TerritoryTracker tracker = new TerritoryTracker();
        Assertions.assertNull(tracker.update(registry, OVERWORLD, 0, 0));
        Assertions.assertNull(TerritoryTracker.resolveAt(registry, OVERWORLD, 0, 0));
    }

    private static TerritoryGroup withZOrder(String id, Set<ChunkPos> chunks, int order) {
        return new SimpleTerritory(id, OVERWORLD, chunks, Component.literal(id), 0x33AAFF,
                0.25F, BorderStyle.ofTheme(0x33AAFF),
                List.of(), order);
    }
}
