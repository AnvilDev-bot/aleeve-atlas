package dev.dubhe.map.cartography;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// 每维度独立保存快照；原子替换避免失败导入破坏旧快照。
public final class ImportedMapStore {
    private final Path directory;
    private final String dimension;
    private final List<MapSnapshot> snapshots = new ArrayList<>();
    private long revision;

    public ImportedMapStore(Path directory, String dimension) {
        this.directory = directory;
        this.dimension = dimension;
        if (!Files.isDirectory(directory)) return;
        try (var paths = Files.list(directory)) {
            for (Path file : paths.filter(p -> p.getFileName().toString().endsWith(".nbt")).toList()) {
                try {
                    MapSnapshot snapshot = MapSnapshot.load(NbtIo.readCompressed(file, NbtAccounter.create(1_048_576)));
                    if (dimension.equals(snapshot.dimension())) this.snapshots.add(snapshot);
                } catch (IOException | RuntimeException e) {
                    LoggerFactory.getLogger(ImportedMapStore.class).warn("Skipping invalid imported map {}", file, e);
                }
            }
            this.sort();
        } catch (IOException e) {
            LoggerFactory.getLogger(ImportedMapStore.class).warn("Cannot read imported maps {}", directory, e);
        }
    }

    public void put(MapSnapshot snapshot) throws IOException {
        if (!this.dimension.equals(snapshot.dimension())) throw new IllegalArgumentException("Wrong dimension");
        long latest = this.snapshots.stream().mapToLong(MapSnapshot::importedAt).max().orElse(0);
        snapshot = snapshot.imported(Math.max(System.currentTimeMillis(), latest + 1));
        Files.createDirectories(this.directory);
        Path file = this.directory.resolve(snapshot.mapId() + ".nbt");
        Path temp = Files.createTempFile(this.directory, "import-", ".tmp");
        try {
            NbtIo.writeCompressed(snapshot.save(), temp);
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
        int id = snapshot.mapId();
        this.snapshots.removeIf(existing -> existing.mapId() == id);
        this.snapshots.add(snapshot);
        this.sort();
        this.revision++;
    }

    private void sort() {
        this.snapshots.sort(Comparator.comparingInt(MapSnapshot::scale)
                .thenComparing(Comparator.comparingLong(MapSnapshot::importedAt).reversed())
                .thenComparingInt(MapSnapshot::mapId));
    }

    public long revision() { return this.revision; }

    public List<MapSnapshot> intersecting(int minX, int minZ, int maxX, int maxZ) {
        return this.snapshots.stream().filter(s -> s.minX() <= maxX && s.minZ() <= maxZ
                && (long) s.minX() + s.size() > minX && (long) s.minZ() + s.size() > minZ).toList();
    }

    public static byte pixelAt(List<MapSnapshot> snapshots, int x, int z) {
        for (MapSnapshot snapshot : snapshots) {
            byte pixel = snapshot.pixelAt(x, z);
            if ((Byte.toUnsignedInt(pixel) >> 2) != 0) return pixel;
        }
        return 0;
    }
}
