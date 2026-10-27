package dev.dubhe.map.waypoint;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/// 路径点同步载荷解码的容错测试。
///
/// 维度标识非法时解码不能抛异常：载荷在 optional 通道上传输，
/// 解码失败会中断连接，回退主世界并保留其余字段是更合适的处理。
class WaypointSyncPayloadDecodeTest {
    @Test
    void roundTripPreservesWaypointFields() {
        Waypoint original = new Waypoint(UUID.randomUUID(), "Base", new BlockPos(10, 64, -20),
                Level.NETHER, 0xFF00FF00, "home", true, 300, false, "Aleeve", true);
        WaypointSyncPayload payload =
                new WaypointSyncPayload(WaypointSyncPayload.Action.UPSERT, original);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        WaypointSyncPayload.STREAM_CODEC.encode(buf, payload);
        WaypointSyncPayload decoded = WaypointSyncPayload.STREAM_CODEC.decode(buf);

        Assertions.assertEquals(WaypointSyncPayload.Action.UPSERT, decoded.action());
        Assertions.assertEquals(original, decoded.waypoint());
    }

    @Test
    void invalidDimensionFallsBackToOverworld() {
        // 手工构造非法维度标识的载荷：解码应回退主世界并保留其余字段。
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnum(WaypointSyncPayload.Action.UPSERT);
        buf.writeUUID(UUID.randomUUID());
        buf.writeUtf("Broken", 128);
        buf.writeLong(BlockPos.ZERO.asLong());
        buf.writeUtf("not a valid id!!", 256);
        buf.writeInt(0xFFFFFFFF);
        buf.writeUtf("default", 32);
        buf.writeBoolean(true);
        buf.writeVarInt(256);
        buf.writeBoolean(false);
        buf.writeUtf("", 64);
        buf.writeBoolean(true);

        WaypointSyncPayload decoded = WaypointSyncPayload.STREAM_CODEC.decode(buf);

        Assertions.assertNotNull(decoded);
        Assertions.assertEquals(Level.OVERWORLD, decoded.waypoint().dimension(),
                "非法维度标识必须回退主世界");
        Assertions.assertEquals("Broken", decoded.waypoint().name(), "其余字段应保持不变");
    }
}
