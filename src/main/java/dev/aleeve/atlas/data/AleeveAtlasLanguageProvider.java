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

        add("aleeve_atlas.message.server_unsupported",
                "Server support component is not installed.");

        add("aleeve_atlas.hud.light", "Light: %s");

        add("aleeve_atlas.message.ping_received", "%s sent a ping");
        add("aleeve_atlas.message.waypoint_received", "Waypoint from %2$s accepted: %1$s");
        add("aleeve_atlas.message.waypoint_shared", "%2$s shared waypoint %1$s at %3$s, %4$s, %5$s");
        add("aleeve_atlas.message.waypoint_accept", "[Accept]");
        add("aleeve_atlas.message.waypoint_ignore", "[Ignore]");
        add("aleeve_atlas.waypoint.default_name", "Waypoint");
        add("aleeve_atlas.message.waypoint_created", "Waypoint created at %s, %s, %s");
        add("aleeve_atlas.message.death_point_added", "Death point recorded");

        add("aleeve_atlas.message.waypoint_exported", "Exported waypoints to %s");
        add("aleeve_atlas.message.waypoint_export_failed", "Failed to export waypoints");
        add("aleeve_atlas.message.waypoint_imported",
                "Imported %s new and %s updated waypoint(s)");
        add("aleeve_atlas.message.waypoint_import_failed", "Failed to import waypoints");
        add("aleeve_atlas.message.territory_entered", "Entered %s");
        add("aleeve_atlas.message.territory_left", "Left %s");
        add("aleeve_atlas.message.territory_demo_added", "Demo territory added");
        add("aleeve_atlas.message.territory_demo_cleared", "Demo territory cleared");
        add("aleeve_atlas.territory.demo_name", "Demo Territory");
        add("aleeve_atlas.territory.demo_overlay_name", "Overlay Territory");
        add("aleeve_atlas.message.ping_channel_duplicate",
                "Ping Wheel detected while channel mode is AUTO; depending on its settings pings may be broadcast twice.");
        add("aleeve_atlas.territory.border.solid", "Solid");
        add("aleeve_atlas.territory.border.dashed", "Dashed");
        add("aleeve_atlas.radar.category.player", "Players");
        add("aleeve_atlas.radar.category.hostile", "Hostile");
        add("aleeve_atlas.radar.category.friendly", "Friendly");
        add("aleeve_atlas.radar.category.item", "Items");
        add("aleeve_atlas.radar.category.misc", "Misc");
        add("aleeve_atlas.screen.waypoint_list", "Waypoints");
        add("aleeve_atlas.screen.waypoint_list.show", "Show");
        add("aleeve_atlas.screen.waypoint_list.hide", "Hide");
        add("aleeve_atlas.screen.waypoint_list.share", "Share");
        add("aleeve_atlas.screen.waypoint_list.share.tip", "Share this waypoint with your team");
        add("aleeve_atlas.screen.waypoint_list.public", "Public");
        add("aleeve_atlas.screen.waypoint_list.private", "Private");
        add("aleeve_atlas.screen.waypoint_list.delete", "Delete");
        add("aleeve_atlas.screen.waypoint_list.empty", "No waypoints in this dimension");
        add("aleeve_atlas.screen.waypoint_list.death", "[death]");
        add("aleeve_atlas.screen.waypoint_list.scroll_hint", "Scroll to browse");
        add("aleeve_atlas.screen.world_map.share_team", "Share to Team");
        add("aleeve_atlas.screen.world_map.share_team.tip",
                "Share the nearest waypoint with your team");
        add("aleeve_atlas.message.waypoint_none", "No waypoint to share");
        add("aleeve_atlas.message.waypoint_shared_team", "Shared waypoint: %s");

        add("aleeve_atlas.screen.world_map", "World Map");
        add("aleeve_atlas.screen.world_map.hint",
                "Drag to pan, scroll to zoom, ESC or M to close");

        // 配置项翻译键由 AnvilLib 依据配置类反射生成（规格 §12.3：配置项必须纳入语言文件）。
        ConfigData.readConfigClass(this, AtlasClientConfig.class);
        ConfigData.readConfigClass(this, AtlasServerConfig.class);
    }
}
