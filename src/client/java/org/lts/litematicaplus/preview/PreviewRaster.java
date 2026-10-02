package org.lts.litematicaplus.preview;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class PreviewRaster {
    private static final int PADDING = 8;
    private static final int ALPHA_CUTOUT = 24;

    public record View(double yaw, double pitch, double zoom, double panX, double panY) {
        public static final View DEFAULT = new View(Math.toRadians(45.0), Math.toRadians(30.0), 1.0, 0.0, 0.0);

        public View rotated(double deltaYaw, double deltaPitch) {
            double nextYaw = this.yaw + deltaYaw;
            double turn = Math.PI * 2.0;
            nextYaw = nextYaw - Math.floor(nextYaw / turn) * turn;
            double limit = Math.toRadians(89.0);
            return new View(nextYaw, Math.max(-limit, Math.min(limit, this.pitch + deltaPitch)), this.zoom, this.panX,
                    this.panY);
        }

        public View panned(double deltaX, double deltaY) {
            return new View(this.yaw, this.pitch, this.zoom, Math.max(-1.5, Math.min(1.5, this.panX + deltaX)),
                    Math.max(-1.5, Math.min(1.5, this.panY + deltaY)));
        }

        public View zoomed(double factor) {
            return new View(this.yaw, this.pitch, Math.max(0.3, Math.min(8.0, this.zoom * factor)), this.panX, this.panY);
        }
    }

    private PreviewRaster() {
    }

    public static int[] render(PreviewModel model, int size, View view) {
        return render(model, size, view, () -> Thread.currentThread().isInterrupted());
    }

    public static int[] render(PreviewModel model, int size, View view, BooleanSupplier cancelled) {
        int[] pixels = new int[size * size];
        if (model.isEmpty()) {
            return pixels;
        }
        float[] depth = new float[pixels.length];
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        double cosYaw = Math.cos(view.yaw());
        double sinYaw = Math.sin(view.yaw());
        double cosPitch = Math.cos(view.pitch());
        double sinPitch = Math.sin(view.pitch());
        double dx = sinYaw * cosPitch;
        double dy = sinPitch;
        double dz = cosYaw * cosPitch;
        double rx = cosYaw;
        double rz = -sinYaw;
        double ux = -sinYaw * sinPitch;
        double uy = cosPitch;
        double uz = -cosYaw * sinPitch;
        double lowX = Double.POSITIVE_INFINITY;
        double highX = Double.NEGATIVE_INFINITY;
        double lowY = Double.POSITIVE_INFINITY;
        double highY = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? model.minX : model.maxX;
            double y = (corner & 2) == 0 ? model.minY : model.maxY;
            double z = (corner & 4) == 0 ? model.minZ : model.maxZ;
            double sx = x * rx + z * rz;
            double sy = -(x * ux + y * uy + z * uz);
            lowX = Math.min(lowX, sx);
            highX = Math.max(highX, sx);
            lowY = Math.min(lowY, sy);
            highY = Math.max(highY, sy);
        }
        double scale = Math.min((size - PADDING * 2.0) / Math.max(1.0, highX - lowX),
                (size - PADDING * 2.0) / Math.max(1.0, highY - lowY)) * view.zoom();
        double offsetX = size * (0.5 + view.panX()) - (lowX + highX) * 0.5 * scale;
        double offsetY = size * (0.5 + view.panY()) - (lowY + highY) * 0.5 * scale;
        float[] sx = new float[4];
        float[] sy = new float[4];
        float[] sd = new float[4];
        int translucentCount = 0;
        for (int q = 0; q < model.quads; q++) {
            if (model.translucent[q]) {
                translucentCount++;
                continue;
            }
            if ((q & 1023) == 0 && cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            project(model, q, sx, sy, sd, rx, rz, ux, uy, uz, dx, dy, dz, scale, offsetX, offsetY);
            drawQuad(model, q, sx, sy, sd, pixels, depth, size, true);
        }
        if (translucentCount > 0) {
            int[] order = new int[translucentCount];
            float[] keys = new float[translucentCount];
            for (int q = 0, n = 0; q < model.quads; q++) {
                if (model.translucent[q]) {
                    int base = q * 12;
                    float cx = (model.positions[base] + model.positions[base + 6]) * 0.5F;
                    float cy = (model.positions[base + 1] + model.positions[base + 7]) * 0.5F;
                    float cz = (model.positions[base + 2] + model.positions[base + 8]) * 0.5F;
                    order[n] = q;
                    keys[n++] = (float) (cx * dx + cy * dy + cz * dz);
                }
            }
            Integer[] boxed = new Integer[translucentCount];
            for (int i = 0; i < translucentCount; i++) {
                boxed[i] = i;
            }
            Arrays.sort(boxed, (a, b) -> Float.compare(keys[a], keys[b]));
            for (int i = 0; i < translucentCount; i++) {
                if ((i & 1023) == 0 && cancelled.getAsBoolean()) {
                    throw new CancellationException();
                }
                int q = order[boxed[i]];
                project(model, q, sx, sy, sd, rx, rz, ux, uy, uz, dx, dy, dz, scale, offsetX, offsetY);
                drawQuad(model, q, sx, sy, sd, pixels, depth, size, false);
            }
        }
        return pixels;
    }

    private static void project(PreviewModel model, int q, float[] sx, float[] sy, float[] sd, double rx, double rz,
            double ux, double uy, double uz, double dx, double dy, double dz, double scale, double offsetX,
            double offsetY) {
        int base = q * 12;
        for (int i = 0; i < 4; i++) {
            double x = model.positions[base + i * 3];
            double y = model.positions[base + i * 3 + 1];
            double z = model.positions[base + i * 3 + 2];
            sx[i] = (float) ((x * rx + z * rz) * scale + offsetX);
            sy[i] = (float) (-(x * ux + y * uy + z * uz) * scale + offsetY);
            sd[i] = (float) (x * dx + y * dy + z * dz);
        }
    }

    private static void drawQuad(PreviewModel model, int q, float[] sx, float[] sy, float[] sd, int[] pixels,
            float[] depth, int size, boolean opaque) {
        triangle(model, q, 0, 1, 2, sx, sy, sd, pixels, depth, size, opaque);
        triangle(model, q, 0, 2, 3, sx, sy, sd, pixels, depth, size, opaque);
    }

    private static void triangle(PreviewModel model, int q, int ia, int ib, int ic, float[] sx, float[] sy, float[] sd,
            int[] pixels, float[] depth, int size, boolean opaque) {
        float ax = sx[ia];
        float ay = sy[ia];
        float bx = sx[ib];
        float by = sy[ib];
        float cx = sx[ic];
        float cy = sy[ic];
        float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1.0E-4F) {
            return;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = Math.min(size - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = Math.min(size - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        if (minX > maxX || minY > maxY) {
            return;
        }
        int textureIndex = model.textures[q];
        PreviewModel.Texture texture = textureIndex < 0 ? null : model.palette[textureIndex];
        int uvBase = q * 8;
        float ua = model.uvs[uvBase + ia * 2];
        float va = model.uvs[uvBase + ia * 2 + 1];
        float ub = model.uvs[uvBase + ib * 2];
        float vb = model.uvs[uvBase + ib * 2 + 1];
        float uc = model.uvs[uvBase + ic * 2];
        float vc = model.uvs[uvBase + ic * 2 + 1];
        int colorBase = q * 4;
        int ca = model.colors[colorBase + ia];
        int cb = model.colors[colorBase + ib];
        int cc = model.colors[colorBase + ic];
        boolean flat = ca == cb && cb == cc;
        float inverse = 1.0F / area;
        for (int y = minY; y <= maxY; y++) {
            float py = y + 0.5F;
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5F;
                float wa = ((bx - px) * (cy - py) - (by - py) * (cx - px)) * inverse;
                float wb = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) * inverse;
                float wc = 1.0F - wa - wb;
                if (wa < -1.0E-4F || wb < -1.0E-4F || wc < -1.0E-4F) {
                    continue;
                }
                int index = y * size + x;
                float d = wa * sd[ia] + wb * sd[ib] + wc * sd[ic];
                if (d < depth[index] - 1.0E-4F) {
                    continue;
                }
                int tint = flat ? ca : blend3(ca, cb, cc, wa, wb, wc);
                int color;
                if (texture != null) {
                    int sample = texture.sample(wa * ua + wb * ub + wc * uc, wa * va + wb * vb + wc * vc);
                    if ((sample >>> 24) < ALPHA_CUTOUT) {
                        continue;
                    }
                    color = multiply(sample, tint);
                } else {
                    color = tint;
                }
                int alpha = color >>> 24;
                if (opaque) {
                    pixels[index] = color | 0xFF000000;
                    depth[index] = d;
                } else if (alpha >= 250) {
                    pixels[index] = color;
                    depth[index] = d;
                } else if (alpha >= ALPHA_CUTOUT) {
                    pixels[index] = over(color, pixels[index]);
                }
            }
        }
    }

    private static int blend3(int a, int b, int c, float wa, float wb, float wc) {
        int alpha = Math.round((a >>> 24) * wa + (b >>> 24) * wb + (c >>> 24) * wc);
        int r = Math.round((a >> 16 & 0xFF) * wa + (b >> 16 & 0xFF) * wb + (c >> 16 & 0xFF) * wc);
        int g = Math.round((a >> 8 & 0xFF) * wa + (b >> 8 & 0xFF) * wb + (c >> 8 & 0xFF) * wc);
        int bl = Math.round((a & 0xFF) * wa + (b & 0xFF) * wb + (c & 0xFF) * wc);
        return clamp(alpha) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(bl);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    static int multiply(int sample, int tint) {
        int alpha = (sample >>> 24) * (tint >>> 24) / 255;
        int r = (sample >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
        int g = (sample >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
        int b = (sample & 0xFF) * (tint & 0xFF) / 255;
        return alpha << 24 | r << 16 | g << 8 | b;
    }

    static int over(int source, int destination) {
        int alpha = source >>> 24;
        int inverse = 255 - alpha;
        int destinationAlpha = destination >>> 24;
        int outAlpha = alpha + destinationAlpha * inverse / 255;
        if (outAlpha == 0) {
            return 0;
        }
        int r = ((source >> 16 & 0xFF) * alpha + (destination >> 16 & 0xFF) * destinationAlpha * inverse / 255) / outAlpha;
        int g = ((source >> 8 & 0xFF) * alpha + (destination >> 8 & 0xFF) * destinationAlpha * inverse / 255) / outAlpha;
        int b = ((source & 0xFF) * alpha + (destination & 0xFF) * destinationAlpha * inverse / 255) / outAlpha;
        return outAlpha << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }
}
