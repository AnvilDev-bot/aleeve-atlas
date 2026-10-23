package dev.aleeve.atlas.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.aleeve.atlas.api.territory.TerritoryGroup;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/// 领地注册中心测试（规格 §7.1 / §7.2 / §7.7，验收 AC-8）。
class TerritoryRegistryTest {
    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;

    @Test
    void upsertComputesComponentsAndAnchor() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Set<ChunkPos> chunks = new HashSet<>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        registry.upsertTerritory(territory("t1", chunks));

        TerritoryRegistry.CachedTerritory cached = registry.cached("t1");
        assertTrue(cached != null);
        assertEquals(1, cached.components().size());
        ChunkPos anchor = cached.components().get(0).anchor();
        assertTrue(chunks.contains(anchor), "锚点必须位于领地内部");
    }

    @Test
    void enclavesProduceSeparateComponents() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Set<ChunkPos> chunks = Set.of(
                new ChunkPos(0, 0), new ChunkPos(1, 0),
                new ChunkPos(20, 20), new ChunkPos(21, 20));
        registry.upsertTerritory(territory("t2", chunks));

        TerritoryRegistry.CachedTerritory cached = registry.cached("t2");
        assertEquals(2, cached.components().size(), "飞地必须拆为独立分量（AC-8）");
        for (TerritoryRegistry.Component component : cached.components()) {
            assertTrue(component.anchor() != null);
            assertTrue(component.chunks().contains(component.anchor()));
        }
    }

    @Test
    void patchChunksUpdatesOnlyTargetTerritory() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Set<ChunkPos> base = Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0));
        registry.upsertTerritory(territory("a", base));
        registry.upsertTerritory(territory("b", Set.of(new ChunkPos(100, 100))));

        registry.patchChunks(OVERWORLD, "a", Set.of(new ChunkPos(2, 0)), Set.of());

        TerritoryGroup patched = find(registry, "a");
        assertTrue(patched.chunks().contains(new ChunkPos(2, 0)));
        assertEquals(3, patched.chunks().size());
        // 另一领地不受影响。
        assertEquals(1, find(registry, "b").chunks().size());
    }

    @Test
    void removeTerritoryClearsCache() {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.upsertTerritory(territory("gone", Set.of(new ChunkPos(0, 0))));
        registry.removeTerritory("gone");

        assertTrue(registry.registeredTerritories().isEmpty());
        assertNull(registry.cached("gone"));
    }

    @Test
    void hoverHitsOrderedByZOrder() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Set<ChunkPos> shared = Set.of(new ChunkPos(0, 0));
        registry.upsertTerritory(withZOrder(territory("low", shared), 1));
        registry.upsertTerritory(withZOrder(territory("high", shared), 5));

        List<TerritoryGroup> hits = registry.hitAt(OVERWORLD, new ChunkPos(0, 0));
        assertEquals(2, hits.size());
        // 高 zOrder 在前（§7.7 第 3 条：tooltip 从高到低合并）。
        assertEquals("high", hits.get(0).territoryId());
        assertEquals("low", hits.get(1).territoryId());
    }

    @Test
    void anchorLambdaChangeRetriggersComputation() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Set<ChunkPos> chunks = new HashSet<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        registry.upsertTerritory(territory("t", chunks));
        ChunkPos before = registry.cached("t").components().get(0).anchor();

        registry.setAnchorLambda(0.3F);
        ChunkPos after = registry.cached("t").components().get(0).anchor();

        // 参数变化后缓存被重建，锚点仍必须在领地内。
        assertTrue(chunks.contains(before));
        assertTrue(chunks.contains(after));
    }

    private static TerritoryGroup find(TerritoryRegistry registry, String id) {
        for (TerritoryGroup group : registry.registeredTerritories()) {
            if (group.territoryId().equals(id)) {
                return group;
            }
        }
        throw new AssertionError("territory not found: " + id);
    }

    private static TerritoryGroup territory(String id, Set<ChunkPos> chunks) {
        return SimpleTerritory.of(id, OVERWORLD, chunks, Component.literal(id), 0x33AAFF);
    }

    private static TerritoryGroup withZOrder(TerritoryGroup delegate, int zOrder) {
        return new SimpleTerritory(delegate.territoryId(), delegate.dimension(),
                delegate.chunks(), delegate.displayName(), delegate.themeColorRgb(),
                delegate.fillOpacity(), delegate.borderStyle(), delegate.tooltip(), zOrder);
    }
}
