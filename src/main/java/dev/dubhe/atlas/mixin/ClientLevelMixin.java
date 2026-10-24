package dev.dubhe.atlas.mixin;

import dev.dubhe.atlas.client.map.ClientMapManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// 客户端方块变更监听。
///
/// `ClientLevel` 没有暴露方块变更事件，`setBlocksDirty` 是客户端收到方块更新后的
/// 统一入口，在此标记所属区块待重新采样，使地图反映破坏与放置的结果。
@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/mixin");

    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void aleeve_atlas$markChunkDirty(BlockPos pos, BlockState oldState,
            BlockState newState, CallbackInfo ci) {
        // 旧状态与新状态相同表示仅刷新渲染，方块内容未变，无需重新采样。
        if (oldState == newState) {
            return;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Block changed at {}, marking chunk dirty", pos);
        }
        ClientMapManager.markChunkChanged(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
