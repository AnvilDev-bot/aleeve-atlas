package dev.dubhe.atlas.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// 路径点「写入 → 落盘 → 重载 → 读取」全链路测试（规格 §11）。
///
/// 补足此前各模块单独测试、缺乏跨维度端到端覆盖的缺口：
/// 存储布局由单文件改为按维度分文件后，曾连续出现导入导出、同步写入、同步删除三处遗漏。
class WaypointPersistenceIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void writeInOneDimensionIsInvisibleInAnother() {
        WaypointStore overworld = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        WaypointStore nether = WaypointStore.open(tempDir, "srv", Level.NETHER);

        overworld.upsert(Waypoint.create("ow", new BlockPos(1, 60, 1), Level.OVERWORLD,
                0xFFFFFFFF));
        overworld.flush();
        nether.flush();

        // 重载后各自只看到本维度条目。
        WaypointStore reopenedOw = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        WaypointStore reopenedNether = WaypointStore.open(tempDir, "srv", Level.NETHER);
        assertEquals(1, reopenedOw.all().size());
        assertTrue(reopenedNether.all().isEmpty());
    }

    @Test
    void simultaneousUpdatesAcrossDimensionsDoNotInterfere() {
        WaypointStore ow = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        WaypointStore nether = WaypointStore.open(tempDir, "srv", Level.NETHER);

        Waypoint owPoint = Waypoint.create("ow", new BlockPos(10, 64, 10), Level.OVERWORLD,
                0xFFFFFFFF);
        Waypoint netherPoint = Waypoint.create("nether", new BlockPos(20, 32, 20), Level.NETHER,
                0xFFFF0000);
        ow.upsert(owPoint);
        nether.upsert(netherPoint);
        ow.flush();
        nether.flush();

        WaypointStore reopenedOw = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        WaypointStore reopenedNether = WaypointStore.open(tempDir, "srv", Level.NETHER);
        assertEquals("ow", reopenedOw.all().iterator().next().name());
        assertEquals("nether", reopenedNether.all().iterator().next().name());
    }

    @Test
    void deleteThenReloadReflectsRemoval() {
        WaypointStore store = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        Waypoint point = Waypoint.create("temp", new BlockPos(0, 64, 0), Level.OVERWORLD,
                0xFFFFFFFF);
        store.upsert(point);
        store.flush();

        WaypointStore reopened = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        assertTrue(reopened.remove(point.id()));
        reopened.flush();

        WaypointStore finalState = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        assertTrue(finalState.all().isEmpty());
        assertFalse(finalState.get(point.id()).isPresent());
    }

    @Test
    void multipleServerKeysAreIsolated() {
        WaypointStore serverA = WaypointStore.open(tempDir, "server_a", Level.OVERWORLD);
        WaypointStore serverB = WaypointStore.open(tempDir, "server_b", Level.OVERWORLD);

        serverA.upsert(Waypoint.create("a", new BlockPos(1, 64, 1), Level.OVERWORLD,
                0xFFFFFFFF));
        serverA.flush();

        WaypointStore reopenedB = WaypointStore.open(tempDir, "server_b", Level.OVERWORLD);
        assertTrue(reopenedB.all().isEmpty(), "不同服务器/存档必须隔离");
    }

    @Test
    void allFilesLiveUnderSameServerRoot() {
        WaypointStore ow = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        WaypointStore nether = WaypointStore.open(tempDir, "srv", Level.NETHER);

        Path owDir = ow.file().getParent();
        Path netherDir = nether.file().getParent();
        assertEquals(owDir, netherDir, "各维度文件应位于同一 waypoints 目录");
        assertTrue(owDir.endsWith("waypoints"));
        // 上层应为服务器标识目录（与地图缓存同根）。
        assertTrue(owDir.getParent().endsWith("srv"));
    }

    @Test
    void fileIsCreatedOnlyAfterFlush() throws Exception {
        WaypointStore store = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        store.upsert(Waypoint.create("x", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF));
        // 未调 flush 前不应存在文件。
        assertFalse(Files.exists(store.file()), "写入内存后不应立即产生文件");
        store.flush();
        assertTrue(Files.exists(store.file()), "flush 后必须落盘");
    }

    @Test
    void duplicateIdUpsertKeepsSingleEntry() {
        WaypointStore store = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        UUID id = UUID.randomUUID();
        Waypoint first = new Waypoint(id, "v1", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF,
                "default", true, 256, false, "", true);
        Waypoint second = new Waypoint(id, "v2", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF,
                "default", true, 256, false, "", true);

        store.upsert(first);
        store.upsert(second);
        assertEquals(1, store.all().size());
        assertEquals("v2", store.all().iterator().next().name());
    }

    @Test
    void deathPointPruningPersists() {
        WaypointStore store = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        store.upsert(Waypoint.create("normal", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF));
        Set<UUID> deathIds = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            Waypoint death = Waypoint.deathPoint(new BlockPos(i, 64, 0), Level.OVERWORLD);
            deathIds.add(death.id());
            store.upsert(death);
        }
        store.pruneDeathPoints();
        store.flush();

        WaypointStore reopened = WaypointStore.open(tempDir, "srv", Level.OVERWORLD);
        assertEquals(1, reopened.all().size(), "死亡点应被清除且结果持久化");
        assertEquals("normal", reopened.all().iterator().next().name());
        for (UUID id : deathIds) {
            assertFalse(reopened.get(id).isPresent());
        }
    }
}
