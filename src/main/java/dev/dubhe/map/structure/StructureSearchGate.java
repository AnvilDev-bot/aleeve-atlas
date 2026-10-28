package dev.dubhe.map.structure;

import org.jspecify.annotations.Nullable;
import dev.dubhe.map.structure.StructurePayloads.SearchStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// 查询准入与计算分开，繁忙拒绝不消耗玩家的冷却时间。
public final class StructureSearchGate {
    private final Map<UUID, Long> accepted = new HashMap<>();
    private boolean busy;

    public @Nullable SearchStatus begin(UUID player, long milliseconds) {
        if (this.busy) {
            return StructurePayloads.SearchStatus.BUSY;
        }
        Long previous = this.accepted.get(player);
        if (previous != null && milliseconds - previous < 10_000) {
            return StructurePayloads.SearchStatus.COOLDOWN;
        }
        this.busy = true;
        this.accepted.put(player, milliseconds);
        return null;
    }

    public void finish() {
        this.busy = false;
    }

    public void clear(UUID player) {
        this.accepted.remove(player);
    }

    public void reset() {
        this.accepted.clear();
        this.busy = false;
    }
}
