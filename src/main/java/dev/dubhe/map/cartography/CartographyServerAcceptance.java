package dev.dubhe.map.cartography;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.UUID;

/// 使用真实服务端存档、背包和地图数据测试材料事务；仅在隔离验收进程启用。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class CartographyServerAcceptance {
    private CartographyServerAcceptance() { }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (System.getenv("ALEEVE_ATLAS_CARTOGRAPHY_SERVER_TEST") == null) return;
        try {
            var player = FakePlayerFactory.getMinecraft(event.getServer().overworld());
            player.getInventory().clearContent(); player.getInventory().setSelectedSlot(0);
            player.setGameMode(GameType.SURVIVAL);
            byte[] pixels = new byte[MapSnapshot.PIXELS]; pixels[12] = 7; pixels[130] = 50;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MAP));
            var locked = request(pixels, true, 0);
            require(ServerCartography.executeCraft(player, locked).status().equals("glass_missing"), "missing glass rejected");
            require(player.getMainHandItem().is(Items.MAP) && player.getMainHandItem().getCount() == 1, "failure keeps map");
            player.getInventory().setItem(2, new ItemStack(Items.GLASS_PANE, 2));
            locked = request(pixels, true, 0);
            var created = ServerCartography.executeCraft(player, locked);
            require(created.status().equals("crafted"), "locked transaction succeeds");
            require(player.getMainHandItem().is(Items.FILLED_MAP), "single map replaced in hand");
            require(player.getInventory().getItem(2).getCount() == 1, "one glass consumed");
            var data = MapItem.getSavedData(player.getMainHandItem(), player.level());
            require(data.locked && data.scale == 0 && data.centerX == -128 && data.centerZ == 256, "vanilla lock and grid");
            require(Arrays.equals(data.colors, pixels), "preview bytes exactly preserved");
            require(ServerCartography.executeCraft(player, locked).mapId() == created.mapId()
                    && player.getInventory().getItem(2).getCount() == 1, "repeated request returns original map without charging twice");
            require(MapSnapshot.load(new MapSnapshot(created.mapId(), "minecraft:overworld", -128, 256, 0, 63,
                    data.colors, java.util.List.of(), 0).save()).colors()[12] == 7, "saved colors stable");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MAP, 3));
            require(ServerCartography.executeCraft(player, request(pixels, false, 1)).status().equals("changed"), "stale slot rejected");
            require(player.getMainHandItem().getCount() == 3, "stale slot doesn't consume");
            var open = ServerCartography.executeCraft(player, request(pixels, false, 0));
            require(open.status().equals("crafted") && player.getMainHandItem().getCount() == 2, "stack consumes one");
            require(!player.level().getMapData(new net.minecraft.world.level.saveddata.maps.MapId(open.mapId())).locked, "unlocked result");
            require(player.getInventory().getItem(2).getCount() == 1, "unlocked keeps glass");
            require(ServerCartography.executeCraft(player, request(new byte[0], false, 0)).status().equals("invalid"), "bad length rejected");
            require(ServerCartography.executeCraft(player, request(new byte[MapSnapshot.PIXELS], false, 0)).status().equals("empty"), "empty rejected");
            byte[] invalid = pixels.clone(); invalid[1] = (byte) 255;
            require(ServerCartography.executeCraft(player, request(invalid, false, 0)).status().equals("invalid"), "bad color rejected");
            var wrongDimension = new CartographyPayloads.CraftRequest(UUID.randomUUID(), InteractionHand.MAIN_HAND, 0,
                    "minecraft:the_nether", -128, 256, false, pixels);
            require(ServerCartography.executeCraft(player, wrongDimension).status().equals("changed"), "wrong dimension rejected");
            var badGrid = new CartographyPayloads.CraftRequest(UUID.randomUUID(), InteractionHand.MAIN_HAND, 0,
                    "minecraft:overworld", -127, 256, false, pixels);
            require(ServerCartography.executeCraft(player, badGrid).status().equals("region"), "unaligned region rejected");

            player.getInventory().clearContent();
            player.setGameMode(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MAP));
            require(ServerCartography.executeCraft(player, request(pixels, true, 0)).status().equals("crafted"), "creative lock without glass");
            require(player.getMainHandItem().is(Items.MAP) && player.getMainHandItem().getCount() == 1, "creative retains empty map");
            player.setGameMode(GameType.SURVIVAL);
            for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MAP, 2));
            require(ServerCartography.executeCraft(player, request(pixels, false, 0)).status().equals("crafted"), "full inventory succeeds via drop");
            require(player.getMainHandItem().getCount() == 1, "full inventory consumes once");
            LoggerFactory.getLogger(CartographyServerAcceptance.class).info("Cartography dedicated transaction acceptance PASSED");
        } catch (Exception e) {
            LoggerFactory.getLogger(CartographyServerAcceptance.class).error("Cartography dedicated transaction acceptance FAILED", e);
        } finally {
            if (event.getServer().isDedicatedServer()) event.getServer().halt(false);
        }
    }
    private static CartographyPayloads.CraftRequest request(byte[] pixels, boolean locked, int slot) {
        return new CartographyPayloads.CraftRequest(UUID.randomUUID(), InteractionHand.MAIN_HAND, slot,
                "minecraft:overworld", -128, 256, locked, pixels);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
