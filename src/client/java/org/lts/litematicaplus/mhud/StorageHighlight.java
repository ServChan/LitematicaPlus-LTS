package org.lts.litematicaplus.mhud;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class StorageHighlight {
    private static final double LABEL_DISTANCE_SQ = 32.0 * 32.0;
    private static final int LABEL_MAX_LINES = 5;
    private static final long PULSE_MS = 2400L;
    private static final double[] HALO_INFLATE = {0.035, 0.085, 0.15};
    private static final float[] HALO_WIDTH = {2.5F, 4.0F, 6.0F};
    private static final float[] HALO_ALPHA = {0.55F, 0.26F, 0.11F};

    public interface Sink {
        void box(AABB box, int lineColor, float width, int fillColor);
    }

    private static int frames;

    private StorageHighlight() {
    }

    static int frames() {
        return frames;
    }

    static int ownColor() {
        return 0xFF000000 | Color.HSBtoRGB(MaterialHudConfig.current().hue / 360.0F, 0.55F, 1.0F);
    }

    private static List<StorageSearch.Match> sorted() {
        List<StorageSearch.Match> matches = new ArrayList<>(StorageSearch.get().matches());
        matches.sort(Comparator.comparingInt((StorageSearch.Match match) -> match.pos().getY())
                .thenComparingInt(match -> match.pos().getX()).thenComparingInt(match -> match.pos().getZ()));
        return matches;
    }

    public static void emit(Sink sink) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!MaterialHud.enabled() || minecraft.level == null) {
            return;
        }
        List<StorageSearch.Match> matches = sorted();
        if (matches.isEmpty()) {
            return;
        }
        frames++;
        MaterialHudConfig config = MaterialHudConfig.current();
        float strength = config.highlightGlow / 100.0F;
        double phase = (Util.getMillis() % PULSE_MS) / (double) PULSE_MS;
        float pulse = (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0));
        float breathe = 0.85F + 0.15F * pulse;
        for (StorageSearch.Match match : matches) {
            List<Integer> colors = config.ownColor ? List.of(ownColor()) : match.colors();
            AABB box = shape(minecraft, match.pos(), match.pair());
            int count = Math.max(1, colors.size());
            int halo = soft(blend(colors));
            float fillAlpha = (0.10F + 0.22F * strength) * breathe * 0.6F;
            double height = (box.maxY - box.minY) / count;
            for (int i = 0; i < count; i++) {
                AABB band = new AABB(box.minX, box.minY + height * i, box.minZ, box.maxX, box.minY + height * (i + 1),
                        box.maxZ).deflate(0.002);
                sink.box(band, 0, 0.0F, alpha(soft(colors.get(Math.min(i, colors.size() - 1))), fillAlpha));
            }
            for (int layer = 0; layer < HALO_INFLATE.length; layer++) {
                float value = HALO_ALPHA[layer] * (0.45F + 0.55F * strength) * breathe;
                sink.box(box.inflate(HALO_INFLATE[layer]), alpha(halo, value), HALO_WIDTH[layer], 0);
            }
            sink.box(box.inflate(0.004), alpha(lighten(halo, 0.25F), 0.75F + 0.25F * strength), 1.5F, 0);
        }
    }

    static void labels(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        MaterialHudConfig config = MaterialHudConfig.current();
        if (!MaterialHud.enabled() || !config.highlightLabels || minecraft.level == null || minecraft.player == null) {
            return;
        }
        List<StorageSearch.Match> matches = sorted();
        if (matches.isEmpty()) {
            return;
        }
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        Matrix4f projection = camera.getViewRotationProjectionMatrix(new Matrix4f());
        Font font = minecraft.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        List<int[]> placed = new ArrayList<>();
        StorageSearch search = StorageSearch.get();
        for (StorageSearch.Match match : matches) {
            AABB box = shape(minecraft, match.pos(), match.pair());
            Vec3 top = new Vec3((box.minX + box.maxX) / 2.0, box.maxY + 0.4, (box.minZ + box.maxZ) / 2.0);
            if (minecraft.player.position().distanceToSqr(top) > LABEL_DISTANCE_SQ) {
                continue;
            }
            Vector4f clip = new Vector4f((float) (top.x - eye.x), (float) (top.y - eye.y), (float) (top.z - eye.z), 1.0F)
                    .mul(projection);
            if (clip.w <= 0.05F) {
                continue;
            }
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (Math.abs(ndcX) > 1.1F || Math.abs(ndcY) > 1.1F) {
                continue;
            }
            int anchorX = Math.round((ndcX + 1.0F) * 0.5F * width);
            int anchorY = Math.round((1.0F - ndcY) * 0.5F * height);
            List<String> lines = new ArrayList<>();
            List<Integer> lineColors = new ArrayList<>();
            List<Integer> colors = match.colors();
            for (int i = 0; i < colors.size() && i < LABEL_MAX_LINES; i++) {
                String text = search.labelFor(colors.get(i));
                if (!text.isEmpty()) {
                    lines.add(text);
                    lineColors.add(config.ownColor ? soft(ownColor()) : soft(colors.get(i)));
                }
            }
            if (lines.isEmpty()) {
                continue;
            }
            if (colors.size() > LABEL_MAX_LINES) {
                lines.add("+" + (colors.size() - LABEL_MAX_LINES));
                lineColors.add(0xFFFFFFFF);
            }
            int blockWidth = 0;
            for (String line : lines) {
                blockWidth = Math.max(blockWidth, font.width(line));
            }
            int blockHeight = lines.size() * 10;
            int left = anchorX - blockWidth / 2 - 2;
            int bottom = anchorY;
            boolean moved = true;
            while (moved) {
                moved = false;
                for (int[] other : placed) {
                    boolean overlapX = left < other[0] + other[2] && other[0] < left + blockWidth + 4;
                    boolean overlapY = bottom - blockHeight < other[1] + other[3] && other[1] < bottom;
                    if (overlapX && overlapY) {
                        bottom = other[1] - 2;
                        moved = true;
                    }
                }
            }
            int topY = bottom - blockHeight;
            placed.add(new int[] {left, topY, blockWidth + 4, blockHeight});
            graphics.fill(left, topY - 1, left + blockWidth + 4, bottom, 0x90000000);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                graphics.text(font, line, anchorX - font.width(line) / 2, topY + i * 10, lineColors.get(i), false);
            }
        }
    }

    private static AABB shape(Minecraft minecraft, BlockPos pos, BlockPos pair) {
        AABB union = part(minecraft, pos);
        if (pair != null) {
            union = union.minmax(part(minecraft, pair));
        }
        return union;
    }

    private static AABB part(Minecraft minecraft, BlockPos pos) {
        try {
            BlockState state = minecraft.level.getBlockState(pos);
            VoxelShape shape = state.getShape(minecraft.level, pos);
            return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        } catch (RuntimeException exception) {
            return new AABB(pos);
        }
    }

    private static int soft(int color) {
        return lighten(color, 0.18F);
    }

    private static int lighten(int color, float amount) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r += Math.round((255 - r) * amount);
        g += Math.round((255 - g) * amount);
        b += Math.round((255 - b) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int blend(List<Integer> colors) {
        if (colors.isEmpty()) {
            return 0xFFFFFFFF;
        }
        int r = 0;
        int g = 0;
        int b = 0;
        for (int color : colors) {
            r += (color >> 16) & 0xFF;
            g += (color >> 8) & 0xFF;
            b += color & 0xFF;
        }
        int n = colors.size();
        return 0xFF000000 | (r / n) << 16 | (g / n) << 8 | (b / n);
    }

    private static int alpha(int color, float alpha) {
        int value = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return value << 24 | (color & 0x00FFFFFF);
    }
}
