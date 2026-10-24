package dev.dubhe.atlas.ping;

/// 标点广播限频。
///
/// 连续发起标点时按固定冷却间隔放行，冷却期内只保留本地渲染、跳过网络广播。
/// 时间由调用方传入，便于验证而不依赖系统时钟。
public final class BroadcastCooldown {
    private final long cooldownMillis;
    private long lastAllowedMillis = Long.MIN_VALUE;

    /// @param cooldownMillis 两次放行之间的最小间隔（毫秒），小于等于 0 时不做限制
    public BroadcastCooldown(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    /// 判断给定时刻是否允许广播；允许时记录该时刻作为新的冷却起点。
    ///
    /// 首次调用总是放行。
    public boolean tryAcquire(long nowMillis) {
        if (cooldownMillis <= 0L) {
            lastAllowedMillis = nowMillis;
            return true;
        }
        if (lastAllowedMillis != Long.MIN_VALUE
                && nowMillis - lastAllowedMillis < cooldownMillis) {
            return false;
        }
        lastAllowedMillis = nowMillis;
        return true;
    }

    /// 重置冷却起点，切换服务器后首次广播不受上一会话影响。
    public void reset() {
        lastAllowedMillis = Long.MIN_VALUE;
    }
}
