package org.lts.litematicaplus.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;

public final class ExternalMap {
    private static final int MAP_RANGE = 768;
    private static final String CONTROLLER = "com.voxymap.client.map.VoxyMapCameraController";
    private static final MethodType FLAG = MethodType.methodType(boolean.class);
    private static final MethodType COORDINATE = MethodType.methodType(double.class);
    private static final MethodHandle ACTIVE = resolve(CONTROLLER, "isActive", FLAG);
    private static final MethodHandle CENTER_X = resolve(CONTROLLER, "centerX", COORDINATE);
    private static final MethodHandle CENTER_Y = resolve(CONTROLLER, "centerY", COORDINATE);
    private static final MethodHandle CENTER_Z = resolve(CONTROLLER, "centerZ", COORDINATE);
    private static final MethodHandle SHOWN = resolve("com.voxymap.client.map.VoxyMapSettings", "schematicsEnabled", FLAG);
    private static final MethodHandle CAMERA_X = resolve(CONTROLLER, "cameraX", COORDINATE);
    private static final MethodHandle CAMERA_Y = resolve(CONTROLLER, "cameraY", COORDINATE);
    private static final MethodHandle CAMERA_Z = resolve(CONTROLLER, "cameraZ", COORDINATE);
    private static boolean framed;
    private static boolean frameActive;
    private static boolean frameHidden;
    private static Vec3 frameCenter;

    private ExternalMap() {
    }

    private static MethodHandle resolve(String owner, String name, MethodType type) {
        if (!FabricLoader.getInstance().isModLoaded("voxymap")) {
            return null;
        }
        try {
            return MethodHandles.publicLookup().unreflect(Class.forName(owner).getMethod(name)).asType(type);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    public static void beginFrame() {
        framed = false;
        frameActive = liveActive();
        frameCenter = frameActive ? liveCenter() : null;
        frameHidden = frameActive && liveHidden();
        framed = true;
    }

    public static void endFrame() {
        framed = false;
        frameCenter = null;
    }

    public static boolean active() {
        return framed ? frameActive : liveActive();
    }

    public static Vec3 focus(Vec3 camera) {
        if (framed) {
            return frameCenter != null ? frameCenter : camera;
        }
        if (!active()) {
            return camera;
        }
        Vec3 center = liveCenter();
        return center != null ? center : camera;
    }

    public static float farPlane() {
        if (CAMERA_X == null || CAMERA_Y == null || CAMERA_Z == null || CENTER_X == null || !active()) {
            return 0.0F;
        }
        try {
            Vec3 camera = new Vec3((double) CAMERA_X.invokeExact(), (double) CAMERA_Y.invokeExact(),
                    (double) CAMERA_Z.invokeExact());
            return (float) (camera.distanceTo(focus(camera)) * 1.25 + 512.0);
        } catch (Throwable exception) {
            rethrowFatal(exception);
            return 0.0F;
        }
    }

    public static boolean hidden() {
        if (framed) {
            return frameHidden;
        }
        return active() && liveHidden();
    }

    public static int ghostRange(int configured) {
        return active() ? Math.max(configured, MAP_RANGE) : configured;
    }

    private static boolean liveActive() {
        if (ACTIVE == null) {
            return false;
        }
        try {
            return (boolean) ACTIVE.invokeExact();
        } catch (Throwable exception) {
            rethrowFatal(exception);
            return false;
        }
    }

    private static Vec3 liveCenter() {
        if (CENTER_X == null || CENTER_Y == null || CENTER_Z == null) {
            return null;
        }
        try {
            return new Vec3((double) CENTER_X.invokeExact(), (double) CENTER_Y.invokeExact(),
                    (double) CENTER_Z.invokeExact());
        } catch (Throwable exception) {
            rethrowFatal(exception);
            return null;
        }
    }

    private static boolean liveHidden() {
        if (SHOWN == null) {
            return false;
        }
        try {
            return !(boolean) SHOWN.invokeExact();
        } catch (Throwable exception) {
            rethrowFatal(exception);
            return false;
        }
    }

    private static void rethrowFatal(Throwable exception) {
        if (exception instanceof VirtualMachineError error) {
            throw error;
        }
    }
}
