package dev.dubhe.map.client.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 原生纹理坐标下的地图搬移测试。
class CanvasScrollFlipTest {
    private static final int SIZE = 6;

    @Test
    void nativeTextureKeepsLogicalMoveDirection() {
        int[] original = new int[SIZE * SIZE];
        for (int i = 0; i < original.length; i++) {
            original[i] = i + 1;
        }
        int[] texture = original.clone();
        int shift = 2;
        int[] order = CanvasScroll.safeOrder(SIZE, shift);
        for (int col = 0; col < SIZE; col++) {
            for (int row : order) {
                texture[row * SIZE + col] = texture[(row - shift) * SIZE + col];
            }
        }
        for (int targetRow = shift; targetRow < SIZE; targetRow++) {
            for (int col = 0; col < SIZE; col++) {
                Assertions.assertEquals(original[(targetRow - shift) * SIZE + col],
                        texture[targetRow * SIZE + col]);
            }
        }
    }
}
