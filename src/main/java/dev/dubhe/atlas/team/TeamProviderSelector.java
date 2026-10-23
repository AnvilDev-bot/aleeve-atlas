package dev.dubhe.atlas.team;

import dev.dubhe.atlas.AleeveAtlas;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/// 队伍实现选择与配置热重载（规格 §3.1 / §11 配置热重载）。
///
/// 双端均需按 `team.provider` 选择实现：客户端据此渲染同队标记，
/// 服务端据此判定同步转发目标。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class TeamProviderSelector {
    private TeamProviderSelector() {
    }

    /// 配置加载或重载时按 `team.provider` 重新选择实现。
    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent event) {
        TeamProviders.select(AleeveAtlas.CONFIG.team.provider);
    }
}
