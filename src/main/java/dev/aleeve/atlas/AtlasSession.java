package dev.aleeve.atlas;

/// 双端自适应模式状态（规格 §1）。
///
/// 客户端持有唯一实例：默认独立模式，收到服务端握手包后切换到协同模式，断开连接时复位。
public final class AtlasSession {
    private static volatile AtlasMode mode = AtlasMode.STANDALONE;
    private static volatile int maxRadarDistance = 128;

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

    public static void onServerHandshake(int serverMaxRadarDistance) {
        maxRadarDistance = serverMaxRadarDistance;
        mode = AtlasMode.SYNCHRONIZED;
    }

    public static void reset() {
        mode = AtlasMode.STANDALONE;
        maxRadarDistance = 128;
    }
}
