package org.lts.litematicaplus.mixin;

import org.lts.litematicaplus.render.TopView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CameraUniforms", remap = false)
public abstract class IrisCameraUniformsMixin {
    @Inject(method = "getRenderDistanceInBlocks", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void litematicaplus$extendShaderFogInTopView(CallbackInfoReturnable<Integer> cir) {
        if (TopView.active()) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), TopView.shaderFar()));
        }
    }
}
