package org.lts.litematicaplus.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.lts.litematicaplus.render.TopView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$hideCrosshairInTopView(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
            CallbackInfo ci) {
        if (TopView.active()) {
            ci.cancel();
        }
    }
}
