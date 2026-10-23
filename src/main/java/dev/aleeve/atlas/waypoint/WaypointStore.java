package dev.aleeve.atlas.waypoint;

import dev.aleeve.atlas.storage.AtlasPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 路径点存储（规格 §5.1 / §11）。
///
/// 按「服务器标识 → 维度」隔离，与地图缓存同根目录、独立文件，采用 `CompoundTag` 持久化。
public final class WaypointStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/waypoints");
    private static final String FILE_NAME = "waypoints.nbt";

    private final Path file;
    private final Map<UUID, Waypoint> waypoints = new LinkedHashMap<>();
    private boolean dirty;

    private WaypointStore(Path file) {
        this.file = file;
        load();
    }

    /// 打开某服务器标识下的路径点存储。
    public static WaypointStore open(Path gameDir, String serverKey) {
        return new WaypointStore(AtlasPaths.serverRoot(gameDir, serverKey).resolve(FILE_NAME));
    }

    public Collection<Waypoint> all() {
        return waypoints.values();
    }

    public Optional<Waypoint> get(UUID id) {
        return Optional.ofNullable(waypoints.get(id));
    }

    /// 该维度下启用且在可见距离内的路径点。
    public List<Waypoint> visibleIn(ResourceKey<Level> dimension, double x, double z) {
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : waypoints.values()) {
            if (!waypoint.enabled() || !waypoint.dimension().equals(dimension)) {
                continue;
            }
            double dx = waypoint.pos().getX() + 0.5 - x;
            double dz = waypoint.pos().getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= (double) waypoint.visibleDistance()
                    * waypoint.visibleDistance()) {
                result.add(waypoint);
            }
        }
        return result;
    }

    /// 新增或更新路径点。
    public void upsert(Waypoint waypoint) {
        waypoints.put(waypoint.id(), waypoint);
        dirty = true;
    }

    public boolean remove(UUID id) {
        boolean removed = waypoints.remove(id) != null;
        if (removed) {
            dirty = true;
        }
        return removed;
    }

    /// 死亡点保留策略（规格 §5.1）：开关关闭时清除历史死亡点。
    public void pruneDeathPoints() {
        boolean changed = waypoints.values().removeIf(Waypoint::deathPoint);
        if (changed) {
            dirty = true;
        }
    }

    /// 切换服务器前落盘。
    public void flush() {
        if (!dirty) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("waypoints", Waypoint.saveAll(waypoints.values()));
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(tag, file);
            dirty = false;
        } catch (IOException e) {
            LOGGER.warn("Failed to write waypoints {}", file, e);
        }
    }

    public void clear() {
        waypoints.clear();
        dirty = false;
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            ListTag list = tag.getListOrEmpty("waypoints");
            for (Waypoint waypoint : Waypoint.loadAll(list, WaypointStore::resolveDimension)) {
                waypoints.put(waypoint.id(), waypoint);
            }
            dirty = false;
        } catch (IOException e) {
            LOGGER.warn("Failed to read waypoints {}", file, e);
        }
    }

    /// 依次尝试解析维度标识，未知维度回退主世界。
    private static ResourceKey<Level> resolveDimension(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            return Level.OVERWORLD;
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    /// 便捷构造坐标（供外部调用）。
    public static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }
}
