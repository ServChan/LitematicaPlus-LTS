package org.lts.litematicaplus.render;

import net.minecraft.client.renderer.special.SpecialModelRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.engine.SectionSlot;
import org.lts.litematicaplus.world.PlacementVolume;

public final class GhostRenderer {
    static final int ANCHOR_MASK = 1023;
    private static final int FULL_BRIGHT = 15728880;
    private static final int MAX_SPECIALS_PER_FRAME = 384;
    private static final int MAX_RETRIES = 3;
    private static final double SPECIAL_RANGE_SQUARED = 72.0 * 72.0;
    private static final int SHOW_GHOST = 1;
    private static final int SHOW_OVERLAY = 2;
    private static final int HIDDEN_OVER_LOD = 4;
    private static final int SIDE_PLANES = FrustumIntersection.PLANE_MASK_NX | FrustumIntersection.PLANE_MASK_PX
            | FrustumIntersection.PLANE_MASK_NY | FrustumIntersection.PLANE_MASK_PY;
    private static final Identifier BLOCK_ATLAS = Identifier.withDefaultNamespace("textures/atlas/blocks.png");
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Comparator<SectionSlot> NEAR_TO_FAR = (a, b) -> Float.compare(a.sortDistance, b.sortDistance);
    private static final Comparator<SpecialSlot> SPECIALS_NEAR_TO_FAR = (a, b) -> Float.compare(a.slot().sortDistance,
            b.slot().sortDistance);
    private static final List<SectionSlot> GHOSTS = new ArrayList<>();
    private static final List<SectionSlot> OVERLAYS = new ArrayList<>();
    private static final List<SpecialSlot> SPECIALS = new ArrayList<>();
    private static final Matrix4f FRUSTUM_MATRIX = new Matrix4f();
    private static final FrustumIntersection FRUSTUM = new FrustumIntersection();
    private static final Object UNRESOLVED = new Object();
    private static Vec3 viewCamera;
    private static Vec3 viewFocus;
    private static double viewRange;
    private static boolean viewAnywhere;
    private static boolean viewHideOverLod;
    private static Object viewSodium = UNRESOLVED;
    private static boolean drawFailed;
    private static int drawRetries;
    private static Object drawFailedLevel;
    private static Object drawFailedModels;
    private static int lastDrawn;
    private static int lastHidden;
    private static int lastDepth;
    private static boolean submitFailed;
    private static int submitRetries;
    private static Object submitFailedLevel;
    private static Object submitFailedModels;

    private record SpecialSlot(SectionSlot slot, BlockState[] palette, PlacementVolume volume) {
    }

    private GhostRenderer() {
    }

    public static int lastHidden() {
        return lastHidden;
    }

    public static int lastDepth() {
        return lastDepth;
    }

    public static int lastDrawn() {
        return lastDrawn;
    }

    public static boolean failed() {
        return drawFailed || submitFailed;
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(GhostRenderer::collectSubmits);
    }

    private static boolean contextChanged(Minecraft minecraft, Object failedLevel, Object failedModels) {
        return minecraft.level != failedLevel || minecraft.getModelManager().getBlockStateModelSet() != failedModels;
    }

    public static void renderAfterLevel(CameraRenderState camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (drawFailed && drawRetries < MAX_RETRIES && contextChanged(minecraft, drawFailedLevel, drawFailedModels)) {
            drawFailed = false;
            drawRetries++;
            LitematicaPlusClient.LOGGER.warn("Retrying schematic rendering after a world or resource change ({} of {})",
                    drawRetries, MAX_RETRIES);
        }
        ExternalMap.beginFrame();
        try {
            if (drawFailed) {
                try {
                    GhostEngine.get().frame(minecraft, ExternalMap.focus(camera.pos));
                } catch (RuntimeException | LinkageError exception) {
                    LitematicaPlusClient.LOGGER.debug("Schematic engine update failed after a render failure", exception);
                }
                return;
            }
            try {
                draw(camera);
            } catch (RuntimeException | LinkageError exception) {
                drawFailed = true;
                drawFailedLevel = minecraft.level;
                drawFailedModels = minecraft.getModelManager().getBlockStateModelSet();
                LitematicaPlusClient.LOGGER.error("Schematic rendering failed and was switched off until the world or resources change",
                        exception);
            }
        } finally {
            GHOSTS.clear();
            OVERLAYS.clear();
            viewSodium = UNRESOLVED;
            ExternalMap.endFrame();
        }
    }

