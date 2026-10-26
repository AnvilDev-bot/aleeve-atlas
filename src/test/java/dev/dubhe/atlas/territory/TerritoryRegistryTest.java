package dev.dubhe.atlas.territory;

import dev.dubhe.atlas.api.territory.TerritoryGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 领地注册中心测试。
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
        Assertions.assertTrue(cached != null);
        Assertions.assertEquals(1, cached.components().size());
        ChunkPos anchor = cached.components().get(0).anchor();
        Assertions.assertTrue(chunks.contains(anchor), "锚点必须位于领地内部");
    }

    @Test
    void enclavesProduceSeparateComponents() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Set<ChunkPos> chunks = Set.of(
                new ChunkPos(0, 0), new ChunkPos(1, 0),
                new ChunkPos(20, 20), new ChunkPos(21, 20));
        registry.upsertTerritory(territory("t2", chunks));

        TerritoryRegistry.CachedTerritory cached = registry.cached("t2");
        Assertions.assertEquals(2, cached.components().size(), "飞地必须拆为独立分量（AC-8）");
        for (TerritoryRegistry.TerritoryComponent component : cached.components()) {
            Assertions.assertTrue(component.anchor() != null);
            Assertions.assertTrue(component.chunks().contains(component.anchor()));
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
        Assertions.assertTrue(patched.chunks().contains(new ChunkPos(2, 0)));
        Assertions.assertEquals(3, patched.chunks().size());
        // 另一领地不受影响。
        Assertions.assertEquals(1, find(registry, "b").chunks().size());
    }

    @Test
    void patchChunksKeepsUnaffectedComponentCache() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Set<ChunkPos> chunks = Set.of(
                new ChunkPos(0, 0), new ChunkPos(1, 0),
                new ChunkPos(100, 100), new ChunkPos(101, 100));
        registry.upsertTerritory(territory("local", chunks));
        TerritoryRegistry.TerritoryComponent unaffected = registry.cached("local").components()
                .stream().filter(component -> component.chunks().contains(new ChunkPos(100, 100)))
                .findFirst().orElseThrow();

        registry.patchChunks(OVERWORLD, "local", Set.of(new ChunkPos(2, 0)), Set.of());

        TerritoryRegistry.TerritoryComponent preserved = registry.cached("local").components()
                .stream().filter(component -> component.chunks().contains(new ChunkPos(100, 100)))
                .findFirst().orElseThrow();
        Assertions.assertSame(unaffected, preserved, "未受影响的飞地必须保留原缓存对象");
    }

    @Test
    void removeTerritoryClearsCache() {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.upsertTerritory(territory("gone", Set.of(new ChunkPos(0, 0))));
        registry.removeTerritory("gone");

        Assertions.assertTrue(registry.registeredTerritories().isEmpty());
        Assertions.assertNull(registry.cached("gone"));
    }

    @Test
    void hoverHitsOrderedByZOrder() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Set<ChunkPos> shared = Set.of(new ChunkPos(0, 0));
        registry.upsertTerritory(withZOrder(territory("low", shared), 1));
        registry.upsertTerritory(withZOrder(territory("high", shared), 5));

        List<TerritoryGroup> hits = registry.hitAt(OVERWORLD, new ChunkPos(0, 0));
        Assertions.assertEquals(2, hits.size());
        // 高 zOrder 在前。
        Assertions.assertEquals("high", hits.get(0).territoryId());
        Assertions.assertEquals("low", hits.get(1).territoryId());
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
        Assertions.assertTrue(chunks.contains(before));
        Assertions.assertTrue(chunks.contains(after));
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

    private static TerritoryGroup withZOrder(TerritoryGroup delegate, int order) {
        return new SimpleTerritory(delegate.territoryId(), delegate.dimension(),
                delegate.chunks(), delegate.displayName(), delegate.themeColorRgb(),
                delegate.fillOpacity(), delegate.borderStyle(), delegate.tooltip(), order);
    }
}
