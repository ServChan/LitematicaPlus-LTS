package org.lts.litematicaplus.schematic;

public final class BitPackedArray {
    private final long[] data;
    private final int bits;
    private final long size;
    private final long mask;

    public BitPackedArray(int bits, long size) {
        this(bits, size, null);
    }

    public BitPackedArray(int bits, long size, long[] backing) {
        if (bits < 1 || bits > 32) {
            throw new IllegalArgumentException("Bits per entry out of range: " + bits);
        }
        if (size < 0) {
            throw new IllegalArgumentException("Negative size: " + size);
        }
        this.bits = bits;
        this.size = size;
        this.mask = (1L << bits) - 1L;
        int expected = requiredLongs(bits, size);
        if (backing == null) {
            this.data = new long[expected];
        } else if (backing.length != expected) {
            throw new IllegalArgumentException("Backing array has " + backing.length + " longs, expected " + expected);
        } else {
            this.data = backing;
        }
    }

    public static int bitsFor(int paletteSize) {
        return Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(1, paletteSize) - 1));
    }

    public static int requiredLongs(int bits, long size) {
        long total = (size * bits + 63L) >>> 6;
        if (total > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("Volume too large: " + size);
        }
        return (int) total;
    }

    public int get(long index) {
        long bitIndex = index * this.bits;
        int start = (int) (bitIndex >>> 6);
        int offset = (int) (bitIndex & 63L);
        int end = (int) ((bitIndex + this.bits - 1L) >>> 6);
        if (start == end) {
            return (int) (this.data[start] >>> offset & this.mask);
        }
        return (int) ((this.data[start] >>> offset | this.data[end] << (64 - offset)) & this.mask);
    }

    public void set(long index, int value) {
        long bitIndex = index * this.bits;
        int start = (int) (bitIndex >>> 6);
        int offset = (int) (bitIndex & 63L);
        int end = (int) ((bitIndex + this.bits - 1L) >>> 6);
        long masked = value & this.mask;
        this.data[start] = this.data[start] & ~(this.mask << offset) | masked << offset;
        if (start != end) {
            int spill = 64 - offset;
            int remaining = this.bits - spill;
            this.data[end] = this.data[end] >>> remaining << remaining | masked >>> spill;
        }
    }

    public long[] raw() {
        return this.data;
    }

    public int bits() {
        return this.bits;
    }

    public long size() {
        return this.size;
    }
}
