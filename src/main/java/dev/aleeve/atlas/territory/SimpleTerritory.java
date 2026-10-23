package dev.aleeve.atlas.territory;

import dev.aleeve.atlas.api.territory.BorderStyle;
import dev.aleeve.atlas.api.territory.TerritoryGroup;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/// 领地对象的最小实现，供 API 调用方与演示使用（规格 §7.6）。
///
/// 外部模组可以直接实现 {@link TerritoryGroup}，也可以复用本记录以减少样板代码。
public record SimpleTerritory(
        String territoryId,
        ResourceKey<Level> dimension,
        Set<ChunkPos> chunks,
        Component displayName,
        int themeColorRgb,
        float fillOpacity,
        BorderStyle borderStyle,
        List<Component> tooltip,
        int zOrder) implements TerritoryGroup {

    /// 便捷构造：默认 25% 填充、主题色边框、无 tooltip、zOrder 0。
    public static SimpleTerritory of(String id, ResourceKey<Level> dimension,
            Set<ChunkPos> chunks, Component name, int themeColorRgb) {
        return new SimpleTerritory(id, dimension, Set.copyOf(chunks), name, themeColorRgb,
                0.25F, BorderStyle.ofTheme(themeColorRgb), List.of(), 0);
    }
}
