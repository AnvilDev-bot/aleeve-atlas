package dev.aleeve.atlas.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 单维度的区块地图缓存（规格 §2.1 / §11）。
///
/// 按区域文件（32×32 区块）持久化，避免单文件过大；数据格式为 `CompoundTag`。
public final class MapDimensionCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/mapcache");
    private static final int REGION_SHIFT = 5;
    private static final int REGION_SIZE = 1 << REGION_SHIFT;

    private final Path dimensionDir;
    private final Map<Long, ChunkMapData> chunks = new HashMap<>();
    private final Map<Long, RegionFile> loadedRegions = new HashMap<>();

    public MapDimensionCache(Path dimensionDir) {
        this.dimensionDir = dimensionDir;
    }

    public Optional<ChunkMapData> get(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        ChunkMapData cached = chunks.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        RegionFile region = regionFor(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
        ChunkMapData loaded = region.get(chunkX & (REGION_SIZE - 1), chunkZ & (REGION_SIZE - 1));
        if (loaded != null) {
            chunks.put(key, loaded);
            return Optional.of(loaded);
        }
        return Optional.empty();
    }

    public void put(ChunkMapData data) {
        long key = ChunkPos.asLong(data.chunkX(), data.chunkZ());
        chunks.put(key, data);
        RegionFile region = regionFor(data.chunkX() >> REGION_SHIFT, data.chunkZ() >> REGION_SHIFT);
        region.put(data.chunkX() & (REGION_SIZE - 1), data.chunkZ() & (REGION_SIZE - 1), data);
        region.dirty = true;
    }

    /// 已缓存的区块数量（含未落盘）。
    public int loadedCount() {
        return chunks.size();
    }

    /// 全部已缓存区块的摘要（坐标 + 版本号），供地图增量同步比对（规格 §3.3）。
    ///
    /// 仅返回内存中已加载的区块；精确全量摘要需要遍历区域文件，代价较高，
    /// 因此同步按「当前会话已探索」范围进行，符合规格「仅限在线成员」的语义。
    public java.util.List<ChunkMapData> loadedChunks() {
        return java.util.List.copyOf(chunks.values());
    }

    /// 该缓存对应的维度目录（诊断用）。
    public Path dimensionDir() {
        return dimensionDir;
    }

    public void flush() {
        for (RegionFile region : loadedRegions.values()) {
            if (region.dirty) {
                region.save();
            }
        }
    }

    private RegionFile regionFor(int regionX, int regionZ) {
        long key = ChunkPos.asLong(regionX, regionZ);
        return loadedRegions.computeIfAbsent(key,
                ignored -> new RegionFile(dimensionDir.resolve("r." + regionX + "." + regionZ + ".nbt")));
    }

    /// 区域文件：32×32 区块的集合。
    private static final class RegionFile {
        private final Path path;
        private final Map<Long, ChunkMapData> entries = new HashMap<>();
        private boolean dirty;

        private RegionFile(Path path) {
            this.path = path;
            load();
        }

        private void load() {
            if (!Files.isRegularFile(path)) {
                return;
            }
            try {
                CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                CompoundTag chunksTag = tag.getCompoundOrEmpty("chunks");
                for (String key : chunksTag.keySet()) {
                    CompoundTag chunkTag = chunksTag.getCompoundOrEmpty(key);
                    ChunkMapData data = ChunkMapData.load(chunkTag);
                    entries.put(ChunkPos.asLong(data.chunkX() & (REGION_SIZE - 1),
                            data.chunkZ() & (REGION_SIZE - 1)), data);
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to read map region {}", path, e);
            }
        }

        private ChunkMapData get(int localX, int localZ) {
            return entries.get(ChunkPos.asLong(localX, localZ));
        }

        private void put(int localX, int localZ, ChunkMapData data) {
            entries.put(ChunkPos.asLong(localX, localZ), data);
        }

        private void save() {
            CompoundTag tag = new CompoundTag();
            CompoundTag chunksTag = new CompoundTag();
            for (Map.Entry<Long, ChunkMapData> entry : entries.entrySet()) {
                chunksTag.put(Long.toString(entry.getKey()), entry.getValue().save());
            }
            tag.put("chunks", chunksTag);
            try {
                Files.createDirectories(path.getParent());
                NbtIo.writeCompressed(tag, path);
                dirty = false;
            } catch (IOException e) {
                LOGGER.warn("Failed to write map region {}", path, e);
            }
        }
    }
}
