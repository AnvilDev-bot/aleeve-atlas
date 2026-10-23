package dev.aleeve.atlas.client.radar;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/// 实体雷达分类（规格 §6.2）。
public enum RadarCategory {
    PLAYER,
    HOSTILE,
    FRIENDLY,
    ITEM,
    MISC;

    /// 类别的玩家可见名称（规格 §12.3 实体类别名需纳入语言文件）。
    public net.minecraft.network.chat.Component displayName() {
        return net.minecraft.network.chat.Component.translatable(
                "aleeve_atlas.radar.category." + name().toLowerCase(java.util.Locale.ROOT));
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
            if (category == MobCategory.CREATURE
                    || category == MobCategory.AMBIENT
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
