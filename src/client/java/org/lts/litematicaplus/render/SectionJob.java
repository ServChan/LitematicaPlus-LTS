package org.lts.litematicaplus.render;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.SectionVerdict;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public record SectionJob(
        long sectionKey,
        int generation,
        int revision,
        GhostSection section,
        PlacementVolume volume,
        boolean[] specialByPalette,
        boolean[] hiddenByPalette,
        BlockState[] world,
        long[] border,
        int unknownFaces,
        byte[][] neighbours,
        SectionVerdict remembered,
        LayerRange layer,
        ClientLevel level,
        BlockStateModelSet blockModels,
        FluidStateModelSet fluidModels,
        BlockColors blockColors,
        Options options) {

    public record Options(
            boolean ghostBlocks,
            boolean overlay,
            boolean renderColliding,
            boolean ghostErrorsOnly,
            boolean lenientStates,
            boolean looseBlocks,
            boolean soilSwap,
            boolean trustHidden,
            boolean throughWalls,
            boolean worldCache,
            boolean showMissing,
            boolean showExtra,
            boolean showWrongBlock,
            boolean showWrongState,
            float fillAlpha,
            float lineAlpha,
            int missingColor,
            int extraColor,
            int wrongBlockColor,
            int wrongStateColor) {

        public int color(int category) {
            MismatchType type = MismatchType.VALUES[category];
            return switch (type) {
                case MISSING -> this.missingColor;
                case EXTRA -> this.extraColor;
                case WRONG_BLOCK -> this.wrongBlockColor;
                case WRONG_STATE -> this.wrongStateColor;
                default -> type.color();
            };
        }
    }
}
