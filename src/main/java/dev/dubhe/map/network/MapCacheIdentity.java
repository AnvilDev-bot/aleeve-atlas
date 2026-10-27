package dev.dubhe.map.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.dubhe.map.AleeveAtlas;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.UUID;

/// 保存服务端主世界对应的客户端地图缓存标识。
public final class MapCacheIdentity extends SavedData {
    private static final Codec<MapCacheIdentity> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.STRING.fieldOf("id").forGetter(MapCacheIdentity::id))
                    .apply(instance, MapCacheIdentity::new));
    private static final SavedDataType<MapCacheIdentity> TYPE = new SavedDataType<>(
            AleeveAtlas.MOD_ID + "_map_cache_identity", MapCacheIdentity::create, CODEC);

    private final String id;

    private MapCacheIdentity(String id) {
        this.id = id;
    }

    private static MapCacheIdentity create() {
        MapCacheIdentity identity = new MapCacheIdentity(UUID.randomUUID().toString());
        identity.setDirty();
        return identity;
    }

    public static MapCacheIdentity get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public String id() {
        return this.id;
    }
}
