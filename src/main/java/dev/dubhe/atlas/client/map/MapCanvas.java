package dev.dubhe.atlas.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dubhe.atlas.client.profiling.AtlasProfiling;
import dev.dubhe.atlas.map.ChunkMapData;
import dev.dubhe.atlas.map.MapDimensionCache;
import dev.dubhe.atlas.map.MapPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 全屏世界地图的画布纹理。
///
/// 固定覆盖 {@link #CHUNKS_PER_SIDE}² 个区块，纹理原点由视野中心决定；
/// 视野变化时从维度缓存重绘整张画布并上传一次，避免逐帧重采样。
public final class MapCanvas {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/mapcanvas");
    /// 画布覆盖的区块数（每边）。
    ///
    /// 取 65：在最小区块像素尺寸（1 px/区块）下仍可覆盖 1040 像素的视口宽度，
    /// 避免缩小地图时出现大片空白；内存占用约 1040² × 4 ≈ 4.3 MB。
    public static final int CHUNKS_PER_SIDE = 65;
    /// 画布边长（像素）。
    public static final int PIXELS = CHUNKS_PER_SIDE * ChunkMapData.SIZE;
    /// 触发画布重建的最小位移（区块）。
    ///
    /// 渲染端按画布原点与视野中心的差值补偿采样位置，因此允许画布滞后于视野；
    /// 取 2 区块（32 像素）可把拖拽期间的重建次数降低约一半。
    private static final int RECENTER_THRESHOLD_CHUNKS = 2;
    /// 未探索区域底色（接近纯黑，保持低干扰）。
    private static final int UNEXPLORED_BASE_COLOR = 0xFF0A0A0A;
    /// 未探索区域网格线颜色。
    private static final int UNEXPLORED_GRID_COLOR = 0xFF1E1E1E;

    private final DynamicTexture texture;
    private final NativeImage image;
    private final Identifier textureId;
    private int originChunkX;
    private int originChunkZ;
    private boolean initialized;
    /// 是否已按某个原点完成过一次定位；未定位时不能做像素搬移。
    private boolean positioned;

    public MapCanvas(Identifier textureId) {
        this.textureId = textureId;
        this.image = new NativeImage(PIXELS, PIXELS, false);
        this.texture = new DynamicTexture(() -> "aleeve_atlas world map", image);
    }

    public void register() {
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
        initialized = true;
    }

    /// 资源重载后标记为未初始化，使下次打开世界地图时重新注册纹理。
    ///
    /// 资源重载会清空纹理管理器，若继续认为纹理有效，画布会渲染为空白。
    public void onResourceReload() {
        initialized = false;
        positioned = false;
    }

    public Identifier textureId() {
        return textureId;
    }

    public boolean ready() {
        return initialized;
    }

    public int originChunkX() {
        return originChunkX;
    }

    public int originChunkZ() {
        return originChunkZ;
    }

    /// 以给定区块为中心的视野重建画布；视野未变化时直接返回。
    ///
    /// 原点小幅平移时复用已有像素并只补绘新进入边界的条带，
    /// 避免拖拽过程中每次都遍历全部区块。
    public void setCenter(int centerChunkX, int centerChunkZ, MapDimensionCache cache) {
        int targetX = centerChunkX - CHUNKS_PER_SIDE / 2;
        int targetZ = centerChunkZ - CHUNKS_PER_SIDE / 2;
        if (positioned && targetX == originChunkX && targetZ == originChunkZ) {
            return;
        }
        // 渲染端按画布原点与视野中心的差值补偿，因此画布可略滞后于视野。
        // 位移不足阈值时跳过重绘，降低拖拽过程中的重建频率。
        if (positioned
                && Math.abs(targetX - originChunkX) < RECENTER_THRESHOLD_CHUNKS
                && Math.abs(targetZ - originChunkZ) < RECENTER_THRESHOLD_CHUNKS) {
            return;
        }
        long startedAt = System.nanoTime();
        // 仅当已有画布内容且位移在覆盖范围内时才复用像素，首次定位与大幅跳转走全量重绘。
        boolean scrolled = positioned
                && Math.abs(targetX - originChunkX) < CHUNKS_PER_SIDE
                && Math.abs(targetZ - originChunkZ) < CHUNKS_PER_SIDE
                && (targetX != originChunkX || targetZ != originChunkZ);
        if (scrolled) {
            long scrollStartedAt = System.nanoTime();
            shiftOrigin(targetX, targetZ, cache);
            AtlasProfiling.recordCanvasScroll(System.nanoTime() - scrollStartedAt);
        } else {
            originChunkX = targetX;
            originChunkZ = targetZ;
            redraw(cache);
            positioned = true;
        }
        AtlasProfiling.recordCanvas(System.nanoTime() - startedAt);
    }

