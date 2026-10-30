package org.lts.litematicaplus.world;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class GhostSection {
    public static final short OUTSIDE = -1;
    public static final int VOLUME = 4096;

    private short[] data = new short[VOLUME];
    private int solid;
    private int inside;

    private static final GhostSection FULL_AIR = fullAir();

    public GhostSection() {
        Arrays.fill(this.data, OUTSIDE);
    }

    private static GhostSection fullAir() {
        GhostSection section = new GhostSection();
        Arrays.fill(section.data, (short) 0);
        section.inside = VOLUME;
        return section;
    }

    private record Content(short[] data, int hash) {
        Content(short[] data) {
            this(data, Arrays.hashCode(data));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Content content && content.hash == this.hash && Arrays.equals(content.data, this.data);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    public static final class Interner {
        private final Map<Content, short[]> known = new HashMap<>();

        public void share(GhostSection section) {
            section.data = this.known.computeIfAbsent(new Content(section.data), Content::data);
        }
    }

    public static GhostSection sharedFullAir() {
        return FULL_AIR;
    }

    public boolean isFullAir() {
        return this.inside == VOLUME && this.solid == 0;
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
