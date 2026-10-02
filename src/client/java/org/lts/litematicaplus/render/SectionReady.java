package org.lts.litematicaplus.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;

public final class SectionReady {
    private static final String RENDERER = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer";
    private static final MethodHandle INSTANCE = resolve("instanceNullable", MethodType.methodType(Object.class));
    private static final MethodHandle READY = resolve("isSectionReady",
            MethodType.methodType(boolean.class, Object.class, int.class, int.class, int.class), int.class, int.class,
            int.class);

    private SectionReady() {
    }

    private static MethodHandle resolve(String name, MethodType type, Class<?>... parameters) {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) {
            return null;
        }
        try {
            return MethodHandles.publicLookup().unreflect(Class.forName(RENDERER).getMethod(name, parameters)).asType(type);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    public static boolean available() {
        return INSTANCE != null && READY != null;
    }

    public static Object renderer() {
        if (INSTANCE == null || READY == null) {
            return null;
        }
        try {
            return (Object) INSTANCE.invokeExact();
        } catch (Throwable exception) {
            if (exception instanceof VirtualMachineError error) {
                throw error;
            }
            return null;
        }
    }

    public static boolean ready(Object renderer, int sectionX, int sectionY, int sectionZ) {
        if (renderer == null || READY == null) {
            return true;
        }
        try {
            return (boolean) READY.invokeExact(renderer, sectionX, sectionY, sectionZ);
        } catch (Throwable exception) {
            if (exception instanceof VirtualMachineError error) {
                throw error;
            }
            return true;
        }
    }

    public static boolean ready(int sectionX, int sectionY, int sectionZ) {
        return ready(renderer(), sectionX, sectionY, sectionZ);
    }
}
