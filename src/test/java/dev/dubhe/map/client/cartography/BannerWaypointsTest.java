package dev.dubhe.map.client.cartography;

import dev.dubhe.map.cartography.BannerMarker;
import dev.dubhe.map.waypoint.WaypointStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BannerWaypointsTest {
    @TempDir
    Path directory;

    @Test
    void importsNameColorExactHeightAndPrivateWaypointSettings() {
        var store = WaypointStore.open(this.directory, "example", Level.OVERWORLD);
        for (DyeColor color : DyeColor.values()) {
            var marker = new BannerMarker(new BlockPos(-129, -32, 257), color, "example");
            var waypoint = BannerWaypoints.missing(List.of(marker), store).getFirst();
            assertEquals(marker.pos(), waypoint.pos());
            assertEquals("example", waypoint.name());
            assertEquals(0xFF000000 | color.getTextureDiffuseColor(), waypoint.colorArgb());
            assertTrue(waypoint.enabled());
            assertFalse(waypoint.deathPoint());
            assertFalse(waypoint.shareable());
        }
    }

    @Test
    void unnamedAndWhitespaceNamedBannersUseTheirColorNames() {
        var store = WaypointStore.open(this.directory, "example", Level.OVERWORLD);
        var unnamed = new BannerMarker(new BlockPos(0, 64, 0), DyeColor.RED, "");
        var whitespace = new BannerMarker(new BlockPos(1, 64, 0), DyeColor.CYAN, "   ");
        var imported = BannerWaypoints.missing(List.of(unnamed, whitespace), store);
        assertFalse(imported.getFirst().name().isBlank());
        assertFalse(imported.getLast().name().isBlank());
        assertNotEquals(imported.getFirst().name(), imported.getLast().name());
    }

    @Test
    void repeatedImportsAndChangedMapBannersPreserveEditedWaypointAfterReload() {
        var store = WaypointStore.open(this.directory, "example", Level.OVERWORLD);
        var banner = new BannerMarker(new BlockPos(-129, 80, 257), DyeColor.RED, "example");
        var firstImport = BannerWaypoints.missing(List.of(banner, banner), store);
        assertEquals(1, firstImport.size());
        var edited = firstImport.getFirst().withDetails("bar", new BlockPos(1, 2, 3), 0xFF123456, 80, false, true);
        store.upsert(edited);
        store.flush();
        var reopened = WaypointStore.open(this.directory, "example", Level.OVERWORLD);
        var changedBanner = new BannerMarker(banner.pos(), DyeColor.BLUE, "foo");
        assertTrue(BannerWaypoints.missing(List.of(changedBanner), reopened).isEmpty());
        assertEquals(edited, reopened.get(edited.id()).orElseThrow());
        assertTrue(BannerWaypoints.missing(List.of(), reopened).isEmpty());
        assertEquals(1, reopened.all().size());
    }

    @Test
    void differentDimensionsHeightsAndServersKeepIndependentWaypoints() {
        var overworld = WaypointStore.open(this.directory, "example", Level.OVERWORLD);
        var nether = WaypointStore.open(this.directory, "example", Level.NETHER);
        var otherServer = WaypointStore.open(this.directory, "bar", Level.OVERWORLD);
        var banner = new BannerMarker(new BlockPos(10, 64, 20), DyeColor.WHITE, "example");
        var original = BannerWaypoints.missing(List.of(banner), overworld).getFirst();
        overworld.upsert(original);
        overworld.flush();
        var otherDimension = BannerWaypoints.missing(List.of(banner), nether).getFirst();
        assertEquals(Level.NETHER, otherDimension.dimension());
        assertNotEquals(original.id(), otherDimension.id());
        assertEquals(1, BannerWaypoints.missing(List.of(banner), otherServer).size());
        var higher = new BannerMarker(banner.pos().above(), banner.color(), banner.name());
        assertNotEquals(original.id(), BannerWaypoints.missing(List.of(higher), overworld).getFirst().id());
    }
}
