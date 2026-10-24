package dev.dubhe.atlas.client.radar;

import java.util.Locale;

import net.minecraft.network.chat.Component;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/// 实体雷达分类。
public enum RadarCategory {
    PLAYER,
    HOSTILE,
    FRIENDLY,
    /// 极小型环境生物（蝙蝠等），可独立开关。
    AMBIENT,
    ITEM,
    MISC;

    /// 类别的玩家可见名称。
    public Component displayName() {
        return Component.translatable(
                "aleeve_atlas.radar.category." + name().toLowerCase(Locale.ROOT));
    }

    /// 判定一个实体属于哪一类雷达标记；不参与雷达的实体返回 {@code null}。
    public static RadarCategory classify(Entity entity) {
        if (entity instanceof Player) {
            return PLAYER;
        }
        if (entity instanceof ItemEntity) {
            return ITEM;
        }
        if (entity instanceof LivingEntity living) {
            MobCategory category = living.getType().getCategory();
            if (category == MobCategory.MONSTER) {
                return HOSTILE;
            }
            // 极小型环境生物独立分类，可由配置单独过滤。
            if (category == MobCategory.AMBIENT) {
                return AMBIENT;
            }
            if (category == MobCategory.CREATURE
                    || category == MobCategory.WATER_CREATURE
                    || category == MobCategory.WATER_AMBIENT
                    || category == MobCategory.UNDERGROUND_WATER_CREATURE) {
                return FRIENDLY;
            }
            return MISC;
        }
        return MISC;
    }
}
