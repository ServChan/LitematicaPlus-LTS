package org.lts.litematicaplus.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.lts.litematicaplus.render.TopView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$hideHandInTopView(CameraRenderState camera, PlayerRenderState player, GpuTextureView depth,
            CallbackInfo ci) {
        if (TopView.active()) {
            ci.cancel();
        }
    }
}
