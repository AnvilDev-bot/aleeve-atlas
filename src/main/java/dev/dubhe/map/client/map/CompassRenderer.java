package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/// 小地图方向文字与世界地图四角星方向标。
public final class CompassRenderer {
    private static final Identifier WHITE_TEXTURE = AleeveAtlas.id("textures/gui/white.png");
    private static final String[] CARDINALS = { "N", "E", "S", "W" };
    private static final double[][] DIRECTIONS = {
        { 0.0, -1.0 }, { 1.0, 0.0 }, { 0.0, 1.0 }, { -1.0, 0.0 }
    };
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_SHADOW = 0x66000000;
    private static final int WORLD_COMPASS_SIZE = 72;

    private CompassRenderer() {
    }

    /// 方向位置随地图旋转，文字始终保持屏幕正向。
    public static void renderOnMinimap(GuiGraphics graphics, MinimapLayout layout,
            double rotationDegrees) {
        Minecraft minecraft = Minecraft.getInstance();
        double centerX = layout.x() + layout.size() / 2.0;
        double centerY = layout.y() + layout.size() / 2.0;
        double radius = Math.max(6.0, layout.size() / 2.0 - 8.0);
        for (int index = 0; index < CARDINALS.length; index++) {
            double[] point = MinimapLayout.rotatePoint(
                    centerX + DIRECTIONS[index][0] * radius,
                    centerY + DIRECTIONS[index][1] * radius,
                    centerX, centerY, rotationDegrees);
            String label = CARDINALS[index];
            graphics.drawString(minecraft.font, label,
                    (int) Math.round(point[0] - minecraft.font.width(label) / 2.0),
                    (int) Math.round(point[1] - minecraft.font.lineHeight / 2.0),
                    TEXT_COLOR, true);
        }
    }

    /// 在世界地图左上角绘制白色光滑方向标，图形与文字均有黑色半透明投影。
    public static void renderOnWorldMap(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int centerX = 48;
        int centerY = 96;
        graphics.blit(AtlasRenderPipelines.WORLD_COMPASS, WHITE_TEXTURE,
                centerX - WORLD_COMPASS_SIZE / 2, centerY - WORLD_COMPASS_SIZE / 2,
                0.0F, 0.0F, WORLD_COMPASS_SIZE, WORLD_COMPASS_SIZE, 2, 2, 2, 2, 0xFFFFFFFF);
        int radius = 28;
        for (int index = 0; index < CARDINALS.length; index++) {
            int x = (int) Math.round(centerX + DIRECTIONS[index][0] * (radius + 10));
            int y = (int) Math.round(centerY + DIRECTIONS[index][1] * (radius + 10));
            drawWorldLabel(graphics, minecraft, CARDINALS[index], x, y);
        }
    }

    private static void drawWorldLabel(GuiGraphics graphics, Minecraft minecraft,
            String label, int centerX, int centerY) {
        int x = centerX - minecraft.font.width(label) / 2;
        int y = centerY - minecraft.font.lineHeight / 2;
        graphics.drawString(minecraft.font, label, x + 1, y + 1, TEXT_SHADOW, false);
        graphics.drawString(minecraft.font, label, x, y, TEXT_COLOR, false);
    }
}
