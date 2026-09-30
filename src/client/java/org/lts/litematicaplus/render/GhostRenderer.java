package org.lts.litematicaplus.render;

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
    private static final int FULL_BRIGHT = 15728880;
    private static final int MAX_SPECIALS_PER_FRAME = 384;
    private static final double SPECIAL_RANGE_SQUARED = 72.0 * 72.0;
    private static final int SIDE_PLANES = FrustumIntersection.PLANE_MASK_NX | FrustumIntersection.PLANE_MASK_PX
            | FrustumIntersection.PLANE_MASK_NY | FrustumIntersection.PLANE_MASK_PY;
    private static final Identifier BLOCK_ATLAS = Identifier.withDefaultNamespace("textures/atlas/blocks.png");
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final List<SectionSlot> GHOSTS = new ArrayList<>();
    private static final List<SectionSlot> OVERLAYS = new ArrayList<>();
    private static final Matrix4f FRUSTUM_MATRIX = new Matrix4f();
    private static final FrustumIntersection FRUSTUM = new FrustumIntersection();
    private static boolean drawFailed;
    private static boolean submitFailed;

    private GhostRenderer() {
    }

    public static boolean failed() {
        return drawFailed || submitFailed;
    }

    public static void register() {
        LevelRenderEvents.END_MAIN.register(GhostRenderer::renderMain);
        LevelRenderEvents.COLLECT_SUBMITS.register(GhostRenderer::collectSubmits);
    }

    private static void renderMain(LevelRenderContext context) {
        if (drawFailed) {
            return;
        }
        try {
            draw(context);
        } catch (RuntimeException | LinkageError exception) {
            drawFailed = true;
            LitematicaPlusClient.LOGGER.error("Schematic rendering failed and was switched off for this session", exception);
        } finally {
            GHOSTS.clear();
            OVERLAYS.clear();
        }
    }

    private static void draw(LevelRenderContext context) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        GhostEngine engine = GhostEngine.get();
        Minecraft minecraft = Minecraft.getInstance();
        engine.frame(minecraft);
        if (!config.renderEnabled || minecraft.player == null || minecraft.level == null) {
            return;
        }
        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 cameraPos = camera.pos;
        FRUSTUM_MATRIX.set(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        FRUSTUM.set(FRUSTUM_MATRIX);
        double range = (double) config.renderDistance * config.renderDistance;
        for (PlacementRuntime runtime : engine.runtimes()) {
            if (!runtime.placement().enabled() || runtime.volume() == null) {
                continue;
            }
            for (SectionSlot slot : runtime.slots()) {
                boolean ghost = slot.ghost() != null && config.renderGhostBlocks;
                boolean overlay = (slot.fill() != null || slot.lines() != null) && config.renderOverlay;
                if (!ghost && !overlay) {
                    continue;
                }
                float minX = (float) (slot.originX - cameraPos.x);
                float minY = (float) (slot.originY - cameraPos.y);
                float minZ = (float) (slot.originZ - cameraPos.z);
                float centerX = minX + 8.0F;
                float centerY = minY + 8.0F;
                float centerZ = minZ + 8.0F;
                if (centerX * centerX + centerY * centerY + centerZ * centerZ > range) {
                    continue;
                }
                int side = FRUSTUM.intersectAab(minX - 2.0F, minY - 2.0F, minZ - 2.0F,
                        minX + 18.0F, minY + 18.0F, minZ + 18.0F, SIDE_PLANES);
                if (side != FrustumIntersection.INTERSECT && side != FrustumIntersection.INSIDE) {
                    continue;
                }
                if (ghost) {
                    GHOSTS.add(slot);
                }
                if (overlay) {
                    OVERLAYS.add(slot);
                }
            }
        }
        boolean boxes = BoxRenderer.prepare(minecraft, cameraPos);
        if (GHOSTS.isEmpty() && OVERLAYS.isEmpty() && !boxes) {
            return;
        }
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        GpuTextureView colorView = target.getColorTextureView();
        GpuTextureView depthView = target.getDepthTextureView();
        if (colorView == null || depthView == null) {
            return;
        }
        RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer quadBuffer = quadIndices.getBuffer(Math.max(6,
                Math.max(BoxRenderer.maxQuadIndices(), Math.max(engine.maxGhostIndices(), engine.maxFillIndices()))));
        IndexType quadType = quadIndices.type();
        RenderSystem.AutoStorageIndexBuffer lineIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.LINES);
        GpuBuffer lineBuffer = lineIndices.getBuffer(Math.max(6, Math.max(BoxRenderer.maxLineIndices(), engine.maxLineIndices())));
        IndexType lineType = lineIndices.type();
        Matrix4f modelView = RenderSystem.getModelViewMatrixCopy();
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
                for (SectionSlot slot : GHOSTS) {
                    Vector3f offset = new Vector3f((float) (slot.originX - cameraPos.x),
                            (float) (slot.originY - cameraPos.y), (float) (slot.originZ - cameraPos.z));
                    pass.setUniform("DynamicTransforms", dynamic.writeTransform(modelView, color, offset, IDENTITY));
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
        try {
            submitSpecials(context, engine);
        } catch (RuntimeException | LinkageError exception) {
            submitFailed = true;
            LitematicaPlusClient.LOGGER.error("Schematic block entity rendering failed and was switched off", exception);
        }
    }

    private static void submitSpecials(LevelRenderContext context, GhostEngine engine) {
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        if (poseStack == null || collector == null) {
            return;
        }
        Vec3 cameraPos = context.levelState().cameraRenderState.pos;
        int budget = MAX_SPECIALS_PER_FRAME;
        for (PlacementRuntime runtime : engine.runtimes()) {
            PlacementVolume volume = runtime.volume();
            if (volume == null || !runtime.placement().enabled()) {
                continue;
            }
            BlockState[] palette = volume.palette();
            for (SectionSlot slot : runtime.slots()) {
                int[] specials = slot.specials();
                if (specials.length == 0) {
                    continue;
                }
                double dx = slot.originX + 8.0 - cameraPos.x;
                double dy = slot.originY + 8.0 - cameraPos.y;
                double dz = slot.originZ + 8.0 - cameraPos.z;
                if (dx * dx + dy * dy + dz * dz > SPECIAL_RANGE_SQUARED) {
                    continue;
                }
                for (int i = 0; i + 1 < specials.length; i += 2) {
                    int index = specials[i];
                    int paletteIndex = specials[i + 1];
                    if (paletteIndex >= palette.length) {
                        continue;
                    }
                    SpecialBlocks.Entry entry = SpecialBlocks.get(palette[paletteIndex]);
                    if (entry == null) {
                        continue;
                    }
                    poseStack.pushPose();
                    poseStack.translate(slot.originX + (index & 15) - cameraPos.x,
                            slot.originY + (index >> 8) - cameraPos.y,
                            slot.originZ + (index >> 4 & 15) - cameraPos.z);
                    if (entry.transform() != null) {
                        poseStack.mulPose(entry.transform());
                    }
                    entry.renderer().submit(null, poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false, 0);
                    poseStack.popPose();
                    if (--budget <= 0) {
                        return;
                    }
                }
            }
        }
    }
}
