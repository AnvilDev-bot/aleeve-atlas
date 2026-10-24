package dev.dubhe.atlas.client.map;

import dev.dubhe.atlas.config.ConfigBounds;

import dev.dubhe.atlas.config.AtlasClientConfig;
import dev.dubhe.atlas.config.MinimapPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/// 小地图在屏幕上的布局（规格 §2.2）。
///
/// 同时供 HUD 渲染与实体雷达使用，保证地图像素与实体标记共用同一套坐标映射。
public record MinimapLayout(int x, int y, int size, double centerWorldX, double centerWorldZ) {
    /// 1.0 倍缩放时的小地图边长（GUI 像素）。
    public static final int BASE_SIZE = 128;
    /// 小地图与屏幕边缘的间距（GUI 像素）。
    static final int MARGIN = 6;
    /// 小地图覆盖的方块宽度。
    public static final int COVERED_BLOCKS = 16 * (ClientMapManager.VIEW_CHUNK_RADIUS * 2 + 1);

    /// 根据配置与屏幕尺寸计算布局；玩家为空时返回 {@code null}。
    public static MinimapLayout of(AtlasClientConfig config, Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return null;
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int[] box = computeBox(config.minimap.position, config.minimap.scale,
                config.minimap.offsetX, config.minimap.offsetY, screenWidth, screenHeight);
        return new MinimapLayout(box[0], box[1], box[2], player.getX(), player.getZ());
    }

    /// 计算小地图的尺寸与左上角坐标：`[x, y, size]`。
    ///
    /// 尺寸受限不超过屏幕短边可用空间，原点统一钳制到屏幕内，
    /// 避免极端缩放与自定义偏移下算出负坐标或溢出屏幕（规格 §2.2）。
    static int[] computeBox(MinimapPosition position, double configuredScale, int offsetX,
            int offsetY, int screenWidth, int screenHeight) {
        float scale = (float) ConfigBounds.clamp(configuredScale, 0.5, 4.0);
        int margin = MARGIN;
        int maxSize = Math.max(BASE_SIZE / 2, Math.min(screenWidth, screenHeight) - margin * 2);
        int size = Math.min(Math.round(BASE_SIZE * scale), maxSize);
        int[] origin = switch (position) {
            case TOP_LEFT -> new int[] { margin, margin };
            case TOP_RIGHT -> new int[] { screenWidth - size - margin, margin };
            case BOTTOM_LEFT -> new int[] { margin, screenHeight - size - margin };
            case BOTTOM_RIGHT ->
                new int[] { screenWidth - size - margin, screenHeight - size - margin };
            case CUSTOM -> new int[] {
                    clamp(offsetX, 0, Math.max(0, screenWidth - size)),
                    clamp(offsetY, 0, Math.max(0, screenHeight - size)) };
        };
        origin[0] = clamp(origin[0], 0, Math.max(0, screenWidth - size));
        origin[1] = clamp(origin[1], 0, Math.max(0, screenHeight - size));
        return new int[] { origin[0], origin[1], size };
    }

    /// 世界坐标 → 小地图屏幕坐标（X 轴）。
    public double worldToScreenX(double worldX) {
        double centerScreenX = x + size / 2.0;
        return centerScreenX + (worldX - centerWorldX) / COVERED_BLOCKS * size;
    }

    /// 世界坐标 → 小地图屏幕坐标（Z 轴）。
    public double worldToScreenZ(double worldZ) {
        double centerScreenZ = y + size / 2.0;
        return centerScreenZ + (worldZ - centerWorldZ) / COVERED_BLOCKS * size;
    }

    /// 判断屏幕坐标是否落在小地图范围内（圆形形态下按圆形判定）。
    public boolean contains(double screenX, double screenZ, boolean circular) {
        double centerX = x + size / 2.0;
        double centerZ = y + size / 2.0;
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        if (circular) {
            return Math.sqrt(dx * dx + dz * dz) <= size / 2.0;
        }
        return Math.abs(dx) <= size / 2.0 && Math.abs(dz) <= size / 2.0;
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
