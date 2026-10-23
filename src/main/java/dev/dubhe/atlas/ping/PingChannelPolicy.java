package dev.dubhe.atlas.ping;

import dev.dubhe.atlas.config.AtlasClientConfigs;
import dev.dubhe.atlas.config.PingChannelMode;
import net.neoforged.fml.ModList;

/// 标点通道复用策略（规格 §3.2.2 / §9.1 `ping.channelMode`）。
///
/// 当检测到 `Ping Wheel` 等外部标点模组时，本模组按配置决定：
/// - `AUTO`：外部模组存在时仅监听其标点，不再主动广播（避免重复）；
/// - `LISTEN_ONLY`：始终只监听外部标点，本模组不发起广播；
/// - `TAKEOVER`：统一由本模组经队伍同步网络转发外部标点。
///
/// 外部模组不存在时三种配置都退化为本模组的独立广播。
public final class PingChannelPolicy {
    /// `Ping Wheel` 的模组 ID。
    public static final String PING_WHEEL_MOD_ID = "pingwheel";

    private PingChannelPolicy() {
    }

    /// 外部标点模组是否已加载。
    public static boolean externalPingModPresent() {
        return ModList.get() != null && ModList.get().isLoaded(PING_WHEEL_MOD_ID);
    }

    /// 本模组是否应主动广播本地标点（false 表示只监听外部信道）。
    public static boolean shouldBroadcast() {
        PingChannelMode mode = AtlasClientConfigs.CONFIG.ping.channelMode;
        boolean external = externalPingModPresent();
        return switch (mode) {
            case LISTEN_ONLY -> false;
            case TAKEOVER -> true;
            // AUTO：外部模组存在时让位，避免同一次标点被两个系统各广播一次。
            case AUTO -> !external;
        };
    }

    /// 是否存在重复标点风险（供诊断与提示）。
    public static boolean duplicateRisk() {
        return externalPingModPresent() && AtlasClientConfigs.CONFIG.ping.channelMode
                == PingChannelMode.AUTO;
    }

    /// 当前生效策略的可读描述（诊断用）。
    public static String describe() {
        return "mode=%s external=%s broadcast=%s".formatted(
                AtlasClientConfigs.CONFIG.ping.channelMode, externalPingModPresent(),
                shouldBroadcast());
    }
}
