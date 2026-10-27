package dev.dubhe.map.client.map;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.profiling.AtlasProfiling;
import dev.dubhe.map.client.radar.RadarRenderer;
import dev.dubhe.map.client.territory.TerritoryRenderer;
import dev.dubhe.map.client.waypoint.WaypointRenderer;
import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.MinimapPosition;
import dev.dubhe.map.config.MinimapShape;
import dev.dubhe.map.ping.PingRenderer;
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

/// 小地图 HUD 渲染。
///
/// 绘制分两步：先铺整张地图纹理（玩家恒在纹理中心），再叠加
/// {@link AtlasRenderPipelines#MINIMAP_OVERLAY} 着色器层，由片元着色器解析式地
/// 画出圆形遮罩、边框与水滴形玩家指示器。
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
        final long startedAt = System.nanoTime();

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
        // 北向锁定：地图固定北朝上、指示器随视角转；
        // 视角随动：地图绕中心旋转到玩家朝向、指示器固定朝上。
        float rotationDegrees = mapRotationDegrees(player.getYRot(), config.minimap.lockNorth);
        float centerX = x + size / 2.0F;
        float centerY = y + size / 2.0F;

        final RenderPipeline mapPipeline = circular
                ? AtlasRenderPipelines.MINIMAP_CIRCLE
                : RenderPipelines.GUI_TEXTURED;

        // 方形形态旋转后四角会越出小地图边界，需用裁剪区限制。
        // 裁剪必须在旋转前启用：enableScissor 按当前 pose 变换矩形，
        // 旋转后传入的轴对齐矩形会被变换成负宽高或错位区域。
        boolean scissored = !circular;
        if (scissored) {
            graphics.enableScissor(x, y, x + size, y + size);
        }
        graphics.pose().pushMatrix();
        if (rotationDegrees != 0.0F) {
            graphics.pose().translate(centerX, centerY);
            graphics.pose().rotate((float) Math.toRadians(rotationDegrees));
            graphics.pose().translate(-centerX, -centerY);
        }

        // 方形按对角线尺寸绘制后由屏幕方形裁剪，圆形保持原可见范围。
        int drawSize = circular ? size : MinimapLayout.squareDrawSize(size);
        int drawX = circular ? x : Math.round(centerX - drawSize / 2.0F);
        int drawY = circular ? y : Math.round(centerY - drawSize / 2.0F);
        int uvSize = circular ? (int) layout.coveredBlocks()
                : MinimapLayout.squareDrawSize((int) layout.coveredBlocks());
        float uvStart = (MapTexture.PIXELS - uvSize) / 2.0F;
        int mapTint = circular ? encodeCircleTint(uvSize) : 0xFFFFFFFF;
        graphics.blit(mapPipeline, texture.textureId(), drawX, drawY, uvStart, uvStart,
                drawSize, drawSize, uvSize, uvSize, MapTexture.PIXELS, MapTexture.PIXELS, mapTint);
        TerritoryRenderer.renderOnMinimap(graphics, layout, circular);
        graphics.pose().popMatrix();
        if (scissored) {
            graphics.disableScissor();
        }

        // 标记位置随地图旋转，图标与文字在屏幕坐标中保持正向。
        if (scissored) {
            graphics.enableScissor(x, y, x + size, y + size);
        }
        WaypointRenderer.renderOnMinimap(graphics, layout, circular, rotationDegrees);
        PingRenderer.renderOnMinimap(graphics, layout, circular, rotationDegrees);
        RadarRenderer.render(graphics, config, layout, minecraft, rotationDegrees);
        if (scissored) {
            graphics.disableScissor();
        }

        float arrowYaw = arrowYawDegrees(player.getYRot(), config.minimap.lockNorth);
        int tint = encodeOverlayTint(arrowYaw, circular, config.minimap.lockNorth);
        graphics.blit(AtlasRenderPipelines.MINIMAP_OVERLAY, OVERLAY_TEXTURE, x, y, 0.0F, 0.0F,
                size, size, 2, 2, 2, 2, tint);

        CompassRenderer.renderOnMinimap(graphics, layout, rotationDegrees);
        TerritoryRenderer.renderLabelsOnMinimap(graphics, layout, circular, rotationDegrees);
        if (!MinimapLayout.focused(minecraft)) {
            drawInfo(graphics, config, minecraft, size, x, y);
        }
        // 记录渲染耗时，供性能验收读取。
        AtlasProfiling.recordMinimap(
                System.nanoTime() - startedAt);
    }

    /// 地图旋转角：视角随动时把玩家朝向旋转到屏幕上方。
    /// Minecraft yaw=0 朝南，因此需要包含 180° 的南北基准。
    static float mapRotationDegrees(float playerYaw, boolean lockNorth) {
        return lockNorth ? 0.0F : 180.0F - playerYaw;
    }

    /// 玩家指示器旋转角：固定地图方向时随玩家朝向旋转，视角随动时固定朝上。
    static float arrowYawDegrees(float playerYaw, boolean lockNorth) {
        return lockNorth ? playerYaw : 0.0F;
    }

    /// 圆形遮罩使用顶点色 R 传递纹理裁剪比例的倒数，还原屏幕局部坐标。
    private static int encodeCircleTint(int uvSize) {
        float uvScale = MapTexture.PIXELS / (float) uvSize;
        int red = Math.round(uvScale / 8.0F * 255.0F);
        return 0xFF00FFFF | (red << 16);
    }

    /// 把朝向角与显示模式编码进顶点色：R 为 yaw/(2π)，G 为圆形开关，B 为固定北向。
    private static int encodeOverlayTint(float yaw, boolean circular, boolean lockNorth) {
        float normalized = (yaw % 360.0F + 360.0F) % 360.0F / 360.0F;
        int red = Math.round(normalized * 255.0F);
        int green = circular ? 255 : 0;
        int blue = lockNorth ? 255 : 0;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /// 小地图是否贴屏幕底部：此时信息覆层改画在其上方。
    ///
    /// 按小地图底边到屏幕底部的距离判断，兼容四角预设与自定义偏移。
    private static boolean isBottomPosition(MinimapPosition position, int y, int size,
            Minecraft minecraft) {
        if (position == MinimapPosition.BOTTOM_LEFT || position == MinimapPosition.BOTTOM_RIGHT) {
            return true;
        }
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        return y + size + 4 + 10 > screenHeight;
    }

    /// 当前配置下将绘制多少行信息。
    private static int countInfoLines(AtlasClientConfig config, Minecraft minecraft) {
        int lines = 0;
        if (config.minimap.showCoords) {
            lines++;
        }
        if (config.minimap.showBiome && minecraft.level != null) {
            lines++;
        }
        if (config.minimap.showLight && minecraft.level != null) {
            lines++;
        }
        if (config.minimap.showTime && minecraft.level != null) {
            lines++;
        }
        return lines;
    }

    private static void drawInfo(GuiGraphics graphics, AtlasClientConfig config, Minecraft minecraft,
            int size, int x, int y) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        // 小地图贴屏幕底部时把信息绘制在其上方，否则文字会超出屏幕下边界。
        boolean above = isBottomPosition(config.minimap.position, y, size, minecraft);
        int lineCount = countInfoLines(config, minecraft);
        int line = above
                ? y - 4 - lineCount * 10
                : y + size + 4;
        int textX = x + size / 2;
        if (config.minimap.showCoords) {
            Component text = Component.translatable("aleeve_atlas.hud.coords",
                    (int) Math.floor(player.getX()),
                    (int) Math.floor(player.getY()),
                    (int) Math.floor(player.getZ()));
            graphics.drawCenteredString(minecraft.font, text, textX, line, TEXT_COLOR);
            line += 10;
        }
        if (config.minimap.showBiome && minecraft.level != null) {
            var biome = minecraft.level.getBiome(player.blockPosition());
            Component name = biome.unwrapKey()
                    .<Component>map(key -> Component.translatable(
                            "biome." + key.identifier().toLanguageKey()))
                    .orElseGet(() -> Component.translatable(
                            "aleeve_atlas.hud.biome_unknown"));
            graphics.drawCenteredString(minecraft.font, name, textX, line, TEXT_COLOR);
            line += 10;
        }
        if (config.minimap.showLight && minecraft.level != null) {
            int light = minecraft.level.getMaxLocalRawBrightness(player.blockPosition());
            graphics.drawCenteredString(minecraft.font,
                    Component.translatable("aleeve_atlas.hud.light", light), textX, line,
                    TEXT_COLOR);
            line += 10;
        }
        if (config.minimap.showTime && minecraft.level != null) {
            long dayTime = minecraft.level.getDayTime() % 24000L;
            int hours = (int) ((dayTime / 1000L + 6L) % 24L);
            int minutes = (int) ((dayTime % 1000L) * 60L / 1000L);
            graphics.drawCenteredString(minecraft.font,
                    Component.translatable("aleeve_atlas.hud.time",
                            "%02d".formatted(hours), "%02d".formatted(minutes)),
                    textX, line, TEXT_COLOR);
        }
    }
}
