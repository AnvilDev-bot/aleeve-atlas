package dev.aleeve.atlas.client.territory;

import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.api.territory.AtlasTerritory;
import dev.aleeve.atlas.api.territory.TerritoryGroup;
import dev.aleeve.atlas.client.map.MinimapLayout;
import dev.aleeve.atlas.config.AtlasClientConfig;
import dev.aleeve.atlas.config.AtlasClientConfigs;
import dev.aleeve.atlas.config.ConfigBounds;
import dev.aleeve.atlas.territory.TerritoryRegistry;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/// 领地覆层渲染（规格 §7.3 / §7.5 / §7.7）。
///
/// 按连通分量逐区块绘制半透明填充，仅在领地/非领地交界处描边（内部交界不画线），
/// 并按 `zOrder` 决定叠加顺序与边框归属。
public final class TerritoryRenderer {
    private static final int LABEL_COLOR = 0xFFFFFFFF;

    private TerritoryRenderer() {
    }

    /// 在小地图上绘制领地覆层（默认不画标签，可由配置开启）。
    public static void renderOnMinimap(GuiGraphics graphics, MinimapLayout layout,
            boolean circular) {
        AtlasClientConfig config = AtlasClientConfigs.CONFIG;
        if (!config.territory.showOnMinimap) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        List<TerritoryGroup> groups = AtlasTerritory.clientRegistry().orderedForDimension(dimension);
        if (groups.isEmpty()) {
            return;
        }
        double blocksPerPixel = (double) MinimapLayout.COVERED_BLOCKS / layout.size();
        for (TerritoryGroup group : groups) {
            renderGroup(graphics, group, dimension,
                    chunk -> {
                        double worldX = (chunk.x << 4) + 8.0;
                        double worldZ = (chunk.z << 4) + 8.0;
                        double screenX = layout.worldToScreenX(worldX);
                        double screenZ = layout.worldToScreenZ(worldZ);
                        if (!layout.contains(screenX, screenZ, circular)) {
                            return null;
                        }
                        int size = Math.max(1, (int) Math.round(16.0 / blocksPerPixel));
                        return new int[] { (int) Math.round(screenX) - size / 2,
                                (int) Math.round(screenZ) - size / 2, size };
                    });
        }
    }

    /// 单个领地的覆层绘制；`projector` 把区块映射为屏幕矩形（不可见返回 null）。
    private static void renderGroup(GuiGraphics graphics, TerritoryGroup group,
            ResourceKey<Level> dimension, ChunkProjector projector) {
        if (!group.dimension().equals(dimension)) {
            return;
        }
        Set<ChunkPos> chunks = group.chunks();
        int fill = withOpacity(group.themeColorRgb(), group.fillOpacity());
        int border = group.borderStyle().colorArgb();
        int borderWidth = Math.max(1, Math.round(group.borderStyle().width()));

        for (ChunkPos chunk : chunks) {
            int[] rect = projector.project(chunk);
            if (rect == null) {
                continue;
            }
            graphics.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[2], fill);
        }
        // 边框：仅在与非本领地区块交界处绘制（规格 §7.3）。
        for (ChunkPos chunk : chunks) {
            int[] rect = projector.project(chunk);
            if (rect == null) {
                continue;
            }
            int x = rect[0];
            int y = rect[1];
            int size = rect[2];
            if (!chunks.contains(new ChunkPos(chunk.x, chunk.z - 1))) {
                graphics.fill(x, y, x + size, y + borderWidth, border);
            }
            if (!chunks.contains(new ChunkPos(chunk.x, chunk.z + 1))) {
                graphics.fill(x, y + size - borderWidth, x + size, y + size, border);
            }
            if (!chunks.contains(new ChunkPos(chunk.x - 1, chunk.z))) {
                graphics.fill(x, y, x + borderWidth, y + size, border);
            }
            if (!chunks.contains(new ChunkPos(chunk.x + 1, chunk.z))) {
                graphics.fill(x + size - borderWidth, y, x + size, y + size, border);
            }
        }
    }

    /// 各连通分量的名称标签（规格 §7.5：缩放过度时自动隐藏）。
    public static void renderLabelsOnMinimap(GuiGraphics graphics, MinimapLayout layout,
            boolean circular) {
        AtlasClientConfig config = AtlasClientConfigs.CONFIG;
        if (!config.territory.showOnMinimap || !config.territory.showLabelOnMinimap) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        TerritoryRegistry registry = AtlasTerritory.clientRegistry();
        for (TerritoryGroup group : registry.orderedForDimension(minecraft.level.dimension())) {
            TerritoryRegistry.CachedTerritory cached = registry.cached(group.territoryId());
            if (cached == null) {
                continue;
            }
            for (TerritoryRegistry.Component component : cached.components()) {
                if (component.anchor() == null) {
                    continue;
                }
                double worldX = (component.anchor().x << 4) + 8.0;
                double worldZ = (component.anchor().z << 4) + 8.0;
                double screenX = layout.worldToScreenX(worldX);
                double screenZ = layout.worldToScreenZ(worldZ);
                if (!layout.contains(screenX, screenZ, circular)) {
                    continue;
                }
                graphics.drawString(minecraft.font, group.displayName(),
                        (int) Math.round(screenX), (int) Math.round(screenZ), LABEL_COLOR, true);
            }
        }
    }

    /// 主题色 + 填充透明度。
    private static int withOpacity(int rgb, float opacity) {
        int alpha = Math.round(ConfigBounds.clamp(opacity, 0.0F, 1.0F) * 255.0F);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /// 区块 → 屏幕矩形的投影函数。
    @FunctionalInterface
    private interface ChunkProjector {
        int[] project(ChunkPos chunk);
    }
}
