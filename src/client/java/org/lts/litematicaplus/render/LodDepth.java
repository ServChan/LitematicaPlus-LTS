package org.lts.litematicaplus.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.joml.Matrix4f;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL41C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryStack;

public final class LodDepth {
    private static final String VERTEX = """
            #version 330 core
            out vec2 texCoord;
            void main() {
                vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                texCoord = corner;
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 330 core
            uniform sampler2D LodDepth;
            uniform mat4 LodInverse;
            uniform mat4 TargetTransform;
            uniform vec2 DepthMode;
            in vec2 texCoord;
            void main() {
                float depth = texture(LodDepth, texCoord).r;
                if (depth <= 0.0 || depth >= 1.0) {
                    discard;
                }
                float ndc = DepthMode.x > 0.5 ? depth : depth * 2.0 - 1.0;
                vec4 world = LodInverse * vec4(texCoord * 2.0 - 1.0, ndc, 1.0);
                vec4 clip = TargetTransform * vec4(world.xyz / world.w, 1.0);
                float z = clip.z / clip.w;
                float window = DepthMode.x > 0.5 ? z : z * 0.5 + 0.5;
                float edge = 2.0 / 16777215.0;
                window = DepthMode.y > 0.5 ? max(window, edge) : min(window, 1.0 - edge);
                gl_FragDepth = clamp(window, 0.0, 1.0);
            }
            """;

    private static GpuTexture merged;
    private static GpuTextureView mergedView;
    private static int framebuffer;
    private static int framebufferTexture;
    private static int program;
    private static int vertexArray;
    private static int sampler;
    private static int inverseLocation;
    private static int targetLocation;
    private static int modeLocation;
    private static final int MAX_RETRIES = 3;
    private static final Matrix4f INVERSE = new Matrix4f();
    private static boolean failed;
    private static int retries;
    private static Object contextLevel;
    private static int contextWidth = -1;
    private static int contextHeight = -1;

    private LodDepth() {
    }

    public static boolean failed() {
        return failed;
    }

    public static void track(Object level, RenderTarget target) {
        GpuTexture depth = target.getDepthTexture();
        int width = depth == null ? -1 : depth.getWidth(0);
        int height = depth == null ? -1 : depth.getHeight(0);
        boolean levelChanged = level != contextLevel;
        if (!levelChanged && width == contextWidth && height == contextHeight) {
            return;
        }
        contextLevel = level;
        contextWidth = width;
        contextHeight = height;
        if (levelChanged) {
            close();
        }
        if (failed && retries < MAX_RETRIES) {
            retries++;
            failed = false;
            LitematicaPlusClient.LOGGER.warn("Retrying the Voxy terrain depth merge after a world or window change ({} of {})",
                    retries, MAX_RETRIES);
        }
    }

    public static void close() {
        releaseTarget();
        if (program != 0) {
            GL20C.glDeleteProgram(program);
            program = 0;
        }
        if (vertexArray != 0) {
            GL30C.glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (sampler != 0) {
            GL33C.glDeleteSamplers(sampler);
            sampler = 0;
        }
    }

    public static GpuTextureView merge(RenderTarget target, VoxyLod.Frame frame) {
        GpuTextureView fallback = target.getDepthTextureView();
        GpuTexture depth = target.getDepthTexture();
        if (frame == null || failed || depth == null || !(depth instanceof GlTexture)) {
            return fallback;
        }
        try {
            int width = depth.getWidth(0);
            int height = depth.getHeight(0);
            ensureTarget(depth.getFormat(), width, height);
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(depth, merged, 0, 0, 0, 0, 0, width,
                    height);
            ensureProgram();
            ensureFramebuffer();
            blit(frame, frame.target(), width, height);
            return mergedView;
        } catch (RuntimeException | LinkageError exception) {
            failed = true;
            LitematicaPlusClient.LOGGER.warn("Could not merge Voxy terrain depth, schematic parts over distant terrain are hidden instead",
                    exception);
            return fallback;
        }
    }

    private static void ensureTarget(GpuFormat format, int width, int height) {
        if (merged != null && merged.getFormat().equals(format) && merged.getWidth(0) == width
                && merged.getHeight(0) == height) {
            return;
        }
        releaseTarget();
        GpuDevice device = RenderSystem.getDevice();
        merged = device.createTexture(() -> "LitematicaPlus merged depth",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
                format, width, height, 1, 1);
        mergedView = device.createTextureView(merged);
    }

    private static void releaseTarget() {
        if (framebuffer != 0) {
            GL30C.glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
            framebufferTexture = 0;
        }
        if (mergedView != null) {
            mergedView.close();
            mergedView = null;
        }
        if (merged != null) {
            merged.close();
            merged = null;
        }
    }

    private static void ensureFramebuffer() {
        int texture = ((GlTexture) merged).glId();
        if (framebuffer != 0 && framebufferTexture == texture) {
            return;
        }
        if (framebuffer != 0) {
            GL30C.glDeleteFramebuffers(framebuffer);
        }
        framebuffer = GL45C.glCreateFramebuffers();
        framebufferTexture = texture;
        int attachment = merged.getFormat().hasStencilAspect() ? GL30C.GL_DEPTH_STENCIL_ATTACHMENT
                : GL30C.GL_DEPTH_ATTACHMENT;
        GL45C.glNamedFramebufferTexture(framebuffer, attachment, texture, 0);
        GL45C.glNamedFramebufferDrawBuffer(framebuffer, GL11C.GL_NONE);
        GL45C.glNamedFramebufferReadBuffer(framebuffer, GL11C.GL_NONE);
        int status = GL45C.glCheckNamedFramebufferStatus(framebuffer, GL30C.GL_FRAMEBUFFER);
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Merged depth framebuffer is incomplete: " + status);
        }
    }

