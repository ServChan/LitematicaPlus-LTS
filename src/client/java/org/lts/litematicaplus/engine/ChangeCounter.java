package org.lts.litematicaplus.engine;

import java.util.Arrays;

public final class ChangeCounter {
    private static final int WINDOW_SECONDS = 60;

    private final int[] buckets = new int[WINDOW_SECONDS];
    private long second = Long.MIN_VALUE;

    public void record(long nowSecond) {
        advance(nowSecond);
        this.buckets[slot(nowSecond)]++;
    }

    public int total(long nowSecond) {
        advance(nowSecond);
        int sum = 0;
        for (int bucket : this.buckets) {
            sum += bucket;
        }
        return sum;
    }

    private void advance(long nowSecond) {
        if (this.second == Long.MIN_VALUE || nowSecond - this.second >= WINDOW_SECONDS) {
            Arrays.fill(this.buckets, 0);
            this.second = nowSecond;
            return;
        }
        while (this.second < nowSecond) {
            this.second++;
            this.buckets[slot(this.second)] = 0;
        }
    }

    private static int slot(long second) {
        return (int) Math.floorMod(second, (long) WINDOW_SECONDS);
    }
}
