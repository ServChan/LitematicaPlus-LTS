package org.lts.litematicaplus.world;

import java.util.Arrays;

public final class GhostSection {
    public static final short OUTSIDE = -1;
    public static final int VOLUME = 4096;

    private final short[] data = new short[VOLUME];
    private int solid;
    private int inside;

    public GhostSection() {
        Arrays.fill(this.data, OUTSIDE);
    }

    public static int index(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | x & 15;
    }

    public short get(int index) {
        return this.data[index];
    }

    public short get(int x, int y, int z) {
        return this.data[index(x, y, z)];
    }

    public void set(int index, short value) {
        this.data[index] = value;
    }

    public short[] raw() {
        return this.data;
    }

    public int solidCount() {
        return this.solid;
    }

    public int insideCount() {
        return this.inside;
    }

    public void recount(boolean[] solidByPalette) {
        int solidCount = 0;
        int insideCount = 0;
        for (short value : this.data) {
            if (value != OUTSIDE) {
                insideCount++;
                if (solidByPalette[value & 0xFFFF]) {
                    solidCount++;
                }
            }
        }
        this.solid = solidCount;
        this.inside = insideCount;
    }
}
