package dev.dubhe.atlas.sync;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.AtlasSession;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 地图摘要上报调度（规格 §3.3）。
///
/// 触发时机：
/// 1. 进入协同模式后上报一次（新上线/入队追赶同步的起点）；
/// 2. 之后按服务端配置的 `sync.mapDelta.intervalTicks` 周期上报，支撑实时共探同步。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class ClientMapSyncScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/mapsync");

    /// 首次上报延迟（tick）：等待地图缓存加载出本地已有区块。
    private static final int INITIAL_DELAY_TICKS = 100;

    private static int ticksSinceLogin;
    private static int ticksSinceReport;
    private static boolean reportedOnce;

    private ClientMapSyncScheduler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!AtlasSession.synchronizedMode()) {
            return;
        }
        ticksSinceLogin++;
        if (!reportedOnce) {
            if (ticksSinceLogin < INITIAL_DELAY_TICKS) {
                return;
            }
            ClientMapSync.reportLocalDigest();
            reportedOnce = true;
            ticksSinceReport = 0;
            LOGGER.debug("Initial map digest reported");
            return;
        }
        // 周期性上报，让队友持续获知本地新探索区块（§3.3 实时共探）。
        if (++ticksSinceReport >= SyncBudget.intervalTicks()) {
            ticksSinceReport = 0;
            ClientMapSync.reportLocalDigest();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        reset();
    }

    /// 队友入队后立即重报摘要，让新成员尽快获得已有地图（§3.3 / §3.1）。
    @SubscribeEvent
    public static void onTeamChanged(
            dev.dubhe.atlas.api.team.TeamMembershipChangedEvent event) {
        if (event.change() != dev.dubhe.atlas.api.team.TeamMembershipChangedEvent.Change.JOINED) {
            return;
        }
        if (!AtlasSession.synchronizedMode()) {
            return;
        }
        ClientMapSync.reportLocalDigest();
        ticksSinceReport = 0;
        LOGGER.debug("Re-reported map digest after {} joined team {}",
                event.playerId(), event.teamId());
    }

    private static void reset() {
        ticksSinceLogin = 0;
        ticksSinceReport = 0;
        reportedOnce = false;
        ClientMapSync.clear();
        // 清理跨会话残留的雷达渐隐与标点状态（§12.1 内存占用）。
        dev.dubhe.atlas.client.radar.VisibilityFade.clear();
        dev.dubhe.atlas.ping.ClientPings.clear();
    }
}
