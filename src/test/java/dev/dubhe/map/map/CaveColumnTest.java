package dev.dubhe.map.map;

import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaveColumnTest {
    @Test void fullNetherScanSkipsSkyAndBedrockBeforeExposingFloor() {
        var scan = new CaveColumn(MapSlice.FULL_CAVE);
        assertFalse(scan.visible(Blocks.AIR.defaultBlockState()));
        assertFalse(scan.visible(Blocks.BEDROCK.defaultBlockState()));
        assertFalse(scan.visible(Blocks.NETHERRACK.defaultBlockState()));
        assertFalse(scan.visible(Blocks.CAVE_AIR.defaultBlockState()));
        assertTrue(scan.visible(Blocks.NETHERRACK.defaultBlockState()));
    }

    @Test void enclosedSolidColumnsStayHiddenAndFluidsExposeTheirSurface() {
        var scan = new CaveColumn(MapSlice.layered(90, 30));
        for (int y = 90; y > 60; y--) assertFalse(scan.visible(Blocks.STONE.defaultBlockState()));
        assertTrue(scan.visible(Blocks.LAVA.defaultBlockState()));
        var water = new CaveColumn(MapSlice.layered(40, 30));
        assertTrue(water.visible(Blocks.WATER.defaultBlockState()));
    }

    @Test void surfaceAndCaveColumnsSelectDifferentTerrain() {
        assertTrue(new CaveColumn(MapSlice.SURFACE).visible(Blocks.BEDROCK.defaultBlockState()));
        assertFalse(new CaveColumn(MapSlice.layered(127, 30)).visible(Blocks.BEDROCK.defaultBlockState()));
        assertFalse(CaveMode.isRoof(Blocks.OAK_LEAVES.defaultBlockState()));
        assertFalse(CaveMode.isRoof(Blocks.WATER.defaultBlockState()));
        assertFalse(CaveMode.isRoof(Blocks.GLASS.defaultBlockState()));
        assertTrue(CaveMode.isRoof(Blocks.STONE.defaultBlockState()));
    }
}
