package dev.aleeve.atlas.data;

import dev.aleeve.atlas.AleeveAtlas;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class AleeveAtlasDataGen {
    private AleeveAtlasDataGen() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        event.addProvider(new AleeveAtlasLanguageProvider(event.getGenerator().getPackOutput()));
    }
}
