package dev.dubhe.atlas.client.waypoint;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.waypoint.Waypoint;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// 游戏内 3D 视界路径点标识。
///
/// 在 HUD 阶段把路径点世界坐标投影到屏幕，绘制菱形图标、名称与距离；
/// 超出可见距离或位于视野外的路径点不渲染。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class WaypointWorldOverlay {
    /// 超过该距离不再显示（避免屏幕上堆积过多标识）。
    private static final double MAX_RENDER_DISTANCE = 512.0;
    private static final int ICON_HALF_SIZE = 4;

    private WaypointWorldOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        List<Waypoint> waypoints = ClientWaypoints.visibleWaypoints();
        if (waypoints.isEmpty()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        for (Waypoint waypoint : waypoints) {
            Vec3 target = new Vec3(waypoint.pos().getX() + 0.5, waypoint.pos().getY() + 1.2,
                    waypoint.pos().getZ() + 0.5);
            WorldProjection.project(target)
                    .filter(projected -> projected.distance() <= MAX_RENDER_DISTANCE)
                    .ifPresent(projected -> drawMarker(graphics, minecraft, waypoint, projected));
        }
    }

    private static void drawMarker(GuiGraphics graphics, Minecraft minecraft, Waypoint waypoint,
            WorldProjection.Projected projected) {
        int x = (int) Math.round(projected.screenX());
        int y = (int) Math.round(projected.screenY());
        // 菱形图标（与地图标记保持一致的视觉语言）。
        for (int offset = -ICON_HALF_SIZE; offset <= ICON_HALF_SIZE; offset++) {
            int halfWidth = ICON_HALF_SIZE - Math.abs(offset);
            graphics.fill(x - halfWidth, y + offset, x + halfWidth + 1, y + offset + 1,
                    waypoint.colorArgb());
        }
        graphics.fill(x, y - 1, x + 1, y + 1, 0xFF101010);

        // 名称与距离。
        Component label = Component.literal(
                "%s %dm".formatted(waypoint.name(), Math.round(projected.distance())))
                .withStyle(ChatFormatting.WHITE);
        int textWidth = minecraft.font.width(label);
        graphics.drawString(minecraft.font, label, x - textWidth / 2, y + ICON_HALF_SIZE + 3,
                0xFFFFFFFF, true);
    }
}
