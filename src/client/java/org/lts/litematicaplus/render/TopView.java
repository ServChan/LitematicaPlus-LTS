package org.lts.litematicaplus.render;

import net.minecraft.client.Minecraft;
import org.lts.litematicaplus.gui.PlacementEditorScreen;

public final class TopView {
    public static final float YAW = 180.0F;
    public static final float PITCH = 90.0F;
    private static final double MIN_DISTANCE = 6.0;
    private static final double MAX_DISTANCE = 1536.0;
    private static boolean active;
    private static double centerX;
    private static double centerZ;
    private static double focusY;
    private static double distance = 32.0;

    private TopView() {
    }

    public static boolean active() {
        return active;
    }

    public static void enable(double x, double topY, double z, double wantedDistance) {
        centerX = x;
        centerZ = z;
        focusY = topY;
        distance = clamp(wantedDistance);
        active = true;
    }

    public static void disable() {
        active = false;
    }

    public static void tick(Minecraft minecraft) {
        if (active && (minecraft.level == null || !(minecraft.gui.screen() instanceof PlacementEditorScreen))) {
            active = false;
        }
    }

    public static double cameraX() {
        return centerX;
    }

    public static double cameraY() {
        return focusY + distance;
    }

    public static double cameraZ() {
        return centerZ;
    }

    public static double distance() {
        return distance;
    }

    public static float farPlane() {
        return (float) (distance * 1.25 + 512.0);
    }

    public static int shaderFar() {
        return (int) (distance * 3.0) + 128;
    }

    public static void pan(double dx, double dz) {
        centerX += dx;
        centerZ += dz;
    }

    public static int ghostRange(int configured) {
        return active ? Math.max(configured, (int) (distance * 1.6) + 96) : configured;
    }

    public static void zoom(double factor) {
        distance = clamp(distance * factor);
    }

    private static double clamp(double value) {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, value));
    }
}
