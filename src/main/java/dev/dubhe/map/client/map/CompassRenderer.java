package dev.dubhe.map.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/// 小地图方向文字与世界地图四角星方向标。
public final class CompassRenderer {
    private static final String[] CARDINALS = { "N", "E", "S", "W" };
    private static final double[][] DIRECTIONS = {
        { 0.0, -1.0 }, { 1.0, 0.0 }, { 0.0, 1.0 }, { -1.0, 0.0 }
    };
    private static final int MINIMAP_TEXT_COLOR = 0xFFFFFFFF;
    private static final int WORLD_MAP_COLOR = 0xFF101010;

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
                    MINIMAP_TEXT_COLOR, true);
        }
    }

    /// 在世界地图右下角绘制黑色四角星和带阴影的方向文字。
    public static void renderOnWorldMap(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int centerX = minecraft.getWindow().getGuiScaledWidth() - 48;
        int centerY = minecraft.getWindow().getGuiScaledHeight() - 48;
        int radius = 28;
        drawStar(graphics, centerX, centerY, radius, 6);
        for (int index = 0; index < CARDINALS.length; index++) {
            int x = (int) Math.round(centerX + DIRECTIONS[index][0] * (radius + 10));
            int y = (int) Math.round(centerY + DIRECTIONS[index][1] * (radius + 10));
            String label = CARDINALS[index];
            graphics.drawString(minecraft.font, label,
                    x - minecraft.font.width(label) / 2,
                    y - minecraft.font.lineHeight / 2, WORLD_MAP_COLOR, true);
        }
    }

    private static void drawStar(GuiGraphics graphics, int centerX, int centerY,
            int radius, int armWidth) {
        for (int offset = -radius; offset <= radius; offset++) {
            int width = Math.round(armWidth * (1.0F - Math.abs(offset) / (float) radius));
            graphics.fill(centerX - width, centerY + offset,
                    centerX + width + 1, centerY + offset + 1, WORLD_MAP_COLOR);
            int height = Math.round(armWidth * (1.0F - Math.abs(offset) / (float) radius));
            if (offset != 0) {
                graphics.fill(centerX + offset, centerY - height,
                        centerX + offset + 1, centerY + height + 1, WORLD_MAP_COLOR);
            }
        }
        graphics.fill(centerX - armWidth, centerY - armWidth,
                centerX + armWidth + 1, centerY + armWidth + 1, WORLD_MAP_COLOR);
    }
}
