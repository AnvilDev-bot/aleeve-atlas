package dev.dubhe.map.client.map;

import dev.dubhe.map.client.territory.CircleClip;
import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.map.ChunkMapData;
import net.minecraft.client.gui.GuiGraphics;

/// 区块边界和史莱姆区块覆盖层。
public final class ChunkOverlayRenderer {
    private static final int GRID_COLOR = 0x50000000;
    private static final int SLIME_COLOR = 0x5044DD44;

    private ChunkOverlayRenderer() {
    }

    public static void renderOnWorldMap(GuiGraphics graphics, AtlasClientConfig config,
            double centerX, double centerZ, float pixelsPerChunk, int width, int height) {
        if (!config.worldMap.showChunkGrid && !config.worldMap.showSlimeChunks) {
            return;
        }
        Long seed = config.worldMap.showSlimeChunks ? SlimeChunkTracker.currentSeed() : null;
        int minX = chunkAt(centerX - width / 2.0 / pixelsPerChunk);
        int maxX = chunkAt(centerX + width / 2.0 / pixelsPerChunk);
        int minZ = chunkAt(centerZ - height / 2.0 / pixelsPerChunk);
        int maxZ = chunkAt(centerZ + height / 2.0 / pixelsPerChunk);
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                int left = (int) Math.round(width / 2.0
                        + (chunkX * 16.0 - centerX) * pixelsPerChunk);
                int top = (int) Math.round(height / 2.0
                        + (chunkZ * 16.0 - centerZ) * pixelsPerChunk);
                int right = (int) Math.round(width / 2.0
                        + ((chunkX + 1) * 16.0 - centerX) * pixelsPerChunk);
                int bottom = (int) Math.round(height / 2.0
                        + ((chunkZ + 1) * 16.0 - centerZ) * pixelsPerChunk);
                drawCell(graphics, config.worldMap.showChunkGrid,
                        seed != null && SlimeChunkTracker.isSlimeChunk(seed, chunkX, chunkZ),
                        left, top, Math.max(1, right - left), Math.max(1, bottom - top),
                        false, 0.0, 0.0, 0.0);
            }
        }
    }

    public static void renderOnMinimap(GuiGraphics graphics, AtlasClientConfig config,
            MinimapLayout layout, boolean circular) {
        if (!config.minimap.showChunkGrid && !config.minimap.showSlimeChunks) {
            return;
        }
        Long seed = config.minimap.showSlimeChunks ? SlimeChunkTracker.currentSeed() : null;
        int range = (int) Math.ceil(layout.coveredBlocks() / 32.0) + 1;
        int centerChunkX = chunkAt(layout.centerWorldX());
        int centerChunkZ = chunkAt(layout.centerWorldZ());
        for (int chunkX = centerChunkX - range; chunkX <= centerChunkX + range; chunkX++) {
            for (int chunkZ = centerChunkZ - range; chunkZ <= centerChunkZ + range; chunkZ++) {
                int left = (int) Math.floor(layout.worldToScreenX(chunkX * 16.0));
                int top = (int) Math.floor(layout.worldToScreenZ(chunkZ * 16.0));
                int right = (int) Math.ceil(layout.worldToScreenX((chunkX + 1) * 16.0));
                int bottom = (int) Math.ceil(layout.worldToScreenZ((chunkZ + 1) * 16.0));
                drawCell(graphics, config.minimap.showChunkGrid,
                        seed != null && SlimeChunkTracker.isSlimeChunk(seed, chunkX, chunkZ),
                        left, top, Math.max(1, right - left), Math.max(1, bottom - top), circular,
                        layout.x() + layout.size() / 2.0, layout.y() + layout.size() / 2.0,
                        layout.size() / 2.0 - MinimapLayout.CLIP_INSET);
            }
        }
    }

    private static void drawCell(GuiGraphics graphics, boolean grid, boolean slime,
            int x, int y, int width, int height, boolean circular,
            double centerX, double centerZ, double radius) {
        if (slime) {
            fill(graphics, x, y, width, height, SLIME_COLOR, circular, centerX, centerZ, radius);
        }
        if (grid) {
            fill(graphics, x, y, width, 1, GRID_COLOR, circular, centerX, centerZ, radius);
            fill(graphics, x, y, 1, height, GRID_COLOR, circular, centerX, centerZ, radius);
        }
    }

    private static void fill(GuiGraphics graphics, int x, int y, int width, int height,
            int color, boolean circular, double centerX, double centerZ, double radius) {
        if (!circular) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        for (CircleClip.Band band : CircleClip.bands(x, y, width, height,
                centerX, centerZ, radius)) {
            graphics.fill(band.left(), band.top(), band.right(), band.bottom(), color);
        }
    }

    private static int chunkAt(double worldCoordinate) {
        return (int) Math.floor(worldCoordinate / ChunkMapData.SIZE);
    }
}
