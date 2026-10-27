package dev.dubhe.map.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dubhe.map.client.profiling.AtlasProfiling;
import dev.dubhe.map.config.MapColorScheme;
import dev.dubhe.map.map.ChunkColorizer;
import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.map.MapDimensionCache;
import dev.dubhe.map.map.MapPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

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
    private final Map<Long, Long> drawnVersions = new HashMap<>();
    private MapColorScheme colorScheme = MapColorScheme.VANILLA;

    public MapCanvas(Identifier textureId) {
        this.textureId = textureId;
        this.image = new NativeImage(PIXELS, PIXELS, false);
        this.texture = new DynamicTexture(() -> "aleeve_atlas world map", this.image);
    }

    public void register() {
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
        this.initialized = true;
    }

    /// 资源重载后标记为未初始化，使下次打开世界地图时重新注册纹理。
    ///
    /// 资源重载会清空纹理管理器，若继续认为纹理有效，画布会渲染为空白。
    public void onResourceReload() {
        this.initialized = false;
        this.positioned = false;
    }

    public Identifier textureId() {
        return this.textureId;
    }

    public boolean ready() {
        return this.initialized;
    }

    public int originChunkX() {
        return this.originChunkX;
    }

    public int originChunkZ() {
        return this.originChunkZ;
    }

    int pixelAt(int x, int z) {
        return this.image.getPixel(x, z);
    }

    /// 以给定区块为中心的视野重建画布；视野未变化时直接返回。
    ///
    /// 原点小幅平移时复用已有像素并只补绘新进入边界的条带，
    /// 避免拖拽过程中每次都遍历全部区块。
    public void setCenter(int centerChunkX, int centerChunkZ, MapDimensionCache cache,
            MapColorScheme scheme) {
        int targetX = centerChunkX - CHUNKS_PER_SIDE / 2;
        int targetZ = centerChunkZ - CHUNKS_PER_SIDE / 2;
        if (scheme != this.colorScheme) {
            this.colorScheme = scheme;
            this.positioned = false;
        }
        if (this.positioned && targetX == this.originChunkX && targetZ == this.originChunkZ) {
            this.refreshChangedChunks(cache);
            return;
        }
        if (this.positioned
                && Math.abs(targetX - this.originChunkX) < RECENTER_THRESHOLD_CHUNKS
                && Math.abs(targetZ - this.originChunkZ) < RECENTER_THRESHOLD_CHUNKS) {
            this.refreshChangedChunks(cache);
            return;
        }
        long startedAt = System.nanoTime();
        // 仅当已有画布内容且位移在覆盖范围内时才复用像素，首次定位与大幅跳转走全量重绘。
        boolean scrolled = this.positioned
                && Math.abs(targetX - this.originChunkX) < CHUNKS_PER_SIDE
                && Math.abs(targetZ - this.originChunkZ) < CHUNKS_PER_SIDE
                && (targetX != this.originChunkX || targetZ != this.originChunkZ);
        if (scrolled) {
            long scrollStartedAt = System.nanoTime();
            this.shiftOrigin(targetX, targetZ, cache);
            AtlasProfiling.recordCanvasScroll(System.nanoTime() - scrollStartedAt);
            this.refreshChangedChunks(cache);
        } else {
            this.originChunkX = targetX;
            this.originChunkZ = targetZ;
            this.redraw(cache);
            this.positioned = true;
        }
        AtlasProfiling.recordCanvas(System.nanoTime() - startedAt);
    }

    /// 仅重绘缓存版本发生变化的槽位，探索时无需重建整张画布。
    private void refreshChangedChunks(MapDimensionCache cache) {
        if (cache == null) {
            return;
        }
        boolean changed = false;
        for (int dx = 0; dx < CHUNKS_PER_SIDE; dx++) {
            for (int dz = 0; dz < CHUNKS_PER_SIDE; dz++) {
                int chunkX = this.originChunkX + dx;
                int chunkZ = this.originChunkZ + dz;
                long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
                Long drawn = this.drawnVersions.get(key);
                ChunkMapData data = cache.get(chunkX, chunkZ).orElse(null);
                if (data == null && drawn == null) {
                    continue;
                }
                if (data != null && drawn != null && drawn == data.version()) {
                    continue;
                }
                this.redrawChunk(dx, dz, cache);
                changed = true;
            }
        }
        if (changed && this.initialized) {
            this.texture.upload();
        }
    }

    /// 平移到新原点：先把重叠区域按区块粒度搬移，再补绘边缘条带。
    private void shiftOrigin(int targetX, int targetZ, MapDimensionCache cache) {
        int shiftChunksX = targetX - this.originChunkX;
        int shiftChunksZ = targetZ - this.originChunkZ;
        int originShiftPixelsX = shiftChunksX * ChunkMapData.SIZE;
        int originShiftPixelsZ = shiftChunksZ * ChunkMapData.SIZE;
        movePixels(this.image, originShiftPixelsX, originShiftPixelsZ);
        this.originChunkX = targetX;
        this.originChunkZ = targetZ;
        this.drawEdgeStrips(cache, shiftChunksX, shiftChunksZ);
        if (this.initialized) {
            this.texture.upload();
        }
    }

    /// 原点移动后，同一世界坐标的像素必须保留在新的局部坐标处。
    static void movePixels(NativeImage image, int originShiftPixelsX, int originShiftPixelsZ) {
        int contentShiftPixelsX = CanvasScroll.contentShiftForOriginShift(originShiftPixelsX);
        int contentShiftPixelsZ = CanvasScroll.contentShiftForOriginShift(originShiftPixelsZ);
        int[] rowOrder = CanvasScroll.safeOrder(image.getHeight(), contentShiftPixelsZ);
        int[] colOrder = CanvasScroll.safeOrder(image.getWidth(), contentShiftPixelsX);
        for (int row : rowOrder) {
            int sourceRow = CanvasScroll.sourceIndexForOriginShift(row, originShiftPixelsZ);
            for (int col : colOrder) {
                int sourceCol = CanvasScroll.sourceIndexForOriginShift(col, originShiftPixelsX);
                image.setPixel(col, row, image.getPixel(sourceCol, sourceRow));
            }
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
                    this.redrawChunk(dx, dz, cache);
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
                    this.redrawChunk(dx, dz, cache);
                }
            }
        }
    }

    /// 按画布局部坐标重绘单个区块槽位。
    private void redrawChunk(int dx, int dz, MapDimensionCache cache) {
        int chunkX = this.originChunkX + dx;
        int chunkZ = this.originChunkZ + dz;
        int baseX = dx * ChunkMapData.SIZE;
        int baseZ = dz * ChunkMapData.SIZE;
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        ChunkMapData data = cache.get(chunkX, chunkZ).orElse(null);
        if (data != null) {
            this.drawChunk(data, baseX, baseZ);
            this.drawnVersions.put(key, data.version());
        } else {
            // 未探索且未被队友同步的区块绘制网格底纹。
            this.drawUnexplored(baseX, baseZ);
            this.drawnVersions.remove(key);
        }
    }

    private void redraw(MapDimensionCache cache) {
        this.drawnVersions.clear();
        this.fill(MapPalette.TRANSPARENT);
        if (cache != null) {
            for (int dx = 0; dx < CHUNKS_PER_SIDE; dx++) {
                for (int dz = 0; dz < CHUNKS_PER_SIDE; dz++) {
                    this.redrawChunk(dx, dz, cache);
                }
            }
        }
        if (this.initialized) {
            this.texture.upload();
        }
    }

    /// 未探索区块底纹：深色底加每 4 像素的网格线，与已探索地形形成明确区分。
    private void drawUnexplored(int baseX, int baseZ) {
        for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
            for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                boolean line = (localX & 3) == 0 || (localZ & 3) == 0;
                this.setPixel(baseX + localX, baseZ + localZ,
                        line ? UNEXPLORED_GRID_COLOR : UNEXPLORED_BASE_COLOR);
            }
        }
    }

    private void drawChunk(ChunkMapData data, int baseX, int baseZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] lighting = ChunkColorizer.lighting(data, this.colorScheme);
        for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
            for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                int shadow = lighting == null ? 0 : lighting[localZ * ChunkMapData.SIZE + localX];
                this.setPixel(baseX + localX, baseZ + localZ,
                        ChunkColorizer.colorAt(data, localX, localZ, this.colorScheme,
                                Minecraft.getInstance().level, cursor, shadow));
            }
        }
    }

    private void fill(int argb) {
        for (int y = 0; y < PIXELS; y++) {
            for (int x = 0; x < PIXELS; x++) {
                this.setPixel(x, y, argb);
            }
        }
    }

    /// 写入原生纹理坐标，X 向右、Y 向下，与原版地图纹理一致。
    private void setPixel(int x, int y, int argb) {
        if (x < 0 || x >= PIXELS || y < 0 || y >= PIXELS) {
            return;
        }
        this.image.setPixel(x, y, argb);
    }

    /// 释放纹理：从纹理管理器注销并关闭，避免注册表残留已关闭对象。
    public void close() {
        if (!this.initialized) {
            return;
        }
        try {
            Minecraft.getInstance().getTextureManager().release(this.textureId);
        } catch (Exception e) {
            LOGGER.warn("Failed to release world map canvas", e);
        } finally {
            this.initialized = false;
            // 关闭后画布内容失效，重新打开时必须走全量重绘。
            this.positioned = false;
        }
    }
}
