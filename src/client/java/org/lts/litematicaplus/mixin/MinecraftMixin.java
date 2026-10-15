package org.lts.litematicaplus.mixin;

import net.minecraft.client.Minecraft;
import org.lts.litematicaplus.compat.AxiomBridge;
import org.lts.litematicaplus.materials.ContainerMemory;
import org.lts.litematicaplus.tool.BreakAssist;
import org.lts.litematicaplus.tool.ToolController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    private int rightClickDelay;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$startAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (AxiomBridge.pausesInput()) {
            return;
        }
        if (minecraft.player != null && ToolController.get().onAttack(minecraft)) {
            cir.setReturnValue(false);
            return;
        }
        BreakAssist.yieldTo(minecraft);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$continueAttack(boolean down, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (AxiomBridge.pausesInput()) {
            return;
        }
        if (down && minecraft.player != null && ToolController.get().holdingTool(minecraft)) {
            ci.cancel();
        } else if (!down && BreakAssist.active()) {
            ci.cancel();
        } else if (down && minecraft.player != null) {
            BreakAssist.yieldTo(minecraft);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$startUseItem(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        ContainerMemory.noteUse(minecraft);
        if (AxiomBridge.pausesInput()) {
            return;
        }
        if (minecraft.player != null && ToolController.get().onUse(minecraft)) {
            this.rightClickDelay = 4;
            ci.cancel();
        }
    }

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$pickBlock(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (AxiomBridge.pausesInput()) {
            return;
        }
        if (minecraft.player != null && ToolController.get().onPick(minecraft)) {
            ci.cancel();
        }
    }
}
