package dev.aleeve.atlas.client.map;

import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.config.AtlasClientConfig;
import dev.aleeve.atlas.config.AtlasClientConfigs;
import dev.aleeve.atlas.config.ConfigBounds;
import dev.aleeve.atlas.config.MinimapShape;
import dev.aleeve.atlas.map.ChunkMapData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// 小地图 HUD 渲染（规格 §2.2）。
///
/// 从 {@link MapTexture} 中截取以玩家为中心的区域绘制；形态、位置、缩放与信息覆层取自客户端配置。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MinimapRenderer {
    /// 1.0 倍缩放时的小地图边长（GUI 像素）。
    private static final int BASE_SIZE = 128;
    /// 小地图覆盖的方块宽度 = 区块数 × 每区块像素。
    private static final int COVERED_BLOCKS =
            ChunkMapData.SIZE * (ClientMapManager.VIEW_CHUNK_RADIUS * 2 + 1);
    private static final int MASK_COLOR = 0xC0000000;
    private static final int BORDER_COLOR = 0xFF101010;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private MinimapRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        AtlasClientConfig config = AtlasClientConfigs.CONFIG;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!config.minimap.enabled || minecraft.options.hideGui) {
            return;
        }
        ClientMapManager manager = AtlasClientMap.manager();
        if (manager == null) {
            return;
        }
        MapTexture texture = manager.texture();
        if (!texture.ready()) {
            return;
        }

        float scale = ConfigBounds.clamp(config.minimap.scale, 0.5F, 4.0F);
        int size = Math.round(BASE_SIZE * scale);
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int[] origin = resolveOrigin(config, size, screenWidth, screenHeight);

        LocalPlayer player = minecraft.player;
        int[] sample = sampleWindow(texture, player, size);
        graphics.blit(texture.textureId(), origin[0], origin[1], size, size,
                sample[0], sample[1], sample[2], sample[3]);

        boolean circular = config.minimap.shape == MinimapShape.CIRCLE;
        if (circular) {
            maskCorners(graphics, origin[0], origin[1], size);
        }
        drawFrame(graphics, origin[0], origin[1], size, circular);
        drawPlayerArrow(graphics, origin[0] + size / 2, origin[1] + size / 2,
                config.minimap.lockNorth ? 0.0F : player.getYRot(), size);
        drawInfo(graphics, config, minecraft, size, origin);
    }

    /// 计算纹理采样窗口：`[u, v, uWidth, vHeight]`（纹理像素坐标）。
    private static int[] sampleWindow(MapTexture texture, LocalPlayer player, int size) {
        int covered = Math.min(COVERED_BLOCKS, MapTexture.PIXELS);
        double relativeX = player.getX() - (texture.originChunkX() * ChunkMapData.SIZE);
        double relativeZ = player.getZ() - (texture.originChunkZ() * ChunkMapData.SIZE);
        int u = clamp((int) Math.round(relativeX - covered / 2.0), 0, MapTexture.PIXELS - covered);
        int v = clamp((int) Math.round(relativeZ - covered / 2.0), 0, MapTexture.PIXELS - covered);
        return new int[] { u, v, covered, covered };
    }

    private static int[] resolveOrigin(AtlasClientConfig config, int size, int screenWidth,
            int screenHeight) {
        int margin = 6;
        return switch (config.minimap.position) {
            case TOP_LEFT -> new int[] { margin, margin };
            case TOP_RIGHT -> new int[] { screenWidth - size - margin, margin };
            case BOTTOM_LEFT -> new int[] { margin, screenHeight - size - margin };
            case BOTTOM_RIGHT ->
                new int[] { screenWidth - size - margin, screenHeight - size - margin };
            case CUSTOM -> new int[] {
                    clamp(config.minimap.offsetX, 0, Math.max(0, screenWidth - size)),
                    clamp(config.minimap.offsetY, 0, Math.max(0, screenHeight - size)) };
        };
    }

    /// 圆形形态：逐行在圆外填充遮罩，使小地图呈圆形（规格 §2.2）。
    private static void maskCorners(GuiGraphics graphics, int x, int y, int size) {
        double radius = size / 2.0;
        double centerX = x + radius;
        double centerY = y + radius;
        for (int row = 0; row < size; row++) {
            double dy = (row + 0.5) - radius;
            double span = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
            int innerLeft = (int) Math.round(centerX - span);
            int innerRight = (int) Math.round(centerX + span);
            if (innerLeft > x) {
                graphics.fill(x, y + row, innerLeft, y + row + 1, MASK_COLOR);
            }
            if (innerRight < x + size) {
                graphics.fill(innerRight, y + row, x + size, y + row + 1, MASK_COLOR);
            }
        }
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int size, boolean circular) {
        if (!circular) {
            graphics.fill(x - 1, y - 1, x + size + 1, y, BORDER_COLOR);
            graphics.fill(x - 1, y + size, x + size + 1, y + size + 1, BORDER_COLOR);
            graphics.fill(x - 1, y, x, y + size, BORDER_COLOR);
            graphics.fill(x + size, y, x + size + 1, y + size, BORDER_COLOR);
            return;
        }
        // 圆形边框：逐行绘制 2px 环带。
        double radius = size / 2.0;
        for (int row = 0; row < size; row++) {
            double dy = (row + 0.5) - radius;
            double outer = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
            double innerRadius = radius - 1.5;
            double inner = Math.sqrt(Math.max(0.0, innerRadius * innerRadius - dy * dy));
            int outerLeft = (int) Math.round(radius - outer);
            int outerRight = (int) Math.round(radius + outer);
            int innerLeft = (int) Math.round(radius - inner);
            int innerRight = (int) Math.round(radius + inner);
            graphics.fill(x + outerLeft, y + row, x + Math.max(outerLeft, innerLeft), y + row + 1,
                    BORDER_COLOR);
            graphics.fill(x + Math.min(outerRight, innerRight), y + row, x + outerRight,
                    y + row + 1, BORDER_COLOR);
        }
    }

    /// 玩家朝向箭头：实心三角，长度随小地图尺寸缩放。
    private static void drawPlayerArrow(GuiGraphics graphics, int centerX, int centerY, float yaw,
            int size) {
        double radians = Math.toRadians(-yaw);
        double length = Math.max(4.0, size * 0.06);
        double halfWidth = length * 0.62;
        double tipX = centerX + Math.sin(radians) * length;
        double tipY = centerY - Math.cos(radians) * length;
        double baseCenterX = centerX - Math.sin(radians) * (length * 0.55);
        double baseCenterY = centerY + Math.cos(radians) * (length * 0.55);
        double leftX = baseCenterX + Math.cos(radians) * halfWidth;
        double leftY = baseCenterY + Math.sin(radians) * halfWidth;
        double rightX = baseCenterX - Math.cos(radians) * halfWidth;
        double rightY = baseCenterY - Math.sin(radians) * halfWidth;
        fillTriangle(graphics, tipX, tipY, leftX, leftY, rightX, rightY, 0xFFFFFFFF);
    }

    /// 逐行光栅化实心三角形。
    private static void fillTriangle(GuiGraphics graphics, double x1, double y1, double x2, double y2,
            double x3, double y3, int color) {
        int minY = (int) Math.floor(Math.min(y1, Math.min(y2, y3)));
        int maxY = (int) Math.ceil(Math.max(y1, Math.max(y2, y3)));
        for (int row = minY; row <= maxY; row++) {
            double scanLine = row + 0.5;
            double left = Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;
            double[] xs = new double[3];
            int count = 0;
            count = intersectRow(x1, y1, x2, y2, scanLine, xs, count);
            count = intersectRow(x2, y2, x3, y3, scanLine, xs, count);
            count = intersectRow(x3, y3, x1, y1, scanLine, xs, count);
            if (count == 0) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                left = Math.min(left, xs[i]);
                right = Math.max(right, xs[i]);
            }
            int from = (int) Math.round(left);
            int to = (int) Math.round(right);
            if (to > from) {
                graphics.fill(from, row, to, row + 1, color);
            }
        }
    }

    private static int intersectRow(double x1, double y1, double x2, double y2, double scanLine,
            double[] out, int count) {
        if (y1 == y2) {
            return count;
        }
        double lower = Math.min(y1, y2);
        double upper = Math.max(y1, y2);
        if (scanLine < lower || scanLine >= upper) {
            return count;
        }
        double t = (scanLine - y1) / (y2 - y1);
        out[count] = x1 + t * (x2 - x1);
        return count + 1;
    }

    private static void drawInfo(GuiGraphics graphics, AtlasClientConfig config, Minecraft minecraft,
            int size, int[] origin) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int line = origin[1] + size + 4;
        int textX = origin[0];
        if (config.minimap.showCoords) {
            Component text = Component.literal("%d, %d, %d".formatted(
                    (int) Math.floor(player.getX()),
                    (int) Math.floor(player.getY()),
                    (int) Math.floor(player.getZ())));
            graphics.drawString(minecraft.font, text, textX, line, TEXT_COLOR);
            line += 10;
        }
        if (config.minimap.showBiome && minecraft.level != null) {
            var biome = minecraft.level.getBiome(player.blockPosition());
            Component name = biome.unwrapKey()
                    .<Component>map(key -> Component.translatable(
                            "biome." + key.identifier().toLanguageKey()))
                    .orElseGet(() -> Component.literal("?"));
            graphics.drawString(minecraft.font, name, textX, line, TEXT_COLOR);
            line += 10;
        }
        if (config.minimap.showLight && minecraft.level != null) {
            int light = minecraft.level.getMaxLocalRawBrightness(player.blockPosition());
            graphics.drawString(minecraft.font,
                    Component.translatable("aleeve_atlas.hud.light", light),
                    textX, line, TEXT_COLOR);
            line += 10;
        }
        if (config.minimap.showTime && minecraft.level != null) {
            long dayTime = minecraft.level.getDayTime() % 24000L;
            int hours = (int) ((dayTime / 1000L + 6L) % 24L);
            int minutes = (int) ((dayTime % 1000L) * 60L / 1000L);
            graphics.drawString(minecraft.font,
                    Component.literal("%02d:%02d".formatted(hours, minutes)),
                    textX, line, TEXT_COLOR);
        }
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
