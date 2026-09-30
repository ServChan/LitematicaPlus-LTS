package org.lts.litematicaplus.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.lts.litematicaplus.tool.LookLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void litematicaplus$lockLook(CallbackInfo ci) {
        LookLock.beforeSendPosition((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void litematicaplus$unlockLook(CallbackInfo ci) {
        LookLock.afterSendPosition((LocalPlayer) (Object) this);
    }
}
