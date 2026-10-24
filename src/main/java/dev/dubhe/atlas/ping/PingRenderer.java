package dev.dubhe.atlas.ping;

import dev.dubhe.atlas.config.AtlasClientConfig;
import dev.dubhe.atlas.team.TeamProviders;
import dev.dubhe.atlas.AtlasSession;
import dev.dubhe.atlas.client.map.MinimapLayout;
import dev.dubhe.atlas.config.MinimapShape;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/// 标点地图渲染（规格 §3.2.2：标点须在小地图与世界地图上以波纹扩散动画呈现）。
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
            // 非同队玩家发起的标点按服务端下发的可见性策略过滤（§4.1）。
            if (!visibleByPolicy(minecraft, ping)) {
                continue;
            }
            double screenX = layout.worldToScreenX(ping.position().x);
            double screenZ = layout.worldToScreenZ(ping.position().z);
            if (!layout.contains(screenX, screenZ, circular)) {
                continue;
            }
            drawPing(graphics, screenX, screenZ, ping);
        }
    }

    /// 按非同队玩家可见性策略判定标点是否可绘制。
    ///
    /// 同队玩家与本地发起的标点始终可见；非同队玩家在 `NEVER` 下隐藏，
    /// `VISIBLE_ONLY` 下需通过可见性判定。
    static boolean visibleByPolicy(Minecraft minecraft, MapPingData ping) {
        if (minecraft.player == null || ping.senderId() == null) {
            return true;
        }
        if (ping.senderId().equals(minecraft.player.getUUID())) {
            return true;
        }
        if (TeamProviders.active().sameTeam(minecraft.player.getUUID(), ping.senderId())) {
            return true;
        }
        return switch (AtlasSession.nonTeamVisibility()) {
            case NEVER -> false;
            case ALWAYS -> true;
            case VISIBLE_ONLY -> {
                var sender = minecraft.level == null ? null
                        : minecraft.level.getPlayerByUUID(ping.senderId());
                // 发送者实体不在客户端可见范围内时视为不可见。
                yield sender != null && minecraft.player.hasLineOfSight(sender);
            }
        };
    }

    /// 在世界地图上绘制活动标点（屏幕坐标由调用方换算）。
    public static void renderOnWorldMap(GuiGraphics graphics, double screenX, double screenZ,
            MapPingData ping) {
        drawPing(graphics, screenX, screenZ, ping);
    }

    /// 波纹扩散动画 + 中心菱标。
    private static void drawPing(GuiGraphics graphics, double screenX, double screenZ,
            MapPingData ping) {
        int x = (int) Math.round(screenX);
        int z = (int) Math.round(screenZ);
        long age = System.currentTimeMillis() - ping.createdAtMillis();
        if (age < 0) {
            age = 0;
        }
        double phase = (double) (age % RIPPLE_PERIOD_MILLIS) / RIPPLE_PERIOD_MILLIS;
        int radius = 2 + (int) Math.round(phase * MAX_RIPPLE_PIXELS);
        int alpha = (int) Math.round(200 * (1.0 - phase));
        if (alpha <= 0) {
            alpha = 1;
        }
        int rippleColor = (alpha << 24) | (ping.teamColorArgb() & 0xFFFFFF);

        // 圆环按行扫描：每行求圆与水平线的交点区间，一次填充覆盖该行左右两段，
        // 绘制调用数与直径同阶，避免按角度逐点填充造成的重复绘制（§12.1）。
        for (int dz = -radius; dz <= radius; dz++) {
            double spanSq = (double) radius * radius - (double) dz * dz;
            if (spanSq <= 0.0) {
                continue;
            }
            int half = (int) Math.round(Math.sqrt(spanSq));
            if (half <= 0) {
                continue;
            }
            // 只保留圆环边缘一个像素宽，内部留空以便看出扩散方向。
            graphics.fill(x - half, z + dz, x - half + 1, z + dz + 1, rippleColor);
            graphics.fill(x + half, z + dz, x + half + 1, z + dz + 1, rippleColor);
        }
        // 中心标记按图标类型区分（规格 §3.2.1 标点图标类型）。
        int centerColor = 0xFF000000 | (ping.teamColorArgb() & 0xFFFFFF);
        drawIcon(graphics, x, z, centerColor, ping.icon());
    }

    /// 按标点图标类型绘制中心标记。
    ///
    /// 四类标点形状各异，便于在密集标点时快速分辨：普通为方块，
    /// 危险为叉形，集合为十字，物资为菱形。
    static void drawIcon(GuiGraphics graphics, int x, int z, int color,
            MapPingData.PingIcon icon) {
        switch (icon) {
            case DANGER -> {
                // 叉形：两条对角线。
                for (int offset = -2; offset <= 2; offset++) {
                    graphics.fill(x + offset, z + offset, x + offset + 1, z + offset + 1, color);
                    graphics.fill(x + offset, z - offset, x + offset + 1, z - offset + 1, color);
                }
            }
            case RALLY -> {
                // 十字：横竖两条。
                graphics.fill(x - 3, z, x + 4, z + 1, color);
                graphics.fill(x, z - 3, x + 1, z + 4, color);
            }
            case SUPPLY -> {
                // 菱形：逐行收窄。
                for (int offset = -2; offset <= 2; offset++) {
                    int half = 2 - Math.abs(offset);
                    graphics.fill(x - half, z + offset, x + half + 1, z + offset + 1, color);
                }
            }
            default -> {
                // 普通标点：实心方块加白心。
                graphics.fill(x - 2, z - 2, x + 2, z + 2, color);
                graphics.fill(x - 1, z - 1, x + 1, z + 1, 0xFFFFFFFF);
            }
        }
    }

    /// 圆形小地图形态判定。
    public static boolean circular(AtlasClientConfig config) {
        return config.minimap.shape == MinimapShape.CIRCLE;
    }

    /// 当前活动的同维度标点数量（诊断用）。
    public static int activeCount() {
        return ClientPings.active().size();
    }

    /// 世界坐标工具。
    public static Vec3 positionOf(MapPingData ping) {
        return ping.position();
    }

    /// 活动标点列表。
    public static List<MapPingData> activePings() {
        return ClientPings.active();
    }
}
