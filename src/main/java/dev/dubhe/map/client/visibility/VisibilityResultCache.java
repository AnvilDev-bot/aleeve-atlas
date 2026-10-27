package dev.dubhe.map.client.visibility;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// GPU 遮挡查询的异步结果缓存。
///
/// 查询由渲染线程提交、GPU 异步执行，结果通常滞后若干帧。本类只保存状态，不接触 GL：
/// 每帧先登记本帧观察到的目标，再读取已就绪的结果，未就绪的目标沿用上一有效值。
/// 从未取得结果的目标默认不可见，避免未被确认的目标泄露到地图上。
public final class VisibilityResultCache {
    /// 单个目标的结果记录。
    static final class Entry {
        /// 查询池槽位；查询完成或从未提交时为 -1。
        int slot = -1;
        boolean pending;
        boolean visible;
        /// 最近一次读取到的原始样本数，用于诊断与验收。
        int samples;
        /// 最近一次确认可见的时间戳，用于判断结果是否过期。
        long lastVisibleMillis;
        long lastSeenFrame;
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    /// 登记本帧仍存在的目标，返回其记录。
    public Entry observe(UUID id, long frame) {
        Entry entry = this.entries.get(id);
        if (entry == null) {
            entry = new Entry();
            this.entries.put(id, entry);
        }
        entry.lastSeenFrame = frame;
        return entry;
    }

    /// 绑定查询池槽位并标记为待读。
    public void attach(Entry entry, int slot) {
        entry.slot = slot;
        entry.pending = true;
    }

    /// 写入已就绪的查询结果，归还池槽位。
    public void complete(Entry entry, boolean visible, long nowMillis) {
        this.complete(entry, visible, 0, nowMillis);
    }

    /// 写入已就绪的查询结果，同时记录原始样本数。
    public void complete(Entry entry, boolean visible, int samples, long nowMillis) {
        entry.pending = false;
        entry.slot = -1;
        entry.visible = visible;
        entry.samples = samples;
        if (visible) {
            entry.lastVisibleMillis = nowMillis;
        }
    }

    /// 最近一次读取到的样本数；没有结果时为 0。
    public int samples(UUID id) {
        Entry entry = this.entries.get(id);
        return entry == null ? 0 : entry.samples;
    }

    /// 取消未提交成功的查询，归还池槽位并保留上一结果。
    public void cancel(Entry entry) {
        entry.pending = false;
        entry.slot = -1;
    }

    /// 当前应采用的可见性：pending 期间沿用上一有效值，未取得结果时默认不可见。
    public boolean isVisible(UUID id) {
        Entry entry = this.entries.get(id);
        return entry != null && entry.visible;
    }

    /// 该目标是否仍处于 pending 状态。
    public boolean isPending(UUID id) {
        Entry entry = this.entries.get(id);
        return entry != null && entry.pending;
    }

    /// 最近一次确认可见距今的毫秒数；从未可见时返回 {@link Long#MAX_VALUE}。
    public long millisSinceVisible(UUID id, long nowMillis) {
        Entry entry = this.entries.get(id);
        if (entry == null || entry.lastVisibleMillis == 0L) {
            return Long.MAX_VALUE;
        }
        return nowMillis - entry.lastVisibleMillis;
    }

    /// 移除超过保留期限且不在 pending 的目标，返回被移除的目标以便回收查询。
    public void prune(long frame, int maxAgeFrames, List<Entry> removed) {
        Iterator<Map.Entry<UUID, Entry>> iterator = this.entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entry> current = iterator.next();
            Entry entry = current.getValue();
            if (entry.pending || entry.lastSeenFrame > frame - maxAgeFrames) {
                continue;
            }
            removed.add(entry);
            iterator.remove();
        }
    }

    /// 移除非 pending 的目标，用于维度或会话切换时丢弃陈旧判定。
    public void clearIdle(List<Entry> removed) {
        Iterator<Map.Entry<UUID, Entry>> iterator = this.entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entry> current = iterator.next();
            if (current.getValue().pending) {
                continue;
            }
            removed.add(current.getValue());
            iterator.remove();
        }
    }

    /// 收集全部记录，用于释放资源。
    public List<Entry> all() {
        return List.copyOf(this.entries.values());
    }

    /// 清空全部记录。
    public void clear() {
        this.entries.clear();
    }

    public int size() {
        return this.entries.size();
    }

    /// 查询占用中的池槽位，避免重复分配。
    public Set<Integer> occupiedSlots() {
        Set<Integer> slots = new HashSet<>();
        for (Entry entry : this.entries.values()) {
            if (entry.slot >= 0) {
                slots.add(entry.slot);
            }
        }
        return slots;
    }
}
