package dev.dubhe.atlas.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// 路径点按维度隔离存储测试（规格 §11）。
class WaypointStoreDimensionTest {
    @TempDir
    Path tempDir;

    @Test
    void eachDimensionGetsItsOwnFile() {
        WaypointStore overworld = WaypointStore.open(tempDir, "server_key", Level.OVERWORLD);
        WaypointStore nether = WaypointStore.open(tempDir, "server_key", Level.NETHER);

        assertFalse(overworld.file().equals(nether.file()), "不同维度必须使用不同文件");
        assertEquals("minecraft_overworld.nbt", overworld.file().getFileName().toString());
        assertEquals("minecraft_the_nether.nbt", nether.file().getFileName().toString());
        // 与地图缓存同根目录下的独立子目录。
        assertTrue(overworld.file().getParent().endsWith("waypoints"));
    }

    @Test
    void waypointsDoNotLeakAcrossDimensions() {
        WaypointStore overworld = WaypointStore.open(tempDir, "key", Level.OVERWORLD);
        WaypointStore nether = WaypointStore.open(tempDir, "key", Level.NETHER);

        overworld.upsert(Waypoint.create("base", new BlockPos(1, 64, 1), Level.OVERWORLD,
                0xFFFFFFFF));
        overworld.flush();

        // 重新打开下界存储，不应看到主世界路径点。
        WaypointStore reopenedNether = WaypointStore.open(tempDir, "key", Level.NETHER);
        assertTrue(reopenedNether.all().isEmpty());
    }

    @Test
    void persistedWaypointsReloadFromOwnFile() {
        WaypointStore overworld = WaypointStore.open(tempDir, "key", Level.OVERWORLD);
        overworld.upsert(Waypoint.create("home", new BlockPos(5, 70, -5), Level.OVERWORLD,
                0xFF00FF00));
        overworld.flush();

        WaypointStore reopened = WaypointStore.open(tempDir, "key", Level.OVERWORLD);
        assertEquals(1, reopened.all().size());
        assertEquals("home", reopened.all().iterator().next().name());
    }

    @Test
    void loadFiltersEntriesOfOtherDimensions() throws Exception {
        // 手工写入一个维度字段与文件不匹配的条目，应被过滤。
        WaypointStore overworld = WaypointStore.open(tempDir, "key", Level.OVERWORLD);
        Path file = overworld.file();
        Files.createDirectories(file.getParent());
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(Waypoint.create("wrong", BlockPos.ZERO, Level.NETHER, 0xFFFFFFFF).save());
        tag.put("waypoints", list);
        net.minecraft.nbt.NbtIo.writeCompressed(tag, file);

        WaypointStore reopened = WaypointStore.open(tempDir, "key", Level.OVERWORLD);
        assertTrue(reopened.all().isEmpty(), "跨维度条目必须被过滤");
    }

    @Test
    void visibleInFiltersByDistance() {
        WaypointStore store = WaypointStore.open(tempDir, "key", Level.OVERWORLD);
        Waypoint near = Waypoint.create("near", new BlockPos(0, 64, 0), Level.OVERWORLD,
                0xFFFFFFFF);
        Waypoint far = new Waypoint(java.util.UUID.randomUUID(), "far", new BlockPos(1000, 64, 0),
                Level.OVERWORLD, 0xFFFFFFFF, "default", true, 100, false, "", true);
        store.upsert(near);
        store.upsert(far);

        List<Waypoint> visible = store.visibleIn(0.0, 0.0);
        assertEquals(1, visible.size());
        assertEquals("near", visible.get(0).name());
    }
}
