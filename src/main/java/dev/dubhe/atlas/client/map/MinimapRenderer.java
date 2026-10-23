package dev.dubhe.atlas.client.map;

import dev.dubhe.atlas.client.profiling.AtlasProfiling;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.client.radar.RadarRenderer;
import dev.dubhe.atlas.client.territory.TerritoryRenderer;
import dev.dubhe.atlas.client.waypoint.WaypointRenderer;
import dev.dubhe.atlas.ping.PingRenderer;
import dev.dubhe.atlas.config.AtlasClientConfig;
import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.config.MinimapShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// 小地图 HUD 渲染（规格 §2.2）。
///
/// 绘制分两步：先铺整张地图纹理（玩家恒在纹理中心），再叠加
/// {@link AtlasRenderPipelines#MINIMAP_OVERLAY} 着色器层，由片元着色器解析式地
/// 画出圆形遮罩、边框与玩家朝向箭头。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MinimapRenderer {
    private static final Identifier OVERLAY_TEXTURE = AleeveAtlas.id("textures/gui/white.png");
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
        long startedAt = System.nanoTime();

        MinimapLayout layout = MinimapLayout.of(config, minecraft);
        if (layout == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int x = layout.x();
        int y = layout.y();
        int size = layout.size();

        boolean circular = config.minimap.shape == MinimapShape.CIRCLE;
        LocalPlayer player = minecraft.player;
        // 北向锁定：地图固定北朝上、箭头随视角转；
        // 视角随动：地图绕中心旋转到玩家朝向、箭头固定朝上（规格 §2.2）。
        float rotationDegrees = config.minimap.lockNorth ? 0.0F : -player.getYRot();
        float centerX = x + size / 2.0F;
        float centerY = y + size / 2.0F;

        RenderPipeline mapPipeline = circular
                ? AtlasRenderPipelines.MINIMAP_CIRCLE
                : RenderPipelines.GUI_TEXTURED;

        graphics.pose().pushMatrix();
        if (rotationDegrees != 0.0F) {
            graphics.pose().translate(centerX, centerY);
            graphics.pose().rotate((float) Math.toRadians(rotationDegrees));
            graphics.pose().translate(-centerX, -centerY);
        }

        // 整张纹理铺满小地图方形：纹理中心即玩家所在位置（偏移 <1 像素）。
        graphics.blit(mapPipeline, texture.textureId(), x, y, 0.0F, 0.0F,
                size, size, MapTexture.PIXELS, MapTexture.PIXELS, MapTexture.PIXELS,
                MapTexture.PIXELS);

        // 领地覆层在地图之上、路径点/雷达之下（规格 §7.3）。
        TerritoryRenderer.renderOnMinimap(graphics, layout, circular);

        // 路径点与实体雷达标记绘制在地图之上、边框与箭头之下（规格 §5.1 / §6）。
        WaypointRenderer.renderOnMinimap(graphics, layout, circular);
        PingRenderer.renderOnMinimap(graphics, layout, circular);
        RadarRenderer.render(graphics, config, layout, minecraft);
        graphics.pose().popMatrix();

        // 叠加层（边框与箭头）不参与地图旋转：箭头固定朝上表示玩家前进方向。
        int tint = encodeOverlayTint(0.0F, circular);
        graphics.blit(AtlasRenderPipelines.MINIMAP_OVERLAY, OVERLAY_TEXTURE, x, y, 0.0F, 0.0F,
                size, size, 2, 2, 2, 2, tint);

        TerritoryRenderer.renderLabelsOnMinimap(graphics, layout, circular);
        drawInfo(graphics, config, minecraft, size, x, y);
        // 记录渲染耗时，供 §12.1 性能验收读取。
        AtlasProfiling.recordMinimap(
                System.nanoTime() - startedAt);
    }

    /// 把朝向角与形态编码进顶点色：R 通道为 yaw/(2π)，G 通道为圆形开关。
    private static int encodeOverlayTint(float yaw, boolean circular) {
        float normalized = (yaw % 360.0F + 360.0F) % 360.0F / 360.0F;
        int red = Math.round(normalized * 255.0F);
        int green = circular ? 255 : 0;
        return 0xFF000000 | (red << 16) | (green << 8);
    }

    private static void drawInfo(GuiGraphics graphics, AtlasClientConfig config, Minecraft minecraft,
            int size, int x, int y) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int line = y + size + 4;
        int textX = x;
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
                    Component.translatable("aleeve_atlas.hud.light", light), textX, line,
                    TEXT_COLOR);
            line += 10;
        }
        if (config.minimap.showTime && minecraft.level != null) {
            long dayTime = minecraft.level.getDayTime() % 24000L;
            int hours = (int) ((dayTime / 1000L + 6L) % 24L);
            int minutes = (int) ((dayTime % 1000L) * 60L / 1000L);
            graphics.drawString(minecraft.font,
                    Component.literal("%02d:%02d".formatted(hours, minutes)), textX, line,
                    TEXT_COLOR);
        }
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
