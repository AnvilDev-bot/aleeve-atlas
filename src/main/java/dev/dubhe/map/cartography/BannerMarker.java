package dev.dubhe.map.cartography;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/// 从原版地图保存数据读取旗帜的真实坐标、底色和名称，无需访问旗帜所在区块。
@NullMarked
public record BannerMarker(BlockPos pos, DyeColor color, String name) {
    public static final int MAX_MARKERS = 256;
    // 与路径点分享协议的名称长度一致，保证导入后仍可手动分享。
    public static final int MAX_NAME_LENGTH = 128;

    public BannerMarker {
        pos = pos.immutable();
        if (Math.abs((long) pos.getX()) > 30_000_000 || Math.abs((long) pos.getZ()) > 30_000_000
                || !BlockPos.of(pos.asLong()).equals(pos) || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid map banner at " + pos);
        }
    }

    public static List<BannerMarker> fromMap(MapItemSavedData data) {
        if (data.getBanners().size() > MAX_MARKERS) {
            throw new IllegalArgumentException("Too many map banners");
        }
        return data.getBanners().stream().map(banner -> {
            String name = banner.name().map(component -> component.getString()).orElse("");
            if (name.length() > MAX_NAME_LENGTH) {
                int end = Character.isHighSurrogate(name.charAt(MAX_NAME_LENGTH - 1))
                        ? MAX_NAME_LENGTH - 1 : MAX_NAME_LENGTH;
                name = name.substring(0, end);
            }
            return new BannerMarker(banner.pos(), banner.color(), name);
        }).toList();
    }

    void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeEnum(this.color);
        buffer.writeUtf(this.name, MAX_NAME_LENGTH);
    }

    static List<BannerMarker> readList(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_MARKERS) {
            throw new IllegalArgumentException("Invalid map banner count: " + count);
        }
        List<BannerMarker> markers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            markers.add(new BannerMarker(buffer.readBlockPos(), buffer.readEnum(DyeColor.class),
                    buffer.readUtf(MAX_NAME_LENGTH)));
        }
        return markers;
    }
}
