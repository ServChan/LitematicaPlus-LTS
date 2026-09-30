package org.lts.litematicaplus.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.SectionVerdict;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.WorldCache;
import org.lts.litematicaplus.world.PlacementVolume;
import org.lwjgl.system.MemoryUtil;

public final class SectionMesher {
    private static final ThreadLocal<SectionMesher> LOCAL = ThreadLocal.withInitial(SectionMesher::new);
    private static final float EXPAND = 0.002F;
    private static final float LINE_WIDTH = 2.5F;
    private static final byte MISSING = (byte) MismatchType.MISSING.ordinal();
    private static final byte EXTRA = (byte) MismatchType.EXTRA.ordinal();
    private static final byte WRONG_BLOCK = (byte) MismatchType.WRONG_BLOCK.ordinal();
    private static final byte WRONG_STATE = (byte) MismatchType.WRONG_STATE.ordinal();
    private static final byte UNKNOWN = (byte) MismatchType.UNKNOWN.ordinal();
    private static final int DOWN = 1;
    private static final int UP = 2;
    private static final int NORTH = 4;
    private static final int SOUTH = 8;
    private static final int WEST = 16;
    private static final int EAST = 32;

    private final BlockVertexWriter ghost = new BlockVertexWriter(1 << 18);
    private final NativeWriter fill = new NativeWriter(1 << 14);
    private final NativeWriter lines = new NativeWriter(1 << 14);
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final IntArrayList specials = new IntArrayList();

    private SectionMesher() {
    }

    public static MeshResult run(SectionJob job) {
        try {
            return LOCAL.get().build(job);
        } catch (Throwable throwable) {
            return MeshResult.failed(job, throwable);
        }
    }

    private MeshResult build(SectionJob live) {
        SectionJob.Options options = live.options();
        PlacementVolume volume = live.volume();
        GhostSection section = live.section();
        SectionJob job = live;
        if (live.world() == null && live.remembered() == null && options.worldCache()) {
            BlockState[] cached = WorldCache.section(live.level(), SectionPos.x(live.sectionKey()),
                    SectionPos.y(live.sectionKey()), SectionPos.z(live.sectionKey()));
            if (cached != null) {
                job = new SectionJob(live.sectionKey(), live.generation(), live.revision(), section, volume,
                        live.specialByPalette(), cached, null, null, live.layer(), live.level(), live.blockModels(),
                        live.fluidModels(), live.blockColors(), options);
            }
        }
        SectionVerdict verdict = job.world() == null && job.remembered() != null ? job.remembered()
                : SectionVerdict.compute(section, volume.globalIds(), volume.solid(), job.world(), options.lenientStates(),
                        options.trustHidden(), job.border());
        int sectionX = SectionPos.x(job.sectionKey());
        int sectionY = SectionPos.y(job.sectionKey());
        int sectionZ = SectionPos.z(job.sectionKey());
        GhostBlockView view = new GhostBlockView(volume, job.layer(), job.level());
        view.bind(sectionX, sectionY, sectionZ);
        ByteBuffer ghostData = null;
        ByteBuffer fillData = null;
        ByteBuffer lineData = null;
        int ghostVertices = 0;
        int fillVertices = 0;
        int lineVertices = 0;
        this.specials.clear();
        boolean inLayer = job.layer().intersects(sectionX << 4, sectionY << 4, sectionZ << 4,
                (sectionX << 4) + 15, (sectionY << 4) + 15, (sectionZ << 4) + 15);
        try {
            if (inLayer && options.ghostBlocks() && section.solidCount() > 0) {
                this.ghost.reset();
                this.ghost.offset((sectionX << 4) & GhostRenderer.ANCHOR_MASK, (sectionY << 4) & GhostRenderer.ANCHOR_MASK,
                        (sectionZ << 4) & GhostRenderer.ANCHOR_MASK);
                buildGhost(job, verdict, view, sectionX << 4, sectionY << 4, sectionZ << 4);
                ghostVertices = this.ghost.vertices();
                ghostData = this.ghost.copy();
            }
            if (inLayer && options.overlay() && verdict.worldKnown() && verdict.errors() > 0
                    && (live.world() != null || options.throughWalls())) {
                this.fill.reset();
                this.lines.reset();
                buildOverlay(job, verdict, view, sectionX << 4, sectionY << 4, sectionZ << 4);
                fillVertices = this.fill.size() / 16;
                lineVertices = this.lines.size() / 24;
                fillData = this.fill.copy();
                lineData = this.lines.copy();
            }
        } catch (Throwable throwable) {
            MemoryUtil.memFree(ghostData);
            MemoryUtil.memFree(fillData);
            MemoryUtil.memFree(lineData);
            throw throwable;
        }
        return new MeshResult(job.sectionKey(), job.generation(), job.revision(), verdict,
                this.specials.toIntArray(), ghostData, ghostVertices, fillData, fillVertices, lineData, lineVertices, null);
    }