    private static void ensureProgram() {
        if (program != 0) {
            return;
        }
        int vertex = compile(GL20C.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20C.GL_FRAGMENT_SHADER, FRAGMENT);
        int linked = GL20C.glCreateProgram();
        GL20C.glAttachShader(linked, vertex);
        GL20C.glAttachShader(linked, fragment);
        GL20C.glLinkProgram(linked);
        GL20C.glDeleteShader(vertex);
        GL20C.glDeleteShader(fragment);
        if (GL20C.glGetProgrami(linked, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetProgramInfoLog(linked);
            GL20C.glDeleteProgram(linked);
            throw new IllegalStateException("Merged depth program failed to link: " + log);
        }
        GL41C.glProgramUniform1i(linked, GL20C.glGetUniformLocation(linked, "LodDepth"), 0);
        inverseLocation = GL20C.glGetUniformLocation(linked, "LodInverse");
        targetLocation = GL20C.glGetUniformLocation(linked, "TargetTransform");
        modeLocation = GL20C.glGetUniformLocation(linked, "DepthMode");
        vertexArray = GL30C.glGenVertexArrays();
        sampler = GL33C.glGenSamplers();
        GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
        GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
        GL33C.glSamplerParameteri(sampler, GL14C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
        program = linked;
    }

    private static int compile(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("Merged depth shader failed to compile: " + log);
        }
        return shader;
    }

    private static void blit(VoxyLod.Frame frame, Matrix4f target, int width, int height) {
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        boolean reverse = DepthStencilState.DEFAULT.depthTest() == CompareOp.GREATER_THAN_OR_EQUAL;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            GL41C.glProgramUniformMatrix4fv(program, inverseLocation, false,
                    INVERSE.set(frame.projectionView()).invert().get(matrix));
            GL41C.glProgramUniformMatrix4fv(program, targetLocation, false, target.get(matrix));
            GL41C.glProgramUniform2f(program, modeLocation, zeroToOne ? 1.0F : 0.0F, reverse ? 1.0F : 0.0F);
            IntBuffer viewport = stack.mallocInt(4);
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            int previousProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            int previousArray = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
            int previousUnit = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
            int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
            boolean stencil = GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST);
            GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            int previousSampler = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
            int depthFunction = reverse ? GL11C.GL_GEQUAL : GL11C.GL_LEQUAL;
            try {
                GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
                GlStateManager._viewport(0, 0, width, height);
                GlStateManager._disableScissorTest();
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GlStateManager._disableCull();
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
                GlStateManager._enableDepthTest();
                GL11C.glEnable(GL11C.GL_DEPTH_TEST);
                GlStateManager._depthFunc(depthFunction);
                GL11C.glDepthFunc(depthFunction);
                GlStateManager._depthMask(true);
                GL11C.glDepthMask(true);
                GlStateManager._glUseProgram(program);
                GlStateManager._bindTexture(frame.depthTexture());
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, frame.depthTexture());
                GL33C.glBindSampler(0, sampler);
                GlStateManager._glBindVertexArray(vertexArray);
                GlStateManager._drawArrays(GL11C.GL_TRIANGLES, 0, 3);
            } finally {
                GL33C.glBindSampler(0, previousSampler);
                GlStateManager._bindTexture(previousTexture);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTexture);
                GlStateManager._activeTexture(previousUnit);
                GL13C.glActiveTexture(previousUnit);
                GlStateManager._glBindVertexArray(previousArray);
                GlStateManager._glUseProgram(previousProgram);
                if (stencil) {
                    GL11C.glEnable(GL11C.GL_STENCIL_TEST);
                }
                GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
                GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
                GlStateManager._viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            }
        }
    }
}
