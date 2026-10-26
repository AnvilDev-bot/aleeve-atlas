package dev.dubhe.atlas.client.map;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.config.AtlasClientConfigs;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 小地图朝向的自动化验收。
///
/// 设置 `ALEEVE_ATLAS_ORIENTATION_SELFTEST` 后依次切换四个主朝向，
/// 便于截图核对地图内容和水滴指示器是否一致。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MinimapOrientationSelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/orientation");
    private static final float[] YAWS = { 0.0F, 90.0F, 180.0F, -90.0F };

    private static int ticks;
    private static int index;
    private static boolean done;

    private MinimapOrientationSelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (done || System.getenv("ALEEVE_ATLAS_ORIENTATION_SELFTEST") == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ticks++;
        if (ticks < 120) {
            return;
        }
        ticks = 0;
        if (index >= YAWS.length) {
            done = true;
            return;
        }
        float yaw = YAWS[index++];
        minecraft.player.setYRot(yaw);
        LOGGER.info("Orientation self-test: yaw={}, lockNorth={}", yaw,
                AtlasClientConfigs.CONFIG.minimap.lockNorth);
    }
}
