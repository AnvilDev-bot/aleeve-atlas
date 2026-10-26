package dev.dubhe.atlas.client.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 翻转 Z 轴的纹理存储与逻辑地图搬移方向测试。
class CanvasScrollFlipTest {
    private static final int SIZE = 6;

    @Test
    void flippedTextureKeepsLogicalMoveDirection() {
        int[] logical = new int[SIZE * SIZE];
        for (int i = 0; i < logical.length; i++) {
            logical[i] = i + 1;
        }
        int[] texture = toFlippedTexture(logical);
        int logicalShift = 2;
        int textureShift = logicalShift;
        int[] order = CanvasScroll.safeOrder(SIZE, textureShift);
        for (int col = 0; col < SIZE; col++) {
            for (int row : order) {
                texture[row * SIZE + col] = texture[(row - textureShift) * SIZE + col];
            }
        }
        int[] moved = fromFlippedTexture(texture);
        for (int targetRow = 0; targetRow < SIZE - logicalShift; targetRow++) {
            for (int col = 0; col < SIZE; col++) {
                Assertions.assertEquals(logical[(targetRow + logicalShift) * SIZE + col],
                        moved[targetRow * SIZE + col]);
            }
        }
    }

    private static int[] toFlippedTexture(int[] logical) {
        int[] texture = new int[logical.length];
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                texture[(SIZE - 1 - row) * SIZE + col] = logical[row * SIZE + col];
            }
        }
        return texture;
    }

    private static int[] fromFlippedTexture(int[] texture) {
        return toFlippedTexture(texture);
    }
}
