package org.lts.litematicaplus.render;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

public final class ExternalMap {
    private static final int MAP_RANGE = 768;
    private static final Method ACTIVE = resolve();

    private ExternalMap() {
    }

    private static Method resolve() {
        if (!FabricLoader.getInstance().isModLoaded("voxymap")) {
            return null;
        }
        try {
            return Class.forName("com.voxymap.client.map.VoxyMapCameraController").getMethod("isActive");
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

    public static int ghostRange(int configured) {
        return active() ? Math.max(configured, MAP_RANGE) : configured;
    }
}
