package dev.aleeve.atlas.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/// 区块采样器（规格 §2.1）：把客户端已加载区块转换为 16×16 的地图像素。
///
/// 采样策略：每列自顶向下寻找第一个非空气方块；若该方块为流体，使用其地图色并按深度压暗。
public final class ChunkMapSampler {
    private static final int SEA_LEVEL = 63;

    private ChunkMapSampler() {
    }

    /// 采样一个已加载区块；区块未加载时返回 {@code null}。
    public static ChunkMapData sample(Level level, ChunkPos pos) {
        return sample(level, pos, null);
    }

    /// 按指定起始高度采样（规格 §2.1 洞穴分层模式）。
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

        for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
            for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                int argb = MapPalette.TRANSPARENT;
                int surfaceY = minY;

                for (int y = topY; y >= minY; y--) {
                    cursor.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    FluidState fluid = state.getFluidState();
                    surfaceY = y;
                    if (!fluid.isEmpty()) {
                        MapColor fluidColor = state.getMapColor(level, cursor);
                        int depth = countFluidDepth(chunk, cursor, y, minY);
                        argb = MapPalette.waterColor(fluidColor, depth);
                    } else {
                        MapColor mapColor = state.getMapColor(level, cursor);
                        argb = MapPalette.colorOf(mapColor,
                                MapPalette.brightnessForHeight(y, SEA_LEVEL));
                        // 植被类方块按生物群系染色（§2.1 地形渲染要素）。
                        argb = MapPalette.tintWithBiome(argb, state, level, cursor,
                                level.getBiome(cursor));
                        // 地表光照阴影：按方块光照与天光调整明暗（§2.1）。
                        int light = level.getMaxLocalRawBrightness(cursor.above());
                        argb = MapPalette.applyLighting(argb, light);
                    }
                    break;
                }

                data.setPixel(localX, localZ, argb, surfaceY);
            }
        }
        return data;
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
