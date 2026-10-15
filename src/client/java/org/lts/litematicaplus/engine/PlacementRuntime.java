package org.lts.litematicaplus.engine;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.SectionVerdict;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class PlacementRuntime {
    private final SchematicPlacement placement;
    final Long2ObjectOpenHashMap<SectionSlot> slots = new Long2ObjectOpenHashMap<>();
    final LongOpenHashSet dirty = new LongOpenHashSet();
    final ArrayList<SectionSlot> drawable = new ArrayList<>();
    final LongArrayFIFOQueue urgent = new LongArrayFIFOQueue();
    final LongOpenHashSet urgentKeys = new LongOpenHashSet();
    final ArrayList<SectionSlot> specialSlots = new ArrayList<>();
    long[] queue = new long[0];
    int queueSize;
    int queuePos;
    double queueX;
    double queueY;
    double queueZ;
    final long[] counts = new long[MismatchType.VALUES.length];
    PlacementVolume volume;
    long[] correctByPalette = new long[0];
    long[] restateByPalette = new long[0];
    boolean[] specialByPalette = new boolean[0];
    int generation;
    volatile int buildToken;
    boolean building;
    int verifiedSections;
    int knownSections;
    LayerRange scope;
    boolean scopeReady = true;
    volatile int scopeGeneration;
    final long[] scopedCounts = new long[MismatchType.VALUES.length];
    long[] scopedTotals = new long[0];
    long[] scopedCorrect = new long[0];
    long[] scopedRestate = new long[0];
    long scopedBlocks;

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

    public List<SectionSlot> specialSlots() {
        return this.specialSlots;
    }

    void enqueueUrgent(long key) {
        if (this.urgentKeys.add(key)) {
            this.urgent.enqueue(key);
        }
    }

    long dequeueUrgent() {
        long key = this.urgent.dequeueLong();
        this.urgentKeys.remove(key);
        return key;
    }

    void clearUrgent() {
        this.urgent.clear();
        this.urgentKeys.clear();
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

    public boolean scoped() {
        return this.scope != null && this.scopeReady;
    }

    public boolean scopePending() {
        return this.scope != null && !this.scopeReady;
    }

    public long count(MismatchType type) {
        return scoped() ? this.scopedCounts[type.ordinal()] : this.counts[type.ordinal()];
    }

    public long fullCount(MismatchType type) {
        return this.counts[type.ordinal()];
    }

    public long totalBlocks() {
        PlacementVolume current = this.volume;
        if (current == null) {
            return 0L;
        }
        return scoped() ? this.scopedBlocks : current.totalBlocks();
    }

    public long[] totalByPalette() {
        PlacementVolume current = this.volume;
        if (current == null) {
            return new long[0];
        }
        return scoped() ? this.scopedTotals : current.totalByPalette();
    }

    public long errors() {
        return count(MismatchType.MISSING) + count(MismatchType.EXTRA) + count(MismatchType.WRONG_BLOCK)
                + count(MismatchType.WRONG_STATE);
    }

    public long[] correctByPalette() {
        return scoped() ? this.scopedCorrect : this.correctByPalette;
    }

    public long[] restateByPalette() {
        return scoped() ? this.scopedRestate : this.restateByPalette;
    }

    public double progress() {
        long total = totalBlocks();
        if (total <= 0L) {
            return 0.0;
        }
        return Math.min(1.0, (double) count(MismatchType.CORRECT) / total);
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
        int[] restateIndices = verdict.restateIndices();
        int[] restate = verdict.restateCounts();
        for (int i = 0; i < restateIndices.length; i++) {
            if (restateIndices[i] < this.restateByPalette.length) {
                this.restateByPalette[restateIndices[i]] += (long) sign * restate[i];
            }
        }
        this.verifiedSections += sign;
        if (verdict.worldKnown()) {
            this.knownSections += sign;
        }
    }

    void addTally(ScopeTally tally, int sign) {
        if (tally == null) {
            return;
        }
        for (int i = 0; i < tally.counts.length; i++) {
            this.scopedCounts[i] += (long) sign * tally.counts[i];
        }
        this.scopedBlocks += (long) sign * tally.blocks;
        for (int i = 0; i < tally.paletteIndices.length; i++) {
            int index = tally.paletteIndices[i];
            if (index < this.scopedTotals.length) {
                this.scopedTotals[index] += (long) sign * tally.totals[i];
                this.scopedCorrect[index] += (long) sign * tally.correct[i];
                this.scopedRestate[index] += (long) sign * tally.restate[i];
            }
        }
    }

    void resetScope() {
        java.util.Arrays.fill(this.scopedCounts, 0L);
        int size = this.correctByPalette.length;
        this.scopedTotals = new long[size];
        this.scopedCorrect = new long[size];
        this.scopedRestate = new long[size];
        this.scopedBlocks = 0L;
        this.scopeGeneration++;
        this.scopeReady = true;
        for (SectionSlot slot : this.slots.values()) {
            slot.tally = null;
        }
    }

    void resetTotals(int paletteSize) {
        java.util.Arrays.fill(this.counts, 0L);
        this.correctByPalette = new long[paletteSize];
        this.restateByPalette = new long[paletteSize];
        this.verifiedSections = 0;
        this.knownSections = 0;
        resetScope();
    }
}
