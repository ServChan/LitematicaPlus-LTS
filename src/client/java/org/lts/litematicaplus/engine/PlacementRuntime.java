package org.lts.litematicaplus.engine;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.SectionVerdict;
import org.lts.litematicaplus.world.PlacementVolume;

public final class PlacementRuntime {
    private final SchematicPlacement placement;
    final Long2ObjectOpenHashMap<SectionSlot> slots = new Long2ObjectOpenHashMap<>();
    final LongOpenHashSet dirty = new LongOpenHashSet();
    final ArrayList<SectionSlot> drawable = new ArrayList<>();
    final LongArrayFIFOQueue urgent = new LongArrayFIFOQueue();
    long[] queue = new long[0];
    int queueSize;
    int queuePos;
    double queueX;
    double queueY;
    double queueZ;
    final long[] counts = new long[MismatchType.VALUES.length];
    PlacementVolume volume;
    long[] correctByPalette = new long[0];
    boolean[] specialByPalette = new boolean[0];
    int generation;
    volatile int buildToken;
    boolean building;
    int verifiedSections;
    int knownSections;

    PlacementRuntime(SchematicPlacement placement) {
        this.placement = placement;
    }

    public SchematicPlacement placement() {
        return this.placement;
    }

    public PlacementVolume volume() {
        return this.volume;
    }

    public Iterable<SectionSlot> slots() {
        return this.slots.values();
    }

    public List<SectionSlot> drawable() {
        return this.drawable;
    }

    public int slotCount() {
        return this.slots.size();
    }

    public boolean building() {
        return this.building;
    }

    public int pendingSections() {
        return this.dirty.size();
    }

    public int verifiedSections() {
        return this.verifiedSections;
    }

    public int knownSections() {
        return this.knownSections;
    }

    public long count(MismatchType type) {
        return this.counts[type.ordinal()];
    }

    public long errors() {
        return count(MismatchType.MISSING) + count(MismatchType.EXTRA) + count(MismatchType.WRONG_BLOCK)
                + count(MismatchType.WRONG_STATE);
    }

    public long[] correctByPalette() {
        return this.correctByPalette;
    }

    public double progress() {
        PlacementVolume current = this.volume;
        if (current == null || current.totalBlocks() <= 0L) {
            return 0.0;
        }
        return Math.min(1.0, (double) count(MismatchType.CORRECT) / current.totalBlocks());
    }

    void add(SectionVerdict verdict) {
        apply(verdict, 1);
    }

    void subtract(SectionVerdict verdict) {
        apply(verdict, -1);
    }

    private void apply(SectionVerdict verdict, int sign) {
        int[] values = verdict.counts();
        for (int i = 0; i < values.length; i++) {
            this.counts[i] += (long) sign * values[i];
        }
        int[] indices = verdict.correctIndices();
        int[] correct = verdict.correctCounts();
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] < this.correctByPalette.length) {
                this.correctByPalette[indices[i]] += (long) sign * correct[i];
            }
        }
        this.verifiedSections += sign;
        if (verdict.worldKnown()) {
            this.knownSections += sign;
        }
    }

    void resetTotals(int paletteSize) {
        java.util.Arrays.fill(this.counts, 0L);
        this.correctByPalette = new long[paletteSize];
        this.verifiedSections = 0;
        this.knownSections = 0;
    }
}
