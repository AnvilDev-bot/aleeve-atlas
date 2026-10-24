package dev.dubhe.atlas.api.territory;

import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/// 单个领地对象（规格 §7.1 / §7.6）。
///
/// 外部领地模组（FTB Chunks、Open Parties and Claims 或自研领地模组）实现本接口后，
/// 经 {@link TerritoryOverlayApi#upsertTerritory} 注入本模组，即可在世界地图与小地图上渲染覆层。
public interface TerritoryGroup {
    /// 唯一标识；相同 ID 的再次注册视为全量更新。
    String territoryId();

    /// 所属维度。
    ResourceKey<Level> dimension();

    /// 该领地声明的全部区块坐标（可为多个不相连的飞地）。
    Set<ChunkPos> chunks();

    /// 展示名称（支持富文本）。
    Component displayName();

    /// 主题颜色（RGB，不含 alpha）。
    int themeColorRgb();

    /// 填充透明度（`0.0 ~ 1.0`），确保不遮挡底层地形。
    float fillOpacity();

    /// 边框样式。
    BorderStyle borderStyle();

    /// 鼠标悬停详情。
    List<Component> tooltip();

    /// 重叠时的渲染优先级，数值越大越靠上（规格 §7.7）。
    default int zOrder() {
        return 0;
    }
}
