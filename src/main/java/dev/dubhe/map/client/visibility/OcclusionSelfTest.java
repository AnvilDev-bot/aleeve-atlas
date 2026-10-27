package dev.dubhe.map.client.visibility;

import com.mojang.authlib.GameProfile;
import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/// 遮挡查询的自动化验收探针。
///
/// 仅在设置 `ALEEVE_ATLAS_OCCLUSION_SELFTEST` 时启用：在玩家前方生成一个远端玩家，
/// 先验证无遮挡时查询判定为可见，再在两者之间砌墙验证判定为遮挡，
/// 最后拆除墙面恢复可见。每一步都记录查询样本数，用于确认判定确实来自 GPU。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class OcclusionSelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/occlusion/selftest");
    private static final int WALL_WIDTH = 5;
    private static final int WALL_HEIGHT = 9;

    private static RemotePlayer target;
    private static BlockPos wallOrigin;
    private static int phaseTicks;
    private static int phase;
    private static boolean done;

    private OcclusionSelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (done || System.getenv("ALEEVE_ATLAS_OCCLUSION_SELFTEST") == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!minecraft.hasSingleplayerServer()) {
            return;
        }
        phaseTicks++;
        switch (phase) {
            case 0 -> spawn(minecraft);
            case 1 -> settleOpen(minecraft);
            case 2 -> buildWall(minecraft);
            case 3 -> settleOccluded(minecraft);
            case 4 -> removeWall(minecraft);
            case 5 -> settleRestored(minecraft);
            default -> {
            }
        }
    }

    private static void spawn(Minecraft minecraft) {
        if (phaseTicks < 40) {
            return;
        }
        ClientLevel level = minecraft.level;
        Vec3 eye = minecraft.player.position();
        // 目标放在当前视线正前方，避免相机朝向与生成方向相反导致视锥剔除。
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 pos = eye.add(look.scale(12.0));
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(
                "aleeve_atlas_occlusion_probe".getBytes(StandardCharsets.UTF_8)),
                "AtlasProbe");
        target = new RemotePlayer(level, profile);
        target.setId(9001);
        target.setPos(pos.x, pos.y, pos.z);
        level.addEntity(target);
        Vec3 wallCenter = eye.add(look.scale(6.0));
        wallOrigin = BlockPos.containing(wallCenter.x, wallCenter.y - 4.0, wallCenter.z);
        LOGGER.info("Occlusion self-test: spawned probe at {} (player eye {})",
                pos, eye);
        phase = 1;
        phaseTicks = 0;
    }

    private static void settleOpen(Minecraft minecraft) {
        if (phaseTicks < 60) {
            return;
        }
        LOGGER.info("Occlusion self-test: open{}", OcclusionDiagnostics.describe(target));
        phase = 2;
        phaseTicks = 0;
    }

    private static void buildWall(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        BlockState stone = Blocks.STONE.defaultBlockState();
        int[] axis = wallAxis(minecraft);
        for (int offset = -WALL_WIDTH; offset <= WALL_WIDTH; offset++) {
            for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                level.setBlock(wallOrigin.offset(axis[0] * offset, dy, axis[1] * offset),
                        stone, 3, 0);
            }
        }
        LOGGER.info("Occlusion self-test: wall built around {}", wallOrigin);
        phase = 3;
        phaseTicks = 0;
    }

    private static void settleOccluded(Minecraft minecraft) {
        if (phaseTicks % 60 == 0) {
            LOGGER.info("Occlusion self-test: occluded{}", OcclusionDiagnostics.describe(target));
        }
        if (phaseTicks < 300) {
            return;
        }
        phase = 4;
        phaseTicks = 0;
    }

    /// 墙面沿视线水平法向展开，任意朝向下都能横跨视线。
    private static int[] wallAxis(Minecraft minecraft) {
        Vec3 look = minecraft.player == null ? new Vec3(0.0, 0.0, 1.0)
                : minecraft.player.getLookAngle();
        int stepX = Math.round((float) -look.z);
        int stepZ = Math.round((float) look.x);
        if (stepX == 0 && stepZ == 0) {
            stepX = 1;
        }
        return new int[] { stepX, stepZ };
    }

    private static void removeWall(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        int[] axis = wallAxis(minecraft);
        for (int offset = -WALL_WIDTH; offset <= WALL_WIDTH; offset++) {
            for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                level.setBlock(wallOrigin.offset(axis[0] * offset, dy, axis[1] * offset),
                        Blocks.AIR.defaultBlockState(), 3, 0);
            }
        }
        LOGGER.info("Occlusion self-test: wall removed");
        phase = 5;
        phaseTicks = 0;
    }

    private static void settleRestored(Minecraft minecraft) {
        if (phaseTicks < 60) {
            return;
        }
        LOGGER.info("Occlusion self-test: restored{}", OcclusionDiagnostics.describe(target));
        LOGGER.info("Occlusion self-test: completed");
        done = true;
        phase = 6;
    }
}
