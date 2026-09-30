package org.lts.litematicaplus.mixin;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockModelRenderState.class)
public interface BlockModelRenderStateAccessor {
    @Accessor("specialRenderer")
    SpecialModelRenderer<?> litematicaplus$specialRenderer();

    @Accessor("specialRendererTransformation")
    Matrix4fc litematicaplus$specialRendererTransformation();
}
