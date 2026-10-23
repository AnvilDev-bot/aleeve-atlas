package dev.aleeve.atlas.client.waypoint;

import dev.aleeve.atlas.client.map.MinimapLayout;
import dev.aleeve.atlas.config.MinimapShape;
import dev.aleeve.atlas.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import java.util.List;

/// 路径点标记渲染（规格 §5.1）。
///
/// 小地图与世界地图共用同一套绘制：以菱形标记 + 深色描边表示路径点位置。
public final class WaypointRenderer {
    private static final int HALF_SIZE = 3;
    private static final int OUTLINE = 0xFF101010;

    private WaypointRenderer() {
    }

    /// 在小地图上绘制可见路径点。
    public static void renderOnMinimap(GuiGraphics graphics, MinimapLayout layout,
            boolean circular) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        List<Waypoint> waypoints = ClientWaypoints.visibleWaypoints();
        for (Waypoint waypoint : waypoints) {
            double screenX = layout.worldToScreenX(waypoint.pos().getX() + 0.5);
            double screenZ = layout.worldToScreenZ(waypoint.pos().getZ() + 0.5);
            if (!layout.contains(screenX, screenZ, circular)) {
                continue;
            }
            drawDiamond(graphics, screenX, screenZ, waypoint.colorArgb());
        }
    }

    /// 在世界地图上绘制路径点（屏幕坐标由调用方换算）。
    public static void renderOnWorldMap(GuiGraphics graphics, double screenX, double screenZ,
            int color) {
        drawDiamond(graphics, screenX, screenZ, color);
    }

    private static void drawDiamond(GuiGraphics graphics, double screenX, double screenZ,
            int color) {
        int x = (int) Math.round(screenX);
        int z = (int) Math.round(screenZ);
        for (int offset = -HALF_SIZE; offset <= HALF_SIZE; offset++) {
            int halfWidth = HALF_SIZE - Math.abs(offset);
            int fromX = x - halfWidth;
            int toX = x + halfWidth + 1;
            graphics.fill(fromX, z + offset, toX, z + offset + 1, color);
        }
        // 中心深色点，便于在浅色地形上定位。
        graphics.fill(x, z - 1, x + 1, z + 1, OUTLINE);
    }

    /// 圆形小地图形态判定工具。
    public static boolean circular(dev.aleeve.atlas.config.AtlasClientConfig config) {
        return config.minimap.shape == MinimapShape.CIRCLE;
    }
}
