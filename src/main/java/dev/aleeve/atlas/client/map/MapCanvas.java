package dev.aleeve.atlas.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aleeve.atlas.map.ChunkMapData;
import dev.aleeve.atlas.map.MapDimensionCache;
import dev.aleeve.atlas.map.MapPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 全屏世界地图的画布纹理（规格 §2.3）。
///
/// 固定覆盖 {@link #CHUNKS_PER_SIDE}² 个区块，纹理原点由视野中心决定；
/// 视野变化时从维度缓存重绘整张画布并上传一次，避免逐帧重采样。
public final class MapCanvas {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/mapcanvas");
    /// 画布覆盖的区块数（每边）。
    ///
    /// 取 65：在最小区块像素尺寸（1 px/区块）下仍可覆盖 1040 像素的视口宽度，
    /// 避免缩小地图时出现大片空白；内存占用约 1040² × 4 ≈ 4.3 MB（规格 §2.3 / §12.1）。
    public static final int CHUNKS_PER_SIDE = 65;
    /// 画布边长（像素）。
    public static final int PIXELS = CHUNKS_PER_SIDE * ChunkMapData.SIZE;

    private final DynamicTexture texture;
    private final NativeImage image;
    private final Identifier textureId;
    private int originChunkX;
    private int originChunkZ;
    private boolean initialized;

    public MapCanvas(Identifier textureId) {
        this.textureId = textureId;
        this.image = new NativeImage(PIXELS, PIXELS, false);
        this.texture = new DynamicTexture(() -> "aleeve_atlas world map", image);
    }

    public void register() {
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
        initialized = true;
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
    public void setCenter(int centerChunkX, int centerChunkZ, MapDimensionCache cache) {
        int targetX = centerChunkX - CHUNKS_PER_SIDE / 2;
        int targetZ = centerChunkZ - CHUNKS_PER_SIDE / 2;
        if (initialized && targetX == originChunkX && targetZ == originChunkZ) {
            return;
        }
        originChunkX = targetX;
        originChunkZ = targetZ;
        redraw(cache);
    }

    private void redraw(MapDimensionCache cache) {
        fill(MapPalette.TRANSPARENT);
        if (cache != null) {
            for (int dx = 0; dx < CHUNKS_PER_SIDE; dx++) {
                for (int dz = 0; dz < CHUNKS_PER_SIDE; dz++) {
                    int chunkX = originChunkX + dx;
                    int chunkZ = originChunkZ + dz;
                    ChunkMapData data = cache.get(chunkX, chunkZ).orElse(null);
                    if (data != null) {
                        drawChunk(data, dx * ChunkMapData.SIZE, dz * ChunkMapData.SIZE);
                    }
                }
            }
        }
        if (initialized) {
            texture.upload();
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

    public void close() {
        try {
            texture.close();
        } catch (Exception e) {
            LOGGER.warn("Failed to close world map canvas", e);
        }
    }
}
