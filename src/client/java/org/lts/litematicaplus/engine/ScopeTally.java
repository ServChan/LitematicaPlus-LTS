package org.lts.litematicaplus.engine;

import java.util.Arrays;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.SectionVerdict;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.LayerRange;

public final class ScopeTally {
    private static final int[] NONE = new int[0];
    private static final int UNKNOWN = MismatchType.UNKNOWN.ordinal();
    private static final int CORRECT = MismatchType.CORRECT.ordinal();
    static final ScopeTally EMPTY = new ScopeTally(new int[MismatchType.VALUES.length], 0, NONE, NONE, NONE);

    final int[] counts;
    final int blocks;
    final int[] paletteIndices;
    final int[] totals;
    final int[] correct;

    private ScopeTally(int[] counts, int blocks, int[] paletteIndices, int[] totals, int[] correct) {
        this.counts = counts;
        this.blocks = blocks;
        this.paletteIndices = paletteIndices;
        this.totals = totals;
        this.correct = correct;
    }

    public int count(MismatchType type) {
        return this.counts[type.ordinal()];
    }

    public int blocks() {
        return this.blocks;
    }

    static final class Scratch {
        private int[] totals = NONE;
        private int[] correct = NONE;
        private int[] touched = new int[64];
        private int touchedCount;

        private void ensure(int size) {
            if (this.totals.length < size) {
                this.totals = new int[size];
                this.correct = new int[size];
            }
        }

        private void touch(int index) {
            if (this.totals[index] == 0 && this.correct[index] == 0) {
                if (this.touchedCount == this.touched.length) {
                    this.touched = Arrays.copyOf(this.touched, this.touched.length * 2);
                }
                this.touched[this.touchedCount++] = index;
            }
        }
    }

    public static ScopeTally compute(GhostSection section, boolean[] solid, SectionVerdict verdict, LayerRange scope,
            int originX, int originY, int originZ) {
        return compute(section, solid, verdict, scope, originX, originY, originZ, new Scratch());
    }

    static ScopeTally compute(GhostSection section, boolean[] solid, SectionVerdict verdict, LayerRange scope,
            int originX, int originY, int originZ, Scratch scratch) {
        if (section == null || !scope.intersects(originX, originY, originZ, originX + 15, originY + 15, originZ + 15)) {
            return EMPTY;
        }
        boolean whole = scope.encloses(originX, originY, originZ, originX + 15, originY + 15, originZ + 15);
        short[] raw = section.raw();
        boolean verified = verdict != null;
        byte[] categories = verified ? verdict.categories() : null;
        boolean known = verified && verdict.worldKnown();
        int[] counts = new int[MismatchType.VALUES.length];
        scratch.ensure(solid.length);
        int[] totals = scratch.totals;
        int[] correct = scratch.correct;
        scratch.touchedCount = 0;
        int blocks = 0;
        for (int index = 0; index < GhostSection.VOLUME; index++) {
            short value = raw[index];
            if (value == GhostSection.OUTSIDE) {
                continue;
            }
            if (!whole && !scope.contains(originX + (index & 15), originY + (index >> 8), originZ + (index >> 4 & 15))) {
                continue;
            }
            int paletteIndex = value & 0xFFFF;
            if (paletteIndex >= solid.length) {
                continue;
            }
            boolean isSolid = solid[paletteIndex];
            if (isSolid) {
                blocks++;
            }
            if (paletteIndex != 0) {
                scratch.touch(paletteIndex);
                totals[paletteIndex]++;
            }
            if (known) {
                int category = categories[index];
                counts[category]++;
                if (category == CORRECT && paletteIndex != 0) {
                    correct[paletteIndex]++;
                }
            } else if (isSolid && verified) {
                counts[UNKNOWN]++;
            }
        }
        int distinct = scratch.touchedCount;
        int[] indices = distinct == 0 ? NONE : new int[distinct];
        int[] packedTotals = distinct == 0 ? NONE : new int[distinct];
        int[] packedCorrect = distinct == 0 ? NONE : new int[distinct];
        for (int i = 0; i < distinct; i++) {
            int paletteIndex = scratch.touched[i];
            indices[i] = paletteIndex;
            packedTotals[i] = totals[paletteIndex];
            packedCorrect[i] = correct[paletteIndex];
            totals[paletteIndex] = 0;
            correct[paletteIndex] = 0;
        }
        scratch.touchedCount = 0;
        return new ScopeTally(counts, blocks, indices, packedTotals, packedCorrect);
    }
}
