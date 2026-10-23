package dev.aleeve.atlas.client.map;

import dev.aleeve.atlas.api.territory.AtlasTerritory;
import dev.aleeve.atlas.territory.SimpleTerritory;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/// 临时演示领地注入（仅用于验收 AC-8/9/10，验证后应移除）。
///
/// 构造一个含内孔洞与外飞地的领地，覆盖连通分量拆分、轮廓描边与标签锚点场景。
final class DemoTerritory {
    private static final String ID = "demo_territory";

    private DemoTerritory() {
    }

    static void registerIfDebugging() {
        // 仅在系统属性显式开启时注入，避免污染正常使用。
        // 临时：验证期间默认开启（截图后恢复为系统属性开关）。

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        int baseX = minecraft.player.chunkPosition().x;
        int baseZ = minecraft.player.chunkPosition().z;
        Set<ChunkPos> chunks = new HashSet<>();
        // 主区域 9×9，中央挖空 3×3 形成环（验证孔洞内圈描边）。
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                chunks.add(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunks.remove(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        // 分离飞地（验证独立分量与独立标签）。
        chunks.add(new ChunkPos(baseX + 8, baseZ + 8));
        chunks.add(new ChunkPos(baseX + 8, baseZ + 9));
        // 对角相接飞地（8 邻域下仍属同一分量）。
        chunks.add(new ChunkPos(baseX + 5, baseZ + 5));

        AtlasTerritory.client().upsertTerritory(SimpleTerritory.of(ID,
                minecraft.level.dimension(), chunks, Component.literal("演示领地"), 0x33AAFF));
    }
}
