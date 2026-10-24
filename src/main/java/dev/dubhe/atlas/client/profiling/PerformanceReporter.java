package dev.dubhe.atlas.client.profiling;

import dev.anvilcraft.lib.v2.util.ClientTickRecorder;
import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.team.TeamProviders;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 渲染性能周期上报（规格 §12.1）。
///
/// 每 600 个有效 tick（约 30 秒）输出一次渲染统计，便于在日志中直接核对性能指标，
/// 无需玩家执行命令。有效 tick 由 AnvilLib `ClientTickRecorder` 统计，
/// 仅在存在世界且未暂停时累加，避免暂停期间把间隔拖长。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class PerformanceReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/perf");
    private static final int REPORT_INTERVAL_TICKS = 600;

    /// 上次输出统计时的有效 tick 数。
    private static int lastReportedTicks;
    /// 队伍诊断只输出一次。
    private static boolean teamDumped;

    private PerformanceReporter() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // 自动化验收：输出队伍适配层状态（正常游玩不触发）。
        if (!teamDumped && System.getenv("ALEEVE_ATLAS_DUMP_TEAM") != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null) {
                teamDumped = true;
                LOGGER.info("{}", TeamProviders.describe(minecraft.player.getUUID()));
            }
        }
        int recordedTicks = ClientTickRecorder.getTicks();
        if (recordedTicks - lastReportedTicks < REPORT_INTERVAL_TICKS) {
            return;
        }
        lastReportedTicks = recordedTicks;
        if (AtlasProfiling.minimap().count() > 0) {
            LOGGER.info("{}", AtlasProfiling.minimapSummary());
        }
        // 世界地图画布仅在拖拽或缩放时重绘，单独输出便于核对（§12.1）。
        if (AtlasProfiling.canvas().count() > 0) {
            LOGGER.info("{}", AtlasProfiling.canvasSummary());
        }
        // 平移路径单独统计，用于判断条带复用是否真正降低开销。
        if (AtlasProfiling.canvasScroll().count() > 0) {
            LOGGER.info("{}", AtlasProfiling.canvasScrollSummary());
        }
        AtlasProfiling.reset();
    }
}
