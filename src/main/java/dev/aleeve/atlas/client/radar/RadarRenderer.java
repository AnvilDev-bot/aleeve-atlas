package dev.aleeve.atlas.client.radar;

import dev.aleeve.atlas.AtlasSession;
import dev.aleeve.atlas.client.map.MinimapLayout;
import dev.aleeve.atlas.config.AtlasClientConfig;
import dev.aleeve.atlas.config.ConfigBounds;
import dev.aleeve.atlas.config.MinimapShape;
import dev.aleeve.atlas.config.RadarDisplayMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/// 实体雷达的小地图标记渲染（规格 §6）。
///
/// 圆点模式下按类别配色绘制；混合联动模式下按住玩家列表键（默认 Tab）时，
/// 玩家标记切换为皮肤头像（规格 §6.1 / §6.3）。
public final class RadarRenderer {
    /// 标记半径（GUI 像素）。
    private static final int DOT_RADIUS = 2;
    private static final int HEAD_SIZE = 8;

    private RadarRenderer() {
    }

    /// 在小地图上绘制雷达标记。
    public static void render(GuiGraphics graphics, AtlasClientConfig config,
            MinimapLayout layout, Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        RadarDisplayMode mode = config.radar.displayMode;
        boolean showIcons = mode == RadarDisplayMode.ICON;
        boolean hybrid = mode == RadarDisplayMode.HYBRID;
        boolean playerListDown = hybrid && minecraft.options.keyPlayerList.isDown();
        boolean circular = config.minimap.shape == MinimapShape.CIRCLE;

        // 服务端下发的最大雷达距离优先于客户端配置（规格 §4.2）。
        int maxDistance = AtlasSession.maxRadarDistance();
        double maxDistanceSq = (double) maxDistance * maxDistance;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || entity.isRemoved()) {
                continue;
            }
            if (entity.distanceToSqr(player) > maxDistanceSq) {
                continue;
            }
            RadarCategory category = RadarCategory.classify(entity);
            if (category == null
                    || !RadarStyle.visible(config.radar.categories, category)) {
                continue;
            }
            double screenX = layout.worldToScreenX(entity.getX());
            double screenZ = layout.worldToScreenZ(entity.getZ());
            if (!layout.contains(screenX, screenZ, circular)) {
                continue;
            }
            if (category == RadarCategory.PLAYER && (showIcons || playerListDown)) {
                drawPlayerHead(graphics, entity, screenX, screenZ);
                continue;
            }
            int color = RadarStyle.color(config.radar.categories, category, entity);
            drawDot(graphics, screenX, screenZ, color);
        }
    }

    private static void drawDot(GuiGraphics graphics, double screenX, double screenZ, int color) {
        int x = (int) Math.round(screenX);
        int z = (int) Math.round(screenZ);
        graphics.fill(x - DOT_RADIUS, z - DOT_RADIUS, x + DOT_RADIUS, z + DOT_RADIUS, color);
        // 深色描边提高在浅色地形上的可辨识度（规格 §6.2 附加视觉反馈）。
        int outline = 0xC0000000;
        graphics.fill(x - DOT_RADIUS, z - DOT_RADIUS, x + DOT_RADIUS, z - DOT_RADIUS + 1, outline);
        graphics.fill(x - DOT_RADIUS, z + DOT_RADIUS - 1, x + DOT_RADIUS, z + DOT_RADIUS, outline);
    }

    /// 玩家皮肤头像：基础层 + 帽子层（规格 §6.3）。
    private static void drawPlayerHead(GuiGraphics graphics, Entity entity, double screenX,
            double screenZ) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        Identifier skinTexture = player.getSkin().body().texturePath();
        int x = (int) Math.round(screenX) - HEAD_SIZE / 2;
        int z = (int) Math.round(screenZ) - HEAD_SIZE / 2;
        // 玩家皮肤纹理中头部正面区域为 (8, 8, 8, 8)，帽子层为 (40, 8, 8, 8)。
        graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, x, z, 8.0F, 8.0F,
                HEAD_SIZE, HEAD_SIZE, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, x, z, 40.0F, 8.0F,
                HEAD_SIZE, HEAD_SIZE, 8, 8, 64, 64);
    }
}
