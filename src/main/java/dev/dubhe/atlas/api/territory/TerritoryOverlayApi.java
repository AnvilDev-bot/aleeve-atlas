package dev.dubhe.atlas.api.territory;

import java.util.Set;
import net.minecraft.world.level.ChunkPos;

/// 领地覆层 API 入口（规格 §7.6）。
///
/// 所有方法必须在客户端主线程调用；高频批量更新应合并为一次 `upsertTerritory`，
/// 避免逐区块触发重算。注册方模组卸载或数据失效时必须调用 `removeTerritory`。
public interface TerritoryOverlayApi {
    /// 注册或全量更新一个领地；`territoryId` 相同视为更新。
    void upsertTerritory(TerritoryGroup territory);

    /// 局部增量更新：仅新增/移除指定区块，触发相关连通分量的局部重算（规格 §7.1）。
    void patchChunks(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            String territoryId, Set<ChunkPos> added, Set<ChunkPos> removed);

    /// 移除一个领地的全部覆层。
    void removeTerritory(String territoryId);

    /// 获取当前已注册的领地（只读快照，供渲染与调试使用）。
    Set<TerritoryGroup> registeredTerritories();
}
