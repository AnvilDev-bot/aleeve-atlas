package dev.dubhe.atlas.territory;

import dev.dubhe.atlas.api.territory.BorderStyle;

import dev.dubhe.atlas.api.territory.TerritoryGroup;
import dev.dubhe.atlas.api.territory.TerritoryOverlayApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/// 领地覆层注册中心（规格 §7.1 / §7.6 的 API 实现）。
///
/// 维护领地注册表与每个连通分量的重算缓存；局部区块变更只重算受影响的领地，
/// 不触发全图重算（规格 §7.1 脏标记更新）。
public final class TerritoryRegistry implements TerritoryOverlayApi {
    /// 单个领地某维度的分量缓存。
    private final Map<String, CachedTerritory> cache = new HashMap<>();
    private final Map<String, TerritoryGroup> territories = new LinkedHashMap<>();
    /// 加权极点的重心牵引权重 λ（规格 §7.4，由配置注入）。
    private float anchorLambda = AnchorSelector.DEFAULT_LAMBDA;

    @Override
    public void upsertTerritory(TerritoryGroup territory) {
        territories.put(territory.territoryId(), territory);
        cache.remove(territory.territoryId());
        recompute(territory);
    }

    @Override
    public void patchChunks(ResourceKey<Level> dimension, String territoryId,
            Set<ChunkPos> added, Set<ChunkPos> removed) {
        TerritoryGroup existing = territories.get(territoryId);
        if (existing == null) {
            return;
        }
        Set<ChunkPos> chunks = new HashSet<>(existing.chunks());
        chunks.removeAll(removed);
        chunks.addAll(added);
        TerritoryGroup patched = new PatchedTerritory(existing, chunks);
        territories.put(territoryId, patched);
        // 局部更新：仅对受影响领地的分量与锚点重算。
        cache.remove(territoryId);
        recompute(patched);
    }

    @Override
    public void removeTerritory(String territoryId) {
        territories.remove(territoryId);
        cache.remove(territoryId);
    }

    @Override
    public Set<TerritoryGroup> registeredTerritories() {
        return Set.copyOf(territories.values());
    }

    /// 该维度下按 zOrder 升序排列的已注册领地（规格 §7.7 叠加顺序）。
    public List<TerritoryGroup> orderedForDimension(ResourceKey<Level> dimension) {
        List<TerritoryGroup> result = new ArrayList<>();
        for (TerritoryGroup territory : territories.values()) {
            if (territory.dimension().equals(dimension)) {
                result.add(territory);
            }
        }
        result.sort(Comparator.comparingInt(TerritoryGroup::zOrder));
        return result;
    }

    /// 查询某区块命中的全部领地，按 zOrder 从高到低（规格 §7.7 第 3 条）。
    public List<TerritoryGroup> hitAt(ResourceKey<Level> dimension, ChunkPos pos) {
        List<TerritoryGroup> hits = new ArrayList<>();
        for (TerritoryGroup territory : territories.values()) {
            if (territory.dimension().equals(dimension) && territory.chunks().contains(pos)) {
                hits.add(territory);
            }
        }
        hits.sort(Comparator.comparingInt(TerritoryGroup::zOrder).reversed());
        return hits;
    }

    /// 某领地的分量缓存（含锚点）；未注册返回 {@code null}。
    public CachedTerritory cached(String territoryId) {
        return cache.get(territoryId);
    }

    /// 设置锚点算法的重心牵引权重（规格 §7.4，取值建议 0.3 ~ 0.5）。
    public void setAnchorLambda(float lambda) {
        this.anchorLambda = lambda;
        cache.clear();
        for (TerritoryGroup territory : territories.values()) {
            recompute(territory);
        }
    }

    public void clear() {
        territories.clear();
        cache.clear();
    }

    private void recompute(TerritoryGroup territory) {
        List<Set<ChunkPos>> components = TerritoryComponents.split(territory.chunks());
        List<Component> computed = new ArrayList<>(components.size());
        float lambda = anchorLambda;
        for (Set<ChunkPos> component : components) {
            ChunkPos anchor = AnchorSelector.select(component, lambda);
            computed.add(new Component(Set.copyOf(component), anchor));
        }
        cache.put(territory.territoryId(), new CachedTerritory(territory, computed));
    }

    /// 单个连通分量：区块集合与其名称锚点。
    public record Component(Set<ChunkPos> chunks, ChunkPos anchor) {
    }

    /// 领地及其连通分量缓存。
    public record CachedTerritory(TerritoryGroup territory, List<Component> components) {
    }

    /// 局部区块更新后的不可变包装。
    private record PatchedTerritory(TerritoryGroup delegate, Set<ChunkPos> chunks)
            implements TerritoryGroup {
        @Override
        public String territoryId() {
            return delegate.territoryId();
        }

        @Override
        public ResourceKey<Level> dimension() {
            return delegate.dimension();
        }

        @Override
        public Set<ChunkPos> chunks() {
            return chunks;
        }

        @Override
        public net.minecraft.network.chat.Component displayName() {
            return delegate.displayName();
        }

        @Override
        public int themeColorRgb() {
            return delegate.themeColorRgb();
        }

        @Override
        public float fillOpacity() {
            return delegate.fillOpacity();
        }

        @Override
        public BorderStyle borderStyle() {
            return delegate.borderStyle();
        }

        @Override
        public List<net.minecraft.network.chat.Component> tooltip() {
            return delegate.tooltip();
        }

        @Override
        public int zOrder() {
            return delegate.zOrder();
        }
    }
}
