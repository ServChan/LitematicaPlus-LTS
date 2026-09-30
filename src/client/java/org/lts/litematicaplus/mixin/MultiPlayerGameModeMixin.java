package org.lts.litematicaplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.lts.litematicaplus.tool.BreakAssist;
import org.lts.litematicaplus.tool.ToolSwitcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$guardDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (BreakAssist.guardBlocks(Minecraft.getInstance(), pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$guardStart(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (BreakAssist.guardBlocks(minecraft, pos)) {
            cir.setReturnValue(false);
            return;
        }
        if (!BreakAssist.bypassGuard()) {
            ToolSwitcher.switchFor(minecraft, pos, false);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void litematicaplus$guardContinue(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (BreakAssist.guardBlocks(Minecraft.getInstance(), pos)) {
            cir.setReturnValue(false);
        }
    }
}
