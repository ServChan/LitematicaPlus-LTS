package org.lts.litematicaplus.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GreedyFacesTest {
    private record Quad(int a, int b, int width, int height, int color) {
    }

    private static List<Quad> merge(int[] colors) {
        List<Quad> quads = new ArrayList<>();
        GreedyFaces.merge(colors, 0, (a, b, width, height, color) -> quads.add(new Quad(a, b, width, height, color)));
        return quads;
    }

    @Test
    void fullPlaneBecomesOneQuad() {
        int[] colors = new int[GreedyFaces.AREA];
        Arrays.fill(colors, 7);
        assertEquals(List.of(new Quad(0, 0, 16, 16, 7)), merge(colors));
    }

    @Test
    void differentColoursStaySeparate() {
        int[] colors = new int[GreedyFaces.AREA];
        colors[0] = 1;
        colors[1] = 2;
        assertEquals(2, merge(colors).size());
    }

    @Test
    void everyCellIsCoveredExactlyOnceWithItsColour() {
        Random random = new Random(42L);
        for (int round = 0; round < 200; round++) {
            int[] colors = new int[GreedyFaces.AREA];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = random.nextInt(3);
            }
            int[] expected = colors.clone();
            int[] painted = new int[GreedyFaces.AREA];
            for (Quad quad : merge(colors)) {
                for (int b = quad.b(); b < quad.b() + quad.height(); b++) {
                    for (int a = quad.a(); a < quad.a() + quad.width(); a++) {
                        assertEquals(0, painted[a + b * 16]);
                        painted[a + b * 16] = quad.color();
                    }
                }
            }
            assertTrue(Arrays.equals(expected, painted));
        }
    }
}
