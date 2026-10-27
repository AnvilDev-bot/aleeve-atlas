package dev.dubhe.map.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.map.MapPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/// 小地图像素纹理。
///
/// 纹理固定覆盖 {@link #COVERED_BLOCKS} 见方的世界范围，且**玩家始终位于纹理中心**：
/// 玩家移动时按像素平移纹理内容，因此 HUD 直接用整张纹理绘制，UV 恒为 `[0, 1]`，
/// 圆形遮罩与边框可由片元着色器按 UV 解析式判定（无需每帧逐行填充）。
public final class MapTexture {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/maptexture");
    /// 纹理覆盖的区块数（每边）。
    public static final int SLOTS = 9;
    /// 纹理边长（像素），等于覆盖的方块数。
    public static final int PIXELS = SLOTS * ChunkMapData.SIZE;

    private final Identifier textureId;
    /// 是否存在待上传的像素改动。
    private boolean dirty;

    private final DynamicTexture texture;
    private final NativeImage image;
    /// 以区块坐标索引的已采样数据。
    private final Map<Long, ChunkMapData> source = new HashMap<>();
    /// 已绘制区块的数据版本，用于跳过未变更区块的重绘。
    private final Map<Long, Long> drawnVersions = new HashMap<>();
    /// 纹理左上角对应的世界像素坐标。
    private int originPixelX;
    private int originPixelZ;
    private boolean initialized;
    private boolean centered;

    public MapTexture(Identifier textureId) {
        this.textureId = textureId;
        this.image = new NativeImage(PIXELS, PIXELS, false);
        this.texture = new DynamicTexture(() -> "aleeve_atlas minimap", this.image);
    }

    /// 注册纹理到纹理管理器；须在客户端资源管理器可用后调用。
    public void register() {
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
        this.initialized = true;
    }

    public Identifier textureId() {
        return this.textureId;
    }

    public int originPixelX() {
        return this.originPixelX;
    }

    public int originPixelZ() {
        return this.originPixelZ;
    }

    public boolean ready() {
        return this.initialized;
    }

    /// 放入一个区块数据；仅当数据版本变化时重绘该区块，上传延后到 `flushDirty`。
    ///
    /// 每 tick 都会把视野内已缓存区块重新放入，若不做版本比对会持续重绘未变更的区块，
    /// 违反「仅在区块加载或方块变更时重渲染」。
    public void putChunk(ChunkMapData data) {
        long key = ChunkPos.asLong(data.chunkX(), data.chunkZ());
        Long drawnVersion = this.drawnVersions.get(key);
        this.source.put(key, data);
        if (!this.initialized || !this.centered) {
            return;
        }
        if (drawnVersion != null && drawnVersion == data.version()) {
            return;
        }
        this.drawChunk(data);
        this.drawnVersions.put(key, data.version());
        this.dirty = true;
    }

    /// 若有脏区块则上传一次纹理（每 tick 末尾调用）。
    public void flushDirty() {
        if (this.dirty && this.initialized) {
            this.upload();
            this.dirty = false;
        }
    }

    /// 重绘阈值（像素）：中心偏移小于该值时不重绘。
    ///
    /// 每帧重绘整张纹理需要数万次像素写入与一次完整 GPU 上传，代价过高；
    /// 取 1 像素可在保证纹理中心贴合玩家的同时避免每帧重绘。
    private static final int RECENTER_THRESHOLD_PIXELS = 1;

    /// 以玩家世界像素坐标为中心平移纹理；仅在偏移超过阈值时重绘。
    public void centerOn(double worldX, double worldZ) {
        int targetOriginX = (int) Math.floor(worldX) - PIXELS / 2;
        int targetOriginZ = (int) Math.floor(worldZ) - PIXELS / 2;
        if (!this.centered) {
            this.originPixelX = targetOriginX;
            this.originPixelZ = targetOriginZ;
            this.centered = true;
            this.redrawAll();
            return;
        }
        int driftX = Math.abs(targetOriginX - this.originPixelX);
        int driftZ = Math.abs(targetOriginZ - this.originPixelZ);
        if (driftX < RECENTER_THRESHOLD_PIXELS && driftZ < RECENTER_THRESHOLD_PIXELS) {
            return;
        }
        this.originPixelX = targetOriginX;
        this.originPixelZ = targetOriginZ;
        this.evictOutsideCoverage();
        this.redrawAll();
    }

    /// 覆盖窗口外额外保留的区块数，给新区域采样留出时间，避免移动时地图突然空白。
    private static final int EVICTION_MARGIN_CHUNKS = 4;

    /// 丢弃已远离纹理覆盖窗口及保留边距的区块数据。
    ///
    /// `source` 若只增不减，长距离跑图后每次重绘都要遍历全部历史区块，
    /// 内存与单帧耗时随游玩时长线性增长。
    private void evictOutsideCoverage() {
        int originChunkX = Math.floorDiv(this.originPixelX, ChunkMapData.SIZE);
        int originChunkZ = Math.floorDiv(this.originPixelZ, ChunkMapData.SIZE);
        this.source.keySet().removeIf(key -> {
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            return chunkX < originChunkX - EVICTION_MARGIN_CHUNKS
                    || chunkX >= originChunkX + SLOTS + EVICTION_MARGIN_CHUNKS
                    || chunkZ < originChunkZ - EVICTION_MARGIN_CHUNKS
                    || chunkZ >= originChunkZ + SLOTS + EVICTION_MARGIN_CHUNKS;
        });
    }

    /// 清空全部内容（切换服务器或维度时调用）。
    public void clearAll() {
        this.source.clear();
        this.drawnVersions.clear();
        this.centered = false;
        this.fill(MapPalette.TRANSPARENT);
        this.upload();
    }

    private void redrawAll() {
        this.fill(MapPalette.TRANSPARENT);
        this.drawnVersions.clear();
        for (ChunkMapData data : this.source.values()) {
            this.drawChunk(data);
            this.drawnVersions.put(ChunkPos.asLong(data.chunkX(), data.chunkZ()), data.version());
        }
        this.upload();
    }

    /// 把一个区块的 16×16 像素写入纹理中对应的位置（可能部分或全部在纹理外）。
    private void drawChunk(ChunkMapData data) {
        int chunkPixelX = data.chunkX() * ChunkMapData.SIZE;
        int chunkPixelZ = data.chunkZ() * ChunkMapData.SIZE;
        int baseX = chunkPixelX - this.originPixelX;
        int baseZ = chunkPixelZ - this.originPixelZ;
        for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
            int y = baseZ + localZ;
            if (y < 0 || y >= PIXELS) {
                continue;
            }
            for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                int x = baseX + localX;
                if (x < 0 || x >= PIXELS) {
                    continue;
                }
                this.setPixel(x, y, data.colorAt(localX, localZ));
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

    private void upload() {
        if (this.initialized) {
            this.texture.upload();
        }
    }

    /// 释放纹理：从纹理管理器注销并关闭，避免注册表残留已关闭对象。
    public void close() {
        if (!this.initialized) {
            return;
        }
        try {
            Minecraft.getInstance().getTextureManager().release(this.textureId);
        } catch (Exception e) {
            LOGGER.warn("Failed to release minimap texture", e);
        } finally {
            this.initialized = false;
        }
    }
}
