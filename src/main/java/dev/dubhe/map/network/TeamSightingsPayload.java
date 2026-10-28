package dev.dubhe.map.network;

import dev.dubhe.map.AleeveAtlas;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 队友当前看见的玩家快照；客户端上报身份，服务端重新填写身份和权威坐标后转发。
public record TeamSightingsPayload(UUID observerId, List<PlayerPositionPayload> sightings)
        implements CustomPacketPayload {
    public static final int MAX_SIGHTINGS = 64;
    public static final Type<TeamSightingsPayload> TYPE = new Type<>(AleeveAtlas.id("team_sightings"));
    public static final StreamCodec<FriendlyByteBuf, TeamSightingsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.observerId());
                buffer.writeUtf(payload.sightings().isEmpty() ? "" : payload.sightings().getFirst().dimensionId(), 256);
                buffer.writeVarInt(payload.sightings().size());
                for (PlayerPositionPayload sighting : payload.sightings()) {
                    buffer.writeUUID(sighting.playerId());
                    buffer.writeUtf(sighting.playerName(), 64);
                    buffer.writeDouble(sighting.x());
                    buffer.writeDouble(sighting.y());
                    buffer.writeDouble(sighting.z());
                }
            }, buffer -> {
                UUID observer = buffer.readUUID();
                String dimension = buffer.readUtf(256);
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_SIGHTINGS) {
                    throw new DecoderException("Invalid sightings count: " + count);
                }
                List<PlayerPositionPayload> sightings = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    sightings.add(new PlayerPositionPayload(buffer.readUUID(), buffer.readUtf(64), dimension,
                            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), false));
                }
                return new TeamSightingsPayload(observer, sightings);
            });

    public TeamSightingsPayload {
        if (sightings.size() > MAX_SIGHTINGS) {
            throw new IllegalArgumentException("Too many sightings");
        }
        sightings = List.copyOf(sightings);
        String dimension = sightings.isEmpty() ? "" : sightings.getFirst().dimensionId();
        if (!sightings.isEmpty() && sightings.stream().anyMatch(sighting -> sighting.removal()
                || !sighting.dimensionId().equals(dimension))) {
            throw new IllegalArgumentException("Sightings must belong to one dimension");
        }
    }

    @Override
    public Type<TeamSightingsPayload> type() {
        return TYPE;
    }
}
