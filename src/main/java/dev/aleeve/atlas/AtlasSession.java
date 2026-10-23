package dev.aleeve.atlas;

import dev.aleeve.atlas.config.NonTeamVisibility;

/// 双端自适应会话状态（规格 §1 / §4.1）。
///
/// 客户端持有唯一实例：默认独立模式，收到服务端握手包后切换到协同模式；
/// 服务端下发的锁定项（非同队玩家可见性策略、雷达最大距离）在此缓存，客户端不可单方面篡改。
public final class AtlasSession {
    private static volatile AtlasMode mode = AtlasMode.STANDALONE;
    private static volatile int maxRadarDistance = 128;
    private static volatile NonTeamVisibility nonTeamVisibility = NonTeamVisibility.VISIBLE_ONLY;

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

    /// 收到服务端握手包时应用下发锁定项。
    public static void onServerHandshake(int serverMaxRadarDistance,
            NonTeamVisibility serverVisibility) {
        maxRadarDistance = serverMaxRadarDistance;
        nonTeamVisibility = serverVisibility;
        mode = AtlasMode.SYNCHRONIZED;
    }

    public static void reset() {
        mode = AtlasMode.STANDALONE;
        maxRadarDistance = 128;
        nonTeamVisibility = NonTeamVisibility.VISIBLE_ONLY;
    }
}