    /// 平移到新原点：先把重叠区域按区块粒度搬移，再补绘边缘条带。
    private void shiftOrigin(int targetX, int targetZ, MapDimensionCache cache) {
        int shiftChunksX = targetX - originChunkX;
        int shiftChunksZ = targetZ - originChunkZ;
        int shiftPixelsX = shiftChunksX * ChunkMapData.SIZE;
        int shiftPixelsZ = shiftChunksZ * ChunkMapData.SIZE;
        // 按行搬运重叠区域；遍历顺序与搬移方向相反，避免覆盖尚未读取的像素。
        int[] rowOrder = CanvasScroll.safeOrder(PIXELS, shiftPixelsZ);
        int[] colOrder = CanvasScroll.safeOrder(PIXELS, shiftPixelsX);
        for (int row : rowOrder) {
            int sourceRow = row - shiftPixelsZ;
            for (int col : colOrder) {
                int sourceCol = col - shiftPixelsX;
                image.setPixel(col, row, image.getPixel(sourceCol, sourceRow));
            }
        }
        originChunkX = targetX;
        originChunkZ = targetZ;
        drawEdgeStrips(cache, shiftChunksX, shiftChunksZ);
        if (initialized) {
            texture.upload();
        }
    }

    /// 补绘因平移而新进入视野的边缘条带。
    private void drawEdgeStrips(MapDimensionCache cache, int shiftChunksX, int shiftChunksZ) {
        if (cache == null) {
            return;
        }
        // 横向条带：左或右边缘的若干列。
        if (shiftChunksX != 0) {
            int firstDx = shiftChunksX > 0 ? CHUNKS_PER_SIDE - shiftChunksX : 0;
            int count = Math.abs(shiftChunksX);
            for (int i = 0; i < count; i++) {
                int dx = firstDx + i;
                for (int dz = 0; dz < CHUNKS_PER_SIDE; dz++) {
                    redrawChunk(dx, dz, cache);
                }
            }
        }
        // 纵向条带：上或下边缘的若干行，跳过已由横向条带覆盖的角落。
        if (shiftChunksZ != 0) {
            int firstDz = shiftChunksZ > 0 ? CHUNKS_PER_SIDE - shiftChunksZ : 0;
            int count = Math.abs(shiftChunksZ);
            for (int i = 0; i < count; i++) {
                int dz = firstDz + i;
                for (int dx = 0; dx < CHUNKS_PER_SIDE; dx++) {
                    if (shiftChunksX != 0) {
                        int firstDx = shiftChunksX > 0 ? CHUNKS_PER_SIDE - shiftChunksX : 0;
                        if (dx >= firstDx && dx < firstDx + Math.abs(shiftChunksX)) {
                            continue;
                        }
                    }
                    redrawChunk(dx, dz, cache);
                }
            }
        }
    }

    /// 按画布局部坐标重绘单个区块槽位。
    private void redrawChunk(int dx, int dz, MapDimensionCache cache) {
        int chunkX = originChunkX + dx;
        int chunkZ = originChunkZ + dz;
        int baseX = dx * ChunkMapData.SIZE;
        int baseZ = dz * ChunkMapData.SIZE;
        ChunkMapData data = cache.get(chunkX, chunkZ).orElse(null);
        if (data != null) {
            drawChunk(data, baseX, baseZ);
        } else {
            // 未探索且未被队友同步的区块绘制网格底纹。
            drawUnexplored(baseX, baseZ);
        }
    }

    private void redraw(MapDimensionCache cache) {
        fill(MapPalette.TRANSPARENT);
        if (cache != null) {
            for (int dx = 0; dx < CHUNKS_PER_SIDE; dx++) {
                for (int dz = 0; dz < CHUNKS_PER_SIDE; dz++) {
                    redrawChunk(dx, dz, cache);
                }
            }
        }
        if (initialized) {
            texture.upload();
        }
    }

    /// 未探索区块底纹：深色底加每 4 像素的网格线，与已探索地形形成明确区分。
    private void drawUnexplored(int baseX, int baseZ) {
        for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
            for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                boolean line = (localX & 3) == 0 || (localZ & 3) == 0;
                setPixel(baseX + localX, baseZ + localZ,
                        line ? UNEXPLORED_GRID_COLOR : UNEXPLORED_BASE_COLOR);
            }
        }
    }

    private void drawChunk(ChunkMapData data, int baseX, int baseZ) {
        for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
            for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                setPixel(baseX + localX, baseZ + localZ, data.colorAt(localX, localZ));
            }
        }
    }

    private void fill(int argb) {
        for (int y = 0; y < PIXELS; y++) {
            for (int x = 0; x < PIXELS; x++) {
                setPixel(x, y, argb);
            }
        }
    }

    private void setPixel(int x, int y, int argb) {
        if (x < 0 || x >= PIXELS || y < 0 || y >= PIXELS) {
            return;
        }
        image.setPixel(x, PIXELS - 1 - y, argb);
    }

    /// 释放纹理：从纹理管理器注销并关闭，避免注册表残留已关闭对象。
    public void close() {
        if (!initialized) {
            return;
        }
        try {
            Minecraft.getInstance().getTextureManager().release(textureId);
        } catch (Exception e) {
            LOGGER.warn("Failed to release world map canvas", e);
        } finally {
            initialized = false;
            // 关闭后画布内容失效，重新打开时必须走全量重绘。
            positioned = false;
        }
    }
}
