package dev.dubhe.atlas.territory;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 领地连通分量与加权极点锚点测试。
class TerritoryComponentsTest {
    @Test
    void diagonalContactsFormSingleComponent() {
        Set<ChunkPos> chunks = Set.of(
                new ChunkPos(0, 0),
                new ChunkPos(1, 1),   // 仅对角相接，仍属同一分量（8 邻域）
                new ChunkPos(2, 2));
        List<Set<ChunkPos>> components = TerritoryComponents.split(chunks);
        Assertions.assertEquals(1, components.size());
    }

    @Test
    void detachedEnclavesSplitIntoSeparateComponents() {
        Set<ChunkPos> chunks = Set.of(
                new ChunkPos(0, 0), new ChunkPos(1, 0),
                new ChunkPos(10, 10), new ChunkPos(11, 10));
        List<Set<ChunkPos>> components = TerritoryComponents.split(chunks);
        Assertions.assertEquals(2, components.size(), "互不相连的飞地必须拆分为独立分量");
        // 排序稳定：按分量最小区块的 (x, z) 升序。
        Assertions.assertEquals(new ChunkPos(0, 0), TerritoryComponents.minOf(components.get(0)));
        Assertions.assertEquals(new ChunkPos(10, 10), TerritoryComponents.minOf(components.get(1)));
    }

    @Test
    void anchorStaysInsideConcaveShape() {
        // L 形（凹多边形）：几何重心落在缺口处，加权极点必须选在领地内部。
        Set<ChunkPos> shape = new HashSet<>();
        for (int x = 0; x < 4; x++) {
            shape.add(new ChunkPos(x, 0));
            shape.add(new ChunkPos(x, 1));
        }
        for (int z = 2; z < 5; z++) {
            shape.add(new ChunkPos(0, z));
        }
        ChunkPos anchor = AnchorSelector.select(shape, AnchorSelector.DEFAULT_LAMBDA);
        Assertions.assertTrue(shape.contains(anchor), "锚点必须落在领地内部");
    }

    @Test
    void anchorIsDeterministic() {
        Set<ChunkPos> square = new HashSet<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                square.add(new ChunkPos(x, z));
            }
        }
        ChunkPos first = AnchorSelector.select(square, 0.4F);
        ChunkPos second = AnchorSelector.select(square, 0.4F);
        Assertions.assertEquals(first, second, "重复计算不得产生跳动");
    }

    @Test
    void anchorPrefersDeepInteriorForRingWithHole() {
        // 环形领地（3x3 外圈 + 中心孔洞）：锚点应选择距边界最远的环带区块。
        Set<ChunkPos> ring = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean border = Math.abs(x) == 2 || Math.abs(z) == 2;
                if (border) {
                    ring.add(new ChunkPos(x, z));
                }
            }
        }
        ChunkPos anchor = AnchorSelector.select(ring, AnchorSelector.DEFAULT_LAMBDA);
        Assertions.assertTrue(ring.contains(anchor));
        Assertions.assertFalse(anchor.equals(new ChunkPos(0, 0)), "孔洞中心不属于领地，不可作为锚点");
    }

    @Test
    void emptyComponentHasNoAnchor() {
        Assertions.assertEquals(null, AnchorSelector.select(Set.of(), 0.4F));
    }
}
