package dev.dubhe.map.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AtlasPathsTest {
    @TempDir
    Path tempDir;

    @Test
    void migrationCopiesMapDataButLeavesOtherServerDataInPlace() throws IOException {
        Path source = AtlasPaths.serverRoot(tempDir, "address-key");
        Path mapFile = source.resolve("minecraft_overworld/region.dat");
        Path waypointFile = source.resolve("waypoints/minecraft_overworld.nbt");
        Path seedFile = source.resolve("slime_seed.nbt");
        Files.createDirectories(mapFile.getParent());
        Files.createDirectories(waypointFile.getParent());
        Files.writeString(mapFile, "map");
        Files.writeString(waypointFile, "waypoints");
        Files.writeString(seedFile, "seed");

        AtlasPaths.migrateMapCache(tempDir, "address-key", "server-key");

        Path target = AtlasPaths.serverRoot(tempDir, "server-key");
        assertEquals("map", Files.readString(target.resolve("minecraft_overworld/region.dat")));
        assertFalse(Files.exists(target.resolve("waypoints")));
        assertFalse(Files.exists(target.resolve("slime_seed.nbt")));
        assertEquals("waypoints", Files.readString(waypointFile));
        assertEquals("seed", Files.readString(seedFile));
    }

    @Test
    void migrationKeepsExistingDestinationData() throws IOException {
        Path sourceFile = AtlasPaths.serverRoot(tempDir, "address-key")
                .resolve("minecraft_overworld/region.dat");
        Path targetFile = AtlasPaths.serverRoot(tempDir, "server-key")
                .resolve("minecraft_overworld/region.dat");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(targetFile.getParent());
        Files.writeString(sourceFile, "old");
        Files.writeString(targetFile, "current");

        AtlasPaths.migrateMapCache(tempDir, "address-key", "server-key");

        assertEquals("current", Files.readString(targetFile));
    }
}
