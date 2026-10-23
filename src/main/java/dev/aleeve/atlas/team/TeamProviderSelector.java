package dev.aleeve.atlas.team;

import dev.aleeve.atlas.AleeveAtlas;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/// 队伍实现选择与配置热重载（规格 §3.1 / §11 配置热重载）。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class TeamProviderSelector {
    private TeamProviderSelector() {
    }

    /// 配置加载或重载时按 `team.provider` 重新选择实现。
    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent event) {
        TeamProviders.select(AleeveAtlas.CONFIG.team.provider);
    }
}
