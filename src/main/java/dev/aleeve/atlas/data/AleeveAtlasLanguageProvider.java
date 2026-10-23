package dev.aleeve.atlas.data;

import dev.aleeve.atlas.AleeveAtlas;
import dev.aleeve.atlas.config.AtlasClientConfig;
import dev.aleeve.atlas.config.AtlasServerConfig;
import dev.anvilcraft.lib.v2.config.ConfigData;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/// 生成 `assets/aleeve_atlas/lang/en_us.json`；`zh_cn.json` 为人工维护的源文件，两者键集必须一致（规格 §12.3）。
public class AleeveAtlasLanguageProvider extends LanguageProvider {
    public AleeveAtlasLanguageProvider(PackOutput output) {
        super(output, AleeveAtlas.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("key.category.aleeve_atlas.main", "Aleeve Atlas");
        add("key.aleeve_atlas.open_world_map", "Open World Map");
        add("key.aleeve_atlas.new_waypoint", "New Waypoint");
        add("key.aleeve_atlas.ping", "Map Ping");

        add("aleeve_atlas.message.world_map_pending", "World map is not implemented yet.");
        add("aleeve_atlas.message.waypoint_pending", "Waypoints are not implemented yet.");
        add("aleeve_atlas.message.ping_pending", "Pings are not implemented yet.");
        add("aleeve_atlas.message.server_unsupported",
                "Server support component is not installed.");

        add("aleeve_atlas.hud.light", "Light: %s");

        add("aleeve_atlas.screen.world_map", "World Map");
        add("aleeve_atlas.screen.world_map.hint",
                "Drag to pan, scroll to zoom, ESC or M to close");

        // 配置项翻译键由 AnvilLib 依据配置类反射生成（规格 §12.3：配置项必须纳入语言文件）。
        ConfigData.readConfigClass(this, AtlasClientConfig.class);
        ConfigData.readConfigClass(this, AtlasServerConfig.class);
    }
}
