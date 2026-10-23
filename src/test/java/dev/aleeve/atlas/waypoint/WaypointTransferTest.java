package dev.aleeve.atlas.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// 路径点导入导出测试（规格 §11）。
class WaypointTransferTest {
    @TempDir
    Path tempDir;

    @Test
    void exportThenImportRoundTripsAllFields() throws IOException {
        Waypoint original = new Waypoint(java.util.UUID.randomUUID(), "Base",
                new BlockPos(12, 70, -34), Level.OVERWORLD, 0xFF00FF00, "home", true, 300,
                false, "Aleeve");
        Path file = tempDir.resolve("export.nbt");

        WaypointTransfer.exportTo(file, List.of(original));
        assertTrue(Files.isRegularFile(file));

        List<Waypoint> imported = WaypointTransfer.importFrom(file);
        assertEquals(1, imported.size());
        Waypoint restored = imported.get(0);
        assertEquals(original.id(), restored.id());
        assertEquals("Base", restored.name());
        assertEquals(new BlockPos(12, 70, -34), restored.pos());
        assertEquals(Level.OVERWORLD, restored.dimension());
        assertEquals(0xFF00FF00, restored.colorArgb());
        assertEquals(300, restored.visibleDistance());
        assertEquals("Aleeve", restored.sourcePlayer());
    }

    @Test
    void importRejectsForeignFile() throws IOException {
        Path file = tempDir.resolve("foreign.nbt");
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("unrelated", "data");
        net.minecraft.nbt.NbtIo.writeCompressed(tag, file);

        assertThrows(IOException.class, () -> WaypointTransfer.importFrom(file));
    }

    @Test
    void mergeAddsNewAndReplacesExisting() {
        Waypoint sharedId = Waypoint.create("old", BlockPos.ZERO, Level.OVERWORLD, 0xFFFFFFFF);
        Waypoint existingOnly = Waypoint.create("existing", new BlockPos(1, 2, 3),
                Level.OVERWORLD, 0xFFFFFFFF);
        Waypoint importedSameId = new Waypoint(sharedId.id(), "new", new BlockPos(9, 9, 9),
                Level.OVERWORLD, 0xFFFF0000, "default", true, 256, false, "");
        Waypoint brandNew = Waypoint.create("fresh", new BlockPos(4, 5, 6), Level.OVERWORLD,
                0xFF00FFFF);

        WaypointTransfer.MergeResult result = WaypointTransfer.merge(
                List.of(importedSameId, brandNew), List.of(sharedId, existingOnly));

        assertEquals(1, result.added().size());
        assertEquals("fresh", result.added().get(0).name());
        assertEquals(1, result.replaced().size());
        assertEquals(sharedId.id(), result.replaced().get(0).id());
        assertEquals(3, result.merged().size());
        // 原有未被覆盖的条目仍在。
        assertTrue(result.merged().stream().anyMatch(w -> w.name().equals("existing")));
        // 同 ID 以导入版本为准。
        assertTrue(result.merged().stream()
                .anyMatch(w -> w.id().equals(sharedId.id()) && w.name().equals("new")));
    }
}
