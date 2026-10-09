package org.lts.litematicaplus.render;

import org.lts.litematicaplus.compat.Addons;
import org.lts.litematicaplus.compat.Addon;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lts.litematicaplus.LitematicaPlusClient;

public final class VoxyLod {
    private static Bridge bridge;
    private static boolean bridgeResolved;
    private static final Matrix4f PROJECTION_VIEW = new Matrix4f();
    private static final Matrix4f TARGET = new Matrix4f();
    private static int lastFrameId = Integer.MIN_VALUE;
    private static Object lastViewport;
    private static Frame lastFrame;
    private static boolean failureLogged;

    public record Frame(int depthTexture, Matrix4f projectionView, Matrix4f target) {
    }

    private record Bridge(MethodHandle system, MethodHandle pipeline, MethodHandle viewport, Class<?> irisPipeline,
            MethodHandle data, MethodHandle toVanillaDepth, MethodHandle translucent, MethodHandle depthTexture,
            MethodHandle textureId, MethodHandle projectionView, MethodHandle frameId, MethodHandle vanillaProjection,
            MethodHandle modelView) {
        static Bridge resolve() {
            FabricLoader loader = FabricLoader.getInstance();
            if (!loader.isModLoaded("voxy") || !loader.isModLoaded("iris")) {
                return null;
            }
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                ClassLoader classLoader = VoxyLod.class.getClassLoader();
                Class<?> holder = Class.forName("me.cortex.voxy.client.core.IVoxyRenderSystemHolder", false, classLoader);
                Class<?> renderSystem = Class.forName("me.cortex.voxy.client.core.VoxyRenderSystem", false, classLoader);
                Class<?> irisPipeline = Class.forName("me.cortex.voxy.client.core.IrisVoxyRenderPipeline", false, classLoader);
                Class<?> pipelineData = Class.forName("me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData", false, classLoader);
                Class<?> framebuffer = Class.forName("me.cortex.voxy.client.core.rendering.util.DepthFramebuffer", false, classLoader);
                Class<?> texture = Class.forName("me.cortex.voxy.client.core.gl.GlTexture", false, classLoader);
                Class<?> viewport = Class.forName("me.cortex.voxy.client.core.rendering.Viewport", false, classLoader);
                Field pipeline = renderSystem.getDeclaredField("pipeline");
                pipeline.setAccessible(true);
                return new Bridge(
                        lookup.unreflect(holder.getMethod("getNullable")).asType(MethodType.methodType(Object.class)),
                        getter(lookup.unreflectGetter(pipeline)),
                        getter(lookup.unreflect(renderSystem.getMethod("getViewport"))),
                        irisPipeline,
                        getter(lookup.unreflect(irisPipeline.getMethod("_getData"))),
                        lookup.unreflectGetter(pipelineData.getField("renderToVanillaDepth"))
                                .asType(MethodType.methodType(boolean.class, Object.class)),
                        getter(lookup.unreflectGetter(irisPipeline.getField("fbTranslucent"))),
                        getter(lookup.unreflect(framebuffer.getMethod("getDepthTex"))),
                        lookup.unreflectGetter(texture.getField("id")).asType(MethodType.methodType(int.class, Object.class)),
                        getter(lookup.unreflectGetter(viewport.getField("MVP"))),
                        lookup.unreflectGetter(viewport.getField("frameId"))
                                .asType(MethodType.methodType(int.class, Object.class)),
                        getter(lookup.unreflectGetter(viewport.getField("vanillaProjection"))),
                        getter(lookup.unreflectGetter(viewport.getField("modelView"))));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                Addons.report(Addon.VOXY, "litematicaplus.addon.problem.lod_unreadable", exception);
                return null;
            }
        }

        private static MethodHandle getter(MethodHandle handle) {
            return handle.asType(MethodType.methodType(Object.class, Object.class));
        }
    }

    private VoxyLod() {
    }

    private static Bridge bridge() {
        if (!bridgeResolved) {
            bridgeResolved = true;
            bridge = Bridge.resolve();
        }
        return bridge;
    }

    public static boolean failed() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("voxy") && loader.isModLoaded("iris") && GraphicsBackend.openGl()
                && (bridge() == null || failureLogged);
    }

    public static Frame frame() {
        if (!GraphicsBackend.openGl()) {
            return null;
        }
        Bridge active = bridge();
        if (active == null) {
            return null;
        }
        try {
            Object system = (Object) active.system().invokeExact();
            if (system == null) {
                return null;
            }
            Object pipeline = (Object) active.pipeline().invokeExact(system);
            if (!active.irisPipeline().isInstance(pipeline)) {
                return null;
            }
            Object data = (Object) active.data().invokeExact(pipeline);
            if (data == null || (boolean) active.toVanillaDepth().invokeExact(data)) {
                return null;
            }
            Object viewport = (Object) active.viewport().invokeExact(system);
            if (viewport == null) {
                return null;
            }
            Object framebuffer = (Object) active.translucent().invokeExact(pipeline);
            Object texture = framebuffer == null ? null : (Object) active.depthTexture().invokeExact(framebuffer);
            if (texture == null) {
                return null;
            }
            int id = (int) active.textureId().invokeExact(texture);
            if (id == 0) {
                return null;
            }
            int frameId = (int) active.frameId().invokeExact(viewport);
            if (frameId == lastFrameId && viewport == lastViewport && lastFrame != null) {
                if (lastFrame.depthTexture() != id) {
                    lastFrame = new Frame(id, PROJECTION_VIEW, TARGET);
                }
                return lastFrame;
            }
            if (!((Object) active.projectionView().invokeExact(viewport) instanceof Matrix4fc matrix)
                    || !((Object) active.vanillaProjection().invokeExact(viewport) instanceof Matrix4fc projection)
                    || !((Object) active.modelView().invokeExact(viewport) instanceof Matrix4fc modelView)) {
                return null;
            }
            lastFrameId = frameId;
            lastViewport = viewport;
            PROJECTION_VIEW.set(matrix);
            TARGET.set(projection).mul(modelView);
            if (lastFrame == null || lastFrame.depthTexture() != id) {
                lastFrame = new Frame(id, PROJECTION_VIEW, TARGET);
            }
            return lastFrame;
        } catch (Throwable exception) {
            if (exception instanceof VirtualMachineError error) {
                throw error;
            }
            if (!failureLogged) {
                failureLogged = true;
                Addons.report(Addon.VOXY, "litematicaplus.addon.problem.lod_read_failed", exception);
            }
            return null;
        }
    }
}
