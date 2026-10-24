package dev.dubhe.atlas.client.territory;

import java.util.HashMap;

import dev.dubhe.atlas.api.territory.AtlasTerritory;
import dev.dubhe.atlas.api.territory.TerritoryGroup;
import dev.dubhe.atlas.client.map.MinimapLayout;
import dev.dubhe.atlas.config.AtlasClientConfig;
import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.config.ConfigBounds;
import dev.dubhe.atlas.territory.TerritoryRegistry;
import java.util.List;
import java.util.Map;
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
        // 各区块的最高 zOrder：重叠区块的边框只由最高优先级领地绘制（§7.7 第 2 条）。
        Map<ChunkPos, Integer> topZOrder = topZOrderByChunk(groups);
        for (TerritoryGroup group : groups) {
            renderGroup(graphics, group, dimension, topZOrder,
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
                    }, circular, layout);
        }
    }

    /// 单个领地的覆层绘制；`projector` 把区块映射为屏幕矩形（不可见返回 null）。
    ///
    /// 圆形小地图下，区块矩形需与圆形求交后逐行填充，
    /// 否则矩形角部会溢出圆形边界（规格 §2.2 圆形形态）。
    private static void renderGroup(GuiGraphics graphics, TerritoryGroup group,
            ResourceKey<Level> dimension, Map<ChunkPos, Integer> topZOrder,
            ChunkProjector projector, boolean circular, MinimapLayout layout) {
        if (!group.dimension().equals(dimension)) {
            return;
        }
        Set<ChunkPos> chunks = group.chunks();
        int fill = withOpacity(group.themeColorRgb(), group.fillOpacity());
        int border = group.borderStyle().colorArgb();
        int borderWidth = Math.max(1, Math.round(group.borderStyle().width()));
        boolean dashed = group.borderStyle().dashed();

        for (ChunkPos chunk : chunks) {
            int[] rect = projector.project(chunk);
            if (rect == null) {
                continue;
            }
            fillClipped(graphics, rect[0], rect[1], rect[2], rect[2], fill, circular, layout);
        }
        // 边框：仅在与非本领地区块交界处绘制（规格 §7.3）。
        for (ChunkPos chunk : chunks) {
            int[] rect = projector.project(chunk);
            if (rect == null) {
                continue;
            }
            // 被更高优先级领地覆盖的区块不绘制本层边框（§7.7 第 2 条）。
            Integer top = topZOrder.get(chunk);
            if (top != null && top > group.zOrder()) {
                continue;
            }
            int x = rect[0];
            int y = rect[1];
            int size = rect[2];
            if (!chunks.contains(new ChunkPos(chunk.x, chunk.z - 1))) {
                fillClipped(graphics, x, y, size, borderWidth, border, circular, layout);
            }
            if (!chunks.contains(new ChunkPos(chunk.x, chunk.z + 1))) {
                fillClipped(graphics, x, y + size - borderWidth, size, borderWidth, border,
                        circular, layout);
            }
            if (!chunks.contains(new ChunkPos(chunk.x - 1, chunk.z))) {
                fillClipped(graphics, x, y, borderWidth, size, border, circular, layout);
            }
            if (!chunks.contains(new ChunkPos(chunk.x + 1, chunk.z))) {
                fillClipped(graphics, x + size - borderWidth, y, borderWidth, size, border,
                        circular, layout);
            }
        }
    }

    /// 统计每个区块命中的最高 `zOrder`（规格 §7.7 第 2 条）。
    private static Map<ChunkPos, Integer> topZOrderByChunk(List<TerritoryGroup> groups) {
        Map<ChunkPos, Integer> top = new HashMap<>();
        for (TerritoryGroup group : groups) {
            for (ChunkPos chunk : group.chunks()) {
                top.merge(chunk, group.zOrder(), Math::max);
            }
        }
        return top;
    }

    /// 绘制一段领地边框；虚线样式下按固定节距间隔填充（规格 §7.6）。
    ///
    /// @param horizontal 该段是否沿 X 轴延伸
    private static void borderSegment(GuiGraphics graphics, int x, int y, int width, int height,
            int color, boolean circular, MinimapLayout layout, boolean dashed,
            boolean horizontal) {
        if (!dashed) {
            fillClipped(graphics, x, y, width, height, color, circular, layout);
            return;
        }
        int length = horizontal ? width : height;
        for (int offset : DashSegments.offsets(length)) {
            int dashLength = DashSegments.lengthAt(length, offset);
            if (horizontal) {
                fillClipped(graphics, x + offset, y, dashLength, height, color, circular, layout);
            } else {
                fillClipped(graphics, x, y + offset, width, dashLength, color, circular, layout);
            }
        }
    }

    /// 按圆形边界裁剪的矩形填充：圆形模式下逐行与圆求交，方形模式直接填充。
    ///
    /// 相邻且在圆形内裁剪区间一致的若干行合并为一次填充，减少绘制调用：
    /// 一块领地由数十个区块组成，逐行填充会产生上千次 `fill`，显著拖慢 HUD 渲染。
    private static void fillClipped(GuiGraphics graphics, int x, int y, int width, int height,
            int color, boolean circular, MinimapLayout layout) {
        if (!circular) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        double centerX = layout.x() + layout.size() / 2.0;
        double centerZ = layout.y() + layout.size() / 2.0;
        double radius = layout.size() / 2.0;
        int runStartRow = -1;
        int runLeft = 0;
        int runRight = 0;
        for (int row = y; row <= y + height; row++) {
            int left = 0;
            int right = 0;
            boolean visible = false;
            if (row < y + height) {
                double dy = row + 0.5 - centerZ;
                double spanSq = radius * radius - dy * dy;
                if (spanSq > 0.0) {
                    double span = Math.sqrt(spanSq);
                    left = Math.max(x, (int) Math.floor(centerX - span));
                    right = Math.min(x + width, (int) Math.ceil(centerX + span));
                    visible = right > left;
                }
            }
            // 与上一行裁剪区间一致则继续累积，否则冲刷上一段。
            if (visible && runStartRow >= 0 && left == runLeft && right == runRight) {
                continue;
            }
            if (runStartRow >= 0) {
                graphics.fill(runLeft, runStartRow, runRight, row, color);
                runStartRow = -1;
            }
            if (visible) {
                runStartRow = row;
                runLeft = left;
                runRight = right;
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
