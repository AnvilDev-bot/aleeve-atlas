package dev.dubhe.atlas.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dubhe.atlas.map.ChunkMapData;
import dev.dubhe.atlas.map.MapPalette;
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
    private final DynamicTexture texture;
    private final NativeImage image;
    /// 以区块坐标索引的已采样数据。
    private final Map<Long, ChunkMapData> source = new HashMap<>();
    /// 纹理左上角对应的世界像素坐标。
    private int originPixelX;
    private int originPixelZ;
    private boolean initialized;
    private boolean centered;

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

    public int originPixelX() {
        return originPixelX;
    }

    public int originPixelZ() {
        return originPixelZ;
    }

    public boolean ready() {
        return initialized;
    }

    /// 放入一个区块数据（放入后立即重绘受影响的纹理区域）。
    public void putChunk(ChunkMapData data) {
        source.put(ChunkPos.asLong(data.chunkX(), data.chunkZ()), data);
        if (initialized && centered) {
            drawChunk(data);
            upload();
        }
    }

    /// 查询已缓存的区块数据。
    public ChunkMapData chunkAt(int chunkX, int chunkZ) {
        return source.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /// 重绘阈值（像素）：中心偏移小于该值时不重绘，改由渲染偏移补偿（§12.1）。
    ///
    /// 每帧按像素重绘 144×144 纹理会产生约 2 万次写入与一次完整 GPU 上传，
    /// 代价过高；以 8 像素为阈值可把重绘频率降低一个数量级，
    /// 期间通过 {@link #pixelOffsetX()}/{@link #pixelOffsetZ()} 让渲染端微调采样位置。
    private static final int RECENTER_THRESHOLD_PIXELS = 1;

    /// 以玩家世界像素坐标为中心平移纹理；仅在偏移超过阈值时重绘。
    public void centerOn(double worldX, double worldZ) {
        int targetOriginX = (int) Math.floor(worldX) - PIXELS / 2;
        int targetOriginZ = (int) Math.floor(worldZ) - PIXELS / 2;
        if (!centered) {
            originPixelX = targetOriginX;
            originPixelZ = targetOriginZ;
            centered = true;
            redrawAll();
            return;
        }
        int driftX = Math.abs(targetOriginX - originPixelX);
        int driftZ = Math.abs(targetOriginZ - originPixelZ);
        if (driftX < RECENTER_THRESHOLD_PIXELS && driftZ < RECENTER_THRESHOLD_PIXELS) {
            return;
        }
        originPixelX = targetOriginX;
        originPixelZ = targetOriginZ;
        redrawAll();
    }

    /// 纹理中心与玩家实际位置的水平偏差（像素），供渲染端补偿。
    public int pixelOffsetX(double worldX) {
        return (int) Math.floor(worldX) - (originPixelX + PIXELS / 2);
    }

    /// 纹理中心与玩家实际位置的垂直偏差（像素），供渲染端补偿。
    public int pixelOffsetZ(double worldZ) {
        return (int) Math.floor(worldZ) - (originPixelZ + PIXELS / 2);
    }

    /// 清空全部内容（切换服务器或维度时调用）。
    public void clearAll() {
        source.clear();
        centered = false;
        fill(MapPalette.TRANSPARENT);
        upload();
    }

    private void redrawAll() {
        fill(MapPalette.TRANSPARENT);
        for (ChunkMapData data : source.values()) {
            drawChunk(data);
        }
        upload();
    }

    /// 把一个区块的 16×16 像素写入纹理中对应的位置（可能部分或全部在纹理外）。
    private void drawChunk(ChunkMapData data) {
        int chunkPixelX = data.chunkX() * ChunkMapData.SIZE;
        int chunkPixelZ = data.chunkZ() * ChunkMapData.SIZE;
        int baseX = chunkPixelX - originPixelX;
        int baseZ = chunkPixelZ - originPixelZ;
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
                setPixel(x, y, data.colorAt(localX, localZ));
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

    /// 写入像素；`NativeImage` 原点在左下，故翻转 Y 轴。
    private void setPixel(int x, int y, int argb) {
        if (x < 0 || x >= PIXELS || y < 0 || y >= PIXELS) {
            return;
        }
        image.setPixel(x, PIXELS - 1 - y, argb);
    }

    private void upload() {
        if (initialized) {
            texture.upload();
        }
    }

    /// 释放纹理：从纹理管理器注销并关闭，避免注册表残留已关闭对象（§12.1）。
    public void close() {
        if (!initialized) {
            return;
        }
        try {
            Minecraft.getInstance().getTextureManager().release(textureId);
        } catch (Exception e) {
            LOGGER.warn("Failed to release minimap texture", e);
        } finally {
            initialized = false;
        }
    }
}
