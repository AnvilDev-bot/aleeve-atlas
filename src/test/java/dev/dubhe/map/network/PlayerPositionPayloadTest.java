package dev.dubhe.map.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/// 在线玩家位置载荷的编解码测试。
class PlayerPositionPayloadTest {
    @Test
    void roundTripPreservesPositionAndIdentity() {
        UUID id = UUID.randomUUID();
        PlayerPositionPayload original = new PlayerPositionPayload(id, "Player",
                "minecraft:the_nether", 12.5, 70.0, -30.25, false);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PlayerPositionPayload.STREAM_CODEC.encode(buf, original);
        PlayerPositionPayload decoded = PlayerPositionPayload.STREAM_CODEC.decode(buf);

        Assertions.assertEquals(id, decoded.playerId());
        Assertions.assertEquals("Player", decoded.playerName());
        Assertions.assertEquals("minecraft:the_nether", decoded.dimensionId());
        Assertions.assertEquals(12.5, decoded.x());
        Assertions.assertEquals(70.0, decoded.y());
        Assertions.assertEquals(-30.25, decoded.z());
    }

    @Test
    void removalFlagSurvivesRoundTrip() {
        PlayerPositionPayload original = new PlayerPositionPayload(UUID.randomUUID(), "Player",
                "minecraft:overworld", 0.0, 64.0, 0.0, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PlayerPositionPayload.STREAM_CODEC.encode(buf, original);
        Assertions.assertTrue(PlayerPositionPayload.STREAM_CODEC.decode(buf).removal());
    }
}
