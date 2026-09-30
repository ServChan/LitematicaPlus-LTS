package org.lts.litematicaplus.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import org.lts.litematicaplus.render.TopView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$hideClouds(CloudStatus status, RenderPass pass, CallbackInfo ci) {
        if (TopView.active()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderOit", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$hideOitClouds(CloudStatus status, OitStage stage, GpuTextureView depth,
            OitRenderPassProvider.Parameters parameters, CallbackInfo ci) {
        if (TopView.active()) {
            ci.cancel();
        }
    }
}