    private void buildGhost(SectionJob job, SectionVerdict verdict, GhostBlockView view, int originX, int originY,
            int originZ) {
        PlacementVolume volume = job.volume();
        BlockState[] palette = volume.palette();
        boolean[] solid = volume.solid();
        boolean[] special = job.specialByPalette();
        short[] raw = job.section().raw();
        byte[] categories = verdict.categories();
        LayerRange layer = job.layer();
        boolean colliding = job.options().renderColliding();
        BlockModelLighter.enableCaching();
        try {
            ModelBlockRenderer blockRenderer = new ModelBlockRenderer(true, true, job.blockColors());
            FluidRenderer fluidRenderer = new FluidRenderer(job.fluidModels());
            BlockQuadOutput output = this.ghost::putBlockBakedQuad;
            FluidRenderer.Output fluidOutput = ignored -> this.ghost;
            for (int index = 0; index < GhostSection.VOLUME; index++) {
                short value = raw[index];
                if (value == GhostSection.OUTSIDE) {
                    continue;
                }
                int paletteIndex = value & 0xFFFF;
                if (!solid[paletteIndex]) {
                    continue;
                }
                byte category = categories[index];
                boolean render = category == MISSING || category == UNKNOWN
                        || colliding && (category == WRONG_BLOCK || category == WRONG_STATE);
                if (!render) {
                    continue;
                }
                int x = index & 15;
                int z = index >> 4 & 15;
                int y = index >> 8;
                if (!layer.contains(originX + x, originY + y, originZ + z)) {
                    continue;
                }
                BlockState state = palette[paletteIndex];
                this.cursor.set(originX + x, originY + y, originZ + z);
                FluidState fluidState = state.getFluidState();
                if (!fluidState.isEmpty()) {
                    fluidRenderer.tesselate(view, this.cursor, fluidOutput, state, fluidState);
                }
                if (state.getRenderShape() == RenderShape.MODEL) {
                    blockRenderer.tesselateBlock(output, x, y, z, view, this.cursor, state,
                            job.blockModels().get(state), state.getSeed(this.cursor));
                }
                if (special != null && paletteIndex < special.length && special[paletteIndex]) {
                    this.specials.add(index);
                    this.specials.add(paletteIndex);
                }
            }
        } finally {
            BlockModelLighter.clearCache();
        }
    }

