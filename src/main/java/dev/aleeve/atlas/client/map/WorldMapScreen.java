package dev.aleeve.atlas.client.map;

import dev.aleeve.atlas.client.AtlasKeyMappings;
import dev.aleeve.atlas.api.territory.AtlasTerritory;
import dev.aleeve.atlas.api.territory.TerritoryGroup;
import dev.aleeve.atlas.territory.TerritoryRegistry;
import dev.aleeve.atlas.territory.TerritoryRuns;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import dev.aleeve.atlas.client.waypoint.ClientWaypoints;
import dev.aleeve.atlas.client.waypoint.WaypointListScreen;
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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
    /// 低于该区块像素尺寸时隐藏领地名称标签（规格 §7.5 / AC-10）。
    private static final float MIN_PIXELS_FOR_LABEL = 3.0F;

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
        addTeamButtons();
    }

    /// 队伍相关按钮：独立模式下置灰并给出说明（规格 §1 / AC-1）。
    private void addTeamButtons() {
        boolean synchronizedMode = dev.aleeve.atlas.AtlasSession.synchronizedMode();
        Component label = Component.translatable("aleeve_atlas.screen.world_map.share_team");
        int buttonWidth = 120;
        int buttonHeight = 20;
        int x = width - buttonWidth - 6;
        int y = 6;
        Button share = Button.builder(label, button -> shareNearestWaypoint())
                .bounds(x, y, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(synchronizedMode
                        ? Component.translatable("aleeve_atlas.screen.world_map.share_team.tip")
                        : Component.translatable(
                                "aleeve_atlas.message.server_unsupported")))
                .build();
        share.active = synchronizedMode;
        addRenderableWidget(share);

        // 路径点管理列表入口（规格 §5.2），与分享按钮保持 6px 间隔。
        addRenderableWidget(Button.builder(
                        Component.translatable("aleeve_atlas.screen.waypoint_list"),
                        button -> Minecraft.getInstance().setScreen(new WaypointListScreen()))
                .bounds(x - 126, y, 120, 20).build());
    }

    /// 把视野中心最近的路径点分享给队伍（规格 §5.2 手动同步）。
    private void shareNearestWaypoint() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Waypoint nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Waypoint waypoint : ClientWaypoints.waypointsFor(minecraft.level.dimension())) {
            double dx = waypoint.pos().getX() + 0.5 - centerX;
            double dz = waypoint.pos().getZ() + 0.5 - centerZ;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = waypoint;
            }
        }
        if (nearest == null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("aleeve_atlas.message.waypoint_none"), true);
            return;
        }
        ClientWaypoints.share(nearest);
        minecraft.player.displayClientMessage(
                Component.translatable("aleeve_atlas.message.waypoint_shared_team",
                        nearest.name()), true);
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

        drawTerritories(graphics, mouseX, mouseY);
        drawWaypoints(graphics);
        drawPings(graphics);
        drawPlayerMarker(graphics);
        drawOverlayText(graphics);
        // 组件渲染后再绘制领地标签，确保标签位于最上层（§7.5）。
        drawTerritoryLabels(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /// 领地覆层（规格 §7.3 / §7.5 / §7.7 / AC-10）。
    ///
    /// 缩小到区块像素小于标签宽度时自动隐藏名称；悬停任一区块高亮其边框并展示信息卡片。
    private void drawTerritories(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        TerritoryRegistry registry = AtlasTerritory.clientRegistry();
        List<TerritoryGroup> groups = registry.orderedForDimension(dimension);
        if (groups.isEmpty()) {
            return;
        }
        ChunkPos hovered = chunkAtScreen(mouseX, mouseY);
        List<TerritoryGroup> hoveredGroups = hovered == null ? List.of()
                : registry.hitAt(dimension, hovered);

        for (TerritoryGroup group : groups) {
            int fill = withOpacity(group.themeColorRgb(), group.fillOpacity());
            boolean highlighted = hoveredGroups.contains(group);
            int border = highlighted ? 0xFFFFFFFF : group.borderStyle().colorArgb();
            // 填充：按横向连续路段批量绘制，减少绘制调用（§12.1）。
            for (TerritoryRuns.Run run : TerritoryRuns.of(group.chunks())) {
                int[] first = chunkScreenRect(new ChunkPos(run.xStart(), run.z()));
                if (first == null) {
                    continue;
                }
                int x = first[0];
                int y = first[1];
                int width = first[2] * run.length();
                int height = first[3];
                // 裁剪到视口内，避免超出屏幕的无效填充。
                int left = Math.max(0, x);
                int top = Math.max(0, y);
                int right = Math.min(width, x + width);
                int bottom = Math.min(height, y + height);
                if (right > left && bottom > top) {
                    graphics.fill(left, top, right, bottom, fill);
                }
            }
            // 边框：仅在与非领地交界处逐区块描边（§7.3）。
            for (ChunkPos chunk : group.chunks()) {
                int[] rect = chunkScreenRect(chunk);
                if (rect == null) {
                    continue;
                }
                drawChunkOutline(graphics, group, chunk, rect, border);
            }
        }

        // 悬停信息卡片按 zOrder 从高到低合并展示（§7.7 第 3 条）。
        if (!hoveredGroups.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (TerritoryGroup group : hoveredGroups) {
                lines.add(group.displayName().copy().withStyle(ChatFormatting.AQUA));
                lines.addAll(group.tooltip());
            }
            drawTooltipLines(graphics, minecraft, lines, mouseX, mouseY);
        }
    }

    /// 绘制简易信息卡片（避免依赖 1.21.11 的 tooltip 组件装配）。
    private void drawTooltipLines(GuiGraphics graphics, Minecraft minecraft,
            List<Component> lines, int mouseX, int mouseY) {
        int lineHeight = 10;
        int padding = 3;
        int maxWidth = 0;
        for (Component line : lines) {
            maxWidth = Math.max(maxWidth, minecraft.font.width(line));
        }
        int boxWidth = maxWidth + padding * 2;
        int boxHeight = lines.size() * lineHeight + padding * 2;
        int x = Math.min(mouseX + 8, Math.max(0, width - boxWidth - 4));
        int y = Math.min(mouseY + 8, Math.max(0, height - boxHeight - 4));
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF0100010);
        graphics.fill(x, y, x + boxWidth, y + 1, 0xFF5000FF);
        graphics.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF5000FF);
        int textY = y + padding;
        for (Component line : lines) {
            graphics.drawString(minecraft.font, line, x + padding, textY, 0xFFFFFFFF, true);
            textY += lineHeight;
        }
    }

    /// 领地名称标签（规格 §7.5）。在组件渲染之后调用，确保标签位于最上层。
    ///
    /// 缩放到区块像素不足阈值时隐藏标签，仅保留色块（AC-10）。
    private void drawTerritoryLabels(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || pixelsPerChunk < MIN_PIXELS_FOR_LABEL) {
            return;
        }
        TerritoryRegistry registry = AtlasTerritory.clientRegistry();
        var groups = registry.orderedForDimension(minecraft.level.dimension());
        if (groups.isEmpty()) {
            return;
        }
        for (TerritoryGroup group : groups) {
            TerritoryRegistry.CachedTerritory cached = registry.cached(group.territoryId());
            if (cached == null) {
                continue;
            }
            for (TerritoryRegistry.Component component : cached.components()) {
                if (component.anchor() == null) {
                    continue;
                }
                // 锚点可能偏移出视口：此时退化为该分量内任一可见区块，保证标签仍可见（§7.5）。
                int[] rect = chunkScreenRect(component.anchor());
                if (rect == null) {
                    rect = firstVisibleChunkRect(component);
                }
                if (rect == null) {
                    continue;
                }
                // 名称标签带深色背景框，避免在浅色地形上难以辨认（§7.5）。
                int textWidth = minecraft.font.width(group.displayName());
                int tx = rect[0] + rect[2] / 2 - textWidth / 2;
                int ty = rect[1] + rect[3] / 2 - 4;
                graphics.fill(tx - 2, ty - 2, tx + textWidth + 2, ty + 10, 0xB0000000);
                graphics.drawString(minecraft.font, group.displayName(), tx, ty, 0xFFFFFFFF,
                        false);
            }
        }
    }

    /// 分量内第一个可见区块的屏幕矩形；全部不可见时返回 null。
    private int[] firstVisibleChunkRect(TerritoryRegistry.Component component) {
        for (ChunkPos chunk : component.chunks()) {
            int[] rect = chunkScreenRect(chunk);
            if (rect != null) {
                return rect;
            }
        }
        return null;
    }

    /// 区块在屏幕上的矩形：`[x, y, width, height]`；完全不可见时返回 null。
    private int[] chunkScreenRect(ChunkPos chunk) {
        double centerChunkX = (chunk.x << 4) + 8.0;
        double centerChunkZ = (chunk.z << 4) + 8.0;
        double screenX = width / 2.0 + (centerChunkX - centerX) * pixelsPerChunk;
        double screenY = height / 2.0 + (centerChunkZ - centerZ) * pixelsPerChunk;
        int size = Math.max(1, Math.round(ChunkMapData.SIZE * pixelsPerChunk));
        int x = (int) Math.round(screenX) - size / 2;
        int y = (int) Math.round(screenY) - size / 2;
        if (x + size < 0 || x > width || y + size < 0 || y > height) {
            return null;
        }
        return new int[] { x, y, size, size };
    }

    /// 仅在领地与非领地交界处描边（规格 §7.3：内部交界不画线）。
    private void drawChunkOutline(GuiGraphics graphics, TerritoryGroup group, ChunkPos chunk,
            int[] rect, int color) {
        int borderWidth = Math.max(1, Math.round(group.borderStyle().width()));
        Set<ChunkPos> chunks = group.chunks();
        int x = rect[0];
        int y = rect[1];
        int size = rect[2];
        if (!chunks.contains(new ChunkPos(chunk.x, chunk.z - 1))) {
            graphics.fill(x, y, x + size, y + borderWidth, color);
        }
        if (!chunks.contains(new ChunkPos(chunk.x, chunk.z + 1))) {
            graphics.fill(x, y + size - borderWidth, x + size, y + size, color);
        }
        if (!chunks.contains(new ChunkPos(chunk.x - 1, chunk.z))) {
            graphics.fill(x, y, x + borderWidth, y + size, color);
        }
        if (!chunks.contains(new ChunkPos(chunk.x + 1, chunk.z))) {
            graphics.fill(x + size - borderWidth, y, x + size, y + size, color);
        }
    }

    /// 鼠标位置对应的区块；在世界地图外返回 null。
    private ChunkPos chunkAtScreen(double mouseX, double mouseY) {
        if (mouseX < 0 || mouseX > width || mouseY < 0 || mouseY > height) {
            return null;
        }
        double worldX = screenToWorldX(mouseX);
        double worldZ = screenToWorldZ(mouseY);
        return new ChunkPos((int) Math.floor(worldX) >> 4, (int) Math.floor(worldZ) >> 4);
    }

    /// 主题色 + 填充透明度。
    private static int withOpacity(int rgb, float opacity) {
        int alpha = Math.round(net.minecraft.util.Mth.clamp(opacity, 0.0F, 1.0F) * 255.0F);
        return (alpha << 24) | (rgb & 0xFFFFFF);
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
