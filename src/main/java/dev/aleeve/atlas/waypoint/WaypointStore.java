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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 路径点存储（规格 §5.1 / §11）。
///
/// 按「服务器标识 → 维度」隔离，与地图缓存同根目录、每维度独立文件，采用 `CompoundTag` 持久化。
public final class WaypointStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/waypoints");
    private static final String DIR_NAME = "waypoints";

    private final Path file;
    private final ResourceKey<Level> dimension;
    private final Map<UUID, Waypoint> waypoints = new LinkedHashMap<>();
    private boolean dirty;

    private WaypointStore(Path file, ResourceKey<Level> dimension) {
        this.file = file;
        this.dimension = dimension;
        load();
    }

    /// 打开某服务器标识下、指定维度的路径点存储（规格 §11 维度隔离）。
    public static WaypointStore open(Path gameDir, String serverKey, ResourceKey<Level> dimension) {
        Path dir = AtlasPaths.serverRoot(gameDir, serverKey).resolve(DIR_NAME);
        String fileName = sanitize(dimension.identifier().toString()) + ".nbt";
        return new WaypointStore(dir.resolve(fileName), dimension);
    }

    /// 该存储对应的维度。
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /// 存储文件路径（诊断与导入导出用）。
    public Path file() {
        return file;
    }

    public Collection<Waypoint> all() {
        return waypoints.values();
    }

    public Optional<Waypoint> get(UUID id) {
        return Optional.ofNullable(waypoints.get(id));
    }

    /// 该维度下启用且在可见距离内的路径点。
    public List<Waypoint> visibleIn(double x, double z) {
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : waypoints.values()) {
            if (!waypoint.enabled()) {
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

    /// 落盘。
    public void flush() {
        if (!dirty) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.identifier().toString());
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
                // 仅接受属于本存储维度的条目，防止文件被移动后串维度。
                if (waypoint.dimension().equals(dimension)) {
                    waypoints.put(waypoint.id(), waypoint);
                }
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

    /// 维度标识中的非法文件名字符替换为下划线。
    private static String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /// 便捷构造坐标（供外部调用）。
    public static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }
}
