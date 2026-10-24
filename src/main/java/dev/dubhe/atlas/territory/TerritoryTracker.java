package dev.dubhe.atlas.territory;

import java.util.Objects;

import dev.dubhe.atlas.api.territory.TerritoryGroup;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/// 领地进出状态跟踪。
///
/// 记录玩家当前所在的领地，并在跨越领地边界时给出「已进入/已离开」提示；
/// 多层重叠时仅播报 `zOrder` 最高的命中领地。
public final class TerritoryTracker {
    /// 上一次所在的领地 ID（无领地为 null）。
    private String currentTerritoryId;

    /// 计算玩家位置应播报的领地；返回 null 表示已离开全部领地。
    ///
    /// 纯函数式：不修改状态，便于测试与重复调用。
    public static TerritoryGroup resolveAt(TerritoryRegistry registry,
            ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        List<TerritoryGroup> hits = registry.hitAt(dimension, new ChunkPos(chunkX, chunkZ));
        // hitAt 已按 zOrder 降序排列，取首个即为最高优先级。
        return hits.isEmpty() ? null : hits.get(0);
    }

    /// 根据新位置更新状态；返回需要播报的变更，无变更时返回 {@code null}。
    ///
    /// 返回的 {@link Change} 中 `entered` 为 true 表示进入（`territory` 非空），
    /// 为 false 表示离开（`territory` 为离开前的领地）。
    public Change update(TerritoryRegistry registry, ResourceKey<Level> dimension, int chunkX,
            int chunkZ) {
        TerritoryGroup next = resolveAt(registry, dimension, chunkX, chunkZ);
        String nextId = next == null ? null : next.territoryId();
        if (Objects.equals(nextId, currentTerritoryId)) {
            return null;
        }
        String previousId = currentTerritoryId;
        TerritoryGroup previous = previousId == null ? null : findById(registry, previousId);
        currentTerritoryId = nextId;
        if (next != null) {
            return new Change(true, next, previous);
        }
        return new Change(false, previous, previous);
    }

    /// 清空状态（切换维度或断开连接时调用，避免跨维度误报）。
    public void reset() {
        currentTerritoryId = null;
    }

    /// 当前所在领地 ID（无则为 null）。
    public String currentTerritoryId() {
        return currentTerritoryId;
    }

    private static TerritoryGroup findById(TerritoryRegistry registry, String id) {
        for (TerritoryGroup group : registry.registeredTerritories()) {
            if (group.territoryId().equals(id)) {
                return group;
            }
        }
        return null;
    }

    /// 一次进出变更；`entered` 表示进入（true）或离开（false）。
    public record Change(boolean entered, TerritoryGroup territory, TerritoryGroup previous) {
    }
}
