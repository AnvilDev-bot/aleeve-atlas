package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.MapColorScheme;
import dev.dubhe.map.map.CaveMode;
import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.map.ChunkMapSampler;
import dev.dubhe.map.map.MapDimensionCache;
import dev.dubhe.map.storage.AtlasPaths;
import dev.dubhe.map.storage.ServerKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// 客户端地图数据管理器。
///
/// 负责：按「服务器标识 → 维度」隔离地图缓存、按 tick 限速采样玩家周围区块、
/// 把采样结果写入小地图纹理。采样仅在区块加载时进行，稳态跑图不重复采样。
public final class ClientMapManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/clientmap");
    /// 每 tick 最多采样的区块数，避免集中采样造成掉帧。
    private static final int SAMPLES_PER_TICK = 6;
    /// 小地图采样半径，额外覆盖方形旋转所需的对角线范围。
    public static final int VIEW_CHUNK_RADIUS = 6;
    /// 洞穴模式下触发重新采样的层高变化阈值（方块）。
    ///
    /// 缓存不区分层高，若层高变化后继续命中旧缓存，地图会停留在旧层级的结构上。
    private static final int CAVE_LAYER_RESCAN_THRESHOLD = 8;
    /// 待重新采样的区块：方块变更时由 {@code ClientLevelMixin} 标记。
    ///
    /// 以 {@link ChunkPos#asLong} 编码为键。标记与消费都在客户端主线程：
    /// 标记来自方块更新包处理，消费来自客户端 tick，无需并发容器。
    private static final Set<Long> DIRTY_SURFACE_CHUNKS = new HashSet<>();
    private static final Set<Long> DIRTY_CAVE_CHUNKS = new HashSet<>();

    private final MapTexture texture = new MapTexture(AleeveAtlas.id("minimap"));
    private final Map<String, MapDimensionCache> caches = new HashMap<>();
    /// 洞穴分层缓存：与地表缓存并行存在，切回地表时立即恢复。
    private MapDimensionCache caveCache;
    private String currentServerKey;
    private ResourceKey<Level> currentDimension;
    private MapColorScheme colorScheme = MapColorScheme.VANILLA;
    private boolean textureRegistered;
    private boolean loggedFirstSample;
    /// 洞穴缓存的采样层高；玩家层高变化超过阈值时清空缓存重新采样。
    private Integer caveLayerTop;

    /// 注册纹理；资源重载后需重新注册。
    public void ensureRegistered() {
        if (!this.textureRegistered) {
            this.texture.register();
            this.textureRegistered = true;
        }
    }

    /// 资源重载后重置注册标志，使下次 tick 重新注册动态纹理。
    public void onResourceReload() {
        this.textureRegistered = false;
    }

    /// 洞穴模式是否需要在本次 tick 重建分层缓存。
    ///
    /// 缓存不区分层高，玩家上下移动超过阈值后继续命中旧缓存会让地图停留在旧层级。
    /// 非洞穴模式或首次进入洞穴时不重建。
    static boolean needsCaveRescan(boolean caveMode, Integer previousTop, int currentTop) {
        if (!caveMode || previousTop == null) {
            return false;
        }
        return Math.abs(currentTop - previousTop) >= CAVE_LAYER_RESCAN_THRESHOLD;
    }

    /// 标记某区块需要重新采样。
    ///
    /// 由客户端方块变更监听调用，使地图反映破坏与放置的结果。
    public static void markChunkChanged(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        DIRTY_SURFACE_CHUNKS.add(key);
        DIRTY_CAVE_CHUNKS.add(key);
    }

    /// 取出并清空当前地图模式待重采样的区块。
    private static Set<Long> dirtySnapshot(Set<Long> dirtyChunks) {
        return dirtyChunks.isEmpty() ? Set.of() : Set.copyOf(dirtyChunks);
    }

    /// 每个客户端 tick 调用：采样玩家周围缺失的区块。
    public void tick(ClientLevel level) {
        this.ensureRegistered();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String serverKey = serverKey(minecraft);
        if (!serverKey.equals(this.currentServerKey)) {
            migrateCacheIfNeeded(minecraft, serverKey);
        }
        MapColorScheme scheme = AtlasClientConfigs.CONFIG.minimap.colorScheme;
        boolean schemeChanged = scheme != this.colorScheme;
        this.colorScheme = scheme;
        if (schemeChanged) {
            LOGGER.info("Map color scheme changed to {}", scheme);
            this.texture.setColorScheme(scheme);
        }
        final MapDimensionCache cache = this.cacheFor(minecraft, serverKey, level.dimension());
        if (!serverKey.equals(this.currentServerKey) || !level.dimension().equals(this.currentDimension)) {
            // 切换服务器或维度前先写入磁盘，避免未到周期写盘时丢失洞穴层数据。
            if (this.caveCache != null) {
                this.caveCache.flush();
            }
            this.currentServerKey = serverKey;
            this.currentDimension = level.dimension();
            DIRTY_SURFACE_CHUNKS.clear();
            DIRTY_CAVE_CHUNKS.clear();
            this.caveCache = null;
            this.texture.clearAll();
        }

        final ChunkPos center = minecraft.player.chunkPosition();
        boolean caveMode = CaveMode.isUnderground(level, minecraft.player.blockPosition());
        this.texture.setCaveMode(caveMode);
        this.texture.setImportedStore(dev.dubhe.map.client.cartography.ClientCartography.currentStore());
        this.texture.centerOn(minecraft.player.getX(), minecraft.player.getZ());
        int caveTop = minecraft.player.blockPosition().getY() + 8;
        if (caveMode && (this.caveCache == null || this.caveLayerTop == null
                || needsCaveRescan(true, this.caveLayerTop, caveTop))) {
            if (this.caveCache != null) {
                this.caveCache.flush();
            }
            this.caveCache = this.caveCacheFor(minecraft, this.currentServerKey,
                    level.dimension(), caveTop);
        }
        this.caveLayerTop = caveMode ? caveTop : null;

        // 方块变更后保留受影响区块的旧图像，直到新的采样成功完成。
        Set<Long> activeDirtyChunks = caveMode ? DIRTY_CAVE_CHUNKS : DIRTY_SURFACE_CHUNKS;
        Set<Long> dirtyChunks = dirtySnapshot(activeDirtyChunks);

        int sampled = 0;
        Set<Long> refreshedDirtyChunks = new HashSet<>();
        int radius = VIEW_CHUNK_RADIUS;
        for (int ring = 0; ring <= radius && sampled < SAMPLES_PER_TICK; ring++) {
            for (int dx = -ring; dx <= ring && sampled < SAMPLES_PER_TICK; dx++) {
                for (int dz = -ring; dz <= ring && sampled < SAMPLES_PER_TICK; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int chunkX = center.x + dx;
                    int chunkZ = center.z + dz;
                    // 洞穴模式使用独立的分层缓存，避免覆盖地表数据。
                    Integer layerTop = caveMode
                            ? minecraft.player.blockPosition().getY() + 8
                            : null;
                    long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                    if (!caveMode) {
                        Optional<ChunkMapData> existing = cache.get(chunkX, chunkZ);
                        if (existing.isPresent() && !dirtyChunks.contains(chunkKey)) {
                            dev.dubhe.map.client.structure.ClientStructures.sampled(chunkX, chunkZ);
                            this.texture.putChunk(existing.get());
                            continue;
                        }
                    } else {
                        Optional<ChunkMapData> existing = this.caveCache.get(chunkX, chunkZ);
                        if (existing.isPresent() && !dirtyChunks.contains(chunkKey)) {
                            dev.dubhe.map.client.structure.ClientStructures.sampled(chunkX, chunkZ);
                            this.texture.putChunk(existing.get());
                            continue;
                        }
                    }
                    ChunkMapData sampledData = ChunkMapSampler.sample(level,
                            new ChunkPos(chunkX, chunkZ), layerTop);
                    if (sampledData == null) {
                        continue;
                    }
                    sampledData.setVersion(System.currentTimeMillis());
                    if (dirtyChunks.contains(chunkKey)) {
                        refreshedDirtyChunks.add(chunkKey);
                    }
                    // 分层数据写入独立缓存，不污染地表地图。
                    if (caveMode) {
                        this.caveCache.put(sampledData);
                    } else {
                        cache.put(sampledData);
                    }
                    this.texture.putChunk(sampledData);
                    dev.dubhe.map.client.structure.ClientStructures.sampled(chunkX, chunkZ);
                    sampled++;
                }
            }
        }
        activeDirtyChunks.removeAll(refreshedDirtyChunks);
        // 合并本 tick 的像素改动，只上传一次纹理。
        this.texture.flushDirty();
        if (sampled > 0 || schemeChanged) {
            WorldMapScreen.onMapDataChanged();
        }
        if (sampled > 0 && !this.loggedFirstSample) {
            this.loggedFirstSample = true;
            LOGGER.info("First map sample written: {} chunk(s) around {},{}, cache dir {}",
                    sampled, center.x, center.z, cache.dimensionDir());
        }
    }

    /// 释放小地图动态纹理（断开连接时调用，避免 GPU 纹理泄漏）。
    public void releaseTexture() {
        this.texture.close();
    }

    public MapTexture texture() {
        return this.texture;
    }

    /// 维度缓存；调用前需保证在客户端主线程。
    public MapDimensionCache currentCache() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        return this.cacheFor(minecraft, serverKey(minecraft), minecraft.level.dimension());
    }

    public boolean hasSampledChunk(int chunkX, int chunkZ) {
        MapDimensionCache surface = this.currentCache();
        Minecraft minecraft = Minecraft.getInstance();
        boolean sameWorld = minecraft.level != null && minecraft.level.dimension().equals(this.currentDimension)
                && serverKey(minecraft).equals(this.currentServerKey);
        return (surface != null && surface.get(chunkX, chunkZ).isPresent())
                || (sameWorld && this.caveCache != null && this.caveCache.get(chunkX, chunkZ).isPresent());
    }

    public void flush() {
        for (MapDimensionCache cache : this.caches.values()) {
            cache.flush();
        }
        // 洞穴分层缓存独立于地表缓存，必须一并写入磁盘。
        if (this.caveCache != null) {
            this.caveCache.flush();
        }
    }

    public void clear() {
        if (this.caveCache != null) {
            this.caveCache.flush();
            this.caveCache = null;
        }
        this.caches.clear();
        this.currentServerKey = null;
        this.currentDimension = null;
        // 脏标记属会话级状态：切换服务器后旧坐标不应触发新会话的缓存失效。
        DIRTY_SURFACE_CHUNKS.clear();
        DIRTY_CAVE_CHUNKS.clear();
        this.texture.clearAll();
    }

    private MapDimensionCache cacheFor(Minecraft minecraft, String serverKey, ResourceKey<Level> dimension) {
        String key = serverKey + "/" + dimension.identifier();
        return this.caches.computeIfAbsent(key, ignored -> {
            Path base = AtlasPaths.dimensionDir(gameDir(minecraft), serverKey, dimension.identifier());
            return new MapDimensionCache(base);
        });
    }

    /// 洞穴分层缓存目录：在地表层目录名后附加 `_cave_<layerTop>`。
    private MapDimensionCache caveCacheFor(Minecraft minecraft, String serverKey,
            ResourceKey<Level> dimension, int layerTop) {
        Path base = AtlasPaths.dimensionDir(gameDir(minecraft), serverKey, dimension.identifier());
        Path caveBase = base.resolveSibling(base.getFileName() + "_cave_" + layerTop);
        return new MapDimensionCache(caveBase);
    }

    private static void migrateCacheIfNeeded(Minecraft minecraft, String newKey) {
        String oldKey = ServerKey.resolve(minecraft);
        if (oldKey.equals(newKey)) {
            return;
        }
        try {
            if (AtlasPaths.migrateMapCache(gameDir(minecraft), oldKey, newKey)) {
                LOGGER.info("Migrated map cache from {} to {}", oldKey, newKey);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to migrate map cache from {} to {}", oldKey, newKey, e);
        }
    }

    private static Path gameDir(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath();
    }

    /// 服务器/存档标识。
    private static String serverKey(Minecraft minecraft) {
        return ServerKey.resolveMapCache(minecraft);
    }
}
