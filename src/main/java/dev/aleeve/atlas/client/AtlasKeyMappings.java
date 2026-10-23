package dev.aleeve.atlas.client;

import dev.aleeve.atlas.AleeveAtlas;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/// 键位注册（规格 §8）。
///
/// 全部使用 `InputConstants.Type.KEYSYM`（GLFW key token），禁止扫描码；
/// 游戏内键位使用 `IN_GAME` 冲突上下文，并注册独立 `KeyMapping.Category`。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasKeyMappings {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(AleeveAtlas.id("main"));

    public static final KeyMapping OPEN_WORLD_MAP = new KeyMapping(
            "key.aleeve_atlas.open_world_map",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY);

    public static final KeyMapping NEW_WAYPOINT = new KeyMapping(
            "key.aleeve_atlas.new_waypoint",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY);

    private AtlasKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_WORLD_MAP);
        event.register(NEW_WAYPOINT);

    }
}
