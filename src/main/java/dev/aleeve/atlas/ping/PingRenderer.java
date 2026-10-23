package dev.aleeve.atlas.ping;

import dev.aleeve.atlas.client.map.MinimapLayout;
import dev.aleeve.atlas.config.MinimapShape;
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
            double screenX = layout.worldToScreenX(ping.position().x);
            double screenZ = layout.worldToScreenZ(ping.position().z);
            if (!layout.contains(screenX, screenZ, circular)) {
                continue;
            }
            drawPing(graphics, screenX, screenZ, ping);
        }
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

        // 按角度遍历圆环，复杂度 O(r) 而非 O(r²)，避免多标点同时存在时的绘制开销。
        int segments = Math.max(12, radius * 8);
        for (int step = 0; step < segments; step++) {
            double angle = 2.0 * Math.PI * step / segments;
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x + dx, z + dz, x + dx + 1, z + dz + 1, rippleColor);
        }
        // 中心标记。
        int centerColor = 0xFF000000 | (ping.teamColorArgb() & 0xFFFFFF);
        graphics.fill(x - 2, z - 2, x + 2, z + 2, centerColor);
        graphics.fill(x - 1, z - 1, x + 1, z + 1, 0xFFFFFFFF);
    }

    /// 圆形小地图形态判定。
    public static boolean circular(dev.aleeve.atlas.config.AtlasClientConfig config) {
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
