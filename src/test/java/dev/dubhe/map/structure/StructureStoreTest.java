package dev.dubhe.map.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureStoreTest {
    @TempDir
    Path directory;

    static StructureRecord record(int chunkX, StructureRecord.Source source) {
        return new StructureRecord("minecraft:overworld", StructureKind.TRIAL_CHAMBERS, chunkX, -2,
                new BlockPos(chunkX * 16, -30, -32), chunkX * 16 - 4, -40, -40, chunkX * 16 + 35, -10, 8, source);
    }

    @Test
    void discoveryUpgradesSearchWithoutLosingPersonalSettings() {
        Path file = this.directory.resolve("structures.nbt");
        StructureStore store = new StructureStore(file, "minecraft:overworld");
        StructureRecord searched = record(-3, StructureRecord.Source.SEARCHED);
        store.put(searched);
        store.toggleFavorite(searched.key());
        store.toggleFollowed(searched.kind());
        assertTrue(store.put(searched.withSource(StructureRecord.Source.DISCOVERED)));
        assertFalse(store.put(searched.withSource(StructureRecord.Source.SHARED)));
        store.flush();
        StructureStore restored = new StructureStore(file, "minecraft:overworld");
        assertEquals(1, restored.all().size());
        assertEquals(StructureRecord.Source.DISCOVERED, restored.all().getFirst().source());
        assertTrue(restored.favorite(searched.key()));
        assertTrue(restored.followed(searched.kind()));
        assertFalse(restored.followed(StructureKind.MANSION));
    }

    @Test
    void searchUsesHorizontalDistanceAndFiltersFavorites() {
        StructureStore store = new StructureStore(this.directory.resolve("structures.nbt"), "minecraft:overworld");
        StructureRecord near = record(-1, StructureRecord.Source.SHARED);
        StructureRecord far = record(20, StructureRecord.Source.DISCOVERED);
        store.put(far);
        store.put(near);
        store.toggleFavorite(far.key());
        assertEquals(near, store.search(null, false, 0, 0).getFirst());
        assertEquals(far, store.search(null, true, 0, 0).getFirst());
        assertTrue(store.search(StructureKind.MANSION, false, 0, 0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new StructureStore(
                this.directory.resolve("nether.nbt"), "minecraft:the_nether").put(near));
    }

    @Test
    void corruptEntryDoesNotHideValidRecords() throws Exception {
        Path file = this.directory.resolve("structures.nbt");
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        root.putString("dimension", "minecraft:overworld");
        ListTag records = new ListTag();
        records.add(new CompoundTag());
        records.add(record(0, StructureRecord.Source.DISCOVERED).save());
        root.put("records", records);
        NbtIo.writeCompressed(root, file);
        assertEquals(1, new StructureStore(file, "minecraft:overworld").all().size());
    }
}
