package org.lts.litematicaplus.verify;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.world.GhostSection;

public final class SectionVerdict {
    private static final int TYPES = MismatchType.VALUES.length;

    private final byte[] categories;
    private final int[] counts;
    private final int[] correctByPalette;
    private final boolean worldKnown;

    private SectionVerdict(byte[] categories, int[] counts, int[] correctByPalette, boolean worldKnown) {
        this.categories = categories;
        this.counts = counts;
        this.correctByPalette = correctByPalette;
        this.worldKnown = worldKnown;
    }

    public static SectionVerdict compute(GhostSection section, int[] globalIds, boolean[] solid, BlockState[] world,
            boolean lenient) {
        byte[] categories = new byte[GhostSection.VOLUME];
        int[] counts = new int[TYPES];
        int[] correct = new int[globalIds.length];
        short[] data = section.raw();
        if (world == null) {
            byte unknown = (byte) MismatchType.UNKNOWN.ordinal();
            for (int i = 0; i < GhostSection.VOLUME; i++) {
                short value = data[i];
                if (value != GhostSection.OUTSIDE && solid[value & 0xFFFF]) {
                    categories[i] = unknown;
                    counts[unknown]++;
                }
            }
            return new SectionVerdict(categories, counts, correct, false);
        }
        StateComparator comparator = StateComparator.get();
        BlockState lastState = null;
        int lastId = 0;
        for (int i = 0; i < GhostSection.VOLUME; i++) {
            short value = data[i];
            if (value == GhostSection.OUTSIDE) {
                continue;
            }
            BlockState worldState = world[i];
            int worldId;
            if (worldState == lastState) {
                worldId = lastId;
            } else {
                worldId = worldState == null ? 0 : Block.getId(worldState);
                lastState = worldState;
                lastId = worldId;
            }
            int paletteIndex = value & 0xFFFF;
            MismatchType type = comparator.compare(globalIds[paletteIndex], worldId, lenient);
            int ordinal = type.ordinal();
            categories[i] = (byte) ordinal;
            counts[ordinal]++;
            if (type == MismatchType.CORRECT) {
                correct[paletteIndex]++;
            }
        }
        return new SectionVerdict(categories, counts, correct, true);
    }

    public byte[] categories() {
        return this.categories;
    }

    public MismatchType category(int index) {
        return MismatchType.VALUES[this.categories[index]];
    }

    public int count(MismatchType type) {
        return this.counts[type.ordinal()];
    }

    public int[] counts() {
        return this.counts;
    }

    public int[] correctByPalette() {
        return this.correctByPalette;
    }

    public boolean worldKnown() {
        return this.worldKnown;
    }

    public int errors() {
        return this.counts[MismatchType.MISSING.ordinal()] + this.counts[MismatchType.EXTRA.ordinal()]
                + this.counts[MismatchType.WRONG_BLOCK.ordinal()] + this.counts[MismatchType.WRONG_STATE.ordinal()];
    }
}
