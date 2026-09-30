package org.lts.litematicaplus.mixin;

import net.minecraft.client.Camera;
import org.lts.litematicaplus.render.TopView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    private float depthFar;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void litematicaplus$topView(float partialTicks, CallbackInfo ci) {
        if (TopView.active()) {
            this.detached = true;
            this.depthFar = Math.max(this.depthFar, TopView.farPlane());
            setPosition(TopView.cameraX(), TopView.cameraY(), TopView.cameraZ());
            setRotation(TopView.YAW, TopView.PITCH);
        }
    }
}
