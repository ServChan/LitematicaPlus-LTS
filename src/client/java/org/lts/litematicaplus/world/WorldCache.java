package org.lts.litematicaplus.world;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.verify.SectionVerdict;

public final class WorldCache {
    private static final int PADDED = 18;
    private static final int PADDED_VOLUME = PADDED * PADDED * PADDED;
    private static final Method SECTION = resolve();

    public record Snapshot(BlockState[] world, long[] border) {
    }

    private WorldCache() {
    }

    private static Method resolve() {
        if (!FabricLoader.getInstance().isModLoaded("voxymap") || !FabricLoader.getInstance().isModLoaded("voxy")) {
            return null;
        }
        try {
            return Class.forName("com.voxymap.client.map.VoxyWorldData").getMethod("chunkSectionPadded", Level.class,
                    int.class, int.class, int.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    public static boolean available() {
        return SECTION != null;
    }

    public static Snapshot section(Level level, int sectionX, int sectionY, int sectionZ) {
        if (SECTION == null || level == null) {
            return null;
        }
        try {
            return SECTION.invoke(null, level, sectionX, sectionY, sectionZ) instanceof BlockState[] padded
                    && padded.length == PADDED_VOLUME ? unpack(padded) : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    static Snapshot unpack(BlockState[] padded) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[] world = new BlockState[GhostSection.VOLUME];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                int source = ((y + 1) * PADDED + z + 1) * PADDED + 1;
                int target = y << 8 | z << 4;
                for (int x = 0; x < 16; x++) {
                    BlockState state = padded[source + x];
                    world[target + x] = state == null ? air : state;
                }
            }
        }
        long[] border = new long[SectionVerdict.BORDER_LONGS];
        for (int b = 0; b < 16; b++) {
            for (int a = 0; a < 16; a++) {
                mark(border, SectionVerdict.FACE_DOWN, a, b, padded[at(a, -1, b)]);
                mark(border, SectionVerdict.FACE_UP, a, b, padded[at(a, 16, b)]);
                mark(border, SectionVerdict.FACE_NORTH, a, b, padded[at(a, b, -1)]);
                mark(border, SectionVerdict.FACE_SOUTH, a, b, padded[at(a, b, 16)]);
                mark(border, SectionVerdict.FACE_WEST, a, b, padded[at(-1, b, a)]);
                mark(border, SectionVerdict.FACE_EAST, a, b, padded[at(16, b, a)]);
            }
        }
        return new Snapshot(world, border);
    }

    private static int at(int x, int y, int z) {
        return ((y + 1) * PADDED + z + 1) * PADDED + x + 1;
    }

    private static void mark(long[] border, int face, int a, int b, BlockState state) {
        SectionVerdict.markBorder(border, face, a, b, state);
    }
}
