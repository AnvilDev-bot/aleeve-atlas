package dev.dubhe.atlas.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/// 路径点「写入 → 保存到磁盘 → 重载 → 读取」全链路测试。
///
/// 覆盖跨维度的端到端行为：存储布局由单文件改为按维度分文件后，
/// 导入导出、同步写入、同步删除三处曾出现遗漏。
class WaypointPersistenceIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void simultaneousUpdatesAcrossDimensionsDoNotInterfere() {
        WaypointStore ow = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        WaypointStore nether = WaypointStore.open(this.tempDir, "srv", Level.NETHER);

        Waypoint owPoint = Waypoint.create("ow", new BlockPos(10, 64, 10), Level.OVERWORLD,
                0xFFFFFFFF);
        Waypoint netherPoint = Waypoint.create("nether", new BlockPos(20, 32, 20), Level.NETHER,
                0xFFFF0000);
        ow.upsert(owPoint);
        nether.upsert(netherPoint);
        ow.flush();
        nether.flush();

        WaypointStore reopenedOw = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        WaypointStore reopenedNether = WaypointStore.open(this.tempDir, "srv", Level.NETHER);
        Assertions.assertEquals("ow", reopenedOw.all().iterator().next().name());
        Assertions.assertEquals("nether", reopenedNether.all().iterator().next().name());
    }

    @Test
    void deleteThenReloadReflectsRemoval() {
        WaypointStore store = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        Waypoint point = Waypoint.create("temp", new BlockPos(0, 64, 0), Level.OVERWORLD,
                0xFFFFFFFF);
        store.upsert(point);
        store.flush();

        WaypointStore reopened = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        Assertions.assertTrue(reopened.remove(point.id()));
        reopened.flush();

        WaypointStore finalState = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        Assertions.assertTrue(finalState.all().isEmpty());
        Assertions.assertFalse(finalState.get(point.id()).isPresent());
    }

    @Test
    void multipleServerKeysAreIsolated() {
        WaypointStore serverA = WaypointStore.open(this.tempDir, "server_a", Level.OVERWORLD);
        WaypointStore serverB = WaypointStore.open(this.tempDir, "server_b", Level.OVERWORLD);

        serverA.upsert(Waypoint.create("a", new BlockPos(1, 64, 1), Level.OVERWORLD,
                0xFFFFFFFF));
        serverA.flush();

        WaypointStore reopenedB = WaypointStore.open(this.tempDir, "server_b", Level.OVERWORLD);
        Assertions.assertTrue(reopenedB.all().isEmpty(), "不同服务器/存档必须隔离");
    }

    @Test
    void allFilesLiveUnderSameServerRoot() {
        WaypointStore ow = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        WaypointStore nether = WaypointStore.open(this.tempDir, "srv", Level.NETHER);

        Path owDir = ow.file().getParent();
        Path netherDir = nether.file().getParent();
        Assertions.assertEquals(owDir, netherDir, "各维度文件应位于同一 waypoints 目录");
        Assertions.assertTrue(owDir.endsWith("waypoints"));
        // 上层应为服务器标识目录（与地图缓存同根）。
        Assertions.assertTrue(owDir.getParent().endsWith("srv"));
    }

    @Test
    void fileIsCreatedOnlyAfterFlush() throws Exception {
        WaypointStore store = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        store.upsert(Waypoint.create("x", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF));
        // 未调 flush 前不应存在文件。
        Assertions.assertFalse(Files.exists(store.file()), "写入内存后不应立即产生文件");
        store.flush();
        Assertions.assertTrue(Files.exists(store.file()), "flush 后必须写入磁盘");
    }

    @Test
    void duplicateIdUpsertKeepsSingleEntry() {
        WaypointStore store = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        UUID id = UUID.randomUUID();
        Waypoint first = new Waypoint(id, "v1", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF,
                "default", true, 256, false, "", true);
        Waypoint second = new Waypoint(id, "v2", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF,
                "default", true, 256, false, "", true);

        store.upsert(first);
        store.upsert(second);
        Assertions.assertEquals(1, store.all().size());
        Assertions.assertEquals("v2", store.all().iterator().next().name());
    }

    @Test
    void deathPointPruningPersists() {
        WaypointStore store = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        store.upsert(Waypoint.create("normal", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF));
        Set<UUID> deathIds = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            Waypoint death = Waypoint.deathPoint(new BlockPos(i, 64, 0), Level.OVERWORLD);
            deathIds.add(death.id());
            store.upsert(death);
        }
        store.pruneDeathPoints();
        store.flush();

        WaypointStore reopened = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        Assertions.assertEquals(1, reopened.all().size(), "死亡点应被清除且结果持久化");
        Assertions.assertEquals("normal", reopened.all().iterator().next().name());
        for (UUID id : deathIds) {
            Assertions.assertFalse(reopened.get(id).isPresent());
        }
    }

    @Test
    void pruneThenAddKeepsNewDeathPoint() {
        // 关闭「保留历史死亡点」时的正确顺序：先清理旧点，再写入本次死亡点。
        // 顺序反了会把刚写入的新点一并删除，死亡点功能完全失效。
        WaypointStore store = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        store.upsert(Waypoint.deathPoint(new BlockPos(0, 64, 0), Level.OVERWORLD));
        store.upsert(Waypoint.deathPoint(new BlockPos(1, 64, 0), Level.OVERWORLD));
        store.flush();

        Waypoint newest = Waypoint.deathPoint(new BlockPos(2, 64, 0), Level.OVERWORLD);
        store.pruneDeathPoints();
        store.upsert(newest);
        store.flush();

        WaypointStore reopened = WaypointStore.open(this.tempDir, "srv", Level.OVERWORLD);
        Assertions.assertEquals(1, reopened.all().size(), "只应保留最新的死亡点");
        Assertions.assertTrue(reopened.get(newest.id()).isPresent(), "本次死亡点必须保留");
    }
}
