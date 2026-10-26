package dev.dubhe.atlas.ping;

import dev.dubhe.atlas.AtlasSession;
import dev.dubhe.atlas.client.map.MapMarkerRenderer;
import dev.dubhe.atlas.client.map.MinimapLayout;
import dev.dubhe.atlas.client.radar.VisibilityGate;
import dev.dubhe.atlas.config.AtlasClientConfig;
import dev.dubhe.atlas.config.MinimapShape;
import dev.dubhe.atlas.config.NonTeamVisibility;
import dev.dubhe.atlas.team.TeamProviders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/// 标点地图渲染。
public final class PingRenderer {
    private static final int MAX_RIPPLE_PIXELS = 10;
    private static final long RIPPLE_PERIOD_MILLIS = 900L;

    private PingRenderer() {
    }

    /// 在小地图上绘制活动标点。
    public static void renderOnMinimap(GuiGraphics graphics, MinimapLayout layout,
            boolean circular) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        String dimension = MapPingData.dimensionId(minecraft.level.dimension());
        for (MapPingData ping : ClientPings.active()) {
            if (!ping.dimensionId().equals(dimension)) {
                continue;
            }
            float opacity = opacityByPolicy(minecraft, ping);
            if (opacity <= 0.0F) {
                continue;
            }
            double screenX = layout.worldToScreenX(ping.position().x);
            double screenZ = layout.worldToScreenZ(ping.position().z);
            if (!layout.containsRotated(screenX, screenZ, circular)) {
                drawOffscreenArrow(graphics, layout, screenX, screenZ, circular, ping, opacity);
                continue;
            }
            double[] fitted = layout.fitInside(screenX, screenZ, circular,
                    MAX_RIPPLE_PIXELS + 3.0);
            drawPing(graphics, fitted[0], fitted[1], ping, opacity);
        }
    }

    /// 计算标点的可见性与渐隐透明度。
    ///
    /// 同队玩家与本地发起的标点始终可见；非同队玩家的发送者必须存在于客户端，
    /// 并与实体雷达共用服务端策略、GPU 遮挡查询和渐隐结果。
    public static float opacityByPolicy(Minecraft minecraft, MapPingData ping) {
        if (minecraft.player == null || ping.senderId() == null) {
            return 1.0F;
        }
        if (ping.senderId().equals(minecraft.player.getUUID())) {
            return 1.0F;
        }
        if (TeamProviders.active().sameTeam(minecraft.player.getUUID(), ping.senderId())) {
            return 1.0F;
        }
        if (AtlasSession.nonTeamVisibility() == NonTeamVisibility.NEVER) {
            return 0.0F;
        }
        if (AtlasSession.nonTeamVisibility() == NonTeamVisibility.ALWAYS) {
            return 1.0F;
        }
        if (minecraft.level == null) {
            return 0.0F;
        }
        Player sender = minecraft.level.getPlayerByUUID(ping.senderId());
        if (sender == null) {
            return 0.0F;
        }
        return VisibilityGate.opacity(minecraft.level, minecraft.player, sender);
    }

    /// 超出地图范围的标点在内缩边缘显示方向箭头。
    private static void drawOffscreenArrow(GuiGraphics graphics, MinimapLayout layout,
            double screenX, double screenZ, boolean circular, MapPingData ping, float opacity) {
        double centerX = layout.x() + layout.size() / 2.0;
        double centerZ = layout.y() + layout.size() / 2.0;
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        if (dx == 0.0 && dz == 0.0) {
            return;
        }
        double[] fitted = layout.fitInside(screenX, screenZ, circular, 7.0);
        double angle = MapMarkerRenderer.directionAngle(dx, dz);
        int color = applyOpacity(ping.icon().colorArgb(), opacity);
        MapMarkerRenderer.drawArrow(graphics, fitted[0], fitted[1], 12, angle, color);
    }

    /// 在世界地图上绘制活动标点（屏幕坐标与透明度由调用方计算）。
    public static void renderOnWorldMap(GuiGraphics graphics, double screenX, double screenZ,
            MapPingData ping, float opacity) {
        // 世界地图铺满整个屏幕，波纹无需裁剪。
        drawPing(graphics, screenX, screenZ, ping, opacity);
    }

    /// 抗锯齿圆形中心 + 波纹扩散动画。
    private static void drawPing(GuiGraphics graphics, double screenX, double screenZ,
            MapPingData ping, float opacity) {
        int x = (int) Math.round(screenX);
        int z = (int) Math.round(screenZ);
        long age = System.currentTimeMillis() - ping.createdAtMillis();
        if (age < 0) {
            age = 0;
        }
        double phase = (double) (age % RIPPLE_PERIOD_MILLIS) / RIPPLE_PERIOD_MILLIS;
        // 半径随时间扩张，颜色逐渐淡出；圆环由片元着色器抗锯齿绘制。
        float ripplePhase = (float) phase;
        int size = Math.round((2.0F + ripplePhase * MAX_RIPPLE_PIXELS) * 2.0F + 4.0F);
        int rippleColor = applyOpacity(ping.icon().colorArgb(), opacity * (1.0F - ripplePhase));
        MapMarkerRenderer.drawRipple(graphics, x, z, size, rippleColor);
        MapMarkerRenderer.drawDot(graphics, x, z, 10,
                applyOpacity(ping.icon().colorArgb(), opacity));
        drawIcon(graphics, x, z, applyOpacity(0xFFFFFFFF, opacity), ping.icon(), opacity);
    }

    /// 按不透明度缩放 ARGB 的 alpha 通道。
    static int applyOpacity(int argb, float opacity) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, opacity));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /// 像素是否落在裁剪矩形内。
    static boolean insideClip(int px, int py, int clipX, int clipY, int clipSize) {
        return px >= clipX && px < clipX + clipSize
                && py >= clipY && py < clipY + clipSize;
    }

    /// 按标点图标类型绘制中心标记。
    ///
    /// 四类标点通过颜色与内层符号区分：普通为圆点，
    /// 危险为叉形，集合为十字，物资为菱形。
    static void drawIcon(GuiGraphics graphics, int x, int z, int color,
            MapPingData.PingIcon icon, float opacity) {
        switch (icon) {
            case DANGER -> {
                for (int offset = -2; offset <= 2; offset++) {
                    graphics.fill(x + offset, z + offset, x + offset + 1, z + offset + 1, color);
                    graphics.fill(x + offset, z - offset, x + offset + 1, z - offset + 1, color);
                }
            }
            case RALLY -> {
                graphics.fill(x - 3, z, x + 4, z + 1, color);
                graphics.fill(x, z - 3, x + 1, z + 4, color);
            }
            case SUPPLY -> MapMarkerRenderer.drawDiamond(graphics, x, z, 8, color);
            default -> MapMarkerRenderer.drawDot(graphics, x, z, 4, color);
        }
    }

    /// 圆形小地图形态判定。
    public static boolean circular(AtlasClientConfig config) {
        return config.minimap.shape == MinimapShape.CIRCLE;
    }

    /// 活动标点列表。
    public static List<MapPingData> activePings() {
        return ClientPings.active();
    }
}