    private void buildOverlay(SectionJob job, SectionVerdict verdict, GhostBlockView view, int originX, int originY,
            int originZ) {
        SectionJob.Options options = job.options();
        byte[] categories = verdict.categories();
        LayerRange layer = job.layer();
        boolean layered = !layer.isAll();
        BlockState[] world = job.world();
        long[] border = job.border();
        boolean surfaceOnly = !options.throughWalls() && world != null;
        for (int index = 0; index < GhostSection.VOLUME; index++) {
            byte category = categories[index];
            if (!shown(category, options)) {
                continue;
            }
            int x = index & 15;
            int z = index >> 4 & 15;
            int y = index >> 8;
            if (layered && !layer.contains(originX + x, originY + y, originZ + z)) {
                continue;
            }
            int exposed = 0;
            if (!same(categories, view, category, x, y - 1, z, originX, originY, originZ, layer, layered)) {
                exposed |= DOWN;
            }
            if (!same(categories, view, category, x, y + 1, z, originX, originY, originZ, layer, layered)) {
                exposed |= UP;
            }
            if (!same(categories, view, category, x, y, z - 1, originX, originY, originZ, layer, layered)) {
                exposed |= NORTH;
            }
            if (!same(categories, view, category, x, y, z + 1, originX, originY, originZ, layer, layered)) {
                exposed |= SOUTH;
            }
            if (!same(categories, view, category, x - 1, y, z, originX, originY, originZ, layer, layered)) {
                exposed |= WEST;
            }
            if (!same(categories, view, category, x + 1, y, z, originX, originY, originZ, layer, layered)) {
                exposed |= EAST;
            }
            if (exposed != 0 && surfaceOnly) {
                if ((exposed & DOWN) != 0 && SectionVerdict.occludes(world, border, x, y - 1, z)) {
                    exposed &= ~DOWN;
                }
                if ((exposed & UP) != 0 && SectionVerdict.occludes(world, border, x, y + 1, z)) {
                    exposed &= ~UP;
                }
                if ((exposed & NORTH) != 0 && SectionVerdict.occludes(world, border, x, y, z - 1)) {
                    exposed &= ~NORTH;
                }
                if ((exposed & SOUTH) != 0 && SectionVerdict.occludes(world, border, x, y, z + 1)) {
                    exposed &= ~SOUTH;
                }
                if ((exposed & WEST) != 0 && SectionVerdict.occludes(world, border, x - 1, y, z)) {
                    exposed &= ~WEST;
                }
                if ((exposed & EAST) != 0 && SectionVerdict.occludes(world, border, x + 1, y, z)) {
                    exposed &= ~EAST;
                }
            }
            if (exposed == 0) {
                continue;
            }
            int rgb = MismatchType.VALUES[category].color() & 0xFFFFFF;
            float fillAlpha = category == MISSING ? options.fillAlpha() * 0.45F : options.fillAlpha();
            if (fillAlpha > 0.004F) {
                emitFill(x, y, z, exposed, abgr(rgb, fillAlpha));
            }
            if (options.lineAlpha() > 0.004F) {
                emitEdges(x, y, z, exposed, abgr(rgb, options.lineAlpha()));
            }
        }
    }

    private static boolean shown(byte category, SectionJob.Options options) {
        if (category == MISSING) {
            return options.showMissing();
        }
        if (category == EXTRA) {
            return options.showExtra();
        }
        if (category == WRONG_BLOCK) {
            return options.showWrongBlock();
        }
        if (category == WRONG_STATE) {
            return options.showWrongState();
        }
        return false;
    }

    private static boolean same(byte[] categories, GhostBlockView view, byte category, int x, int y, int z,
            int originX, int originY, int originZ, LayerRange layer, boolean layered) {
        if (layered && !layer.contains(originX + x, originY + y, originZ + z)) {
            return false;
        }
        if ((x | y | z) >= 0 && x < 16 && y < 16 && z < 16) {
            return categories[GhostSection.index(x, y, z)] == category;
        }
        return category == MISSING && !view.stateAt(originX + x, originY + y, originZ + z).isAir();
    }

    private static int abgr(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return a << 24 | (rgb & 0xFF) << 16 | rgb & 0xFF00 | rgb >> 16 & 0xFF;
    }

