package org.lts.litematicaplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lts.litematicaplus.compat.AxiomBridge;
import org.lts.litematicaplus.tool.ToolController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (handle == 0L || handle != this.minecraft.getWindow().handle()) {
            return;
        }
        if (this.minecraft.gui.overlay() != null || this.minecraft.gui.screen() != null || this.minecraft.player == null
                || AxiomBridge.pausesInput()) {
            return;
        }
        if (ToolController.get().onScroll(this.minecraft, yoffset)) {
            ci.cancel();
        }
    }
}
