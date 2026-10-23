package dev.dubhe.atlas;

import dev.dubhe.atlas.config.NonTeamVisibility;

/// 双端自适应会话状态（规格 §1 / §4.1 / §9.2）。
///
/// 客户端持有唯一实例：默认独立模式，收到服务端握手包后切换到协同模式；
/// 服务端下发的锁定项（非同队玩家可见性策略、雷达最大距离、同步开关）在此缓存，
/// 客户端不可单方面篡改。
public final class AtlasSession {
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
        maxRadarDistance = serverMaxRadarDistance;
        nonTeamVisibility = serverVisibility;
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
