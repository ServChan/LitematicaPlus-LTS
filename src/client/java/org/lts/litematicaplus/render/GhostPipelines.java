package org.lts.litematicaplus.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.lts.litematicaplus.LitematicaPlusClient;

public final class GhostPipelines {
    public static final RenderPipeline GHOST = RenderPipeline.builder()
            .withLocation(id("pipeline/ghost"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexShader("core/block")
            .withFragmentShader("core/block")
            .withShaderDefine("ALPHA_CUTOUT", 0.01F)
            .withVertexBinding(0, DefaultVertexFormat.BLOCK)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();

    public static final RenderPipeline OVERLAY_FILL = fill("pipeline/overlay_fill", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline OVERLAY_FILL_XRAY = fill("pipeline/overlay_fill_xray", CompareOp.ALWAYS_PASS);
    public static final RenderPipeline OVERLAY_LINES = lines("pipeline/overlay_lines", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline OVERLAY_LINES_XRAY = lines("pipeline/overlay_lines_xray", CompareOp.ALWAYS_PASS);

    private GhostPipelines() {
    }

    private static RenderPipeline fill(String path, CompareOp depth) {
        return RenderPipeline.builder()
                .withLocation(id(path))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(depth, false))
                .withCull(false)
                .build();
    }

    private static RenderPipeline lines(String path, CompareOp depth) {
        return RenderPipeline.builder()
                .withLocation(id(path))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withVertexShader("core/rendertype_lines")
                .withFragmentShader("core/rendertype_lines")
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
                .withPrimitiveTopology(PrimitiveTopology.LINES)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(depth, false))
                .withCull(false)
                .build();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(LitematicaPlusClient.MOD_ID, path);
    }
}
