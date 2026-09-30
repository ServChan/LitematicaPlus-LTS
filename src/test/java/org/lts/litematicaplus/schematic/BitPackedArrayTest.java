package org.lts.litematicaplus.schematic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;
import org.junit.jupiter.api.Test;

class BitPackedArrayTest {
    @Test
    void bitsForPaletteMatchesLitematicaRule() {
        assertEquals(2, BitPackedArray.bitsFor(1));
        assertEquals(2, BitPackedArray.bitsFor(4));
        assertEquals(3, BitPackedArray.bitsFor(5));
        assertEquals(4, BitPackedArray.bitsFor(16));
        assertEquals(5, BitPackedArray.bitsFor(17));
        assertEquals(12, BitPackedArray.bitsFor(4096));
    }

    @Test
    void entriesSpanLongBoundariesWithoutPadding() {
        BitPackedArray array = new BitPackedArray(5, 26);
        assertEquals(3, array.raw().length);
        array.set(12, 0b10111);
        assertEquals(0b10111, array.get(12));
        assertEquals(0b0111L << 60, array.raw()[0]);
        assertEquals(1L, array.raw()[1]);
    }

    @Test
    void firstEntriesOccupyTheLowestBits() {
        BitPackedArray array = new BitPackedArray(2, 8);
        array.set(0, 1);
        array.set(1, 2);
        array.set(2, 3);
        assertArrayEquals(new long[]{0b111001L}, array.raw());
    }

    @Test
    void overwritingAnEntryKeepsItsNeighbours() {
        BitPackedArray array = new BitPackedArray(7, 40);
        for (int i = 0; i < 40; i++) {
            array.set(i, 127);
        }
        array.set(9, 0);
        array.set(18, 5);
        for (int i = 0; i < 40; i++) {
            assertEquals(i == 9 ? 0 : i == 18 ? 5 : 127, array.get(i), "index " + i);
        }
    }

    @Test
    void randomRoundTripForEveryWidth() {
        Random random = new Random(7L);
        for (int bits = 1; bits <= 31; bits++) {
            int size = 257;
            BitPackedArray array = new BitPackedArray(bits, size);
            int[] expected = new int[size];
            int mask = (int) ((1L << bits) - 1L);
            for (int pass = 0; pass < 3; pass++) {
                for (int i = 0; i < size; i++) {
                    expected[i] = random.nextInt() & mask;
                    array.set(i, expected[i]);
                }
            }
            for (int i = 0; i < size; i++) {
                assertEquals(expected[i], array.get(i), "bits=" + bits + " index=" + i);
            }
        }
    }

    @Test
    void rejectsBackingArrayOfTheWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> new BitPackedArray(4, 100, new long[3]));
    }
}
