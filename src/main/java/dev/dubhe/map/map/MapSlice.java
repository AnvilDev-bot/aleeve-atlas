package dev.dubhe.map.map;

/// 两张地图共用的采样范围；缓存按 16 格分层，区块另存精确扫描高度。
public record MapSlice(int topY, int depth) {
    public static final MapSlice SURFACE = new MapSlice(Integer.MAX_VALUE, 0);
    public static final MapSlice FULL_CAVE = new MapSlice(Integer.MIN_VALUE, 0);

    public static MapSlice layered(int topY, int depth) {
        return new MapSlice(topY, Math.clamp(depth, 1, 64));
    }

    public boolean cave() { return this.topY != Integer.MAX_VALUE; }
    public boolean fullCave() { return this.topY == Integer.MIN_VALUE; }

    public String cacheLayer() {
        return !this.cave() ? "surface" : this.fullCave() ? "full" : "layer_" + Math.floorDiv(this.topY, 16);
    }

    public int scanTop(int worldMaxY) {
        return !this.cave() || this.fullCave() ? worldMaxY - 1 : Math.min(this.topY, worldMaxY - 1);
    }

    public int scanBottom(int worldMinY) {
        return !this.cave() || this.fullCave() ? worldMinY : Math.max(worldMinY, this.topY + 1 - this.depth);
    }
}
