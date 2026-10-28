package dev.dubhe.map.sync;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 同步发送预算测试。
///
/// 单 tick 分片数与每秒字节数共同限制发送速率，任一超限都必须拒绝发送。
class SyncBudgetTest {
    @Test
    void packetLargerThanOneSecondBudgetProgressesButCarriesDebt() {
        int previous = dev.dubhe.map.AleeveAtlas.CONFIG.sync.mapDeltaMaxBytesPerSecond;
        try {
            dev.dubhe.map.AleeveAtlas.CONFIG.sync.mapDeltaMaxBytesPerSecond = 8192;
            var budget = new SyncBudget();
            budget.reset();
            Assertions.assertTrue(budget.canSend(20_000));
            budget.record(20_000);
            budget.beginTick();
            Assertions.assertFalse(budget.canSend(1));
        } finally {
            dev.dubhe.map.AleeveAtlas.CONFIG.sync.mapDeltaMaxBytesPerSecond = previous;
        }
    }
    @Test
    void packetsPerTickAreCapped() {
        SyncBudget budget = new SyncBudget();
        budget.reset();
        budget.beginTick();
        // 单 tick 最多 4 个分片。
        for (int i = 0; i < 4; i++) {
            Assertions.assertTrue(budget.canSend(1), "第 " + (i + 1) + " 个分片应被允许");
            budget.record(1);
        }
        Assertions.assertFalse(budget.canSend(1), "同一 tick 的第 5 个分片必须被拒绝");
    }

    @Test
    void newTickResetsPacketCounter() {
        SyncBudget budget = new SyncBudget();
        budget.reset();
        budget.beginTick();
        for (int i = 0; i < 4; i++) {
            budget.record(1);
        }
        Assertions.assertFalse(budget.canSend(1));
        budget.beginTick();
        Assertions.assertTrue(budget.canSend(1), "新 tick 必须重置分片计数");
    }

    @Test
    void byteBudgetRejectsOversizedTransfer() {
        SyncBudget budget = new SyncBudget();
        budget.reset();
        budget.beginTick();
        // 单次请求超过每秒配额时直接拒绝，避免超额发送后才中断。
        Assertions.assertFalse(budget.canSend(Integer.MAX_VALUE),
                "超过每秒字节配额的单次请求必须被拒绝");
    }

    @Test
    void intervalTicksIsAtLeastTen() {
        // 服务端默认 40 tick，实现侧不允许低于 10，避免同步过于频繁。
        Assertions.assertTrue(SyncBudget.intervalTicks() >= 10);
    }
}
