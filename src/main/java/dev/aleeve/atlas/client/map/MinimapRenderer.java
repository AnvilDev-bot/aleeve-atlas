package dev.aleeve.atlas.client.map;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.config.AtlasClientConfig;
import dev.aleeve.atlas.config.AtlasClientConfigs;
import dev.aleeve.atlas.config.ConfigBounds;
import dev.aleeve.atlas.config.MinimapShape;
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
    /// 1.0 倍缩放时的小地图边长（GUI 像素）。
    private static final int BASE_SIZE = 128;
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

        float scale = ConfigBounds.clamp(config.minimap.scale, 0.5F, 4.0F);
        int size = Math.round(BASE_SIZE * scale);
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int[] origin = resolveOrigin(config, size, screenWidth, screenHeight);
        int x = origin[0];
        int y = origin[1];

        boolean circular = config.minimap.shape == MinimapShape.CIRCLE;
        // 整张纹理铺满小地图方形：纹理中心即玩家所在位置。
        // 圆形形态由片元着色器丢弃圆外像素，圆外不渲染任何内容。
        RenderPipeline mapPipeline = circular
                ? AtlasRenderPipelines.MINIMAP_CIRCLE
                : RenderPipelines.GUI_TEXTURED;
        graphics.blit(mapPipeline, texture.textureId(), x, y, 0.0F, 0.0F,
                size, size, MapTexture.PIXELS, MapTexture.PIXELS, MapTexture.PIXELS,
                MapTexture.PIXELS);

        // 叠加层：边框与玩家朝向箭头由着色器绘制。
        LocalPlayer player = minecraft.player;
        float yaw = config.minimap.lockNorth ? 0.0F : player.getYRot();
        int tint = encodeOverlayTint(yaw, circular);
        graphics.blit(AtlasRenderPipelines.MINIMAP_OVERLAY, OVERLAY_TEXTURE, x, y, 0.0F, 0.0F,
                size, size, 2, 2, 2, 2, tint);

        drawInfo(graphics, config, minecraft, size, origin);
    }

    /// 把朝向角与形态编码进顶点色：R 通道为 yaw/(2π)，G 通道为圆形开关。
    private static int encodeOverlayTint(float yaw, boolean circular) {
        float normalized = (yaw % 360.0F + 360.0F) % 360.0F / 360.0F;
        int red = Math.round(normalized * 255.0F);
        int green = circular ? 255 : 0;
        return 0xFF000000 | (red << 16) | (green << 8);
    }

    private static int[] resolveOrigin(AtlasClientConfig config, int size, int screenWidth,
            int screenHeight) {
        int margin = 6;
        return switch (config.minimap.position) {
            case TOP_LEFT -> new int[] { margin, margin };
            case TOP_RIGHT -> new int[] { screenWidth - size - margin, margin };
            case BOTTOM_LEFT -> new int[] { margin, screenHeight - size - margin };
            case BOTTOM_RIGHT ->
                new int[] { screenWidth - size - margin, screenHeight - size - margin };
            case CUSTOM -> new int[] {
                    clamp(config.minimap.offsetX, 0, Math.max(0, screenWidth - size)),
                    clamp(config.minimap.offsetY, 0, Math.max(0, screenHeight - size)) };
        };
    }

    private static void drawInfo(GuiGraphics graphics, AtlasClientConfig config, Minecraft minecraft,
            int size, int[] origin) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int line = origin[1] + size + 4;
        int textX = origin[0];
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
