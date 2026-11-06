package org.lts.litematicaplus.verify;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.lts.litematicaplus.LitematicaPlusConfig;

public final class ShelfHighlight {
    public static final int STOCKED = 0xFF57C97A;
    public static final int EMPTY = 0xFF6E6E7A;
    private static final int REFRESH_TICKS = 10;
    private static final int LIMIT = 512;

    public record Shelf(BlockPos pos, int filled) {
        public boolean stocked() {
            return this.filled > 0;
        }
    }

    private static List<Shelf> shelves = List.of();
    private static int ticks;

    private ShelfHighlight() {
    }

    public static List<Shelf> shelves() {
        return shelves;
    }

    public static void tick(Minecraft minecraft) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!config.shelfHighlight || minecraft.level == null || minecraft.player == null) {
            shelves = List.of();
            return;
        }
        if (++ticks % REFRESH_TICKS != 0 && !shelves.isEmpty()) {
            return;
        }
        shelves = scan(minecraft.level, minecraft.player.blockPosition(), config.shelfRange);
    }

    static List<Shelf> scan(ClientLevel level, BlockPos eye, int range) {
        List<Shelf> found = new ArrayList<>();
        double limit = (double) range * range;
        int centerX = SectionPos.blockToSectionCoord(eye.getX());
        int centerZ = SectionPos.blockToSectionCoord(eye.getZ());
        int chunks = range / 16 + 1;
        for (int cx = centerX - chunks; cx <= centerX + chunks; cx++) {
            for (int cz = centerZ - chunks; cz <= centerZ + chunks; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof ShelfBlockEntity shelf)) {
                        continue;
                    }
                    BlockPos pos = entity.getBlockPos();
                    if (pos.distToCenterSqr(eye.getX() + 0.5, eye.getY() + 0.5, eye.getZ() + 0.5) > limit) {
                        continue;
                    }
                    int filled = 0;
                    for (ItemStack stack : shelf.getItems()) {
                        if (!stack.isEmpty()) {
                            filled++;
                        }
                    }
                    found.add(new Shelf(pos.immutable(), filled));
                    if (found.size() >= LIMIT) {
                        return List.copyOf(found);
                    }
                }
            }
        }
        return List.copyOf(found);
    }
}
