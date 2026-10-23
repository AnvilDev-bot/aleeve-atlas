package dev.aleeve.atlas;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(AleeveAtlas.MOD_ID)
public final class AleeveAtlas {
    public static final String MOD_ID = "aleeve_atlas";

    public AleeveAtlas(IEventBus modEventBus, ModContainer modContainer) {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
