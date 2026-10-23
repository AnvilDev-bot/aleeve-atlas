package dev.aleeve.atlas.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aleeve.atlas.map.ChunkMapData;
import dev.aleeve.atlas.map.MapPalette;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 小地图像素纹理（规格 §2.1 / §2.2）。
///
/// 以「区块槽位」方式维护一张固定大小的纹理：槽位 (slotX, slotZ) 对应世界区块
/// `(originChunkX + slotX, originChunkZ + slotZ)`。当玩家跨越窗口边界时整体平移槽位内容，
/// 仅在区块数据更新时重绘对应 16×16 区域，避免每帧重采样（规格 §12.1 性能要求）。
public final class MapTexture {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/maptexture");
    /// 纹理包含的区块数（每边）；9 → 144×144 像素。
    public static final int SLOTS = 9;
    public static final int PIXELS = SLOTS * ChunkMapData.SIZE;

    private final Identifier textureId;
    private final DynamicTexture texture;
    private final NativeImage image;
    private final ChunkMapData[][] slots = new ChunkMapData[SLOTS][SLOTS];
    private final Map<Long, ChunkMapData> source = new HashMap<>();
    private int originChunkX;
    private int originChunkZ;
    private boolean initialized;

    public MapTexture(Identifier textureId) {
        this.textureId = textureId;
        this.image = new NativeImage(PIXELS, PIXELS, false);
        this.texture = new DynamicTexture(() -> "aleeve_atlas minimap", image);
    }

    /// 注册纹理到纹理管理器；须在客户端资源管理器可用后调用。
    public void register() {
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
        initialized = true;
    }

    public Identifier textureId() {
        return textureId;
    }

    public int originChunkX() {
        return originChunkX;
    }

    public int originChunkZ() {
        return originChunkZ;
    }

    /// 以给定区块为中心重排窗口原点（若已居中则不变）。
    public void ensureCentered(int centerChunkX, int centerChunkZ) {
        int half = SLOTS / 2;
        int targetOriginX = centerChunkX - half;
        int targetOriginZ = centerChunkZ - half;
        if (targetOriginX == originChunkX && targetOriginZ == originChunkZ) {
            return;
        }
        int shiftX = targetOriginX - originChunkX;
        int shiftZ = targetOriginZ - originChunkZ;
        shiftSlots(shiftX, shiftZ);
        originChunkX = targetOriginX;
        originChunkZ = targetOriginZ;
        redrawAll();
    }

    /// 放入一个区块数据（放入后立即重绘其槽位）。
    public void putChunk(ChunkMapData data) {
        int slotX = data.chunkX() - originChunkX;
        int slotZ = data.chunkZ() - originChunkZ;
        source.put(ChunkPos.asLong(data.chunkX(), data.chunkZ()), data);
        if (slotX < 0 || slotX >= SLOTS || slotZ < 0 || slotZ >= SLOTS) {
            return;
        }
        slots[slotX][slotZ] = data;
        drawChunk(slotX, slotZ, data);
        upload();
    }

    /// 查询已缓存的区块数据。
    public ChunkMapData chunkAt(int chunkX, int chunkZ) {
        return source.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    public boolean ready() {
        return initialized;
    }

    /// 清空全部槽位与缓存（切换服务器或维度时调用）。
    public void clearAll() {
        for (int slotX = 0; slotX < SLOTS; slotX++) {
            for (int slotZ = 0; slotZ < SLOTS; slotZ++) {
                slots[slotX][slotZ] = null;
                clearChunk(slotX, slotZ);
            }
        }
        source.clear();
        originChunkX = 0;
        originChunkZ = 0;
        upload();
    }

    private void shiftSlots(int shiftX, int shiftZ) {
        ChunkMapData[][] next = new ChunkMapData[SLOTS][SLOTS];
        for (int slotX = 0; slotX < SLOTS; slotX++) {
            for (int slotZ = 0; slotZ < SLOTS; slotZ++) {
                int srcX = slotX + shiftX;
                int srcZ = slotZ + shiftZ;
                if (srcX >= 0 && srcX < SLOTS && srcZ >= 0 && srcZ < SLOTS) {
                    next[slotX][slotZ] = slots[srcX][srcZ];
                }
            }
        }
        for (int slotX = 0; slotX < SLOTS; slotX++) {
            System.arraycopy(next[slotX], 0, slots[slotX], 0, SLOTS);
        }
    }

    private void redrawAll() {
        for (int slotX = 0; slotX < SLOTS; slotX++) {
            for (int slotZ = 0; slotZ < SLOTS; slotZ++) {
                ChunkMapData data = slots[slotX][slotZ];
                if (data != null) {
                    drawChunk(slotX, slotZ, data);
                } else {
                    clearChunk(slotX, slotZ);
                }
            }
        }
        upload();
    }

    private void drawChunk(int slotX, int slotZ, ChunkMapData data) {
        int baseX = slotX * ChunkMapData.SIZE;
        int baseZ = slotZ * ChunkMapData.SIZE;
        for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
            for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                setPixel(baseX + localX, baseZ + localZ, data.colorAt(localX, localZ));
            }
        }
    }

    private void clearChunk(int slotX, int slotZ) {
        int baseX = slotX * ChunkMapData.SIZE;
        int baseZ = slotZ * ChunkMapData.SIZE;
        for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
            for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                setPixel(baseX + localX, baseZ + localZ, MapPalette.TRANSPARENT);
            }
        }
    }

    /// 写入像素；`NativeImage` 原点在左下，故翻转 Y 轴。
    private void setPixel(int x, int y, int argb) {
        if (x < 0 || x >= PIXELS || y < 0 || y >= PIXELS) {
            return;
        }
        image.setPixel(x, PIXELS - 1 - y, argb);
    }

    private void upload() {
        if (!initialized) {
            return;
        }
        texture.upload();
    }

    public void close() {
        try {
            texture.close();
        } catch (Exception e) {
            LOGGER.warn("Failed to close minimap texture", e);
        }
    }
}
