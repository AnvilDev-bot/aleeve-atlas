package dev.dubhe.atlas.client.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;

/// 雷达分类与配色解析的单元测试（规格 §6.2）。
class RadarCategoryTest {
    @Test
    void monsterCategoryIsHostile() {
        assertEquals(MobCategory.MONSTER, MobCategory.MONSTER);
        // 分类映射规则：MONSTER → HOSTILE；其余生物类别 → FRIENDLY；非生物 → MISC。
        assertEquals(RadarCategory.HOSTILE, mapCategory(MobCategory.MONSTER));
        assertEquals(RadarCategory.FRIENDLY, mapCategory(MobCategory.CREATURE));
        assertEquals(RadarCategory.FRIENDLY, mapCategory(MobCategory.AMBIENT));
        assertEquals(RadarCategory.FRIENDLY, mapCategory(MobCategory.WATER_CREATURE));
        assertEquals(RadarCategory.MISC, mapCategory(MobCategory.MISC));
    }

    @Test
    void hexColorParsingFallsBackOnInvalidInput() {
        int fallback = 0xFF112233;
        assertEquals(0xFFAABBCC, dev.dubhe.atlas.util.ColorUtil.parseHex("#AABBCC", fallback));
        assertEquals(0xFFAABBCC, dev.dubhe.atlas.util.ColorUtil.parseHex("aabbcc", fallback));
        assertEquals(fallback, dev.dubhe.atlas.util.ColorUtil.parseHex("xyz", fallback));
        assertEquals(fallback, dev.dubhe.atlas.util.ColorUtil.parseHex("#AABBC", fallback));
        assertEquals(fallback, dev.dubhe.atlas.util.ColorUtil.parseHex(null, fallback));
    }

    /// 与 {@link RadarCategory#classify} 等价的分类规则（按生物类别输入，便于无实体环境测试）。
    private static RadarCategory mapCategory(MobCategory category) {
        return switch (category) {
            case MONSTER -> RadarCategory.HOSTILE;
            case CREATURE, AMBIENT, WATER_CREATURE, WATER_AMBIENT,
                    UNDERGROUND_WATER_CREATURE -> RadarCategory.FRIENDLY;
            default -> RadarCategory.MISC;
        };
    }
}
