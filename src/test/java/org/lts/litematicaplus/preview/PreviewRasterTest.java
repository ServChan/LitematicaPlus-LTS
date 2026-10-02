package org.lts.litematicaplus.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class PreviewRasterTest {
    private static PreviewModel cube(int rgb, boolean translucent, int alpha) {
        PreviewModel.Builder builder = new PreviewModel.Builder();
        for (int face = 0; face < 6; face++) {
            builder.cubeFace(face, 0.0F, 0.0F, 0.0F, 1.0F, rgb, -1, translucent, alpha);
        }
        return builder.build();
    }

    @Test
    void cubeFillsTheMiddleAndLeavesCornersTransparent() {
        int[] pixels = PreviewRaster.render(cube(0x808080, false, 0xFF), 64, PreviewRaster.View.DEFAULT, () -> false);
        assertEquals(0xFF, pixels[32 * 64 + 32] >>> 24);
        assertEquals(0, pixels[0]);
        assertEquals(0, pixels[63 * 64 + 63]);
    }

    @Test
    void topFaceIsBrighterThanTheSides() {
        int[] pixels = PreviewRaster.render(cube(0xC0C0C0, false, 0xFF), 128, PreviewRaster.View.DEFAULT, () -> false);
        int first = 0;
        while (first < 128 && pixels[first * 128 + 64] == 0) {
            first++;
        }
        int last = 127;
        while (last > 0 && pixels[last * 128 + 64] == 0) {
            last--;
        }
        int top = pixels[(first + 4) * 128 + 64];
        int bottom = pixels[(last - 4) * 128 + 64];
        assertTrue((top & 0xFF) > (bottom & 0xFF), "top " + Integer.toHexString(top) + " side " + Integer.toHexString(bottom));
    }

    @Test
    void translucentQuadsBlendOverOpaqueOnes() {
        PreviewModel.Builder builder = new PreviewModel.Builder();
        for (int face = 0; face < 6; face++) {
            builder.cubeFace(face, 0.0F, 0.0F, 0.0F, 1.0F, 0x0000FF, -1, false, 0xFF);
            builder.cubeFace(face, -0.5F, -0.5F, -0.5F, 2.0F, 0xFF0000, -1, true, 0x80);
        }
        int[] pixels = PreviewRaster.render(builder.build(), 64, PreviewRaster.View.DEFAULT, () -> false);
        int middle = pixels[32 * 64 + 32];
        assertTrue((middle >> 16 & 0xFF) > 0, Integer.toHexString(middle));
        assertTrue((middle & 0xFF) > 0, Integer.toHexString(middle));
    }

    @Test
    void emptyModelRendersNothingAndCancellationStops() {
        int[] empty = PreviewRaster.render(new PreviewModel.Builder().build(), 16, PreviewRaster.View.DEFAULT, () -> false);
        for (int pixel : empty) {
            assertEquals(0, pixel);
        }
        assertThrows(CancellationException.class,
                () -> PreviewRaster.render(cube(0xFFFFFF, false, 0xFF), 16, PreviewRaster.View.DEFAULT, () -> true));
    }

    @Test
    void viewChangesStayInRange() {
        PreviewRaster.View view = PreviewRaster.View.DEFAULT.rotated(0.0, 10.0);
        assertEquals(Math.toRadians(89.0), view.pitch(), 1.0E-9);
        assertTrue(view.zoomed(1000.0).zoom() <= 8.0);
        assertTrue(view.panned(9.0, -9.0).panX() <= 1.5);
        assertNotEquals(PreviewRaster.View.DEFAULT, PreviewRaster.View.DEFAULT.rotated(0.1, 0.0));
        assertEquals(0xFF332211, PreviewService.toAbgr(new int[]{0xFF112233})[0]);
    }
}
