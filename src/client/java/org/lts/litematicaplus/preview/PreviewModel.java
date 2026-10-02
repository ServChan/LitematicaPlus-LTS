package org.lts.litematicaplus.preview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class PreviewModel {
    public record Texture(int width, int height, int[] argb) {
        public static final Texture NONE = new Texture(0, 0, new int[0]);

        int sample(float u, float v) {
            if (this.width == 0 || this.height == 0) {
                return 0xFFFF00FF;
            }
            int x = Math.min(this.width - 1, Math.max(0, (int) (u * this.width)));
            int y = Math.min(this.height - 1, Math.max(0, (int) (v * this.height)));
            return this.argb[y * this.width + x];
        }
    }

    final int quads;
    final float[] positions;
    final float[] uvs;
    final int[] colors;
    final int[] textures;
    final boolean[] translucent;
    final Texture[] palette;
    final float minX;
    final float minY;
    final float minZ;
    final float maxX;
    final float maxY;
    final float maxZ;
    final boolean simplified;

    private PreviewModel(Builder builder) {
        this.quads = builder.quads;
        this.positions = Arrays.copyOf(builder.positions, builder.quads * 12);
        this.uvs = Arrays.copyOf(builder.uvs, builder.quads * 8);
        this.colors = Arrays.copyOf(builder.colors, builder.quads * 4);
        this.textures = Arrays.copyOf(builder.textures, builder.quads);
        this.translucent = Arrays.copyOf(builder.translucent, builder.quads);
        this.palette = builder.palette.toArray(new Texture[0]);
        boolean empty = builder.quads == 0;
        this.minX = empty ? 0.0F : builder.minX;
        this.minY = empty ? 0.0F : builder.minY;
        this.minZ = empty ? 0.0F : builder.minZ;
        this.maxX = empty ? 0.0F : builder.maxX;
        this.maxY = empty ? 0.0F : builder.maxY;
        this.maxZ = empty ? 0.0F : builder.maxZ;
        this.simplified = builder.simplified;
    }

    public int quads() {
        return this.quads;
    }

    public boolean simplified() {
        return this.simplified;
    }

    public boolean isEmpty() {
        return this.quads == 0;
    }

    public static final class Builder {
        private static final float[][][] CUBE = {
                {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
                {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
                {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}},
                {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
                {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
                {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}}
        };
        private static final float[] CUBE_SHADE = {0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};
        private static final float[] CUBE_UV = {0, 1, 1, 1, 1, 0, 0, 0};

        private int quads;
        private float[] positions = new float[12 * 256];
        private float[] uvs = new float[8 * 256];
        private int[] colors = new int[4 * 256];
        private int[] textures = new int[256];
        private boolean[] translucent = new boolean[256];
        private final List<Texture> palette = new ArrayList<>();
        private final Map<Texture, Integer> paletteIndex = new IdentityHashMap<>();
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private boolean simplified;
        private final float[] faceXyz = new float[12];
        private final int[] faceArgb = new int[4];

        public int quads() {
            return this.quads;
        }

        public Builder simplified(boolean value) {
            this.simplified = value;
            return this;
        }

        public int texture(Texture texture) {
            if (texture == null || texture.width() == 0) {
                return -1;
            }
            Integer known = this.paletteIndex.get(texture);
            if (known != null) {
                return known;
            }
            this.palette.add(texture);
            this.paletteIndex.put(texture, this.palette.size() - 1);
            return this.palette.size() - 1;
        }

        public void quad(float[] xyz, float[] uv, int[] argb, int texture, boolean isTranslucent) {
            ensure();
            int q = this.quads++;
            System.arraycopy(xyz, 0, this.positions, q * 12, 12);
            System.arraycopy(uv, 0, this.uvs, q * 8, 8);
            System.arraycopy(argb, 0, this.colors, q * 4, 4);
            this.textures[q] = texture;
            this.translucent[q] = isTranslucent;
            for (int i = 0; i < 12; i += 3) {
                include(xyz[i], xyz[i + 1], xyz[i + 2]);
            }
        }

        public void cubeFace(int face, float x, float y, float z, float size, int rgb, int texture, boolean isTranslucent,
                int alpha) {
            float[] xyz = this.faceXyz;
            float[][] corners = CUBE[face];
            for (int i = 0; i < 4; i++) {
                xyz[i * 3] = x + corners[i][0] * size;
                xyz[i * 3 + 1] = y + corners[i][1] * size;
                xyz[i * 3 + 2] = z + corners[i][2] * size;
            }
            int shaded = shade(rgb, CUBE_SHADE[face], alpha);
            int[] argb = this.faceArgb;
            argb[0] = shaded;
            argb[1] = shaded;
            argb[2] = shaded;
            argb[3] = shaded;
            quad(xyz, CUBE_UV, argb, texture, isTranslucent);
        }

        private static int shade(int rgb, float factor, int alpha) {
            int r = Math.min(255, Math.round((rgb >> 16 & 0xFF) * factor));
            int g = Math.min(255, Math.round((rgb >> 8 & 0xFF) * factor));
            int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
            return (alpha & 0xFF) << 24 | r << 16 | g << 8 | b;
        }

        private void include(float x, float y, float z) {
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.minZ = Math.min(this.minZ, z);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
            this.maxZ = Math.max(this.maxZ, z);
        }

        private void ensure() {
            if (this.quads == this.textures.length) {
                int next = this.textures.length * 2;
                this.positions = Arrays.copyOf(this.positions, next * 12);
                this.uvs = Arrays.copyOf(this.uvs, next * 8);
                this.colors = Arrays.copyOf(this.colors, next * 4);
                this.textures = Arrays.copyOf(this.textures, next);
                this.translucent = Arrays.copyOf(this.translucent, next);
            }
        }

        public PreviewModel build() {
            return new PreviewModel(this);
        }
    }
}
