package dev.dubhe.map.client.territory;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/// 对角区块共享角点的补角规则测试。
class TerritoryDiagonalTest {
    @Test
    void diagonalOnlyConnectionAddsSharedCorner() {
        int[] connector = TerritoryRenderer.diagonalConnector(
                Set.of(new ChunkPos(0, 0), new ChunkPos(1, 1)),
                new ChunkPos(0, 0), 1, 10, 20, 16, 2);
        Assertions.assertArrayEquals(new int[] { 25, 35, 2, 2 }, connector);
    }

    @Test
    void orthogonalBridgeDoesNotAddDiagonalCorner() {
        Assertions.assertNull(TerritoryRenderer.diagonalConnector(
                Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(1, 1)),
                new ChunkPos(0, 0), 1, 10, 20, 16, 2));
    }
}
