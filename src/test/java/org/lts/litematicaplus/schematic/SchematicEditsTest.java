package org.lts.litematicaplus.schematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;
import org.lts.litematicaplus.placement.PlacementBake;
import org.lts.litematicaplus.placement.PlacementLayout;
import org.lts.litematicaplus.placement.PlacementTransform;
import org.lts.litematicaplus.util.LocalizedException;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

class SchematicEditsTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static Schematic schematic(RegionBuilder... builders) {
        Map<String, SchematicRegion> regions = new LinkedHashMap<>();
        for (RegionBuilder builder : builders) {
            SchematicRegion region = builder.build();
            regions.put(region.name(), region);
        }
        return new Schematic(new SchematicMetadata(), regions);
    }

    @Test
    void trimKeepsBlocksWhereTheyWere() {
        RegionBuilder builder = new RegionBuilder("Main", 10, 20, 30, 8, 8, 8);
        builder.set(2, 3, 4, Blocks.STONE.defaultBlockState());
        builder.set(5, 6, 4, Blocks.DIRT.defaultBlockState());
        CompoundTag tile = new CompoundTag();
        tile.putString("id", "minecraft:chest");
        builder.blockEntity(5, 6, 4, tile);
        CompoundTag entity = new CompoundTag();
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(3.5));
        position.add(DoubleTag.valueOf(4.0));
        position.add(DoubleTag.valueOf(4.5));
        entity.put("Pos", position);
        builder.entity(entity);
        Schematic trimmed = SchematicTrim.trim(schematic(builder));
        SchematicRegion region = trimmed.regions().get("Main");
        assertEquals(12, region.minX());
        assertEquals(23, region.minY());
        assertEquals(34, region.minZ());
        assertEquals(4, region.absX());
        assertEquals(4, region.absY());
        assertEquals(1, region.absZ());
        assertEquals(Blocks.STONE.defaultBlockState(), region.state(0, 0, 0));
        assertEquals(Blocks.DIRT.defaultBlockState(), region.state(3, 3, 0));
        assertTrue(region.blockEntities().containsKey(SchematicRegion.localKey(3, 3, 0)));
        ListTag moved = region.entities().get(0).getListOrEmpty("Pos");
        assertEquals(1.5, moved.getDoubleOr(0, 0.0));
        assertEquals(1.0, moved.getDoubleOr(1, 0.0));
        assertEquals(0.5, moved.getDoubleOr(2, 0.0));
    }

    @Test
    void trimLeavesATightRegionUntouchedAndEmptyOneAsSingleCell() {
        RegionBuilder tight = new RegionBuilder("Tight", 0, 0, 0, 1, 1, 1);
        tight.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        SchematicRegion region = tight.build();
        assertSame(region, SchematicTrim.trim(region));
        SchematicRegion empty = SchematicTrim.trim(new RegionBuilder("Empty", 2, 2, 2, 5, 5, 5).build());
        assertEquals(1L, empty.volume());
    }

    @Test
    void compareFindsEveryKindOfChange() throws IOException {
        BlockState east = Blocks.OAK_STAIRS.defaultBlockState();
        BlockState west = east.setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.WEST);
        RegionBuilder before = new RegionBuilder("A", 0, 0, 0, 4, 1, 1);
        before.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        before.set(1, 0, 0, Blocks.DIRT.defaultBlockState());
        before.set(2, 0, 0, east);
        RegionBuilder after = new RegionBuilder("B", 0, 0, 0, 5, 1, 1);
        after.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        after.set(1, 0, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        after.set(2, 0, 0, west);
        after.set(4, 0, 0, Blocks.GLASS.defaultBlockState());
        SchematicDiff.Result result = SchematicDiff.compare(schematic(before), schematic(after), "diff");
        assertEquals(1L, result.same());
        assertEquals(1L, result.added());
        assertEquals(1L, result.replaced());
        assertEquals(1L, result.restated());
        assertEquals(0L, result.removed());
        SchematicRegion changes = result.changes().regions().get("Changes");
        assertEquals(Blocks.STRUCTURE_VOID.defaultBlockState(), changes.state(0, 0, 0));
        assertEquals(Blocks.GLASS.defaultBlockState(), changes.state(4, 0, 0));
        SchematicDiff.Result swapped = SchematicDiff.compare(schematic(after), schematic(before), "diff");
        assertEquals(1L, swapped.removed());
    }

    @Test
    void bakeWritesTheTurnedPlacementWithEditsAndHonoursTheLayer() throws LocalizedException {
        RegionBuilder builder = new RegionBuilder("Main", 0, 0, 0, 3, 2, 1);
        builder.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        builder.set(1, 0, 0, Blocks.DIRT.defaultBlockState());
        builder.set(0, 1, 0, Blocks.GLASS.defaultBlockState());
        PlacementLayout layout = PlacementLayout.of(new PlacementTransform(100, 64, 200, Rotation.CLOCKWISE_90, Mirror.NONE));
        BlockPos edited = new BlockPos(100, 64, 201);
        PlacementVolume volume = PlacementVolume.build(schematic(builder), layout, Set.of(), Map.of(), null,
                Map.of(edited.asLong(), Blocks.GOLD_BLOCK.defaultBlockState()));
        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), volume.stateAt(100, 64, 201));
        Schematic baked = PlacementBake.bake(volume, LayerRange.ALL, List.of(), "baked", "");
        SchematicRegion region = baked.regions().get("Placement");
        assertEquals(1, region.absX());
        assertEquals(2, region.absY());
        assertEquals(3, region.absZ());
        assertEquals(Blocks.STONE.defaultBlockState(), region.state(0, 0, 0));
        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), region.state(0, 0, 1));
        LayerRange bottom = new LayerRange(LayerRange.Mode.SINGLE, Direction.Axis.Y, 64, 64, 64);
        assertEquals(1, PlacementBake.bake(volume, bottom, List.of(), "layer", "").regions().get("Placement").absY());
        LayerRange above = new LayerRange(LayerRange.Mode.SINGLE, Direction.Axis.Y, 90, 90, 90);
        assertThrows(LocalizedException.class, () -> PlacementBake.bake(volume, above, List.of(), "none", ""));
    }
}
