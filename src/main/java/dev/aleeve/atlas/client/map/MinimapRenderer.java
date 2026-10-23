package dev.aleeve.atlas.client.map;

import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.config.AtlasClientConfig;
import dev.aleeve.atlas.config.AtlasClientConfigs;
import dev.aleeve.atlas.config.ConfigBounds;
import dev.aleeve.atlas.map.ChunkMapData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// 小地图 HUD 渲染（规格 §2.2）。
///
/// 从 {@link MapTexture} 的纹理中截取以玩家为中心的方形区域绘制；形态、位置、缩放取自客户端配置。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MinimapRenderer {
    private static final int BASE_SIZE = 128;

    private MinimapRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        AtlasClientConfig config = AtlasClientConfigs.CONFIG;
        Minecraft minecraft = Minecraft.getInstance();
        if (config == null || minecraft.player == null || minecraft.level == null) {
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
        int margin = 6;
        int[] origin = resolveOrigin(config, size, screenWidth, screenHeight, margin);

        int centerX = origin[0] + size / 2;
        int centerZ = origin[1] + size / 2;
        float pixelsPerBlock = (float) size / (ChunkMapData.SIZE * (ClientMapManager.VIEW_CHUNK_RADIUS * 2 + 1));
        float worldBlocks = size / pixelsPerBlock;

        LocalPlayer player = minecraft.player;
        double relativeX = player.getX() - (texture.originChunkX() * ChunkMapData.SIZE);
        double relativeZ = player.getZ() - (texture.originChunkZ() * ChunkMapData.SIZE);
        int textureX = (int) Math.round(relativeX - worldBlocks / 2.0);
        int textureZ = (int) Math.round(relativeZ - worldBlocks / 2.0);
        int textureSize = Math.round(worldBlocks);
        textureX = clamp(textureX, 0, MapTexture.PIXELS - textureSize);
        textureZ = clamp(textureZ, 0, MapTexture.PIXELS - textureSize);

        graphics.blit(texture.textureId(), origin[0], origin[1], size, size,
                textureX, textureZ, textureSize, textureSize);

        drawFrame(graphics, ours(origin[0], origin[1], size));
        drawPlayerArrow(graphics, centerX, centerZ, config.minimap.lockNorth ? 0.0F : player.getYRot());
        drawInfo(graphics, config, minecraft, size, origin);
    }

    private static int[] resolveOrigin(AtlasClientConfig config, int size, int screenWidth,
            int screenHeight, int margin) {
        return switch (config.minimap.position) {
            case TOP_LEFT -> new int[] { margin, margin };
            case TOP_RIGHT -> new int[] { screenWidth - size - margin, margin };
            case BOTTOM_LEFT -> new int[] { margin, screenHeight - size - margin };
            case BOTTOM_RIGHT -> new int[] { screenWidth - size - margin, screenHeight - size - margin };
            case CUSTOM -> new int[] {
                    clamp(config.minimap.offsetX, 0, Math.max(0, screenWidth - size)),
                    clamp(config.minimap.offsetY, 0, Math.max(0, screenHeight - size)) };
        };
    }

    private static int[] ours(int x, int y, int size) {
        return new int[] { x, y, x + size, y + size };
    }

    private static void drawFrame(GuiGraphics graphics, int[] box) {
        int border = 0xFF101010;
        graphics.fill(box[0] - 1, box[1] - 1, box[2] + 1, box[1], border);
        graphics.fill(box[0] - 1, box[3], box[2] + 1, box[3] + 1, border);
        graphics.fill(box[0] - 1, box[1], box[0], box[3], border);
        graphics.fill(box[2], box[1], box[2] + 1, box[3], border);
    }

    /// 玩家朝向箭头：以三角近似绘制，北向上时固定指向上方。
    private static void drawPlayerArrow(GuiGraphics graphics, int centerX, int centerZ, float yaw) {
        double radians = Math.toRadians(-yaw);
        float length = 5.0F;
        int tipX = centerX + (int) Math.round(Math.sin(radians) * length);
        int tipY = centerZ - (int) Math.round(Math.cos(radians) * length);
        int leftX = centerX + (int) Math.round(Math.sin(radians + 2.5) * (length * 0.7));
        int leftY = centerZ - (int) Math.round(Math.cos(radians + 2.5) * (length * 0.7));
        int rightX = centerX + (int) Math.round(Math.sin(radians - 2.5) * (length * 0.7));
        int rightY = centerZ - (int) Math.round(Math.cos(radians - 2.5) * (length * 0.7));
        int color = 0xFFFFFFFF;
        graphics.fill(tipX, tipY, tipX + 1, tipY + 1, color);
        graphics.fill(leftX, leftY, leftX + 1, leftY + 1, color);
        graphics.fill(rightX, rightY, rightX + 1, rightY + 1, color);
    }

    private static void drawInfo(GuiGraphics graphics, AtlasClientConfig config, Minecraft minecraft,
            int size, int[] origin) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int line = origin[1] + size + 4;
        int color = 0xFFFFFFFF;
        int textX = origin[0];
        if (config.minimap.showCoords) {
            Component text = Component.literal(
                    "%d, %d, %d".formatted((int) player.getX(), (int) player.getY(), (int) player.getZ()));
            graphics.drawString(minecraft.font, text, textX, line, color);
            line += 10;
        }
        if (config.minimap.showBiome && minecraft.level != null) {
            var biome = minecraft.level.getBiome(player.blockPosition());
            Component name = biome.unwrapKey()
                    .<Component>map(key -> Component.translatable(
                            "biome." + key.identifier().toLanguageKey()))
                    .orElseGet(() -> Component.literal("?"));
            graphics.drawString(minecraft.font, name, textX, line, color);
            line += 10;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
