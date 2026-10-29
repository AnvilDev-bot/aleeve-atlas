package dev.dubhe.map.map;

import net.minecraft.world.level.block.state.BlockState;

/// 自上向下扫描的一列：完整洞穴先穿过顶棚，再寻找空气/流体下方的地形。
final class CaveColumn {
    private boolean enteredRoof;
    private boolean open;

    CaveColumn(MapSlice slice) {
        this.enteredRoof = !slice.fullCave();
        this.open = !slice.cave();
    }

    boolean visible(BlockState state) {
        if (!this.enteredRoof) {
            if (CaveMode.isRoof(state)) this.enteredRoof = true;
            return false;
        }
        if (state.isAir()) {
            this.open = true;
            return false;
        }
        if (!state.getFluidState().isEmpty()) this.open = true;
        return this.open;
    }
}
