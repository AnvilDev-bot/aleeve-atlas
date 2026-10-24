package dev.dubhe.atlas.client.map;

import dev.dubhe.atlas.AleeveAtlas;
import dev.dubhe.atlas.map.ChunkMapData;
import dev.dubhe.atlas.map.CaveMode;
import dev.dubhe.atlas.map.ChunkMapSampler;
import dev.dubhe.atlas.map.MapDimensionCache;
import dev.dubhe.atlas.storage.AtlasPaths;
import dev.dubhe.atlas.storage.ServerKey;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 客户端地图数据管理器（规格 §2.1）。
///
/// 负责：按「服务器标识 → 维度」隔离地图缓存、按 tick 限速采样玩家周围区块、
/// 把采样结果写入小地图纹理。采样仅在区块加载时进行，稳态跑图不重复采样（规格 §2.1 / §12.1）。
public final class ClientMapManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/clientmap");
    /// 每 tick 最多采样的区块数，避免集中采样造成掉帧。
    private static final int SAMPLES_PER_TICK = 6;
    /// 小地图覆盖的区块半径（以玩家区块为中心的窗口）。
    public static final int VIEW_CHUNK_RADIUS = 4;
    /// 洞穴模式下触发重新采样的层高变化阈值（方块）。
    ///
    /// 缓存不区分层高，若层高变化后继续命中旧缓存，地图会停留在旧层级的结构上（§2.1）。
    private static final int CAVE_LAYER_RESCAN_THRESHOLD = 8;

    private final MapTexture texture = new MapTexture(AleeveAtlas.id("minimap"));
    private final Map<String, MapDimensionCache> caches = new HashMap<>();
    /// 洞穴分层缓存：与地表缓存并行存在，切回地表时立即恢复（§2.1）。
    private MapDimensionCache caveCache;
    private String currentServerKey;
    private ResourceKey<Level> currentDimension;
    private boolean textureRegistered;
    private boolean loggedFirstSample;
    /// 洞穴缓存的采样层高；玩家层高变化超过阈值时清空缓存重新采样（§2.1）。
    private Integer caveLayerTop;

    /// 注册纹理；资源重载后需重新注册。
    public void ensureRegistered() {
        if (!textureRegistered) {
            texture.register();
            textureRegistered = true;
        }
    }

    /// 资源重载后重置注册标志，使下次 tick 重新注册动态纹理（§2.2）。
    public void onResourceReload() {
        textureRegistered = false;
    }

    /// 洞穴模式是否需要在本次 tick 重建分层缓存（§2.1）。
    ///
    /// 缓存不区分层高，玩家上下移动超过阈值后继续命中旧缓存会让地图停留在旧层级。
    /// 非洞穴模式或首次进入洞穴时不重建。
    static boolean needsCaveRescan(boolean caveMode, Integer previousTop, int currentTop) {
        if (!caveMode || previousTop == null) {
            return false;
        }
        return Math.abs(currentTop - previousTop) >= CAVE_LAYER_RESCAN_THRESHOLD;
    }
    /// 每个客户端 tick 调用：采样玩家周围缺失的区块。
    public void tick(ClientLevel level) {
        ensureRegistered();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String serverKey = serverKey(minecraft);
        MapDimensionCache cache = cacheFor(minecraft, serverKey, level.dimension());
        if (!serverKey.equals(currentServerKey) || !level.dimension().equals(currentDimension)) {
            // 切换服务器或维度前先写入磁盘，避免未到周期写盘时丢失洞穴层数据（§11）。
            if (caveCache != null) {
                caveCache.flush();
            }
            currentServerKey = serverKey;
            currentDimension = level.dimension();
            caveCache = caveCacheFor(minecraft, serverKey, level.dimension());
            texture.clearAll();
        }

        ChunkPos center = minecraft.player.chunkPosition();
        texture.centerOn(minecraft.player.getX(), minecraft.player.getZ());
        // 进入地下或有顶棚遮挡时自动切换到洞穴分层模式（§2.1）。
        boolean caveMode = CaveMode.isUnderground(level, minecraft.player.blockPosition());
        int caveTop = minecraft.player.blockPosition().getY() + 8;
        if (needsCaveRescan(caveMode, caveLayerTop, caveTop)) {
            // 层高变化明显：重建洞穴缓存，让下一次采样反映新层级（§2.1）。
            caveCache.flush();
            caveCache = caveCacheFor(minecraft, currentServerKey, level.dimension());
        }
        caveLayerTop = caveMode ? caveTop : null;

        int sampled = 0;
        int radius = VIEW_CHUNK_RADIUS;
        for (int ring = 0; ring <= radius && sampled < SAMPLES_PER_TICK; ring++) {
            for (int dx = -ring; dx <= ring && sampled < SAMPLES_PER_TICK; dx++) {
                for (int dz = -ring; dz <= ring && sampled < SAMPLES_PER_TICK; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int chunkX = center.x + dx;
                    int chunkZ = center.z + dz;
                    // 洞穴模式使用独立的分层缓存，避免覆盖地表数据（§2.1）。
                    Integer layerTop = caveMode
                            ? minecraft.player.blockPosition().getY() + 8
                            : null;
                    if (!caveMode) {
                        Optional<ChunkMapData> existing = cache.get(chunkX, chunkZ);
                        if (existing.isPresent()) {
                            texture.putChunk(existing.get());
                            continue;
                        }
                    } else if (caveCache.get(chunkX, chunkZ).isPresent()) {
                        texture.putChunk(caveCache.get(chunkX, chunkZ).get());
                        continue;
                    }
                    ChunkMapData sampledData = ChunkMapSampler.sample(level,
                            new ChunkPos(chunkX, chunkZ), layerTop);
                    if (sampledData == null) {
                        continue;
                    }
                    sampledData.setVersion(System.currentTimeMillis());
                    // 分层数据写入独立缓存，不污染地表地图（§2.1）。
                    if (caveMode) {
                        caveCache.put(sampledData);
                    } else {
                        cache.put(sampledData);
                    }
                    texture.putChunk(sampledData);
                    sampled++;
                }
            }
        }
        // 合并本 tick 的像素改动，只上传一次纹理（§12.1）。
        texture.flushDirty();
        if (sampled > 0 && !loggedFirstSample) {
            loggedFirstSample = true;
            LOGGER.info("First map sample written: {} chunk(s) around {},{}, cache dir {}",
                    sampled, center.x, center.z, cache.dimensionDir());
        }
    }

    /// 释放小地图动态纹理（断开连接时调用，避免 GPU 纹理泄漏）。
    public void releaseTexture() {
        texture.close();
    }

    public MapTexture texture() {
        return texture;
    }

    /// 维度缓存；调用前需保证在客户端主线程。
    public MapDimensionCache currentCache() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        return cacheFor(minecraft, serverKey(minecraft), minecraft.level.dimension());
    }

    public void flush() {
        for (MapDimensionCache cache : caches.values()) {
            cache.flush();
        }
        // 洞穴分层缓存独立于地表缓存，必须一并写入磁盘（规格 §2.1 / §11）。
        if (caveCache != null) {
            caveCache.flush();
        }
    }

    public void clear() {
        if (caveCache != null) {
            caveCache.flush();
            caveCache = null;
        }
        caches.clear();
        currentServerKey = null;
        currentDimension = null;
        texture.clearAll();
    }

    private MapDimensionCache cacheFor(Minecraft minecraft, String serverKey, ResourceKey<Level> dimension) {
        String key = serverKey + "/" + dimension.identifier();
        return caches.computeIfAbsent(key, ignored -> {
            Path dir = AtlasPaths.dimensionDir(gameDir(minecraft), serverKey, dimension.identifier());
            return new MapDimensionCache(dir);
        });
    }

    /// 洞穴分层缓存目录：在地表层目录下加 `_cave` 后缀区分（§2.1 / §11）。
    private MapDimensionCache caveCacheFor(Minecraft minecraft, String serverKey,
            ResourceKey<Level> dimension) {
        Path base = AtlasPaths.dimensionDir(gameDir(minecraft), serverKey, dimension.identifier());
        return new MapDimensionCache(base.resolveSibling(base.getFileName() + "_cave"));
    }

    private static Path gameDir(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath();
    }

    /// 服务器/存档标识（规格 §11）。
    private static String serverKey(Minecraft minecraft) {
        return ServerKey.resolve(minecraft);
    }
}
