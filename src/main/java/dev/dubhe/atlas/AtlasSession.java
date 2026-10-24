package dev.dubhe.atlas;

import dev.dubhe.atlas.config.NonTeamVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 双端自适应会话状态（规格 §1 / §4.1 / §9.2）。
///
/// 客户端持有唯一实例：默认独立模式，收到服务端握手包后切换到协同模式；
/// 服务端下发的锁定项（非同队玩家可见性策略、雷达最大距离、同步开关）在此缓存，
/// 客户端不可单方面篡改。
public final class AtlasSession {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/session");
    /// 雷达距离的合理范围，与服务端 `@BoundedDiscrete` 声明一致。
    private static final int MIN_RADAR_DISTANCE = 16;
    private static final int MAX_RADAR_DISTANCE = 512;

    private static volatile AtlasMode mode = AtlasMode.STANDALONE;
    private static volatile int maxRadarDistance = 128;
    private static volatile NonTeamVisibility nonTeamVisibility = NonTeamVisibility.VISIBLE_ONLY;
    private static volatile boolean serverSyncEnabled;

    private AtlasSession() {
    }

    public static AtlasMode mode() {
        return mode;
    }

    public static boolean synchronizedMode() {
        return mode == AtlasMode.SYNCHRONIZED;
    }

    public static int maxRadarDistance() {
        return maxRadarDistance;
    }

    /// 服务端下发的非同队玩家可见性策略（规格 §4.1）。
    public static NonTeamVisibility nonTeamVisibility() {
        return nonTeamVisibility;
    }

    /// 服务端是否启用协同同步（规格 §9.2 `sync.enabled`）。
    public static boolean serverSyncEnabled() {
        return serverSyncEnabled;
    }

    /// 收到服务端握手包时应用下发锁定项。
    ///
    /// 服务端关闭 `sync.enabled` 时保持独立模式，同步类功能不可用。
    public static void onServerHandshake(int serverMaxRadarDistance,
            NonTeamVisibility serverVisibility, boolean syncEnabled) {
        // 服务端配置有 16~512 的边界声明，客户端仍做一次防御：异常值会让距离剔除失效。
        if (serverMaxRadarDistance < MIN_RADAR_DISTANCE
                || serverMaxRadarDistance > MAX_RADAR_DISTANCE) {
            LOGGER.warn("Server radar distance {} out of range, clamped to {}..{}",
                    serverMaxRadarDistance, MIN_RADAR_DISTANCE, MAX_RADAR_DISTANCE);
            maxRadarDistance = Math.max(MIN_RADAR_DISTANCE,
                    Math.min(MAX_RADAR_DISTANCE, serverMaxRadarDistance));
        } else {
            maxRadarDistance = serverMaxRadarDistance;
        }
        // 可见性策略为 null 时下游会退化为「全部显示」，属于不安全的默认值，
        // 因此按最保守的 VISIBLE_ONLY 处理并记录日志（§4.1）。
        if (serverVisibility == null) {
            LOGGER.warn("Server sent null visibility policy, falling back to VISIBLE_ONLY");
            nonTeamVisibility = NonTeamVisibility.VISIBLE_ONLY;
        } else {
            nonTeamVisibility = serverVisibility;
        }
        serverSyncEnabled = syncEnabled;
        mode = syncEnabled ? AtlasMode.SYNCHRONIZED : AtlasMode.STANDALONE;
    }

    public static void reset() {
        mode = AtlasMode.STANDALONE;
        maxRadarDistance = 128;
        nonTeamVisibility = NonTeamVisibility.VISIBLE_ONLY;
        serverSyncEnabled = false;
    }
}
