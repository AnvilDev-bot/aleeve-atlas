package dev.dubhe.map.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StructureAlertsTest {
    @Test
    void entryHysteresisAndSourceUpgradeDoNotRepeatAlerts() {
        var record = StructureStoreTest.record(0, StructureRecord.Source.SEARCHED);
        var tracker = new StructureAlerts();
        tracker.update(List.of(record), r -> true, record.maxX() + 65, 0);
        assertNull(tracker.next());
        tracker.update(List.of(record), r -> true, record.maxX() + 64, 0);
        assertEquals(record, tracker.next());
        var discovered = record.withSource(StructureRecord.Source.DISCOVERED);
        tracker.update(List.of(discovered), r -> true, record.maxX() + 80, 0);
        tracker.update(List.of(discovered), r -> true, 0, 0);
        assertNull(tracker.next());
        tracker.update(List.of(discovered), r -> true, record.maxX() + 81, 0);
        tracker.update(List.of(discovered), r -> true, 0, 0);
        assertEquals(discovered, tracker.next());
    }

    @Test
    void queuesTargetsOnceAndDropsUnwatchedOrDepartedTargets() {
        var first = StructureStoreTest.record(0, StructureRecord.Source.DISCOVERED);
        var second = StructureStoreTest.record(3, StructureRecord.Source.SHARED);
        var records = List.of(first, second);
        var tracker = new StructureAlerts();
        tracker.update(records, r -> r.key().equals(first.key()) || r.kind() == first.kind(), 0, 0);
        assertEquals(first, tracker.next());
        tracker.update(records, r -> r.key().equals(first.key()), 0, 0);
        assertNull(tracker.next());
        tracker.clear();
        tracker.update(records, r -> true, 0, 0);
        tracker.update(records, r -> true, 10000, 10000);
        assertNull(tracker.next());
        tracker.clear();
        tracker.update(records, r -> true, 0, 0);
        assertEquals(first, tracker.next());
        assertEquals(second, tracker.next());
        assertNull(tracker.next());
    }
}
