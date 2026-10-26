package dev.dubhe.atlas.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/// 区块采样器：把客户端已加载区块转换为 16×16 的地图像素。
///
/// 每列自顶向下寻找具有地图色的方块，陆地按相邻高差调色，水体按深度调色。
public final class ChunkMapSampler {
    private ChunkMapSampler() {
    }

    /// 采样一个已加载区块；区块未加载时返回 {@code null}。
    public static ChunkMapData sample(Level level, ChunkPos pos) {
        return sample(level, pos, null);
    }

    /// 按指定起始高度采样。
    ///
    /// `layerTopY` 为 {@code null} 时从世界顶部向下扫描（地表模式）；
    /// 指定高度时仅扫描该高度以下（洞穴模式），使地图反映玩家所在层级的结构。
    public static ChunkMapData sample(Level level, ChunkPos pos, Integer layerTopY) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) {
            return null;
        }
        ChunkMapData data = new ChunkMapData(pos.x, pos.z);
        int minY = level.getMinY();
        int topY = layerTopY == null
                ? level.getMaxY() - 1
                : Math.min(layerTopY, level.getMaxY() - 1);
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // 原版地图沿 Z 方向比较相邻列高度，因此按 X、Z 的顺序处理像素。
        for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
            int previousHeight = Integer.MIN_VALUE;
            for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                int argb = MapPalette.TRANSPARENT;
                int surfaceY = minY;

                for (int y = topY; y >= minY; y--) {
                    cursor.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(cursor);
                    FluidState fluid = state.getFluidState();
                    if (state.isAir() || (fluid.isEmpty()
                            && state.getMapColor(level, cursor) == MapColor.NONE)) {
                        continue;
                    }
                    surfaceY = y;
                    MapColor mapColor = state.getMapColor(level, cursor);
                    int depth = 0;
                    if (!fluid.isEmpty()) {
                        // 水下的非实体方块使用流体本身的地图色，避免显示为透明或方块底色。
                        state = correctStateForFluidBlock(level, state, cursor);
                        mapColor = state.getMapColor(level, cursor);
                        depth = countFluidDepth(chunk, cursor, y, minY);
                    }
                    int referenceHeight = previousHeight == Integer.MIN_VALUE
                            ? surfaceY : previousHeight;
                    MapColor.Brightness brightness = mapColor == MapColor.WATER
                            ? MapPalette.brightnessForWater(depth, worldX, worldZ)
                            : MapPalette.brightnessForTerrain(surfaceY, referenceHeight,
                                    worldX, worldZ);
                    argb = MapPalette.colorOf(mapColor, brightness);
                    break;
                }

                data.setPixel(localX, localZ, argb, surfaceY);
                previousHeight = surfaceY;
            }
        }
        return data;
    }

    /// 与原版 `MapItem.getCorrectStateForFluidBlock` 一致，选择流体的地图状态。
    private static BlockState correctStateForFluidBlock(Level level, BlockState state,
            BlockPos.MutableBlockPos cursor) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !state.isFaceSturdy(level, cursor, Direction.UP)
                ? fluid.createLegacyBlock() : state;
    }

    private static int countFluidDepth(LevelChunk chunk, BlockPos.MutableBlockPos cursor,
            int startY, int minY) {
        int depth = 0;
        for (int y = startY; y >= minY && depth < 64; y--) {
            cursor.set(cursor.getX(), y, cursor.getZ());
            if (chunk.getBlockState(cursor).getFluidState().isEmpty()) {
                break;
            }
            depth++;
        }
        return depth;
    }
}
