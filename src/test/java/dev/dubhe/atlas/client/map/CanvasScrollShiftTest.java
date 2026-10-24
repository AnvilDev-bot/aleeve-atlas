package dev.dubhe.atlas.client.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// 画布像素搬移的重叠正确性测试。
///
/// 源区域与目标区域重叠时，遍历顺序错误会覆盖尚未读取的像素，产生画面撕裂。
/// 此处用整型网格模拟实际搬移过程，验证搬移结果是原图的有效子区域。
class CanvasScrollShiftTest {
    private static final int SIZE = 8;

    @Test
    void shiftRightKeepsOriginalRelativeOrder() {
        int[] original = fill(SIZE);
        int[] grid = original.clone();
        shift(grid, SIZE, 3, true);
        for (int row = 0; row < SIZE; row++) {
            for (int targetCol = 3; targetCol < SIZE; targetCol++) {
                int sourceCol = targetCol - 3;
                assertEquals(original[index(row, sourceCol)], grid[row * SIZE + targetCol],
                        "行 " + row + " 目标列 " + targetCol + " 应来自源列 " + sourceCol);
            }
        }
    }

    @Test
    void shiftLeftKeepsOriginalRelativeOrder() {
        int[] original = fill(SIZE);
        int[] grid = original.clone();
        shift(grid, SIZE, -3, true);
        for (int row = 0; row < SIZE; row++) {
            for (int targetCol = 0; targetCol < SIZE - 3; targetCol++) {
                int sourceCol = targetCol + 3;
                assertEquals(original[index(row, sourceCol)], grid[row * SIZE + targetCol],
                        "行 " + row + " 目标列 " + targetCol + " 应来自源列 " + sourceCol);
            }
        }
    }

    @Test
    void shiftDownKeepsOriginalRelativeOrder() {
        int[] original = fill(SIZE);
        int[] grid = original.clone();
        shift(grid, SIZE, 3, false);
        for (int targetRow = 3; targetRow < SIZE; targetRow++) {
            int sourceRow = targetRow - 3;
            for (int col = 0; col < SIZE; col++) {
                assertEquals(original[index(sourceRow, col)], grid[targetRow * SIZE + col],
                        "目标行 " + targetRow + " 列 " + col + " 应来自源行 " + sourceRow);
            }
        }
    }

    @Test
    void shiftUpKeepsOriginalRelativeOrder() {
        int[] original = fill(SIZE);
        int[] grid = original.clone();
        shift(grid, SIZE, -3, false);
        for (int targetRow = 0; targetRow < SIZE - 3; targetRow++) {
            int sourceRow = targetRow + 3;
            for (int col = 0; col < SIZE; col++) {
                assertEquals(original[index(sourceRow, col)], grid[targetRow * SIZE + col],
                        "目标行 " + targetRow + " 列 " + col + " 应来自源行 " + sourceRow);
            }
        }
    }

    @Test
    void overlapLengthNeverExceedsGridSize() {
        for (int shift = -SIZE + 1; shift < SIZE; shift++) {
            int overlap = CanvasScroll.overlapLength(SIZE, shift);
            assertTrue(overlap > 0 && overlap <= SIZE,
                    "shift=" + shift + " 时重叠长度应在 (0, " + SIZE + "] 内，实际 " + overlap);
        }
    }

    /// 初始值设为 index*2+1，使每个像素值唯一可追溯来源。
    private static int[] fill(int size) {
        int[] grid = new int[size * size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                grid[row * size + col] = index(row, col) * 2 + 1;
            }
        }
        return grid;
    }

    private static int index(int row, int col) {
        return row * SIZE + col;
    }

    /// 按 CanvasScroll 的遍历顺序搬移网格，模拟 MapCanvas 的重叠搬移。
    private static void shift(int[] grid, int size, int shift, boolean horizontal) {
        int[] order = CanvasScroll.safeOrder(size, shift);
        for (int outer = 0; outer < size; outer++) {
            for (int position : order) {
                int source = position - shift;
                if (horizontal) {
                    grid[outer * size + position] = grid[outer * size + source];
                } else {
                    grid[position * size + outer] = grid[source * size + outer];
                }
            }
        }
    }
}
