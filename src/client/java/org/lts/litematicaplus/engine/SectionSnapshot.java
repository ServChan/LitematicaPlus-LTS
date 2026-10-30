package org.lts.litematicaplus.engine;

import java.util.Arrays;
import java.util.Queue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.lts.litematicaplus.verify.SectionVerdict;
import org.lts.litematicaplus.world.GhostSection;

final class SectionSnapshot {
    private static final int FACES = 6;

    private final PalettedContainer<BlockState> center;
    private final PalettedContainer<BlockState>[] sides;
    private final int closedFaces;
    private final boolean borderIncomplete;
    private final int unknownFaces;

    private SectionSnapshot(PalettedContainer<BlockState> center, PalettedContainer<BlockState>[] sides, int closedFaces,
            boolean borderIncomplete, int unknownFaces) {
        this.center = center;
        this.sides = sides;
        this.closedFaces = closedFaces;
        this.borderIncomplete = borderIncomplete;
        this.unknownFaces = unknownFaces;
    }

    @SuppressWarnings("unchecked")
    static SectionSnapshot capture(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
        LevelChunk chunk = level.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return null;
        }
        PalettedContainer<BlockState>[] sides = new PalettedContainer[FACES];
        int closed = 0;
        boolean incomplete = false;
        int unknown = 0;
        for (int face = 0; face < FACES; face++) {
            int neighbourX = sectionX + (face == SectionVerdict.FACE_WEST ? -1 : face == SectionVerdict.FACE_EAST ? 1 : 0);
            int neighbourY = sectionY + (face == SectionVerdict.FACE_DOWN ? -1 : face == SectionVerdict.FACE_UP ? 1 : 0);
            int neighbourZ = sectionZ + (face == SectionVerdict.FACE_NORTH ? -1 : face == SectionVerdict.FACE_SOUTH ? 1 : 0);
            LevelChunk owner = neighbourX == sectionX && neighbourZ == sectionZ ? chunk
                    : level.getChunkSource().getChunk(neighbourX, neighbourZ, ChunkStatus.FULL, false);
            if (owner == null) {
                closed |= 1 << face;
                incomplete = true;
                unknown |= 1 << face;
            } else if (neighbourY < owner.getMinSectionY()) {
                closed |= 1 << face;
            } else {
                sides[face] = copy(owner, neighbourY);
            }
        }
        return new SectionSnapshot(copy(chunk, sectionY), sides, closed, incomplete, unknown);
    }

    private static PalettedContainer<BlockState> copy(LevelChunk chunk, int sectionY) {
        if (sectionY < chunk.getMinSectionY() || sectionY > chunk.getMaxSectionY()) {
            return null;
        }
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
        return section == null || section.hasOnlyAir() ? null : section.getStates().copy();
    }

    boolean borderIncomplete() {
        return this.borderIncomplete;
    }

    int unknownFaces() {
        return this.unknownFaces;
    }

    BlockState[] world(Queue<BlockState[]> pool) {
        BlockState[] target = pool.poll();
        if (target == null) {
            target = new BlockState[GhostSection.VOLUME];
        }
        if (this.center == null) {
            Arrays.fill(target, Blocks.AIR.defaultBlockState());
            return target;
        }
        int index = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    target[index++] = this.center.get(x, y, z);
                }
            }
        }
        return target;
    }

    long[] border() {
        long[] bits = new long[SectionVerdict.BORDER_LONGS];
        for (int face = 0; face < FACES; face++) {
            if ((this.closedFaces & 1 << face) != 0) {
                SectionVerdict.closeBorderFace(bits, face);
                continue;
            }
            PalettedContainer<BlockState> side = this.sides[face];
            if (side == null) {
                continue;
            }
            int fixed = (face & 1) == 0 ? 15 : 0;
            for (int b = 0; b < 16; b++) {
                for (int a = 0; a < 16; a++) {
                    BlockState state = face < 2 ? side.get(a, fixed, b)
                            : face < 4 ? side.get(a, b, fixed) : side.get(fixed, b, a);
                    SectionVerdict.markBorder(bits, face, a, b, state);
                }
            }
        }
        return bits;
    }
}
