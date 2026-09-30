package org.lts.litematicaplus.render;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;

public final class ExternalMap {
    private static final int MAP_RANGE = 768;
    private static final Method ACTIVE = resolve("isActive");
    private static final Method CENTER_X = resolve("centerX");
    private static final Method CENTER_Y = resolve("centerY");
    private static final Method CENTER_Z = resolve("centerZ");
    private static final Method SHOWN = resolveSetting();
    private static final Method CAMERA_X = resolve("cameraX");
    private static final Method CAMERA_Y = resolve("cameraY");
    private static final Method CAMERA_Z = resolve("cameraZ");

    private ExternalMap() {
    }

    private static Method resolve(String name) {
        if (!FabricLoader.getInstance().isModLoaded("voxymap")) {
            return null;
        }
        try {
            return Class.forName("com.voxymap.client.map.VoxyMapCameraController").getMethod(name);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    private static Method resolveSetting() {
        if (!FabricLoader.getInstance().isModLoaded("voxymap")) {
            return null;
        }
        try {
            return Class.forName("com.voxymap.client.map.VoxyMapSettings").getMethod("schematicsEnabled");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    public static boolean active() {
        if (ACTIVE == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(ACTIVE.invoke(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return false;
        }
    }

    public static Vec3 focus(Vec3 camera) {
        if (CENTER_X == null || CENTER_Y == null || CENTER_Z == null || !active()) {
            return camera;
        }
        try {
            return new Vec3((Double) CENTER_X.invoke(null), (Double) CENTER_Y.invoke(null), (Double) CENTER_Z.invoke(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return camera;
        }
    }

    public static float farPlane() {
        if (CAMERA_X == null || CAMERA_Y == null || CAMERA_Z == null || CENTER_X == null || !active()) {
            return 0.0F;
        }
        try {
            Vec3 camera = new Vec3((Double) CAMERA_X.invoke(null), (Double) CAMERA_Y.invoke(null),
                    (Double) CAMERA_Z.invoke(null));
            return (float) (camera.distanceTo(focus(camera)) * 1.25 + 512.0);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return 0.0F;
        }
    }

    public static boolean hidden() {
        if (SHOWN == null || !active()) {
            return false;
        }
        try {
            return Boolean.FALSE.equals(SHOWN.invoke(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return false;
        }
    }

    public static int ghostRange(int configured) {
        return active() ? Math.max(configured, MAP_RANGE) : configured;
    }
}
