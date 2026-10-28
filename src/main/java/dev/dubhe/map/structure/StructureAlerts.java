package dev.dubhe.map.structure;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

/// 进入和离开采用不同水平距离，避免在提醒边界反复播报。
public final class StructureAlerts {
    private final Set<String> nearby = new HashSet<>();
    private final Queue<StructureRecord> queue = new ArrayDeque<>();

    public void update(Collection<StructureRecord> records, Predicate<StructureRecord> watched, double x, double z) {
        Set<String> retained = new HashSet<>();
        for (StructureRecord record : records) {
            if (watched.test(record) && record.distanceSquared(x, z) <= 80 * 80) {
                retained.add(record.key());
            }
        }
        this.nearby.retainAll(retained);
        this.queue.removeIf(record -> !retained.contains(record.key()));
        records.stream().filter(watched).filter(record -> record.distanceSquared(x, z) <= 64 * 64)
                .sorted(Comparator.comparingDouble(record -> record.distanceSquared(x, z)))
                .forEach(record -> {
                    if (this.nearby.add(record.key())) {
                        this.queue.add(record);
                    }
                });
    }

    public @Nullable StructureRecord next() {
        return this.queue.poll();
    }

    public void clear() {
        this.nearby.clear();
        this.queue.clear();
    }
}
