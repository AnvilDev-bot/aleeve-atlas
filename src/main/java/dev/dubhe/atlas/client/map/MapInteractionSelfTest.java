package dev.dubhe.atlas.client.map;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.client.waypoint.ClientWaypoints;
import dev.dubhe.atlas.client.waypoint.WaypointEditScreen;
import dev.dubhe.atlas.map.ChunkMapData;
import dev.dubhe.atlas.map.MapDimensionCache;
import dev.dubhe.atlas.ping.ClientPings;
import dev.dubhe.atlas.ping.MapPingData;
import dev.dubhe.atlas.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 设置 ALEEVE_ATLAS_MAP_INTERACTION_TEST 后验证真实轮盘与地图菜单。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MapInteractionSelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/map-interaction-test");
    private static WorldMapScreen map;
    private static int ticks;
    private static int step;
    private static boolean finished;
    private static boolean selecting;
    private static double selectionX;
    private static double selectionY;

    private MapInteractionSelfTest() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (finished || (System.getenv("ALEEVE_ATLAS_MAP_INTERACTION_TEST") == null
                && System.getenv("ALEEVE_ATLAS_CANVAS_TEST") == null)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || ++ticks < 30) {
            return;
        }
        ticks = 0;
        try {
            if (System.getenv("ALEEVE_ATLAS_CANVAS_TEST") != null) {
                verifyCanvas(minecraft);
                finished = true;
                LOGGER.info("Canvas interaction test PASSED");
                return;
            }
            runStep(minecraft);
            LOGGER.info("Map interaction test passed step {}", step++);
            if (step > 14) {
                finished = true;
                LOGGER.info("Map interaction test PASSED");
            }
        } catch (RuntimeException exception) {
            finished = true;
            LOGGER.error("Map interaction test FAILED at step {}", step, exception);
        }
    }

    private static void runStep(Minecraft minecraft) {
        if (step == 0) {
            AtlasWorldMap.open();
            map = (WorldMapScreen) minecraft.screen;
            return;
        }
        double x = map.width / 2.0;
        double y = map.height / 2.0;
        if (step == 1) {
            ClientPings.clear();
            MouseButtonEvent middle = mouse(x, y, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            map.mouseClicked(middle, false);
            map.mouseReleased(middle);
            require(ClientPings.active().stream().anyMatch(p -> p.icon() == MapPingData.PingIcon.GENERAL),
                    "Quick middle click must create GENERAL");
        } else if (step >= 2 && step <= 9) {
            int index = (step - 2) / 2;
            if (step % 2 == 0) {
                openWheel(x, y);
                double angle = index * Math.PI / 2;
                double radius = Math.min(map.width, map.height) * 0.25;
                selectionX = x - Math.sin(angle) * radius;
                selectionY = y - Math.cos(angle) * radius;
                selecting = true;
            } else {
                require(minecraft.screen == map, "Wheel must return to same map");
                MapPingData.PingIcon expected = MapPingData.PingIcon.values()[index];
                require(ClientPings.active().stream().anyMatch(p -> p.icon() == expected),
                        "Wheel must create " + expected);
            }
        } else if (step == 10) {
            openWheel(x, y);
            var wheel = minecraft.screen;
            int before = ClientPings.active().size();
            wheel.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
            wheel.mouseReleased(mouse(x, y, GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
            require(minecraft.screen == map && ClientPings.active().size() == before,
                    "Escape must cancel without a ping");
        } else if (step == 11) {
            map.mouseClicked(mouse(x + 32, y + 32, GLFW.GLFW_MOUSE_BUTTON_RIGHT), false);
            map.mouseClicked(mouse(x + 33, y + 33, GLFW.GLFW_MOUSE_BUTTON_LEFT), false);
            require(minecraft.screen instanceof WaypointEditScreen, "Context create must open waypoint editor");
            minecraft.screen.onClose();
        } else if (step == 12) {
            Waypoint waypoint = Waypoint.create("Map interaction test", minecraft.player.blockPosition(),
                    minecraft.level.dimension(), 0xFFFFFFFF);
            ClientWaypoints.store().upsert(waypoint);
            map.mouseClicked(mouse(x, y, GLFW.GLFW_MOUSE_BUTTON_RIGHT), false);
            map.mouseClicked(mouse(x + 1, y + 1, GLFW.GLFW_MOUSE_BUTTON_LEFT), false);
            require(ClientWaypoints.store().get(waypoint.id()).isEmpty(), "Context delete must remove waypoint");
        } else if (step == 13) {
            map.mouseClicked(mouse(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT), false);
            map.mouseDragged(mouse(x + 280, y - 220, GLFW.GLFW_MOUSE_BUTTON_LEFT), 280, -220);
            map.mouseDragged(mouse(x - 240, y + 180, GLFW.GLFW_MOUSE_BUTTON_LEFT), -520, 400);
            map.mouseDragged(mouse(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT), 240, -180);
            map.mouseReleased(mouse(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        } else {
            verifyCanvas(minecraft);
        }
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Pre event) {
        if (!selecting || !(event.getScreen() instanceof PingWheelMenu wheel)) {
            return;
        }
        selecting = false;
        wheel.render(event.getGuiGraphics(), (int) selectionX, (int) selectionY,
                event.getPartialTick());
        if (step == 3) {
            Minecraft minecraft = Minecraft.getInstance();
            Screenshot.grab(minecraft.gameDirectory, "atlas-wheel.png", minecraft.getMainRenderTarget(),
                    1, message -> LOGGER.info("Wheel screenshot: {}", message.getString()));
        }
        wheel.mouseReleased(mouse(selectionX, selectionY, GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
    }

    private static void verifyCanvas(Minecraft minecraft) {
        MapDimensionCache cache = new MapDimensionCache(
                minecraft.gameDirectory.toPath().resolve("map-interaction-test-cache"));
        for (int chunkX = 0; chunkX < 130; chunkX++) {
            for (int chunkZ = 0; chunkZ < 66; chunkZ++) {
                ChunkMapData data = new ChunkMapData(chunkX, chunkZ);
                for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                    for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
                        int worldX = chunkX * ChunkMapData.SIZE + localX;
                        int worldZ = chunkZ * ChunkMapData.SIZE + localZ;
                        data.setPixel(localX, localZ, worldColor(worldX, worldZ), 64);
                    }
                }
                cache.put(data);
            }
        }
        MapCanvas canvas = new MapCanvas(AleeveAtlas.id("map_interaction_test_canvas"));
        canvas.register();
        try {
            canvas.setCenter(32, 32, cache);
            require(canvas.originChunkX() == 0 && canvas.originChunkZ() == 0,
                    "Initial canvas origin");
            requireCanvasPixel(canvas, 0, 0, 0, 0);
            canvas.setCenter(33, 32, cache);
            require(canvas.originChunkX() == 0, "Sub-threshold movement must reuse origin");
            canvas.setCenter(34, 33, cache);
            require(canvas.originChunkX() == 2 && canvas.originChunkZ() == 1,
                    "Positive recenter origin");
            requireCanvasPixel(canvas, 0, 0, 2 * ChunkMapData.SIZE, ChunkMapData.SIZE);
            int edge = (MapCanvas.CHUNKS_PER_SIDE - 1) * ChunkMapData.SIZE;
            requireCanvasPixel(canvas, edge, edge, 66 * ChunkMapData.SIZE,
                    65 * ChunkMapData.SIZE);
            canvas.setCenter(32, 33, cache);
            requireCanvasPixel(canvas, 0, 0, 0, ChunkMapData.SIZE);
            canvas.setCenter(97, 33, cache);
            require(canvas.originChunkX() == 65 && canvas.originChunkZ() == 1,
                    "Full redraw origin");
            requireCanvasPixel(canvas, 0, 0, 65 * ChunkMapData.SIZE, ChunkMapData.SIZE);
            requireCanvasPixel(canvas, edge, edge, 129 * ChunkMapData.SIZE,
                    65 * ChunkMapData.SIZE);
        } finally {
            canvas.close();
        }
    }

    private static void requireCanvasPixel(MapCanvas canvas, int x, int z,
            int worldX, int worldZ) {
        int actual = canvas.pixelAt(x, z);
        int expected = worldColor(worldX, worldZ);
        require(actual == expected, "Canvas pixel mismatch at " + worldX + "," + worldZ
                + ": actual=" + Integer.toHexString(actual)
                + " expected=" + Integer.toHexString(expected));
    }

    private static int worldColor(int worldX, int worldZ) {
        return 0xFF000000 | ((worldX & 255) << 8) | (worldZ & 255);
    }

    private static void openWheel(double x, double y) {
        map.mouseClicked(mouse(x, y, GLFW.GLFW_MOUSE_BUTTON_MIDDLE), false);
        for (int tick = 0; tick < 8; tick++) {
            map.tick();
        }
        require(Minecraft.getInstance().screen instanceof PingWheelMenu, "Long press must open AnvilLib wheel");
    }

    private static MouseButtonEvent mouse(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
