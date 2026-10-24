package dev.dubhe.atlas.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dubhe.atlas.map.ChunkMapData;
import dev.dubhe.atlas.network.ChunkDeltaPayload;
import dev.dubhe.atlas.network.ChunkDigestPayload;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 追赶同步的差异计算测试。
///
/// 摘要比对规则：对方缺失的区块需回传，对方版本更低的区块需回传，版本相同的跳过。
class ClientMapSyncDeltaTest {
    @Test
    void missingChunksAreIncluded() {
        List<ChunkMapData> local = List.of(chunk(0, 0, 100L), chunk(1, 0, 100L));
        // 对方摘要为空，两个区块都应回传。
        List<ChunkDeltaPayload.ChunkData> deltas =
                ClientMapSync.computeDeltas(List.of(), local);
        assertEquals(2, deltas.size());
    }

    @Test
    void outdatedChunksAreIncluded() {
        List<ChunkMapData> local = List.of(chunk(2, 3, 200L));
        // 对方版本更低，需回传。
        List<ChunkDigestPayload.Entry> theirs = List.of(new ChunkDigestPayload.Entry(2, 3, 150L));
        List<ChunkDeltaPayload.ChunkData> deltas =
                ClientMapSync.computeDeltas(theirs, local);
        assertEquals(1, deltas.size());
        assertEquals(2, deltas.get(0).chunkX());
        assertEquals(3, deltas.get(0).chunkZ());
    }

    @Test
    void upToDateChunksAreSkipped() {
        List<ChunkMapData> local = List.of(chunk(4, 5, 300L));
        // 对方版本相同或更高，无需回传。
        List<ChunkDigestPayload.Entry> same = List.of(new ChunkDigestPayload.Entry(4, 5, 300L));
        List<ChunkDigestPayload.Entry> newer = List.of(new ChunkDigestPayload.Entry(4, 5, 400L));
        assertTrue(ClientMapSync.computeDeltas(same, local).isEmpty());
        assertTrue(ClientMapSync.computeDeltas(newer, local).isEmpty());
    }

    @Test
    void negativeCoordinatesStayDistinct() {
        // 负坐标必须能与正坐标区分，否则会误判为同一区块。
        List<ChunkMapData> local = List.of(chunk(-1, -1, 100L));
        List<ChunkDigestPayload.Entry> positive = List.of(new ChunkDigestPayload.Entry(1, 1, 100L));
        List<ChunkDeltaPayload.ChunkData> deltas =
                ClientMapSync.computeDeltas(positive, local);
        assertEquals(1, deltas.size(), "(-1,-1) 与 (1,1) 必须视为不同区块");
    }

    @Test
    void chunkKeyDistinguishesAxisOrder() {
        // (x, z) 与 (z, x) 必须是不同的键。
        assertTrue(ClientMapSync.chunkKey(3, 7) != ClientMapSync.chunkKey(7, 3));
        assertTrue(ClientMapSync.chunkKey(-3, 7) != ClientMapSync.chunkKey(3, -7));
    }

    private static ChunkMapData chunk(int chunkX, int chunkZ, long version) {
        ChunkMapData data = new ChunkMapData(chunkX, chunkZ);
        data.setVersion(version);
        return data;
    }
}
