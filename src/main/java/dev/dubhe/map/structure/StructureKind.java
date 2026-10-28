package dev.dubhe.map.structure;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// 结构注册名称与原版地图装饰资源的对应关系。
public enum StructureKind {
    DESERT_VILLAGE("village_desert", "village_desert", "desert_village"),
    PLAINS_VILLAGE("village_plains", "village_plains", "plains_village"),
    SAVANNA_VILLAGE("village_savanna", "village_savanna", "savanna_village"),
    SNOWY_VILLAGE("village_snowy", "village_snowy", "snowy_village"),
    TAIGA_VILLAGE("village_taiga", "village_taiga", "taiga_village"),
    MANSION("mansion", "mansion", "woodland_mansion"),
    MONUMENT("monument", "monument", "ocean_monument"),
    JUNGLE_TEMPLE("jungle_pyramid", "jungle_temple", "jungle_temple"),
    SWAMP_HUT("swamp_hut", "swamp_hut", "swamp_hut"),
    TRIAL_CHAMBERS("trial_chambers", "trial_chambers", "trial_chambers");

    private final Identifier structureId;
    private final Identifier decorationId;
    private final Identifier sprite;

    StructureKind(String structure, String decoration, String sprite) {
        this.structureId = Identifier.withDefaultNamespace(structure);
        this.decorationId = Identifier.withDefaultNamespace(decoration);
        this.sprite = Identifier.withDefaultNamespace(sprite);
    }

    public Identifier structureId() {
        return this.structureId;
    }

    public Identifier decorationId() {
        return this.decorationId;
    }

    public Identifier sprite() {
        return this.sprite;
    }

    public String translationKey() {
        return "aleeve_atlas.structure.type." + this.structureId.getPath();
    }

    public static @Nullable StructureKind fromId(Identifier id) {
        for (StructureKind kind : values()) {
            if (kind.structureId.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
