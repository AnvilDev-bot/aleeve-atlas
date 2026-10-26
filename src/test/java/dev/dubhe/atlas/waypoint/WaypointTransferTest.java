package dev.dubhe.atlas.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/// 路径点导入导出测试。
class WaypointTransferTest {
    @TempDir
    Path tempDir;

    @Test
    void exportThenImportRoundTripsAllFields() throws IOException {
        Waypoint original = new Waypoint(UUID.randomUUID(), "Base",
                new BlockPos(12, 70, -34), Level.OVERWORLD, 0xFF00FF00, "home", true, 300,
                false, "Aleeve", true);
        Path file = this.tempDir.resolve("export.nbt");

        WaypointTransfer.exportTo(file, List.of(original));
        Assertions.assertTrue(Files.isRegularFile(file));

        List<Waypoint> imported = WaypointTransfer.importFrom(file);
        Assertions.assertEquals(1, imported.size());
        Waypoint restored = imported.get(0);
        Assertions.assertEquals(original.id(), restored.id());
        Assertions.assertEquals("Base", restored.name());
        Assertions.assertEquals(new BlockPos(12, 70, -34), restored.pos());
        Assertions.assertEquals(Level.OVERWORLD, restored.dimension());
        Assertions.assertEquals(0xFF00FF00, restored.colorArgb());
        Assertions.assertEquals(300, restored.visibleDistance());
        Assertions.assertEquals("Aleeve", restored.sourcePlayer());
    }

    @Test
    void exportIncludesMultipleDimensions() throws IOException {
        Waypoint overworld = Waypoint.create("ow", new BlockPos(1, 64, 1), Level.OVERWORLD,
                0xFFFFFFFF);
        Waypoint nether = Waypoint.create("nether", new BlockPos(2, 32, 2), Level.NETHER,
                0xFFFF0000);
        Path file = this.tempDir.resolve("multi.nbt");
        WaypointTransfer.exportTo(file, List.of(overworld, nether));

        List<Waypoint> imported = WaypointTransfer.importFrom(file);
        Assertions.assertEquals(2, imported.size());
        Assertions.assertTrue(imported.stream().anyMatch(w -> w.dimension().equals(Level.OVERWORLD)));
        Assertions.assertTrue(imported.stream().anyMatch(w -> w.dimension().equals(Level.NETHER)));
    }

    @Test
    void importRejectsForeignFile() throws IOException {
        Path file = this.tempDir.resolve("foreign.nbt");
        CompoundTag tag = new CompoundTag();
        tag.putString("unrelated", "data");
        NbtIo.writeCompressed(tag, file);

        Assertions.assertThrows(IOException.class, () -> WaypointTransfer.importFrom(file));
    }

    @Test
    void importRejectsNewerFormat() throws IOException {
        // 旧版客户端不应静默导入新版格式：字段可能缺失或被重新解释。
        Path file = this.tempDir.resolve("newer.nbt");
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "aleeve_atlas_waypoints");
        tag.putInt("format", WaypointTransfer.FORMAT_VERSION + 1);
        NbtIo.writeCompressed(tag, file);

        Assertions.assertThrows(IOException.class, () -> WaypointTransfer.importFrom(file));
    }

    @Test
    void importAcceptsCurrentFormat() throws IOException {
        // 当前版本必须可导入，确认校验没有把合法文件挡掉。
        Path file = this.tempDir.resolve("current.nbt");
        List<Waypoint> source = List.of(
                Waypoint.create("home", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF));
        WaypointTransfer.exportTo(file, source);

        List<Waypoint> imported = WaypointTransfer.importFrom(file);
        Assertions.assertEquals(1, imported.size());
        Assertions.assertEquals("home", imported.get(0).name());
    }

    @Test
    void mergeAddsNewAndReplacesExisting() {
        Waypoint sharedId = Waypoint.create("old", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF);
        Waypoint existingOnly = Waypoint.create("existing", new BlockPos(1, 2, 3),
                Level.OVERWORLD, 0xFFFFFFFF);
        Waypoint importedSameId = new Waypoint(sharedId.id(), "new", new BlockPos(9, 9, 9),
                Level.OVERWORLD, 0xFFFF0000, "default", true, 256, false, "", true);
        Waypoint brandNew = Waypoint.create("fresh", new BlockPos(4, 5, 6), Level.OVERWORLD,
                0xFF00FFFF);

        WaypointTransfer.MergeResult result = WaypointTransfer.merge(
                List.of(importedSameId, brandNew), List.of(sharedId, existingOnly));

        Assertions.assertEquals(1, result.added().size());
        Assertions.assertEquals("fresh", result.added().get(0).name());
        Assertions.assertEquals(1, result.replaced().size());
        Assertions.assertEquals(sharedId.id(), result.replaced().get(0).id());
        Assertions.assertEquals(3, result.merged().size());
        // 原有未被覆盖的条目仍在。
        Assertions.assertTrue(result.merged().stream().anyMatch(w -> w.name().equals("existing")));
        // 同 ID 以导入版本为准。
        Assertions.assertTrue(result.merged().stream()
                .anyMatch(w -> w.id().equals(sharedId.id()) && w.name().equals("new")));
    }
}
