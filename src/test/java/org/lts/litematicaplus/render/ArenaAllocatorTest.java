package org.lts.litematicaplus.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ArenaAllocatorTest {
    @Test
    void reusesFreedSpaceAndMergesNeighbours() {
        ArenaAllocator arena = new ArenaAllocator(100L);
        long a = arena.allocate(40L);
        long b = arena.allocate(40L);
        long c = arena.allocate(20L);
        assertEquals(-1L, arena.allocate(1L));
        arena.free(a, 40L);
        arena.free(c, 20L);
        assertEquals(2, arena.fragments());
        arena.free(b, 40L);
        assertEquals(1, arena.fragments());
        assertTrue(arena.empty());
        assertEquals(0L, arena.allocate(100L));
    }

    @Test
    void randomChurnNeverOverlaps() {
        Random random = new Random(7L);
        ArenaAllocator arena = new ArenaAllocator(1 << 16);
        List<long[]> live = new ArrayList<>();
        for (int step = 0; step < 20000; step++) {
            if (live.isEmpty() || random.nextBoolean()) {
                long size = (random.nextInt(64) + 1) * 28L;
                long offset = arena.allocate(size);
                if (offset >= 0L) {
                    for (long[] other : live) {
                        assertFalse(offset < other[0] + other[1] && other[0] < offset + size);
                    }
                    assertEquals(0L, offset % 28L);
                    live.add(new long[]{offset, size});
                }
            } else {
                long[] block = live.remove(random.nextInt(live.size()));
                arena.free(block[0], block[1]);
            }
        }
        for (long[] block : live) {
            arena.free(block[0], block[1]);
        }
        assertTrue(arena.empty());
        assertEquals(1, arena.fragments());
    }
}
