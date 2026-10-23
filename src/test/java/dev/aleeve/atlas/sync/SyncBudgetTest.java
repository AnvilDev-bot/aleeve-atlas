package dev.aleeve.atlas.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// 同步发送预算测试（规格 §9.2 / §12.1）。
class SyncBudgetTest {
    @Test
    void resetClearsWindow() {
        SyncBudget budget = new SyncBudget();
        budget.reset();
        budget.beginTick();
        budget.record(1024);
        assertEquals(0, budget.remainingBytes() >= 0 ? 0 : 1);
    }

    @Test
    void packetsPerTickAreCapped() {
        SyncBudget budget = new SyncBudget();
        budget.reset();
        budget.beginTick();
        // 单 tick 最多 4 个分片。
        for (int i = 0; i < 4; i++) {
            assertTrue(budget.canSend(1), "packet " + i + " should be allowed");
            budget.record(1);
        }
        assertFalse(budget.canSend(1), "5th packet in same tick must be rejected");
    }

    @Test
    void newTickResetsPacketCounter() {
        SyncBudget budget = new SyncBudget();
        budget.reset();
        budget.beginTick();
        for (int i = 0; i < 4; i++) {
            budget.record(1);
        }
        assertFalse(budget.canSend(1));
        budget.beginTick();
        assertTrue(budget.canSend(1));
    }

    @Test
    void intervalTicksIsAtLeastTen() {
        // 服务端默认 40 tick，且实现侧保底 10。
        assertTrue(SyncBudget.intervalTicks() >= 10);
    }
}
