package org.lts.litematicaplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import org.lts.litematicaplus.tool.BlockEntityAssist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleOpenSignEditor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void litematicaplus$signEditor(ClientboundOpenSignEditorPacket packet, CallbackInfo ci) {
        if (BlockEntityAssist.onOpenSignEditor(Minecraft.getInstance(), packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleOpenScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void litematicaplus$openScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (BlockEntityAssist.onOpenScreen(Minecraft.getInstance(), packet)) {
            ci.cancel();
        }
    }
}
