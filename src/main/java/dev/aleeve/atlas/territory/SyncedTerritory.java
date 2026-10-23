package dev.aleeve.atlas.territory;

import dev.aleeve.atlas.api.territory.BorderStyle;
import dev.aleeve.atlas.api.territory.TerritoryGroup;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/// 可经网络传输的领地对象（规格 §7.1 服务端下发的数据形态）。
///
/// 与 {@link SimpleTerritory} 的区别：只包含可序列化字段（标识、维度、区块集合、
/// 名称、颜色、透明度、层级），边框样式与 tooltip 在客户端重建。
public record SyncedTerritory(String territoryId, ResourceKey<Level> dimension,
        Set<ChunkPos> chunks, Component displayName, int themeColorRgb, float fillOpacity,
        int zOrder) implements TerritoryGroup {

    /// 按维度标识构造，供负载解码使用。
    public SyncedTerritory(String territoryId, Identifier dimensionId, Set<ChunkPos> chunks,
            Component displayName, int themeColorRgb, float fillOpacity, int zOrder) {
        this(territoryId, ResourceKey.create(Registries.DIMENSION, dimensionId), chunks,
                displayName, themeColorRgb, fillOpacity, zOrder);
    }

    /// 替换区块集合并保留其余字段（分片发送用）。
    public SyncedTerritory withChunks(Set<ChunkPos> newChunks) {
        return new SyncedTerritory(territoryId, dimension, newChunks, displayName, themeColorRgb,
                fillOpacity, zOrder);
    }

    /// 合并另一分片带来的区块，用于客户端重组。
    public SyncedTerritory mergeChunks(Set<ChunkPos> additional) {
        Set<ChunkPos> merged = new HashSet<>(chunks);
        merged.addAll(additional);
        return withChunks(merged);
    }

    @Override
    public BorderStyle borderStyle() {
        return BorderStyle.ofTheme(themeColorRgb);
    }

    @Override
    public List<Component> tooltip() {
        return List.of();
    }
}
