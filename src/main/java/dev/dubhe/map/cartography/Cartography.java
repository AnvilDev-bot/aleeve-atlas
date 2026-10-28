package dev.dubhe.map.cartography;

import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.map.MapPalette;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;

/// 客户端预览和服务端校验共用的原版地图规则。
public final class Cartography {
    private Cartography() { }

    public static int center(double coordinate) {
        return (int) (Math.floor((coordinate + 64) / 128) * 128);
    }

    public static boolean validRegion(Level level, int x, int z) {
        return x % 128 == 0 && z % 128 == 0
                && Math.abs((long) x) <= 29_999_872 && Math.abs((long) z) <= 29_999_872
                && level.getWorldBorder().isWithinBounds(x - 64.0, z - 64.0)
                && level.getWorldBorder().isWithinBounds(x + 63.999, z + 63.999);
    }

    public static int glassSlot(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.GLASS_PANE)) return i;
        }
        return -1;
    }

    public static boolean hasContent(byte[] colors) {
        for (byte color : colors) if ((Byte.toUnsignedInt(color) >> 2) != 0) return true;
        return false;
    }

    public static byte terrainPixel(ChunkMapData data, int x, int z) {
        var state = data.stateAt(x, z);
        MapColor color = state.vanillaMapColor();
        if (color == MapColor.NONE) return 0;
        int height = data.heightAt(x, z);
        int wx = data.chunkX() * 16 + x, wz = data.chunkZ() * 16 + z;
        var brightness = color == MapColor.WATER
                ? MapPalette.brightnessForWater(state.fluidDepth(), wx, wz)
                : MapPalette.brightnessForTerrain(height, z == 0 ? height : data.heightAt(x, z - 1), wx, wz);
        return color.getPackedId(brightness);
    }
}
