package org.lts.litematicaplus.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.selection.AreaBox;
import org.lts.litematicaplus.selection.SelectionManager;
import org.lts.litematicaplus.tool.EntityAssist;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.world.PlacementVolume;

public final class BoxRenderer {
    private static final int PLACEMENT_COLOR = 0xFF2FC7FF;
    private static final int PLACEMENT_SELECTED_COLOR = 0xFF00E5FF;
    private static final int PLACEMENT_LOCKED_COLOR = 0xFF8FA7BF;
    private static final int ORIGIN_COLOR = 0xFFFFD166;
    private static final int SELECTION_COLOR = 0xFFE0E0E0;
    private static final int SELECTION_ACTIVE_COLOR = 0xFF55FF55;
    private static final int SELECTION_ACTIVE_FILL = 0x2055FF55;
    private static final int CORNER_FIRST_COLOR = 0xFFFF5555;
    private static final int CORNER_SECOND_COLOR = 0xFF5B8CFF;
    private static final double MAX_DISTANCE = 768.0;
    private static final double TOP_VIEW_DISTANCE = 2560.0;
    private static final NativeWriter LINES = new NativeWriter(1 << 12);
    private static final NativeWriter FILLS = new NativeWriter(1 << 12);
    private static final NativeWriter XRAY_LINES = new NativeWriter(1 << 10);
    private static final NativeWriter XRAY_FILLS = new NativeWriter(1 << 10);
    private static final DynamicMesh LINE_MESH = new DynamicMesh("LitematicaPlus box lines", 24);
    private static final DynamicMesh FILL_MESH = new DynamicMesh("LitematicaPlus box fills", 16);
    private static final DynamicMesh XRAY_LINE_MESH = new DynamicMesh("LitematicaPlus focus lines", 24);
    private static final DynamicMesh XRAY_FILL_MESH = new DynamicMesh("LitematicaPlus focus fills", 16);
    private static BlockPos focus;
    private static int focusColor = 0xFFFFFFFF;

    private BoxRenderer() {
    }

    public static void setFocus(BlockPos pos, int color) {
        focus = pos == null ? null : pos.immutable();
        focusColor = color;
    }