    private static void beginView(CameraRenderState camera, LitematicaPlusConfig config, VoxyLod.Frame lod) {
        viewCamera = camera.pos;
        viewFocus = ExternalMap.focus(camera.pos);
        viewAnywhere = GhostEngine.ghostsAnywhere();
        int configuredRange = TopView.ghostRange(config.renderDistance);
        viewRange = (double) configuredRange * configuredRange;
        viewHideOverLod = lod != null && LodDepth.failed();
        viewSodium = UNRESOLVED;
        FRUSTUM_MATRIX.set(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        FRUSTUM.set(FRUSTUM_MATRIX);
    }

    private static int classify(SectionSlot slot, LitematicaPlusConfig config, boolean ghost, boolean overlay) {
        if (!ghost && !overlay) {
            return 0;
        }
        float minX = (float) (slot.originX - viewCamera.x);
        float minY = (float) (slot.originY - viewCamera.y);
        float minZ = (float) (slot.originZ - viewCamera.z);
        float centerX = minX + 8.0F;
        float centerY = minY + 8.0F;
        float centerZ = minZ + 8.0F;
        float distance = centerX * centerX + centerY * centerY + centerZ * centerZ;
        double focusX = slot.originX + 8.0 - viewFocus.x;
        double focusY = slot.originY + 8.0 - viewFocus.y;
        double focusZ = slot.originZ + 8.0 - viewFocus.z;
        if (focusX * focusX + focusY * focusY + focusZ * focusZ > viewRange) {
            return 0;
        }
        slot.sortDistance = distance;
        int side = FRUSTUM.intersectAab(minX - 2.0F, minY - 2.0F, minZ - 2.0F,
                minX + 18.0F, minY + 18.0F, minZ + 18.0F, SIDE_PLANES);
        if (side != FrustumIntersection.INTERSECT && side != FrustumIntersection.INSIDE) {
            return 0;
        }
        int result = 0;
        if (viewHideOverLod && slot.chunkLoaded() && (ghost && !viewAnywhere || overlay && !config.overlayThroughWalls)
                && !SectionReady.ready(sodium(), slot.originX >> 4, slot.originY >> 4, slot.originZ >> 4)) {
            result |= HIDDEN_OVER_LOD;
            ghost = ghost && viewAnywhere;
            overlay = overlay && config.overlayThroughWalls;
        }
        if (ghost) {
            result |= SHOW_GHOST;
        }
        if (overlay) {
            result |= SHOW_OVERLAY;
        }
        return result;
    }

    private static Object sodium() {
        if (viewSodium == UNRESOLVED) {
            viewSodium = SectionReady.renderer();
        }
        return viewSodium;
    }

    private static void draw(CameraRenderState camera) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        GhostEngine engine = GhostEngine.get();
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 focus = ExternalMap.focus(camera.pos);
        boolean onMap = ExternalMap.active();
        boolean anywhere = GhostEngine.ghostsAnywhere();
        engine.frame(minecraft, focus);
        if (!config.renderEnabled || minecraft.player == null || minecraft.level == null || ExternalMap.hidden()) {
            return;
        }
        Vec3 cameraPos = camera.pos;
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        LodDepth.track(minecraft.level, target);
        VoxyLod.Frame lod = VoxyLod.frame();
        beginView(camera, config, lod);
        boolean hideOverLod = viewHideOverLod;
        int hidden = 0;
        for (PlacementRuntime runtime : engine.runtimes()) {
            if (!runtime.placement().enabled() || runtime.volume() == null) {
                continue;
            }
            List<SectionSlot> drawable = runtime.drawable();
            for (int slotIndex = 0; slotIndex < drawable.size(); slotIndex++) {
                SectionSlot slot = drawable.get(slotIndex);
                boolean ghost = slot.ghost() != null && config.renderGhostBlocks && (anywhere || slot.chunkLoaded());
                boolean overlay = (slot.fill() != null || slot.lines() != null) && config.renderOverlay && !onMap
                        && (slot.chunkLoaded() || config.overlayThroughWalls);
                int shown = classify(slot, config, ghost, overlay);
                if ((shown & HIDDEN_OVER_LOD) != 0) {
                    hidden++;
                }
                if ((shown & SHOW_GHOST) != 0) {
                    GHOSTS.add(slot);
                }
                if ((shown & SHOW_OVERLAY) != 0) {
                    OVERLAYS.add(slot);
                }
            }
        }
        GHOSTS.sort(NEAR_TO_FAR);
        lastDrawn = GHOSTS.size();
        lastHidden = hidden;
        lastDepth = lod == null ? 0 : hideOverLod ? 2 : 1;
        boolean boxes = BoxRenderer.prepare(minecraft, cameraPos);
        if (GHOSTS.isEmpty() && OVERLAYS.isEmpty() && !boxes) {
            return;
        }
        GpuTextureView colorView = target.getColorTextureView();
        if (colorView == null || target.getDepthTextureView() == null) {
            return;
        }
        GpuTextureView depthView = lod == null ? target.getDepthTextureView()
                : LodDepth.merge(target, lod);
        if (lod != null && LodDepth.failed()) {
            lastDepth = 2;
        }
        RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer quadBuffer = quadIndices.getBuffer(Math.max(6,
                Math.max(BoxRenderer.maxQuadIndices(), Math.max(engine.maxGhostIndices(), engine.maxFillIndices()))));
        IndexType quadType = quadIndices.type();
        RenderSystem.AutoStorageIndexBuffer lineIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.LINES);
        GpuBuffer lineBuffer = lineIndices.getBuffer(Math.max(6, Math.max(BoxRenderer.maxLineIndices(), engine.maxLineIndices())));
        IndexType lineType = lineIndices.type();
        Matrix4f modelView = new Matrix4f(camera.viewRotationMatrix);
        DynamicGpuData dynamic = RenderSystem.getDynamicUniforms();
        AbstractTexture atlas = minecraft.getTextureManager().getTexture(BLOCK_ATLAS);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "LitematicaPlus schematics", colorView, Optional.empty(), depthView, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            if (!GHOSTS.isEmpty()) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(GhostPipelines.GHOST));
                pass.setUniform("Sampler0", atlas.getTextureView(), atlas.getSampler());
                pass.setUniform("Sampler2", minecraft.gameRenderer.lightmap(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setIndexBuffer(quadBuffer, quadType);
                Vector4f color = new Vector4f(1.0F, 1.0F, 1.0F, config.ghostAlpha);
                boolean anchored = false;
                int anchorX = 0;
                int anchorY = 0;
                int anchorZ = 0;
                for (SectionSlot slot : GHOSTS) {
                    int slotX = slot.originX & ~ANCHOR_MASK;
                    int slotY = slot.originY & ~ANCHOR_MASK;
                    int slotZ = slot.originZ & ~ANCHOR_MASK;
                    if (!anchored || slotX != anchorX || slotY != anchorY || slotZ != anchorZ) {
                        anchored = true;
                        anchorX = slotX;
                        anchorY = slotY;
                        anchorZ = slotZ;
                        Vector3f offset = new Vector3f((float) (anchorX - cameraPos.x), (float) (anchorY - cameraPos.y),
                                (float) (anchorZ - cameraPos.z));
                        pass.setUniform("DynamicTransforms", dynamic.writeTransform(modelView, color, offset, IDENTITY));
                    }
                    pass.setVertexBuffer(0, slot.ghost().slice());
                    pass.drawIndexed(slot.ghostIndices(), 1, 0, 0, 0);
                }
            }
            if (!OVERLAYS.isEmpty()) {
                boolean xray = config.overlayThroughWalls;
                pass.setPipeline(RenderSystem.getCompiledPipeline(
                        xray ? GhostPipelines.OVERLAY_FILL_XRAY : GhostPipelines.OVERLAY_FILL));
                pass.setIndexBuffer(quadBuffer, quadType);
                for (SectionSlot slot : OVERLAYS) {
                    if (slot.fill() == null) {
                        continue;
                    }
                    pass.setUniform("DynamicTransforms", dynamic.writeTransform(translated(modelView, slot, cameraPos)));
                    pass.setVertexBuffer(0, slot.fill().slice());
                    pass.drawIndexed(slot.fillIndices(), 1, 0, 0, 0);
                }
                pass.setPipeline(RenderSystem.getCompiledPipeline(
                        xray ? GhostPipelines.OVERLAY_LINES_XRAY : GhostPipelines.OVERLAY_LINES));
                pass.setIndexBuffer(lineBuffer, lineType);
                for (SectionSlot slot : OVERLAYS) {
                    if (slot.lines() == null) {
                        continue;
                    }
                    pass.setUniform("DynamicTransforms", dynamic.writeTransform(translated(modelView, slot, cameraPos)));
                    pass.setVertexBuffer(0, slot.lines().slice());
                    pass.drawIndexed(slot.lineIndices(), 1, 0, 0, 0);
                }
            }
            if (boxes) {
                BoxRenderer.draw(pass, modelView, dynamic, quadBuffer, quadType, lineBuffer, lineType);
            }
        }
    }

    private static Matrix4f translated(Matrix4f modelView, SectionSlot slot, Vec3 cameraPos) {
        return new Matrix4f(modelView).translate((float) (slot.originX - cameraPos.x),
                (float) (slot.originY - cameraPos.y), (float) (slot.originZ - cameraPos.z));
    }

    private static void collectSubmits(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (submitFailed && submitRetries < MAX_RETRIES
                && contextChanged(minecraft, submitFailedLevel, submitFailedModels)) {
            submitFailed = false;
            submitRetries++;
            LitematicaPlusClient.LOGGER.warn("Retrying schematic block entity rendering after a world or resource change ({} of {})",
                    submitRetries, MAX_RETRIES);
        }
        if (submitFailed) {
            return;
        }
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!config.renderEnabled || !config.renderGhostBlocks || !config.renderBlockEntities) {
            return;
        }
        GhostEngine engine = GhostEngine.get();
        if (engine.runtimes().isEmpty()) {
            return;
        }
        ExternalMap.beginFrame();
        try {
            submitSpecials(context, engine, config, minecraft);
        } catch (RuntimeException | LinkageError exception) {
            submitFailed = true;
            submitFailedLevel = minecraft.level;
            submitFailedModels = minecraft.getModelManager().getBlockStateModelSet();
            LitematicaPlusClient.LOGGER.error("Schematic block entity rendering failed and was switched off until the world or resources change",
                    exception);
        } finally {
            SPECIALS.clear();
            viewSodium = UNRESOLVED;
            ExternalMap.endFrame();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void submit(SpecialModelRenderer<T> renderer, Object argument, PoseStack poseStack,
            SubmitNodeCollector collector) {
        renderer.submit((T) argument, poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false, 0);
    }

    private static void submitSpecials(LevelRenderContext context, GhostEngine engine, LitematicaPlusConfig config,
            Minecraft minecraft) {
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        if (poseStack == null || collector == null || minecraft.player == null || minecraft.level == null
                || ExternalMap.hidden()) {
            return;
        }
        SpecialArguments.beginFrame();
        engine.refreshModels(minecraft);
        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 cameraPos = camera.pos;
        beginView(camera, config, VoxyLod.frame());
        for (PlacementRuntime runtime : engine.runtimes()) {
            PlacementVolume volume = runtime.volume();
            if (volume == null || !runtime.placement().enabled()) {
                continue;
            }
            BlockState[] palette = volume.palette();
            List<SectionSlot> specialSlots = runtime.specialSlots();
            for (int slotIndex = 0; slotIndex < specialSlots.size(); slotIndex++) {
                SectionSlot slot = specialSlots.get(slotIndex);
                if (slot.specials().length == 0) {
                    continue;
                }
                double dx = slot.originX + 8.0 - cameraPos.x;
                double dy = slot.originY + 8.0 - cameraPos.y;
                double dz = slot.originZ + 8.0 - cameraPos.z;
                if (dx * dx + dy * dy + dz * dz > SPECIAL_RANGE_SQUARED) {
                    continue;
                }
                boolean ghost = viewAnywhere || slot.chunkLoaded();
                if ((classify(slot, config, ghost, false) & SHOW_GHOST) != 0) {
                    SPECIALS.add(new SpecialSlot(slot, palette, volume));
                }
            }
        }
        SPECIALS.sort(SPECIALS_NEAR_TO_FAR);
        int budget = MAX_SPECIALS_PER_FRAME;
        for (SpecialSlot special : SPECIALS) {
            SectionSlot slot = special.slot();
            BlockState[] palette = special.palette();
            int[] specials = slot.specials();
            for (int i = 0; i + 1 < specials.length; i += 2) {
                int index = specials[i];
                int paletteIndex = specials[i + 1];
                if (paletteIndex >= palette.length) {
                    continue;
                }
                BlockState state = palette[paletteIndex];
                SpecialBlocks.Entry entry = SpecialBlocks.get(state);
                if (entry == null) {
                    continue;
                }
                int x = slot.originX + (index & 15);
                int y = slot.originY + (index >> 8);
                int z = slot.originZ + (index >> 4 & 15);
                Object argument = SpecialArguments.get(special.volume(), x, y, z, state, entry.renderer());
                poseStack.pushPose();
                poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                if (entry.transform() != null) {
                    poseStack.mulPose(entry.transform());
                }
                submit(entry.renderer(), argument, poseStack, collector);
                poseStack.popPose();
                if (--budget <= 0) {
                    return;
                }
            }
        }
    }
}
