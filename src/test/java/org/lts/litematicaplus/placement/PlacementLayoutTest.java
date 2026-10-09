package org.lts.litematicaplus.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.world.PlacementVolume;

class PlacementLayoutTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    private static BlockState stairs(Direction facing) {
        return Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    private static Schematic house() {
        RegionBuilder base = new RegionBuilder("Base", 0, 0, 0, 3, 1, 1);
        base.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        base.set(2, 0, 0, Blocks.GLASS.defaultBlockState());
        RegionBuilder tower = new RegionBuilder("Tower", 0, 1, 0, 2, 1, 1);
        tower.set(0, 0, 0, Blocks.OAK_PLANKS.defaultBlockState());
        tower.set(1, 0, 0, stairs(Direction.EAST));
        Map<String, SchematicRegion> regions = new LinkedHashMap<>();
        regions.put("Base", base.build());
        regions.put("Tower", tower.build());
        return new Schematic(new SchematicMetadata(), regions);
    }

    @Test
    void identityLayoutMatchesThePlainTransform() {
        PlacementTransform transform = new PlacementTransform(10, 64, -5, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);
        PlacementVolume plain = PlacementVolume.build(house(), transform, Set.of());
        PlacementVolume layered = PlacementVolume.build(house(), PlacementLayout.of(transform), Set.of(), Map.of());
        assertEquals(plain.totalBlocks(), layered.totalBlocks());
        for (int x = plain.minX(); x <= plain.maxX(); x++) {
            for (int y = plain.minY(); y <= plain.maxY(); y++) {
                for (int z = plain.minZ(); z <= plain.maxZ(); z++) {
                    assertEquals(plain.stateAt(x, y, z), layered.stateAt(x, y, z), x + " " + y + " " + z);
                }
            }
        }
    }

    @Test
    void movedAndRotatedRegionLandsWhereExpected() {
        PlacementTransform transform = new PlacementTransform(100, 64, 100, Rotation.NONE, Mirror.NONE);
        PlacementLayout layout = new PlacementLayout(transform,
                Map.of("Tower", new RegionPlacement(5, -1, 0, Rotation.CLOCKWISE_90, Mirror.NONE)), PlacementGrid.NONE);
        PlacementVolume volume = PlacementVolume.build(house(), layout, Set.of(), Map.of());
        assertEquals(Blocks.STONE.defaultBlockState(), volume.stateAt(100, 64, 100));
        assertEquals(Blocks.OAK_PLANKS.defaultBlockState(), volume.stateAt(105, 64, 100));
        assertEquals(stairs(Direction.SOUTH), volume.stateAt(105, 64, 101));
        assertTrue(volume.stateAt(100, 65, 100).isAir());
        assertEquals(4L, volume.totalBlocks());
    }

    @Test
    void gridRepeatsTheWholeSchematicWithAutomaticSpacing() {
        PlacementTransform transform = new PlacementTransform(0, 64, 0, Rotation.NONE, Mirror.NONE);
        PlacementLayout layout = new PlacementLayout(transform, Map.of(),
                new PlacementGrid(1, 2, 0, 0, 0, 1, 0, 0, 0));
        PlacementVolume volume = PlacementVolume.build(house(), layout, Set.of(), Map.of());
        assertEquals(4L * 4 * 2, volume.totalBlocks());
        assertEquals(Blocks.STONE.defaultBlockState(), volume.stateAt(-3, 64, 0));
        assertEquals(Blocks.STONE.defaultBlockState(), volume.stateAt(6, 64, 0));
        assertEquals(Blocks.GLASS.defaultBlockState(), volume.stateAt(8, 64, 1));
        assertEquals(stairs(Direction.EAST), volume.stateAt(4, 65, 1));
        assertEquals(4 * 2 * 2, volume.regionBoxes().size());
        assertEquals(volume.regionBoxes(), PlacementVolume.regionBoxes(house(), layout, Set.of()));
    }

    @Test
    void customGridStepLeavesGaps() {
        PlacementTransform transform = new PlacementTransform(0, 64, 0, Rotation.NONE, Mirror.NONE);
        PlacementLayout layout = new PlacementLayout(transform, Map.of(),
                new PlacementGrid(0, 1, 0, 1, 0, 0, 5, 4, 0));
        PlacementVolume volume = PlacementVolume.build(house(), layout, Set.of(), Map.of());
        assertEquals(Blocks.STONE.defaultBlockState(), volume.stateAt(5, 64, 0));
        assertEquals(Blocks.STONE.defaultBlockState(), volume.stateAt(5, 68, 0));
        assertTrue(volume.stateAt(3, 64, 0).isAir());
    }

    @Test
    void oversizedGridIsRefused() {
        PlacementLayout layout = new PlacementLayout(PlacementTransform.IDENTITY, Map.of(),
                new PlacementGrid(8, 8, 0, 0, 8, 8, 0, 0, 0));
        assertThrows(UncheckedIOException.class, () -> PlacementVolume.build(house(), layout, Set.of(), Map.of()));
    }

    @Test
    void composedStateRotationMatchesSequentialApplication() {
        BlockState[] states = {stairs(Direction.EAST), stairs(Direction.NORTH),
                Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X)};
        for (Rotation subRotation : Rotation.values()) {
            for (Mirror subMirror : Mirror.values()) {
                for (Rotation mainRotation : Rotation.values()) {
                    for (Mirror mainMirror : Mirror.values()) {
                        PlacementTransform main = new PlacementTransform(0, 0, 0, mainRotation, mainMirror);
                        PlacementTransform sub = new PlacementTransform(0, 0, 0, subRotation, subMirror);
                        RegionMapping mapping = new RegionMapping(main,
                                new RegionPlacement(0, 0, 0, subRotation, subMirror), 0, 0, 0, 0, 0, 0);
                        for (BlockState state : states) {
                            assertEquals(main.apply(sub.apply(state)), mapping.apply(state),
                                    subRotation + " " + subMirror + " then " + mainRotation + " " + mainMirror);
                        }
                        for (int[] probe : new int[][]{{1, 0}, {0, 1}, {3, -2}}) {
                            int midX = sub.worldX(probe[0], probe[1]);
                            int midZ = sub.worldZ(probe[0], probe[1]);
                            assertEquals(main.worldX(midX, midZ), mapping.worldX(probe[0], probe[1]));
                            assertEquals(main.worldZ(midX, midZ), mapping.worldZ(probe[0], probe[1]));
                        }
                    }
                }
            }
        }
    }
}
