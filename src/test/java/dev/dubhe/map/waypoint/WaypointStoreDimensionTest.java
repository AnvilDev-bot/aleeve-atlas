package dev.dubhe.map.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/// 路径点按维度隔离存储测试。
class WaypointStoreDimensionTest {
    @TempDir
    Path tempDir;

    @Test
    void eachDimensionGetsItsOwnFile() {
        WaypointStore overworld = WaypointStore.open(this.tempDir, "server_key", Level.OVERWORLD);
        WaypointStore nether = WaypointStore.open(this.tempDir, "server_key", Level.NETHER);

        Assertions.assertFalse(overworld.file().equals(nether.file()), "不同维度必须使用不同文件");
        Assertions.assertEquals("minecraft_overworld.nbt", overworld.file().getFileName().toString());
        Assertions.assertEquals("minecraft_the_nether.nbt", nether.file().getFileName().toString());
        // 与地图缓存同根目录下的独立子目录。
        Assertions.assertTrue(overworld.file().getParent().endsWith("waypoints"));
    }

    @Test
    void waypointsDoNotLeakAcrossDimensions() {
        WaypointStore overworld = WaypointStore.open(this.tempDir, "key", Level.OVERWORLD);
        WaypointStore nether = WaypointStore.open(this.tempDir, "key", Level.NETHER);

        overworld.upsert(Waypoint.create("base", new BlockPos(1, 64, 1), Level.OVERWORLD,
                0xFFFFFFFF));
        overworld.flush();

        // 重新打开下界存储，不应看到主世界路径点。
        WaypointStore reopenedNether = WaypointStore.open(this.tempDir, "key", Level.NETHER);
        Assertions.assertTrue(reopenedNether.all().isEmpty());
    }

    @Test
    void persistedWaypointsReloadFromOwnFile() {
        WaypointStore overworld = WaypointStore.open(this.tempDir, "key", Level.OVERWORLD);
        overworld.upsert(Waypoint.create("home", new BlockPos(5, 70, -5), Level.OVERWORLD,
                0xFF00FF00));
        overworld.flush();

        WaypointStore reopened = WaypointStore.open(this.tempDir, "key", Level.OVERWORLD);
        Assertions.assertEquals(1, reopened.all().size());
        Assertions.assertEquals("home", reopened.all().iterator().next().name());
    }

    @Test
    void loadFiltersEntriesOfOtherDimensions() throws Exception {
        // 手工写入一个维度字段与文件不匹配的条目，应被过滤。
        WaypointStore overworld = WaypointStore.open(this.tempDir, "key", Level.OVERWORLD);
        Path file = overworld.file();
        Files.createDirectories(file.getParent());
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        list.add(Waypoint.create("wrong", BlockPos.ZERO, Level.NETHER, 0xFFFFFFFF).save());
        tag.put("waypoints", list);
        NbtIo.writeCompressed(tag, file);

        WaypointStore reopened = WaypointStore.open(this.tempDir, "key", Level.OVERWORLD);
        Assertions.assertTrue(reopened.all().isEmpty(), "跨维度条目必须被过滤");
    }

    @Test
    void visibleInFiltersByDistance() {
        WaypointStore store = WaypointStore.open(this.tempDir, "key", Level.OVERWORLD);
        Waypoint near = new Waypoint(UUID.randomUUID(), "near", new BlockPos(0, 64, 0),
                Level.OVERWORLD, 0xFFFFFFFF, "default", true, 32, false, "", true);
        Waypoint far = new Waypoint(UUID.randomUUID(), "far", new BlockPos(1000, 64, 0),
                Level.OVERWORLD, 0xFFFFFFFF, "default", true, 100, false, "", true);
        Waypoint unlimited = new Waypoint(UUID.randomUUID(), "unlimited", new BlockPos(1000, 64, 0),
                Level.OVERWORLD, 0xFFFFFFFF, "default", true,
                Waypoint.DEFAULT_VISIBLE_DISTANCE, false, "", true);
        store.upsert(near);
        store.upsert(far);
        store.upsert(unlimited);

        List<Waypoint> visible = store.visibleIn(0.0, 0.0);
        Assertions.assertEquals(2, visible.size());
        Assertions.assertTrue(visible.stream().anyMatch(point -> point.name().equals("near")));
        Assertions.assertTrue(visible.stream().anyMatch(point -> point.name().equals("unlimited")));
    }

    @Test
    void openAllScansEveryDimensionFileOnDisk() {
        // 导入导出需要覆盖全部维度：仅遍历内存中已加载的存储会遗漏未访问过的维度。
        WaypointStore overworld = WaypointStore.open(this.tempDir, "scan_key", Level.OVERWORLD);
        overworld.upsert(Waypoint.create("ow", new BlockPos(1, 64, 1), Level.OVERWORLD,
                0xFFFFFFFF));
        overworld.flush();
        WaypointStore nether = WaypointStore.open(this.tempDir, "scan_key", Level.NETHER);
        nether.upsert(Waypoint.create("nether", new BlockPos(2, 64, 2), Level.NETHER,
                0xFFFFFFFF));
        nether.flush();

        List<WaypointStore> stores = WaypointStore.openAll(this.tempDir, "scan_key");
        Assertions.assertEquals(2, stores.size(), "两个维度文件都应被扫描到");

        long total = stores.stream().mapToLong(store -> store.all().size()).sum();
        Assertions.assertEquals(2, total, "两个维度的路径点都应可读取");

        // 维度必须从文件内容还原，文件名中的冒号已被替换为下划线，无法据此推断。
        boolean hasNether = stores.stream()
                .anyMatch(store -> store.dimension().equals(Level.NETHER));
        Assertions.assertTrue(hasNether, "下界维度必须被正确识别");
    }
}
