package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.storage.ServerKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 按手动配置、主机存档和 `/seed` 返回结果解析史莱姆区块。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class SlimeChunkTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/slimechunks");
    private static final long SLIME_SALT = 987234911L;
    private static final Pattern SEED_PATTERN = Pattern.compile("-?\\d+");
    private static final Map<String, Long> LEARNED_SEEDS = new ConcurrentHashMap<>();
    private static String invalidSeed;

    private SlimeChunkTracker() {
    }

    /// 手动种子优先，其次读取主机存档，最后使用管理员执行 `/seed` 的记录。
    public static Long currentSeed() {
        String configured = AtlasClientConfigs.CONFIG.slimeChunks.seed.trim();
        if (!configured.isEmpty()) {
            try {
                return Long.parseLong(configured);
            } catch (NumberFormatException e) {
                if (!configured.equals(invalidSeed)) {
                    invalidSeed = configured;
                    LOGGER.warn("Invalid slime chunk seed: {}", configured);
                }
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            return server.getWorldData().worldGenOptions().seed();
        }
        return LEARNED_SEEDS.get(ServerKey.resolve(minecraft));
    }

    public static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        return WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, seed, SLIME_SALT).nextInt(10) == 0;
    }

    @SubscribeEvent
    public static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (!(event.getMessage().getContents() instanceof TranslatableContents contents)
                || !"commands.seed.success".equals(contents.getKey())
                || contents.getArgs().length == 0) {
            return;
        }
        Object argument = contents.getArgs()[0];
        String response = argument instanceof Component component
                ? component.getString() : String.valueOf(argument);
        Matcher matcher = SEED_PATTERN.matcher(response);
        if (!matcher.find()) {
            LOGGER.warn("Unable to read /seed response: {}", response);
            return;
        }
        try {
            long seed = Long.parseLong(matcher.group());
            Minecraft minecraft = Minecraft.getInstance();
            LEARNED_SEEDS.put(ServerKey.resolve(minecraft), seed);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "aleeve_atlas.message.slime_seed_recorded", seed), true);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("Unable to read /seed response: {}", contents.getArgs()[0], e);
        }
    }
}
