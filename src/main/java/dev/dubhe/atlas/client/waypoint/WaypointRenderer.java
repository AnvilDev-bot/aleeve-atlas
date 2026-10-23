package dev.dubhe.atlas.client.waypoint;

import dev.dubhe.atlas.client.map.MinimapLayout;
import dev.dubhe.atlas.config.MinimapShape;
import dev.dubhe.atlas.waypoint.Waypoint;
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

    /// 在小地图上绘制可见路径点（规格 §5.1：小地图边缘/内部同时渲染）。
    ///
    /// 落在小地图范围内的路径点绘制在对应坐标；超出范围的钳制到边缘，
    /// 以缩小标记提示方向，避免玩家看不到视野外路径点的方位。
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
            if (layout.contains(screenX, screenZ, circular)) {
                drawDiamond(graphics, screenX, screenZ, waypoint.colorArgb());
                continue;
            }
            drawEdgeMarker(graphics, layout, screenX, screenZ, circular,
                    waypoint.colorArgb());
        }
    }

    /// 小地图边缘标记：把超出范围的路径点钳制到最外圈，绘制小尺寸方向点。
    private static void drawEdgeMarker(GuiGraphics graphics, MinimapLayout layout,
            double screenX, double screenZ, boolean circular, int color) {
        double centerX = layout.x() + layout.size() / 2.0;
        double centerZ = layout.y() + layout.size() / 2.0;
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        if (dx == 0.0 && dz == 0.0) {
            return;
        }
        // 缩放到边界内侧 3 像素处，避免被边框遮住。
        double limit = layout.size() / 2.0 - 3.0;
        double scale;
        if (circular) {
            double length = Math.sqrt(dx * dx + dz * dz);
            scale = limit / length;
        } else {
            double maxAxis = Math.max(Math.abs(dx), Math.abs(dz));
            scale = limit / maxAxis;
        }
        if (scale > 1.0) {
            scale = 1.0;
        }
        int x = (int) Math.round(centerX + dx * scale);
        int z = (int) Math.round(centerZ + dz * scale);
        // 边缘标记用 1 像素点，与内部大菱形区分。
        graphics.fill(x - 1, z - 1, x + 1, z + 1, color);
        graphics.fill(x, z, x + 1, z + 1, 0xFF101010);
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
    public static boolean circular(dev.dubhe.atlas.config.AtlasClientConfig config) {
        return config.minimap.shape == MinimapShape.CIRCLE;
    }
}
