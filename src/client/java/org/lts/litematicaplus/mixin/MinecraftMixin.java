package org.lts.litematicaplus.mixin;

import net.minecraft.client.Minecraft;
import org.lts.litematicaplus.tool.ToolController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$startAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.player != null && ToolController.get().onAttack(minecraft)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$continueAttack(boolean down, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (down && minecraft.player != null && ToolController.get().holdingTool(minecraft)) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$startUseItem(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.player != null && ToolController.get().onUse(minecraft)) {
            ci.cancel();
        }
    }

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$pickBlock(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.player != null && ToolController.get().onPick(minecraft)) {
            ci.cancel();
        }
    }
}
