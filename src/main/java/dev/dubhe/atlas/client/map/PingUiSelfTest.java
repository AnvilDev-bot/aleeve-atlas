package dev.dubhe.atlas.client.map;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.ping.ClientPings;
import dev.dubhe.atlas.ping.MapPingData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 临时标点交互自测，设置 `ALEEVE_ATLAS_PING_UI_SELFTEST` 后执行。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class PingUiSelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/ping-ui");
    private static int phase;
    private static int ticks;
    private static boolean done;

    private PingUiSelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (done || System.getenv("ALEEVE_ATLAS_PING_UI_SELFTEST") == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || ++ticks < 40) {
            return;
        }
        ticks = 0;
        if (phase == 0) {
            if (minecraft.screen == null) {
                ClientPings.clear();
                AtlasWorldMap.open();
                phase++;
            }
            return;
        }
        if (!(minecraft.screen instanceof WorldMapScreen screen)) {
            return;
        }
        boolean advanced = switch (phase) {
            case 1 -> quickPing(screen);
            case 2 -> wheelPing(screen);
            case 3 -> contextAdd(screen);
            case 4 -> contextDelete(screen);
            default -> false;
        };
        if (advanced) {
            LOGGER.info("Ping UI self-test completed phase {}", phase);
            phase++;
            if (phase > 4) {
                done = true;
                LOGGER.info("Ping UI self-test completed");
            }
        }
    }

    private static boolean quickPing(WorldMapScreen screen) {
        middleClick(screen, screen.width / 2.0, screen.height / 2.0);
        return ClientPings.active().stream()
                .anyMatch(ping -> ping.icon() == MapPingData.PingIcon.NORMAL);
    }

    private static boolean wheelPing(WorldMapScreen screen) {
        double x = screen.width / 2.0;
        double y = screen.height / 2.0;
        screen.mouseClicked(new MouseButtonEvent(x, y,
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 0)), false);
        for (int i = 0; i < 8; i++) {
            screen.tick();
        }
        screen.mouseReleased(new MouseButtonEvent(x + 42, y,
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 0)));
        return ClientPings.active().stream()
                .anyMatch(ping -> ping.icon() == MapPingData.PingIcon.DANGER);
    }

    private static boolean contextAdd(WorldMapScreen screen) {
        double x = screen.width / 2.0 + 100;
        double y = screen.height / 2.0 + 60;
        int before = ClientPings.active().size();
        rightClick(screen, x, y);
        leftClick(screen, x, y);
        return ClientPings.active().size() > before;
    }

    private static boolean contextDelete(WorldMapScreen screen) {
        double x = screen.width / 2.0;
        double y = screen.height / 2.0;
        int before = ClientPings.active().size();
        rightClick(screen, x, y);
        leftClick(screen, x, y);
        return ClientPings.active().size() < before;
    }

    private static void middleClick(WorldMapScreen screen, double x, double y) {
        MouseButtonEvent event = new MouseButtonEvent(x, y,
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static void rightClick(WorldMapScreen screen, double x, double y) {
        screen.mouseClicked(new MouseButtonEvent(x, y,
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0)), false);
    }

    private static void leftClick(WorldMapScreen screen, double x, double y) {
        screen.mouseClicked(new MouseButtonEvent(x, y,
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
    }
}