    private void emitFill(int x, int y, int z, int exposed, int color) {
        float x0 = x - EXPAND;
        float y0 = y - EXPAND;
        float z0 = z - EXPAND;
        float x1 = x + 1 + EXPAND;
        float y1 = y + 1 + EXPAND;
        float z1 = z + 1 + EXPAND;
        this.fill.ensure(6 * 4 * 16);
        if ((exposed & DOWN) != 0) {
            this.fill.positionColor(x0, y0, z0, color);
            this.fill.positionColor(x1, y0, z0, color);
            this.fill.positionColor(x1, y0, z1, color);
            this.fill.positionColor(x0, y0, z1, color);
        }
        if ((exposed & UP) != 0) {
            this.fill.positionColor(x0, y1, z0, color);
            this.fill.positionColor(x0, y1, z1, color);
            this.fill.positionColor(x1, y1, z1, color);
            this.fill.positionColor(x1, y1, z0, color);
        }
        if ((exposed & NORTH) != 0) {
            this.fill.positionColor(x0, y0, z0, color);
            this.fill.positionColor(x0, y1, z0, color);
            this.fill.positionColor(x1, y1, z0, color);
            this.fill.positionColor(x1, y0, z0, color);
        }
        if ((exposed & SOUTH) != 0) {
            this.fill.positionColor(x0, y0, z1, color);
            this.fill.positionColor(x1, y0, z1, color);
            this.fill.positionColor(x1, y1, z1, color);
            this.fill.positionColor(x0, y1, z1, color);
        }
        if ((exposed & WEST) != 0) {
            this.fill.positionColor(x0, y0, z0, color);
            this.fill.positionColor(x0, y0, z1, color);
            this.fill.positionColor(x0, y1, z1, color);
            this.fill.positionColor(x0, y1, z0, color);
        }
        if ((exposed & EAST) != 0) {
            this.fill.positionColor(x1, y0, z0, color);
            this.fill.positionColor(x1, y1, z0, color);
            this.fill.positionColor(x1, y1, z1, color);
            this.fill.positionColor(x1, y0, z1, color);
        }
    }

    private void emitEdges(int x, int y, int z, int exposed, int color) {
        float x0 = x - EXPAND;
        float y0 = y - EXPAND;
        float z0 = z - EXPAND;
        float x1 = x + 1 + EXPAND;
        float y1 = y + 1 + EXPAND;
        float z1 = z + 1 + EXPAND;
        this.lines.ensure(12 * 4 * 24);
        edge(exposed, DOWN | NORTH, x0, y0, z0, x1, y0, z0, color);
        edge(exposed, DOWN | SOUTH, x0, y0, z1, x1, y0, z1, color);
        edge(exposed, UP | NORTH, x0, y1, z0, x1, y1, z0, color);
        edge(exposed, UP | SOUTH, x0, y1, z1, x1, y1, z1, color);
        edge(exposed, WEST | NORTH, x0, y0, z0, x0, y1, z0, color);
        edge(exposed, EAST | NORTH, x1, y0, z0, x1, y1, z0, color);
        edge(exposed, WEST | SOUTH, x0, y0, z1, x0, y1, z1, color);
        edge(exposed, EAST | SOUTH, x1, y0, z1, x1, y1, z1, color);
        edge(exposed, WEST | DOWN, x0, y0, z0, x0, y0, z1, color);
        edge(exposed, EAST | DOWN, x1, y0, z0, x1, y0, z1, color);
        edge(exposed, WEST | UP, x0, y1, z0, x0, y1, z1, color);
        edge(exposed, EAST | UP, x1, y1, z0, x1, y1, z1, color);
    }

    private void edge(int exposed, int faces, float ax, float ay, float az, float bx, float by, float bz, int color) {
        if ((exposed & faces) != faces) {
            return;
        }
        byte nx = (byte) (bx > ax ? 127 : 0);
        byte ny = (byte) (by > ay ? 127 : 0);
        byte nz = (byte) (bz > az ? 127 : 0);
        this.lines.lineVertex(ax, ay, az, color, nx, ny, nz, LINE_WIDTH);
        this.lines.lineVertex(ax, ay, az, color, nx, ny, nz, LINE_WIDTH);
        this.lines.lineVertex(bx, by, bz, color, nx, ny, nz, LINE_WIDTH);
        this.lines.lineVertex(bx, by, bz, color, nx, ny, nz, LINE_WIDTH);
    }
}
