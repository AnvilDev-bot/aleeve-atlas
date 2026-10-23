package dev.aleeve.atlas.map;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

/// 单个区块的地图数据（规格 §2.1）。
///
/// 保存 `16 x 16` 像素的 ARGB 颜色与每列地表高度，可序列化为 `CompoundTag`（规格 §11：优先 NBT 持久化）。
public final class ChunkMapData {
    public static final int SIZE = 16;
    public static final int PIXELS = SIZE * SIZE;

    private final int chunkX;
    private final int chunkZ;
    private final int[] colors;
    private final int[] heights;
    /// 数据版本号，供增量同步摘要比对使用（规格 §3.3）。
    private long version;

    public ChunkMapData(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.colors = new int[PIXELS];
        this.heights = new int[PIXELS];
        this.version = 0L;
    }

    private ChunkMapData(int chunkX, int chunkZ, int[] colors, int[] heights, long version) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.colors = colors;
        this.heights = heights;
        this.version = version;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public long version() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public int colorAt(int localX, int localZ) {
        return colors[localZ * SIZE + localX];
    }

    public int[] colors() {
        return colors;
    }

    public int[] heights() {
        return heights;
    }

    public int heightAt(int localX, int localZ) {
        return heights[localZ * SIZE + localX];
    }

    public void setPixel(int localX, int localZ, int argb, int height) {
        int index = localZ * SIZE + localX;
        colors[index] = argb;
        heights[index] = height;
    }

    public boolean isEmpty() {
        for (int color : colors) {
            if (color != MapPalette.TRANSPARENT) {
                return false;
            }
        }
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", chunkX);
        tag.putInt("z", chunkZ);
        tag.putLong("v", version);
        tag.putIntArray("c", colors);
        tag.putIntArray("h", heights);
        return tag;
    }

    public static ChunkMapData load(CompoundTag tag) {
        int[] colors = tag.getIntArray("c").orElse(new int[PIXELS]);
        int[] heights = tag.getIntArray("h").orElse(new int[PIXELS]);
        if (colors.length != PIXELS) {
            colors = Arrays.copyOf(colors, PIXELS);
        }
        if (heights.length != PIXELS) {
            heights = Arrays.copyOf(heights, PIXELS);
        }
        return new ChunkMapData(
                tag.getInt("x").orElse(0),
                tag.getInt("z").orElse(0),
                colors,
                heights,
                tag.getLongOr("v", 0L));
    }

    public ChunkPos pos() {
        return new ChunkPos(chunkX, chunkZ);
    }
}
