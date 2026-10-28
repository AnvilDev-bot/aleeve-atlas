package dev.dubhe.map.sync;

import dev.dubhe.map.network.ChunkDigestPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// 收齐摘要后才能计算缺失区块，否则其他分片中的已有区块会被误判为缺失。
final class DigestAssembly {
    private final Map<Integer, List<ChunkDigestPayload.Entry>> parts = new HashMap<>();
    private int expected;
    private long updated;

    List<ChunkDigestPayload.Entry> add(ChunkDigestPayload payload, long now) {
        if (this.expected != payload.batchCount() || now - this.updated > 60_000L) {
            this.parts.clear();
        }
        this.expected = payload.batchCount();
        this.updated = now;
        this.parts.put(payload.batchIndex(), payload.entries());
        if (this.parts.size() != this.expected) {
            return null;
        }
        return this.parts.values().stream().flatMap(List::stream).toList();
    }

    boolean expired(long now) {
        return now - this.updated > 60_000L;
    }
}
