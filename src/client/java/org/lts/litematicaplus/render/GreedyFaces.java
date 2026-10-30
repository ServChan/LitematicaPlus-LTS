package org.lts.litematicaplus.render;

final class GreedyFaces {
    static final int SIDE = 16;
    static final int AREA = SIDE * SIDE;

    @FunctionalInterface
    interface Sink {
        void quad(int a, int b, int width, int height, int color);
    }

    private GreedyFaces() {
    }

    static void merge(int[] colors, int offset, Sink sink) {
        for (int b = 0; b < SIDE; b++) {
            for (int a = 0; a < SIDE; a++) {
                int color = colors[offset + a + b * SIDE];
                if (color == 0) {
                    continue;
                }
                int width = 1;
                while (a + width < SIDE && colors[offset + a + width + b * SIDE] == color) {
                    width++;
                }
                int height = 1;
                while (b + height < SIDE && rowMatches(colors, offset, a, b + height, width, color)) {
                    height++;
                }
                for (int row = b; row < b + height; row++) {
                    for (int column = a; column < a + width; column++) {
                        colors[offset + column + row * SIDE] = 0;
                    }
                }
                sink.quad(a, b, width, height, color);
            }
        }
    }

    private static boolean rowMatches(int[] colors, int offset, int a, int b, int width, int color) {
        for (int column = a; column < a + width; column++) {
            if (colors[offset + column + b * SIDE] != color) {
                return false;
            }
        }
        return true;
    }
}
