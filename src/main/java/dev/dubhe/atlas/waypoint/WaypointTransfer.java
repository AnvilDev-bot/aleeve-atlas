package dev.dubhe.atlas.waypoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/// 路径点导入导出（规格 §11：路径点存储需支持导入导出）。
///
/// 导出为单个 NBT 文件（含版本号与全部路径点），导入时按 ID 合并：
/// 已存在同 ID 则覆盖，其余保留，避免导入破坏现有数据。
public final class WaypointTransfer {
    /// 导出文件格式版本，便于后续兼容处理。
    public static final int FORMAT_VERSION = 1;
    private static final String ROOT = "aleeve_atlas_waypoints";

    private WaypointTransfer() {
    }

    /// 导出路径点到指定文件。
    public static void exportTo(Path file, Iterable<Waypoint> waypoints) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("format", FORMAT_VERSION);
        root.put("waypoints", Waypoint.saveAll(waypoints));
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        NbtIo.writeCompressed(root, file);
    }

    /// 从文件导入路径点；返回导入的条目列表。
    public static List<Waypoint> importFrom(Path file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        if (!root.contains(ROOT) && !root.contains("waypoints")) {
            throw new IOException("Not a waypoint export file: " + file);
        }
        ListTag list = root.getListOrEmpty("waypoints");
        return Waypoint.loadAll(list, WaypointTransfer::resolveDimension);
    }

    /// 把导入的路径点合并进入现有集合（按 ID 覆盖）。
    ///
    /// 返回新建的 `WaypointStore` 中需要写入磁盘的条目；调用方负责 `upsert` 与 `flush`。
    public static MergeResult merge(List<Waypoint> imported, Iterable<Waypoint> existing) {
        Map<UUID, Waypoint> byId = new LinkedHashMap<>();
        for (Waypoint waypoint : existing) {
            byId.put(waypoint.id(), waypoint);
        }
        List<Waypoint> added = new ArrayList<>();
        List<Waypoint> replaced = new ArrayList<>();
        for (Waypoint waypoint : imported) {
            if (byId.containsKey(waypoint.id())) {
                replaced.add(waypoint);
            } else {
                added.add(waypoint);
            }
            byId.put(waypoint.id(), waypoint);
        }
        return new MergeResult(added, replaced, List.copyOf(byId.values()));
    }

    /// 合并结果：新增条目、覆盖条目与合并后的全集。
    public record MergeResult(List<Waypoint> added, List<Waypoint> replaced,
            List<Waypoint> merged) {
    }

    private static ResourceKey<Level> resolveDimension(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            return Level.OVERWORLD;
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }
}
