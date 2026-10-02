package org.lts.litematicaplus.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lts.litematicaplus.TestBootstrap;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.SectionVerdict;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.LayerRange;

class ScopeTallyTest {
    private static GhostSection section;
    private static SectionVerdict verdict;
    private static final boolean[] SOLID = {false, true};

    @BeforeAll
    static void build() {
        TestBootstrap.ensure();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        section = new GhostSection();
        BlockState[] world = new BlockState[GhostSection.VOLUME];
        java.util.Arrays.fill(world, air);
        for (int y = 0; y < 2; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = GhostSection.index(x, y, z);
                    section.set(index, (short) 1);
                    if (y == 0 && x < 8) {
                        world[index] = stone;
                    }
                }
            }
        }
        section.recount(SOLID);
        verdict = SectionVerdict.compute(section, new int[]{Block.getId(air), Block.getId(stone)}, SOLID, world, false);
    }

    @Test
    void singleLayerCountsOnlyThatLayer() {
        LayerRange layer = new LayerRange(LayerRange.Mode.SINGLE, Direction.Axis.Y, 0, 0, 0);
        ScopeTally tally = ScopeTally.compute(section, SOLID, verdict, layer, 0, 0, 0);
        assertEquals(256, tally.blocks());
        assertEquals(128, tally.count(MismatchType.CORRECT));
        assertEquals(128, tally.count(MismatchType.MISSING));
        assertEquals(1, tally.paletteIndices.length);
        assertEquals(256, tally.totals[0]);
        assertEquals(128, tally.correct[0]);
    }

    @Test
    void horizontalClipNarrowsEveryLayer() {
        LayerRange clipped = LayerRange.ALL.withClip(new LayerRange.Clip(true, true, 0, 3, false, 0, 0));
        ScopeTally tally = ScopeTally.compute(section, SOLID, verdict, clipped, 0, 0, 0);
        assertEquals(128, tally.blocks());
        assertEquals(64, tally.count(MismatchType.CORRECT));
        assertEquals(64, tally.count(MismatchType.MISSING));
    }

    @Test
    void enclosedSectionMatchesTheWholeVerdictAndDisjointIsEmpty() {
        LayerRange around = LayerRange.ALL.withClip(new LayerRange.Clip(true, true, -5, 40, true, -5, 40));
        ScopeTally whole = ScopeTally.compute(section, SOLID, verdict, around, 0, 0, 0);
        assertEquals(512, whole.blocks());
        assertEquals(verdict.count(MismatchType.CORRECT), whole.count(MismatchType.CORRECT));
        assertEquals(verdict.count(MismatchType.MISSING), whole.count(MismatchType.MISSING));
        LayerRange away = LayerRange.ALL.withClip(new LayerRange.Clip(true, true, 100, 120, false, 0, 0));
        assertSame(ScopeTally.EMPTY, ScopeTally.compute(section, SOLID, verdict, away, 0, 0, 0));
    }
}
