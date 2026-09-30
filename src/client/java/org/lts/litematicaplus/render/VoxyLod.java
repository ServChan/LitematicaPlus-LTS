package org.lts.litematicaplus.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lts.litematicaplus.LitematicaPlusClient;

public final class VoxyLod {
    private static final Bridge BRIDGE = Bridge.resolve();
    private static int lastFrameId = Integer.MIN_VALUE;
    private static boolean failureLogged;

    public record Frame(int depthTexture, Matrix4f projectionView, Matrix4f target) {
    }

    private record Bridge(Method system, Field pipeline, Method viewport, Class<?> irisPipeline, Method data,
            Field toVanillaDepth, Field translucent, Method depthTexture, Field textureId, Field projectionView,
            Field frameId, Field vanillaProjection, Field modelView) {
        static Bridge resolve() {
            FabricLoader loader = FabricLoader.getInstance();
            if (!loader.isModLoaded("voxy") || !loader.isModLoaded("iris")) {
                return null;
            }
            try {
                Class<?> holder = Class.forName("me.cortex.voxy.client.core.IVoxyRenderSystemHolder");
                Class<?> renderSystem = Class.forName("me.cortex.voxy.client.core.VoxyRenderSystem");
                Class<?> irisPipeline = Class.forName("me.cortex.voxy.client.core.IrisVoxyRenderPipeline");
                Class<?> pipelineData = Class.forName("me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData");
                Class<?> framebuffer = Class.forName("me.cortex.voxy.client.core.rendering.util.DepthFramebuffer");
                Class<?> texture = Class.forName("me.cortex.voxy.client.core.gl.GlTexture");
                Class<?> viewport = Class.forName("me.cortex.voxy.client.core.rendering.Viewport");
                Field pipeline = renderSystem.getDeclaredField("pipeline");
                pipeline.setAccessible(true);
                return new Bridge(holder.getMethod("getNullable"), pipeline, renderSystem.getMethod("getViewport"),
                        irisPipeline, irisPipeline.getMethod("_getData"),
                        pipelineData.getField("renderToVanillaDepth"), irisPipeline.getField("fbTranslucent"),
                        framebuffer.getMethod("getDepthTex"), texture.getField("id"), viewport.getField("MVP"),
                        viewport.getField("frameId"), viewport.getField("vanillaProjection"),
                        viewport.getField("modelView"));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                LitematicaPlusClient.LOGGER.warn("Voxy terrain depth is not readable, distant schematic parts may be hidden: {}",
                        exception.toString());
                return null;
            }
        }
    }

    private VoxyLod() {
    }

    public static Frame frame() {
        if (BRIDGE == null) {
            return null;
        }
        try {
            Object system = BRIDGE.system().invoke(null);
            if (system == null) {
                return null;
            }
            Object pipeline = BRIDGE.pipeline().get(system);
            if (!BRIDGE.irisPipeline().isInstance(pipeline)) {
                return null;
            }
            Object data = BRIDGE.data().invoke(pipeline);
            if (data == null || BRIDGE.toVanillaDepth().getBoolean(data)) {
                return null;
            }
            Object viewport = BRIDGE.viewport().invoke(system);
            if (viewport == null) {
                return null;
            }
            int frameId = BRIDGE.frameId().getInt(viewport);
            if (frameId == lastFrameId) {
                return null;
            }
            lastFrameId = frameId;
            Object framebuffer = BRIDGE.translucent().get(pipeline);
            Object texture = framebuffer == null ? null : BRIDGE.depthTexture().invoke(framebuffer);
            if (texture == null || !(BRIDGE.projectionView().get(viewport) instanceof Matrix4fc matrix)
                    || !(BRIDGE.vanillaProjection().get(viewport) instanceof Matrix4fc projection)
                    || !(BRIDGE.modelView().get(viewport) instanceof Matrix4fc modelView)) {
                return null;
            }
            int id = BRIDGE.textureId().getInt(texture);
            return id == 0 ? null : new Frame(id, new Matrix4f(matrix), new Matrix4f(projection).mul(modelView));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (!failureLogged) {
                failureLogged = true;
                LitematicaPlusClient.LOGGER.warn("Reading Voxy terrain depth failed: {}", exception.toString());
            }
            return null;
        }
    }
}
