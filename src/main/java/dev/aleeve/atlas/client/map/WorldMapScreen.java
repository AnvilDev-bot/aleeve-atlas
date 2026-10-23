package dev.aleeve.atlas.client.map;

import dev.aleeve.atlas.client.AtlasKeyMappings;
import dev.aleeve.atlas.client.waypoint.ClientWaypoints;
import dev.aleeve.atlas.client.waypoint.WaypointRenderer;
import dev.aleeve.atlas.ping.ClientPings;
import dev.aleeve.atlas.ping.MapPingData;
import dev.aleeve.atlas.ping.PingRenderer;
import dev.aleeve.atlas.waypoint.Waypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import dev.aleeve.atlas.config.ConfigBounds;
import dev.aleeve.atlas.map.ChunkMapData;
import dev.aleeve.atlas.map.MapDimensionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.glfw.GLFW;

/// 全屏世界地图（规格 §2.3）。
///
/// 支持鼠标拖拽平移与滚轮无级缩放；未探索区块显示为黑色网格背景。
/// 双击创建路径点 / 发起标点将随 §5、§3.2 的功能接入。
public final class WorldMapScreen extends Screen {
    /// 每区块像素数的缩放范围。
    private static final float MIN_PIXELS_PER_CHUNK = 1.0F;
    private static final float MAX_PIXELS_PER_CHUNK = 12.0F;
    private static final int BACKGROUND_COLOR = 0xF0101010;
    private static final int PLAYER_COLOR = 0xFFFFFFFF;

