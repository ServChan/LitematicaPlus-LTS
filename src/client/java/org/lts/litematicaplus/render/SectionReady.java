package org.lts.litematicaplus.render;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

public final class SectionReady {
    private static final String RENDERER = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer";
    private static final Method INSTANCE = resolve("instanceNullable");
    private static final Method READY = resolve("isSectionReady", int.class, int.class, int.class);

    private SectionReady() {
    }

    private static Method resolve(String name, Class<?>... parameters) {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) {
            return null;
        }
        try {
            return Class.forName(RENDERER).getMethod(name, parameters);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    public static boolean available() {
        return INSTANCE != null && READY != null;
    }

    public static boolean ready(int sectionX, int sectionY, int sectionZ) {
        if (INSTANCE == null || READY == null) {
            return true;
        }
        try {
            Object renderer = INSTANCE.invoke(null);
            return renderer == null || Boolean.TRUE.equals(READY.invoke(renderer, sectionX, sectionY, sectionZ));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return true;
        }
    }
}
