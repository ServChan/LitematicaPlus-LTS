package org.lts.litematicaplus.verify;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.world.GhostSection;

public final class SectionVerdict {
    private static final int TYPES = MismatchType.VALUES.length;

    private static final int[] NO_INTS = new int[0];
    private static final byte[] ALL_NONE = uniform(MismatchType.NONE);
    private static final byte[] ALL_CORRECT = uniform(MismatchType.CORRECT);

    private final byte[] categories;
    private final int[] counts;
    private final int[] correctIndices;
    private final int[] correctCounts;
    private final boolean worldKnown;

    private SectionVerdict(byte[] categories, int[] counts, int[] correctIndices, int[] correctCounts,
            boolean worldKnown) {
        this.categories = categories;
        this.counts = counts;
        this.correctIndices = correctIndices;
        this.correctCounts = correctCounts;
        this.worldKnown = worldKnown;
    }

    private static byte[] uniform(MismatchType type) {
        byte[] values = new byte[GhostSection.VOLUME];
        java.util.Arrays.fill(values, (byte) type.ordinal());
        return values;
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
            return new SectionVerdict(categories, counts, NO_INTS, NO_INTS, false);
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
        int distinct = 0;
        for (int value : correct) {
            if (value != 0) {
                distinct++;
            }
        }
        int[] indices = distinct == 0 ? NO_INTS : new int[distinct];
        int[] values = distinct == 0 ? NO_INTS : new int[distinct];
        for (int i = 0, next = 0; i < correct.length && next < distinct; i++) {
            if (correct[i] != 0) {
                indices[next] = i;
                values[next++] = correct[i];
            }
        }
        byte[] shared = categories;
        int inside = 0;
        for (int value : counts) {
            inside += value;
        }
        if (counts[MismatchType.NONE.ordinal()] == inside && MismatchType.NONE.ordinal() == 0) {
            shared = ALL_NONE;
        } else if (counts[MismatchType.CORRECT.ordinal()] == GhostSection.VOLUME) {
            shared = ALL_CORRECT;
        }
        return new SectionVerdict(shared, counts, indices, values, true);
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

    public int[] correctIndices() {
        return this.correctIndices;
    }

    public int[] correctCounts() {
        return this.correctCounts;
    }

    public int correctFor(int paletteIndex) {
        for (int i = 0; i < this.correctIndices.length; i++) {
            if (this.correctIndices[i] == paletteIndex) {
                return this.correctCounts[i];
            }
        }
        return 0;
    }

    public boolean worldKnown() {
        return this.worldKnown;
    }

    public int errors() {
        return this.counts[MismatchType.MISSING.ordinal()] + this.counts[MismatchType.EXTRA.ordinal()]
                + this.counts[MismatchType.WRONG_BLOCK.ordinal()] + this.counts[MismatchType.WRONG_STATE.ordinal()];
    }
}
