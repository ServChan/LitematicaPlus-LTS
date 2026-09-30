package org.lts.litematicaplus.world;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldCache {
    private static final Method SECTION = resolve();

    private WorldCache() {
    }

    private static Method resolve() {
        if (!FabricLoader.getInstance().isModLoaded("voxymap") || !FabricLoader.getInstance().isModLoaded("voxy")) {
            return null;
        }
        try {
            return Class.forName("com.voxymap.client.map.VoxyWorldData").getMethod("chunkSection", Level.class,
                    int.class, int.class, int.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    public static boolean available() {
        return SECTION != null;
    }

    public static BlockState[] section(Level level, int sectionX, int sectionY, int sectionZ) {
        if (SECTION == null || level == null) {
            return null;
        }
        try {
            return SECTION.invoke(null, level, sectionX, sectionY, sectionZ) instanceof BlockState[] states
                    && states.length == GhostSection.VOLUME ? states : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }
}
