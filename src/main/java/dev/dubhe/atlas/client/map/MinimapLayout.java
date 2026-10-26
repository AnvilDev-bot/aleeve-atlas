package dev.dubhe.atlas.client.map;

import dev.dubhe.atlas.config.AtlasClientConfig;
import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.config.ConfigBounds;
import dev.dubhe.atlas.config.MinimapPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/// 小地图在屏幕上的布局。
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
    /// 避免极端缩放与自定义偏移下算出负坐标或溢出屏幕。
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
        double centerScreenX = this.x + this.size / 2.0;
        return centerScreenX + (worldX - this.centerWorldX) / COVERED_BLOCKS * this.size;
    }

    /// 世界坐标 → 小地图屏幕坐标（Z 轴）。
    public double worldToScreenZ(double worldZ) {
        double centerScreenZ = this.y + this.size / 2.0;
        return centerScreenZ + (worldZ - this.centerWorldZ) / COVERED_BLOCKS * this.size;
    }

    /// 判断屏幕坐标是否落在小地图范围内（圆形形态下按圆形判定）。
    public boolean contains(double screenX, double screenZ, boolean circular) {
        double centerX = this.x + this.size / 2.0;
        double centerZ = this.y + this.size / 2.0;
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        if (circular) {
            return Math.sqrt(dx * dx + dz * dz) <= this.size / 2.0;
        }
        return Math.abs(dx) <= this.size / 2.0 && Math.abs(dz) <= this.size / 2.0;
    }

    /// 按当前地图旋转角判断坐标是否落在可视范围内，供标记分类使用。
    public boolean containsRotated(double screenX, double screenZ, boolean circular) {
        return this.containsRotated(screenX, screenZ, circular, currentRotationDegrees());
    }

    /// 按指定地图旋转角判断坐标是否落在可视范围内。
    public boolean containsRotated(double screenX, double screenZ, boolean circular,
            double rotationDegrees) {
        double centerX = this.x + this.size / 2.0;
        double centerZ = this.y + this.size / 2.0;
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        if (circular) {
            return Math.sqrt(dx * dx + dz * dz) <= this.size / 2.0;
        }
        double radians = Math.toRadians(rotationDegrees);
        double rotatedX = dx * Math.cos(radians) - dz * Math.sin(radians);
        double rotatedZ = dx * Math.sin(radians) + dz * Math.cos(radians);
        return Math.abs(rotatedX) <= this.size / 2.0 && Math.abs(rotatedZ) <= this.size / 2.0;
    }

    /// 把标记中心限制在地图边界内，并为标记半径预留边距。
    ///
    /// 圆形地图按半径缩放，方形地图按旋转后的最大坐标轴等比缩放；
    /// 标记方向保持不变，旋转后的中心仍落在可视方框内。
    public double[] fitInside(double screenX, double screenZ, boolean circular, double margin) {
        return this.fitInside(screenX, screenZ, circular, margin, currentRotationDegrees());
    }

    /// 按指定地图旋转角约束标记中心，供方向与旋转边界测试使用。
    public double[] fitInside(double screenX, double screenZ, boolean circular, double margin,
            double rotationDegrees) {
        double centerX = this.x + this.size / 2.0;
        double centerZ = this.y + this.size / 2.0;
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        double limit = Math.max(0.0, this.size / 2.0 - margin);
        if (circular) {
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > limit && length > 0.0) {
                double scale = limit / length;
                dx *= scale;
                dz *= scale;
            }
        } else {
            double radians = Math.toRadians(rotationDegrees);
            double rotatedX = dx * Math.cos(radians) - dz * Math.sin(radians);
            double rotatedZ = dx * Math.sin(radians) + dz * Math.cos(radians);
            double maxAxis = Math.max(Math.abs(rotatedX), Math.abs(rotatedZ));
            if (maxAxis > limit && maxAxis > 0.0) {
                double scale = limit / maxAxis;
                dx *= scale;
                dz *= scale;
            }
        }
        return new double[] { centerX + dx, centerZ + dz };
    }

    private static double currentRotationDegrees() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return 0.0;
        }
        return MinimapRenderer.mapRotationDegrees(minecraft.player.getYRot(),
                AtlasClientConfigs.CONFIG.minimap.lockNorth);
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