    static boolean prepare(Minecraft minecraft, Vec3 camera) {
        LINES.reset();
        FILLS.reset();
        XRAY_LINES.reset();
        XRAY_FILLS.reset();
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        boolean visible = !minecraft.gui.hud.isHidden();
        if (visible && minecraft.player != null && focus != null) {
            double pad = 0.04;
            box(XRAY_LINES, XRAY_FILLS, camera, focus.getX() - pad, focus.getY() - pad, focus.getZ() - pad,
                    focus.getX() + 1 + pad, focus.getY() + 1 + pad, focus.getZ() + 1 + pad, focusColor, 4.0F,
                    focusColor & 0x50FFFFFF);
            Vec3 look = minecraft.player.getViewVector(1.0F);
            line(XRAY_LINES, (float) (look.x * 0.6), (float) (look.y * 0.6 - 0.12), (float) (look.z * 0.6),
                    (float) (focus.getX() + 0.5 - camera.x), (float) (focus.getY() + 0.5 - camera.y),
                    (float) (focus.getZ() + 0.5 - camera.z), abgr(focusColor), 2.0F);
        }
        if (visible && config.renderPlacementBoxes) {
            SchematicPlacement selected = PlacementManager.get().selected();
            for (PlacementRuntime runtime : GhostEngine.get().runtimes()) {
                SchematicPlacement placement = runtime.placement();
                Schematic schematic = placement.schematic();
                if (schematic == null || !placement.enabled()) {
                    continue;
                }
                boolean active = placement == selected;
                int color = placement.locked() ? PLACEMENT_LOCKED_COLOR : active ? PLACEMENT_SELECTED_COLOR : PLACEMENT_COLOR;
                for (PlacementVolume.RegionBox region : PlacementVolume.regionBoxes(schematic, placement.transform(),
                        placement.disabledRegions())) {
                    if (!near(camera, region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(),
                            region.maxZ())) {
                        continue;
                    }
                    box(LINES, null, camera, region.minX() - 0.01, region.minY() - 0.01, region.minZ() - 0.01,
                            region.maxX() + 1.01, region.maxY() + 1.01, region.maxZ() + 1.01, color, active ? 2.5F : 1.5F, 0);
                }
                if (active) {
                    box(LINES, null, camera, placement.x() - 0.03, placement.y() - 0.03, placement.z() - 0.03,
                            placement.x() + 1.03, placement.y() + 1.03, placement.z() + 1.03, ORIGIN_COLOR, 3.0F, 0);
                }
            }
        }
        if (visible && config.entityPlacement) {
            for (EntityAssist.Ghost ghost : EntityAssist.visible()) {
                AABB bounds = ghost.box();
                int color = switch (ghost.status()) {
                    case INCOMPLETE -> MismatchType.WRONG_STATE.color();
                    case BLOCKED -> MismatchType.WRONG_BLOCK.color();
                    default -> MismatchType.MISSING.color();
                };
                box(LINES, FILLS, camera, bounds.minX - 0.01, bounds.minY - 0.01, bounds.minZ - 0.01, bounds.maxX + 0.01,
                        bounds.maxY + 0.01, bounds.maxZ + 0.01, color, 2.0F, color & 0x38FFFFFF);
            }
        }
        if (visible && config.renderSelectionBoxes) {
            SelectionManager selection = SelectionManager.get();
            List<AreaBox> boxes = selection.boxes();
            for (int i = 0; i < boxes.size(); i++) {
                AreaBox area = boxes.get(i);
                if (area.isEmpty() || !near(camera, area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
                    continue;
                }
                boolean active = i == selection.selectedIndex();
                box(LINES, active ? FILLS : null, camera, area.minX() - 0.006, area.minY() - 0.006, area.minZ() - 0.006,
                        area.maxX() + 1.006, area.maxY() + 1.006, area.maxZ() + 1.006,
                        active ? SELECTION_ACTIVE_COLOR : SELECTION_COLOR, active ? 2.5F : 1.5F, SELECTION_ACTIVE_FILL);
                corner(camera, area.corner1(), CORNER_FIRST_COLOR);
                corner(camera, area.corner2(), CORNER_SECOND_COLOR);
            }
        }
        LINE_MESH.upload(LINES);
        FILL_MESH.upload(FILLS);
        XRAY_LINE_MESH.upload(XRAY_LINES);
        XRAY_FILL_MESH.upload(XRAY_FILLS);
        return !LINE_MESH.isEmpty() || !FILL_MESH.isEmpty() || !XRAY_LINE_MESH.isEmpty() || !XRAY_FILL_MESH.isEmpty();
    }

    public static void close() {
        LINE_MESH.close();
        FILL_MESH.close();
        XRAY_LINE_MESH.close();
        XRAY_FILL_MESH.close();
    }

    static int maxQuadIndices() {
        return Math.max(FILL_MESH.quadIndices(), XRAY_FILL_MESH.quadIndices());
    }

    static int maxLineIndices() {
        return Math.max(LINE_MESH.quadIndices(), XRAY_LINE_MESH.quadIndices());
    }

    static void draw(RenderPass pass, Matrix4f modelView, DynamicGpuData dynamic, GpuBuffer quadBuffer, IndexType quadType,
            GpuBuffer lineBuffer, IndexType lineType) {
        drawMesh(pass, modelView, dynamic, FILL_MESH, GhostPipelines.OVERLAY_FILL, quadBuffer, quadType);
        drawMesh(pass, modelView, dynamic, LINE_MESH, GhostPipelines.OVERLAY_LINES, lineBuffer, lineType);
        drawMesh(pass, modelView, dynamic, XRAY_FILL_MESH, GhostPipelines.OVERLAY_FILL_XRAY, quadBuffer, quadType);
        drawMesh(pass, modelView, dynamic, XRAY_LINE_MESH, GhostPipelines.OVERLAY_LINES_XRAY, lineBuffer, lineType);
    }

    private static void drawMesh(RenderPass pass, Matrix4f modelView, DynamicGpuData dynamic, DynamicMesh mesh,
            RenderPipeline pipeline, GpuBuffer indexBuffer, IndexType indexType) {
        if (mesh.isEmpty()) {
            return;
        }
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        pass.setIndexBuffer(indexBuffer, indexType);
        pass.setUniform("DynamicTransforms", dynamic.writeTransform(new Matrix4f(modelView)));
        pass.setVertexBuffer(0, mesh.slice());
        pass.drawIndexed(mesh.quadIndices(), 1, 0, 0, 0);
    }

    private static void corner(Vec3 camera, BlockPos pos, int color) {
        if (pos != null) {
            box(LINES, null, camera, pos.getX() - 0.012, pos.getY() - 0.012, pos.getZ() - 0.012,
                    pos.getX() + 1.012, pos.getY() + 1.012, pos.getZ() + 1.012, color, 3.0F, 0);
        }
    }

    private static boolean near(Vec3 camera, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        double dx = Math.max(Math.max(minX - camera.x, 0.0), camera.x - maxX - 1.0);
        double dy = Math.max(Math.max(minY - camera.y, 0.0), camera.y - maxY - 1.0);
        double dz = Math.max(Math.max(minZ - camera.z, 0.0), camera.z - maxZ - 1.0);
        double limit = TopView.active() ? TOP_VIEW_DISTANCE : MAX_DISTANCE;
        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }

    private static int abgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF;
    }

