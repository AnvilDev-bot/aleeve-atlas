package dev.dubhe.atlas.ping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// 标点广播限频测试。
///
/// 冷却期内不得再次放行，避免连续点击放大带宽占用；
/// 本地标记不受影响，仅抑制网络广播。
class BroadcastCooldownTest {
    private static final long COOLDOWN = 250L;

    @Test
    void firstBroadcastIsAlwaysAllowed() {
        BroadcastCooldown cooldown = new BroadcastCooldown(COOLDOWN);
        assertTrue(cooldown.tryAcquire(1_000L));
    }

    @Test
    void secondBroadcastWithinCooldownIsRejected() {
        BroadcastCooldown cooldown = new BroadcastCooldown(COOLDOWN);
        assertTrue(cooldown.tryAcquire(1_000L));
        assertFalse(cooldown.tryAcquire(1_100L), "冷却期内的广播必须被拒绝");
        assertFalse(cooldown.tryAcquire(1_249L), "边界前一刻仍应拒绝");
    }

    @Test
    void broadcastAtCooldownBoundaryIsAllowed() {
        BroadcastCooldown cooldown = new BroadcastCooldown(COOLDOWN);
        assertTrue(cooldown.tryAcquire(1_000L));
        assertTrue(cooldown.tryAcquire(1_250L), "恰好达到冷却间隔时应放行");
    }

    @Test
    void cooldownRestartsFromLastAllowedTime() {
        // 被拒绝的尝试不应刷新冷却起点，否则连续点击会无限推迟下一次放行。
        BroadcastCooldown cooldown = new BroadcastCooldown(COOLDOWN);
        assertTrue(cooldown.tryAcquire(1_000L));
        assertFalse(cooldown.tryAcquire(1_200L));
        assertFalse(cooldown.tryAcquire(1_240L));
        assertTrue(cooldown.tryAcquire(1_250L), "冷却起点应为上次放行时刻");
    }

    @Test
    void resetAllowsImmediateBroadcast() {
        BroadcastCooldown cooldown = new BroadcastCooldown(COOLDOWN);
        assertTrue(cooldown.tryAcquire(1_000L));
        assertFalse(cooldown.tryAcquire(1_100L));
        cooldown.reset();
        assertTrue(cooldown.tryAcquire(1_100L), "重置后应立即可广播");
    }

    @Test
    void nonPositiveCooldownAllowsEveryBroadcast() {
        BroadcastCooldown cooldown = new BroadcastCooldown(0L);
        assertTrue(cooldown.tryAcquire(1_000L));
        assertTrue(cooldown.tryAcquire(1_001L));
    }
}
