package dev.dubhe.map.map;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/// 参考 NeoMclansMap 的群系染色与地形阴影配色。
///
/// 基础色仍取方块地图色，避免依赖外部颜色表；水体与水下地表按深度混合，
/// 最后根据北、西两向的高度差叠加 HSV 阴影。
final class BiomeShadePalette {
    private static final int MAX_DEPTH = 15;
    private static final int MAX_SHADOW_DISTANCE = 2;
    private static final Set<Block> GRASS_BLOCKS = Set.of(
            Blocks.GRASS_BLOCK, Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.LILY_PAD);
    private static final Set<Block> FOLIAGE_BLOCKS = Set.of(
            Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.VINE);
    private static final Set<Block> WATER_BLOCKS = Set.of(
            Blocks.WATER, Blocks.WATER_CAULDRON, Blocks.BUBBLE_COLUMN);

    private BiomeShadePalette() {
    }

    /// 按群系对草地、树叶和水体进行乘法染色。
    static int colorWithBiome(int baseColor, BlockState state, Holder<Biome> biome,
            BlockPos pos) {
        Block block = state.getBlock();
        if (GRASS_BLOCKS.contains(block)) {
            return multiplyColor(baseColor,
                    BiomeColors.GRASS_COLOR_RESOLVER.getColor(biome.value(), pos.getX(), pos.getZ()));
        }
        if (FOLIAGE_BLOCKS.contains(block)) {
            return multiplyColor(baseColor,
                    BiomeColors.FOLIAGE_COLOR_RESOLVER.getColor(biome.value(), pos.getX(), pos.getZ()));
        }
        if (WATER_BLOCKS.contains(block)) {
            return multiplyColor(baseColor,
                    BiomeColors.WATER_COLOR_RESOLVER.getColor(biome.value(), pos.getX(), pos.getZ()));
        }
        return 0xFF000000 | (baseColor & 0xFFFFFF);
    }

    /// 水体颜色与水下地表颜色按深度混合。
    static int blendWaterColor(int waterColor, int groundColor, int depth) {
        float ratio = Math.min(1.0F, depth / (float) MAX_DEPTH);
        int red = (int) (red(groundColor) * (1.0F - ratio) + red(waterColor) * ratio);
        int green = (int) (green(groundColor) * (1.0F - ratio) + green(waterColor) * ratio);
        int blue = (int) (blue(groundColor) * (1.0F - ratio) + blue(waterColor) * ratio);
        return 0xFF000000 | clamp(red, 0, 255) << 16
                | clamp(green, 0, 255) << 8 | clamp(blue, 0, 255);
    }

    /// 对按色板解析出的颜色应用高度阴影，透明像素保持透明。
    static int applyShadow(int color, int brightness) {
        return color == MapPalette.TRANSPARENT ? color : mixColorWithShadow(color, brightness);
    }

    /// 计算每个像素的阴影强度，供测试与渲染共用。
    static int[] computeLighting(int[] heights) {
        int[] lighting = new int[ChunkMapData.PIXELS];
        for (int x = 0; x < ChunkMapData.SIZE; x++) {
            for (int z = 0; z < ChunkMapData.SIZE; z++) {
                int shadow = 0;
                int current = heights[index(x, z)];
                for (int offset = 1; offset <= MAX_SHADOW_DISTANCE; offset++) {
                    if (z - offset >= 0) {
                        shadow += adjustedDifference(heights[index(x, z - offset)] - current) / offset;
                    }
                    if (x - offset >= 0) {
                        shadow += adjustedDifference(heights[index(x - offset, z)] - current) / offset;
                    }
                }
                lighting[index(x, z)] = clamp(shadow * 4, -255, 255);
            }
        }
        return lighting;
    }

    private static int adjustedDifference(int difference) {
        if (difference > 0) {
            return clamp(difference, 1, 6) + 5;
        }
        if (difference < 0) {
            return clamp(difference, -6, -1) - 2;
        }
        return 0;
    }

    private static int mixColorWithShadow(int color, int brightness) {
        float[] hsv = rgbToHsv(color);
        float value = clamp(hsv[2] - brightness, 20.0F, hsv[2] + 40.0F);
        int[] rgb = hsvToRgb(hsv[0] - brightness * 0.1F, hsv[1], value);
        return 0xFF000000 | rgb[0] << 16 | rgb[1] << 8 | rgb[2];
    }

    private static int multiplyColor(int color, int otherColor) {
        int red = red(color) * red(otherColor) / 255;
        int green = green(color) * green(otherColor) / 255;
        int blue = blue(color) * blue(otherColor) / 255;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static float[] rgbToHsv(int color) {
        float red = red(color) / 255.0F;
        float green = green(color) / 255.0F;
        float blue = blue(color) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        float hue;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (max == red) {
            hue = 60.0F * (((green - blue) / delta) % 6.0F);
        } else if (max == green) {
            hue = 60.0F * ((blue - red) / delta + 2.0F);
        } else {
            hue = 60.0F * ((red - green) / delta + 4.0F);
        }
        return new float[] { hue, max == 0.0F ? 0.0F : delta / max * 100.0F, max * 100.0F };
    }

    private static int[] hsvToRgb(float hue, float saturation, float value) {
        float s = clamp(saturation / 100.0F, 0.0F, 1.0F);
        float v = clamp(value / 100.0F, 0.0F, 1.0F);
        float h = hue % 360.0F;
        if (h < 0.0F) {
            h += 360.0F;
        }
        if (s == 0.0F) {
            int gray = Math.round(v * 255.0F);
            return new int[] { gray, gray, gray };
        }
        float segment = h / 60.0F;
        int index = (int) Math.floor(segment);
        float fraction = segment - index;
        float p = v * (1.0F - s);
        float q = v * (1.0F - s * fraction);
        float t = v * (1.0F - s * (1.0F - fraction));
        float red;
        float green;
        float blue;
        switch (index) {
            case 0 -> {
                red = v;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = v;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = v;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = v;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = v;
            }
            default -> {
                red = v;
                green = p;
                blue = q;
            }
        }
        return new int[] { Math.round(red * 255.0F), Math.round(green * 255.0F),
                Math.round(blue * 255.0F) };
    }

    private static int index(int x, int z) {
        return z * ChunkMapData.SIZE + x;
    }

    private static int red(int color) {
        return color >>> 16 & 0xFF;
    }

    private static int green(int color) {
        return color >>> 8 & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