    private final MapCanvas canvas;
    private final MapDimensionCache cache;
    /// 视野中心（世界方块坐标）。
    private double centerX;
    private double centerZ;
    private float pixelsPerChunk = 4.0F;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    public WorldMapScreen(MapCanvas canvas, MapDimensionCache cache, double centerX,
            double centerZ) {
        super(Component.translatable("aleeve_atlas.screen.world_map"));
        this.canvas = canvas;
        this.cache = cache;
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    @Override
    protected void init() {
        if (!canvas.ready()) {
            canvas.register();
        }
        refreshCanvas();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int viewWidth = width;
        int viewHeight = height;

        // 黑色背景代表未探索区域（规格 §2.3）。不调用 renderBackground：模糊背景每帧只允许一次。
        graphics.fill(0, 0, viewWidth, viewHeight, BACKGROUND_COLOR);

        if (canvas.ready()) {
            int canvasOriginPixelX = canvas.originChunkX() * ChunkMapData.SIZE;
            int canvasOriginPixelZ = canvas.originChunkZ() * ChunkMapData.SIZE;
            // 视口左上角对应的世界坐标：屏幕像素 / 每区块像素 = 世界方块偏移。
            double topLeftWorldX = centerX - (double) viewWidth / 2.0 / pixelsPerChunk;
            double topLeftWorldZ = centerZ - (double) viewHeight / 2.0 / pixelsPerChunk;

            // 画布在世界坐标中的覆盖范围，与视口求交，避免采样越界导致画面拉伸。
            double canvasLeft = canvasOriginPixelX;
            double canvasTop = canvasOriginPixelZ;
            double canvasRight = canvasOriginPixelX + MapCanvas.PIXELS;
            double canvasBottom = canvasOriginPixelZ + MapCanvas.PIXELS;
            double visibleLeft = Math.max(topLeftWorldX, canvasLeft);
            double visibleTop = Math.max(topLeftWorldZ, canvasTop);
            double visibleRight = Math.min(topLeftWorldX + (double) viewWidth / pixelsPerChunk,
                    canvasRight);
            double visibleBottom = Math.min(topLeftWorldZ + (double) viewHeight / pixelsPerChunk,
                    canvasBottom);
            if (visibleRight > visibleLeft && visibleBottom > visibleTop) {
                int screenX = (int) Math.round((visibleLeft - topLeftWorldX) * pixelsPerChunk);
                int screenY = (int) Math.round((visibleTop - topLeftWorldZ) * pixelsPerChunk);
                int screenWidth = (int) Math.round((visibleRight - visibleLeft) * pixelsPerChunk);
                int screenHeight = (int) Math.round((visibleBottom - visibleTop) * pixelsPerChunk);
                int u = (int) Math.round(visibleLeft - canvasLeft);
                int v = (int) Math.round(visibleTop - canvasTop);
                int uWidth = (int) Math.round(visibleRight - visibleLeft);
                int vHeight = (int) Math.round(visibleBottom - visibleTop);
                graphics.blit(RenderPipelines.GUI_TEXTURED, canvas.textureId(), screenX, screenY,
                        u, v, screenWidth, screenHeight, uWidth, vHeight, MapCanvas.PIXELS,
                        MapCanvas.PIXELS);
            }
        }

        drawPlayerMarker(graphics);
        drawWaypoints(graphics);
        drawPings(graphics);
        drawOverlayText(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /// 世界地图上的路径点标记（规格 §5.1）。
    private void drawWaypoints(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (var waypoint : ClientWaypoints.waypointsFor(minecraft.level.dimension())) {
            double screenX = width / 2.0
                    + (waypoint.pos().getX() + 0.5 - centerX) * pixelsPerChunk;
            double screenY = height / 2.0
                    + (waypoint.pos().getZ() + 0.5 - centerZ) * pixelsPerChunk;
            if (screenX < 0 || screenX > width || screenY < 0 || screenY > height) {
                continue;
            }
            WaypointRenderer.renderOnWorldMap(graphics, screenX, screenY, waypoint.colorArgb());
        }
    }

    /// 世界地图上的临时标点波纹（规格 §3.2.2）。
    private void drawPings(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        String dimension = MapPingData.dimensionId(minecraft.level.dimension());
        for (MapPingData ping : PingRenderer.activePings()) {
            if (!ping.dimensionId().equals(dimension)) {
                continue;
            }
            double screenX = width / 2.0 + (ping.position().x - centerX) * pixelsPerChunk;
            double screenY = height / 2.0 + (ping.position().z - centerZ) * pixelsPerChunk;
            if (screenX < 0 || screenX > width || screenY < 0 || screenY > height) {
                continue;
            }
            PingRenderer.renderOnWorldMap(graphics, screenX, screenY, ping);
        }
    }

    /// 屏幕坐标 → 世界坐标（X 轴）。
    private double screenToWorldX(double screenX) {
        return centerX + (screenX - width / 2.0) / pixelsPerChunk;
    }

    /// 屏幕坐标 → 世界坐标（Z 轴）。
    private double screenToWorldZ(double screenY) {
        return centerZ + (screenY - height / 2.0) / pixelsPerChunk;
    }

    /// 背景由 {@link #render} 自行填充，避免父类每帧二次模糊（1.21.11 限制）。
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /// 玩家在小地图上的位置标记。
    private void drawPlayerMarker(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        double screenX = width / 2.0 + (minecraft.player.getX() - centerX) * pixelsPerChunk;
        double screenY = height / 2.0 + (minecraft.player.getZ() - centerZ) * pixelsPerChunk;
        if (screenX < 0 || screenX > width || screenY < 0 || screenY > height) {
            return;
        }
        int x = (int) Math.round(screenX);
        int y = (int) Math.round(screenY);
        graphics.fill(x - 3, y - 3, x + 4, y + 4, PLAYER_COLOR);
    }

    private void drawOverlayText(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Component center = Component.literal("X %.0f  Z %.0f  (%.1fx)"
                .formatted(centerX, centerZ, pixelsPerChunk));
        graphics.drawString(minecraft.font, center, 6, 6, 0xFFFFFFFF);
        Component hint = Component.translatable("aleeve_atlas.screen.world_map.hint");
        graphics.drawString(minecraft.font, hint, 6, height - 14, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 双击左键在地图位置创建路径点（规格 §2.3 / §8）。
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && doubleClick) {
            createWaypointAt(event.x(), event.y());
            dragging = false;
            return true;
        }
        // 右键在该位置发起临时标点，并通知外部模组（规格 §2.3 / §3.2.2）。
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            createPingAt(event.x(), event.y());
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = true;
            lastMouseX = event.x();
            lastMouseY = event.y();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /// 在世界地图指定位置创建路径点。
    private void createWaypointAt(double screenX, double screenY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        double worldX = screenToWorldX(screenX);
        double worldZ = screenToWorldZ(screenY);
        // 远距离位置取下限高度，近似地表的 Y 值由地图缓存提供。
        int y = surfaceYAt(worldX, worldZ);
        Waypoint waypoint = Waypoint.create(
                Component.translatable("aleeve_atlas.waypoint.default_name").getString(),
                new BlockPos((int) Math.floor(worldX), y, (int) Math.floor(worldZ)),
                minecraft.level.dimension(), 0xFF55FFFF);
        ClientWaypoints.add(waypoint);
        minecraft.player.displayClientMessage(
                Component.translatable("aleeve_atlas.message.waypoint_created",
                        waypoint.pos().getX(), waypoint.pos().getY(), waypoint.pos().getZ())
                        .withStyle(net.minecraft.ChatFormatting.GREEN),
                true);
    }

    /// 在世界地图指定位置发起临时标点。
    private void createPingAt(double screenX, double screenY) {
        double worldX = screenToWorldX(screenX);
        double worldZ = screenToWorldZ(screenY);
        int y = surfaceYAt(worldX, worldZ);
        ClientPings.createLocal(new Vec3(worldX, y, worldZ), MapPingData.PingIcon.NORMAL);
    }

    /// 由地图缓存推断地表高度；无数据时回退到海平面以上默认值。
    private int surfaceYAt(double worldX, double worldZ) {
        ChunkPos pos = new ChunkPos((int) Math.floor(worldX) >> 4, (int) Math.floor(worldZ) >> 4);
        var data = cache == null ? null : cache.get(pos.x, pos.z).orElse(null);
        if (data == null) {
            return 64;
        }
        int localX = (int) Math.floor(worldX) & 15;
        int localZ = (int) Math.floor(worldZ) & 15;
        return data.heightAt(localX, localZ);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            // 事件携带的是从按下点起的总位移，因此用相邻事件差得到帧增量。
            centerX -= (event.x() - lastMouseX) / pixelsPerChunk;
            centerZ -= (event.y() - lastMouseY) / pixelsPerChunk;
            lastMouseX = event.x();
            lastMouseY = event.y();
            refreshCanvas();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            float previous = pixelsPerChunk;
            pixelsPerChunk = ConfigBounds.clamp(
                    (float) (pixelsPerChunk * (scrollY > 0 ? 1.25 : 0.8)),
                    MIN_PIXELS_PER_CHUNK, MAX_PIXELS_PER_CHUNK);
            if (previous != pixelsPerChunk) {
                refreshCanvas();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // ESC 与呼出键均可关闭世界地图（规格 §8）。
        if (event.key() == GLFW.GLFW_KEY_ESCAPE
                || AtlasKeyMappings.OPEN_WORLD_MAP.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /// 按当前视野中心重绘画布。
    private void refreshCanvas() {
        int centerChunkX = (int) Math.floor(centerX / ChunkMapData.SIZE);
        int centerChunkZ = (int) Math.floor(centerZ / ChunkMapData.SIZE);
        canvas.setCenter(centerChunkX, centerChunkZ, cache);
    }
}
