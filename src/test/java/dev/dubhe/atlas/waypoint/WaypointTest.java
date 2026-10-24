package dev.dubhe.atlas.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/// 路径点序列化与可见性过滤测试（规格 §5.1 / §11）。
class WaypointTest {
    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;

    @Test
    void roundTripPreservesAllFields() {
        Waypoint original = new Waypoint(UUID.randomUUID(), "Base", new BlockPos(10, 64, -20),
                OVERWORLD, 0xFF00FF00, "home", true, 300, false, "Aleeve", true);

        CompoundTag tag = original.save();
        Waypoint restored = Waypoint.load(tag, WaypointTest::resolve);

        assertEquals(original.id(), restored.id());
        assertEquals("Base", restored.name());
        assertEquals(new BlockPos(10, 64, -20), restored.pos());
        assertEquals(OVERWORLD, restored.dimension());
        assertEquals(0xFF00FF00, restored.colorArgb());
        assertEquals("home", restored.icon());
        assertTrue(restored.enabled());
        assertEquals(300, restored.visibleDistance());
        assertFalse(restored.deathPoint());
        assertEquals("Aleeve", restored.sourcePlayer());
        assertTrue(restored.shareable(), "私密标记必须随序列化保留");
    }

    @Test
    void deathPointFlagSurvivesSerialization() {
        Waypoint death = Waypoint.deathPoint(new BlockPos(0, 70, 0), OVERWORLD);
        Waypoint restored = Waypoint.load(death.save(), WaypointTest::resolve);
        assertTrue(restored.deathPoint());
        assertEquals("death", restored.icon());
    }

    @Test
    void listSerializationKeepsAllEntries() {
        List<Waypoint> source = List.of(
                Waypoint.create("A", new BlockPos(1, 2, 3), OVERWORLD, 0xFFFFFFFF),
                Waypoint.create("B", new BlockPos(4, 5, 6), OVERWORLD, 0xFFFF0000));
        ListTag list = Waypoint.saveAll(source);
        List<Waypoint> restored = Waypoint.loadAll(list, WaypointTest::resolve);

        assertEquals(2, restored.size());
        assertEquals("A", restored.get(0).name());
        assertEquals("B", restored.get(1).name());
    }

    @Test
    void unknownDimensionFallsBackToOverworld() {
        CompoundTag tag = Waypoint.create("X", BlockPos.ZERO, OVERWORLD, 0xFFFFFFFF).save();
        tag.putString("dim", "not a valid id!");
        Waypoint restored = Waypoint.load(tag, WaypointTest::resolve);
        assertEquals(OVERWORLD, restored.dimension());
    }

    private static ResourceKey<Level> resolve(String raw) {
        Identifier id = Identifier.tryParse(raw);
        return id == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, id);
    }

    @Test
    void withShareableKeepsOtherFields() {
        Waypoint original = new Waypoint(UUID.randomUUID(), "Secret", new BlockPos(1, 70, 2),
                OVERWORLD, 0xFF123456, "default", true, 200, false, "Aleeve", true);
        Waypoint priv = original.withShareable(false);

        assertFalse(priv.shareable(), "切换后应为私密");
        assertEquals(original.id(), priv.id());
        assertEquals(original.name(), priv.name());
        assertEquals(original.pos(), priv.pos());
        assertEquals(original.colorArgb(), priv.colorArgb());
        assertEquals(original.visibleDistance(), priv.visibleDistance());
        assertEquals(original.sourcePlayer(), priv.sourcePlayer());
    }
}