    private static void box(NativeWriter lines, NativeWriter fills, Vec3 camera, double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ, int color, float width, int fillColor) {
        float x0 = (float) (minX - camera.x);
        float y0 = (float) (minY - camera.y);
        float z0 = (float) (minZ - camera.z);
        float x1 = (float) (maxX - camera.x);
        float y1 = (float) (maxY - camera.y);
        float z1 = (float) (maxZ - camera.z);
        int stroke = abgr(color);
        line(lines, x0, y0, z0, x1, y0, z0, stroke, width);
        line(lines, x0, y0, z1, x1, y0, z1, stroke, width);
        line(lines, x0, y1, z0, x1, y1, z0, stroke, width);
        line(lines, x0, y1, z1, x1, y1, z1, stroke, width);
        line(lines, x0, y0, z0, x0, y1, z0, stroke, width);
        line(lines, x1, y0, z0, x1, y1, z0, stroke, width);
        line(lines, x0, y0, z1, x0, y1, z1, stroke, width);
        line(lines, x1, y0, z1, x1, y1, z1, stroke, width);
        line(lines, x0, y0, z0, x0, y0, z1, stroke, width);
        line(lines, x1, y0, z0, x1, y0, z1, stroke, width);
        line(lines, x0, y1, z0, x0, y1, z1, stroke, width);
        line(lines, x1, y1, z0, x1, y1, z1, stroke, width);
        if (fills == null || fillColor >>> 24 == 0) {
            return;
        }
        int fill = abgr(fillColor);
        fills.ensure(24 * 16);
        fills.positionColor(x0, y0, z0, fill);
        fills.positionColor(x1, y0, z0, fill);
        fills.positionColor(x1, y0, z1, fill);
        fills.positionColor(x0, y0, z1, fill);
        fills.positionColor(x0, y1, z0, fill);
        fills.positionColor(x0, y1, z1, fill);
        fills.positionColor(x1, y1, z1, fill);
        fills.positionColor(x1, y1, z0, fill);
        fills.positionColor(x0, y0, z0, fill);
        fills.positionColor(x0, y1, z0, fill);
        fills.positionColor(x1, y1, z0, fill);
        fills.positionColor(x1, y0, z0, fill);
        fills.positionColor(x0, y0, z1, fill);
        fills.positionColor(x1, y0, z1, fill);
        fills.positionColor(x1, y1, z1, fill);
        fills.positionColor(x0, y1, z1, fill);
        fills.positionColor(x0, y0, z0, fill);
        fills.positionColor(x0, y0, z1, fill);
        fills.positionColor(x0, y1, z1, fill);
        fills.positionColor(x0, y1, z0, fill);
        fills.positionColor(x1, y0, z0, fill);
        fills.positionColor(x1, y1, z0, fill);
        fills.positionColor(x1, y1, z1, fill);
        fills.positionColor(x1, y0, z1, fill);
    }

    private static void line(NativeWriter lines, float ax, float ay, float az, float bx, float by, float bz, int color,
            float width) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-5F) {
            return;
        }
        byte nx = (byte) Math.round(dx / length * 127.0F);
        byte ny = (byte) Math.round(dy / length * 127.0F);
        byte nz = (byte) Math.round(dz / length * 127.0F);
        lines.ensure(4 * 24);
        lines.lineVertex(ax, ay, az, color, nx, ny, nz, width);
        lines.lineVertex(ax, ay, az, color, nx, ny, nz, width);
        lines.lineVertex(bx, by, bz, color, nx, ny, nz, width);
        lines.lineVertex(bx, by, bz, color, nx, ny, nz, width);
    }
}
