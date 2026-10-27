package dev.dubhe.map.sync;

import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.network.ChunkDeltaPayload;
import dev.dubhe.map.network.ChunkDigestPayload;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        Assertions.assertEquals(2, deltas.size());
    }

    @Test
    void outdatedChunksAreIncluded() {
        List<ChunkMapData> local = List.of(chunk(2, 3, 200L));
        // 对方版本更低，需回传。
        List<ChunkDigestPayload.Entry> theirs = List.of(new ChunkDigestPayload.Entry(2, 3, 150L));
        List<ChunkDeltaPayload.ChunkData> deltas =
                ClientMapSync.computeDeltas(theirs, local);
        Assertions.assertEquals(1, deltas.size());
        Assertions.assertEquals(2, deltas.get(0).chunkX());
        Assertions.assertEquals(3, deltas.get(0).chunkZ());
    }

    @Test
    void upToDateChunksAreSkipped() {
        List<ChunkMapData> local = List.of(chunk(4, 5, 300L));
        // 对方版本相同或更高，无需回传。
        List<ChunkDigestPayload.Entry> same = List.of(new ChunkDigestPayload.Entry(4, 5, 300L));
        List<ChunkDigestPayload.Entry> newer = List.of(new ChunkDigestPayload.Entry(4, 5, 400L));
        Assertions.assertTrue(ClientMapSync.computeDeltas(same, local).isEmpty());
        Assertions.assertTrue(ClientMapSync.computeDeltas(newer, local).isEmpty());
    }

    @Test
    void negativeCoordinatesStayDistinct() {
        // 负坐标必须能与正坐标区分，否则会误判为同一区块。
        List<ChunkMapData> local = List.of(chunk(-1, -1, 100L));
        List<ChunkDigestPayload.Entry> positive = List.of(new ChunkDigestPayload.Entry(1, 1, 100L));
        List<ChunkDeltaPayload.ChunkData> deltas =
                ClientMapSync.computeDeltas(positive, local);
        Assertions.assertEquals(1, deltas.size(), "(-1,-1) 与 (1,1) 必须视为不同区块");
    }

    @Test
    void chunkKeyDistinguishesAxisOrder() {
        // (x, z) 与 (z, x) 必须是不同的键。
        Assertions.assertTrue(ClientMapSync.chunkKey(3, 7) != ClientMapSync.chunkKey(7, 3));
        Assertions.assertTrue(ClientMapSync.chunkKey(-3, 7) != ClientMapSync.chunkKey(3, -7));
    }

    private static ChunkMapData chunk(int chunkX, int chunkZ, long version) {
        ChunkMapData data = new ChunkMapData(chunkX, chunkZ);
        data.setVersion(version);
        return data;
    }
}
