package org.lts.litematicaplus.verify;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.world.GhostSection;

public final class SectionVerdict {
    private static final int TYPES = MismatchType.VALUES.length;

    public static final int FACE_DOWN = 0;
    public static final int FACE_UP = 1;
    public static final int FACE_NORTH = 2;
    public static final int FACE_SOUTH = 3;
    public static final int FACE_WEST = 4;
    public static final int FACE_EAST = 5;
    public static final int COVER_OFFSET = 24;
    public static final int BORDER_LONGS = 48;
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
        return compute(section, globalIds, solid, world, lenient, false, false, false, null);
    }

    public static boolean borderSolid(long[] border, int face, int a, int b) {
        if (border == null) {
            return true;
        }
        int bit = a + b * 16;
        return (border[face * 4 + (bit >> 6)] >>> (bit & 63) & 1L) != 0L;
    }

    public static boolean borderCovers(long[] border, int face, int a, int b) {
        if (border == null) {
            return true;
        }
        int bit = a + b * 16;
        return (border[COVER_OFFSET + face * 4 + (bit >> 6)] >>> (bit & 63) & 1L) != 0L;
    }

    public static boolean coversState(BlockState state) {
        return state == null || state.isSolidRender() || !state.getFluidState().isEmpty()
                || state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static boolean conceals(BlockState state) {
        return state == null || state.isSolidRender();
    }

    public static void copyBorderFace(long[] from, long[] to, int face) {
        System.arraycopy(from, face * 4, to, face * 4, 4);
        System.arraycopy(from, COVER_OFFSET + face * 4, to, COVER_OFFSET + face * 4, 4);
    }

    public static void markBorder(long[] border, int face, int a, int b, BlockState state) {
        int bit = a + b * 16;
        long mask = 1L << (bit & 63);
        if (conceals(state)) {
            border[face * 4 + (bit >> 6)] |= mask;
        }
        if (coversState(state)) {
            border[COVER_OFFSET + face * 4 + (bit >> 6)] |= mask;
        }
    }

    public static void closeBorderFace(long[] border, int face) {
        Arrays.fill(border, face * 4, face * 4 + 4, -1L);
        Arrays.fill(border, COVER_OFFSET + face * 4, COVER_OFFSET + face * 4 + 4, -1L);
    }

    public static boolean covers(BlockState[] world, long[] border, int x, int y, int z) {
        if (y < 0) {
            return borderCovers(border, FACE_DOWN, x, z);
        }
        if (y > 15) {
            return borderCovers(border, FACE_UP, x, z);
        }
        if (z < 0) {
            return borderCovers(border, FACE_NORTH, x, y);
        }
        if (z > 15) {
            return borderCovers(border, FACE_SOUTH, x, y);
        }
        if (x < 0) {
            return borderCovers(border, FACE_WEST, z, y);
        }
        if (x > 15) {
            return borderCovers(border, FACE_EAST, z, y);
        }
        return coversState(world[x | z << 4 | y << 8]);
    }

    public static boolean occludes(BlockState[] world, long[] border, int x, int y, int z) {
        if (y < 0) {
            return borderSolid(border, FACE_DOWN, x, z);
        }
        if (y > 15) {
            return borderSolid(border, FACE_UP, x, z);
        }
        if (z < 0) {
            return borderSolid(border, FACE_NORTH, x, y);
        }
        if (z > 15) {
            return borderSolid(border, FACE_SOUTH, x, y);
        }
        if (x < 0) {
            return borderSolid(border, FACE_WEST, z, y);
        }
        if (x > 15) {
            return borderSolid(border, FACE_EAST, z, y);
        }
        return occluding(world, x | z << 4 | y << 8);
    }

    private static boolean occluding(BlockState[] world, int index) {
        BlockState state = world[index];
        return state != null && conceals(state);
    }

    static boolean hidden(BlockState[] world, int index) {
        return hidden(world, null, index);
    }

    static boolean hidden(BlockState[] world, long[] border, int index) {
        int x = index & 15;
        int z = index >> 4 & 15;
        int y = index >> 8;
        return occludes(world, border, x - 1, y, z) && occludes(world, border, x + 1, y, z)
                && occludes(world, border, x, y, z - 1) && occludes(world, border, x, y, z + 1)
                && occludes(world, border, x, y - 1, z) && occludes(world, border, x, y + 1, z);
    }

    public static SectionVerdict compute(GhostSection section, int[] globalIds, boolean[] solid, BlockState[] world,
            boolean lenient, boolean loose, boolean soilSwap, boolean trustHidden, long[] border) {
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
            MismatchType type = comparator.compare(globalIds[paletteIndex], worldId, lenient, loose, soilSwap);
            if (trustHidden && type == MismatchType.WRONG_BLOCK && hidden(world, border, i)) {
                type = MismatchType.CORRECT;
            }
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
