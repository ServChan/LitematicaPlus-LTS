package org.lts.litematicaplus.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.engine.GhostEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void litematicaplus$blockUpdated(BlockPos pos, BlockState old, BlockState current, int updateFlags,
            CallbackInfo ci) {
        GhostEngine.get().onBlockChanged(pos);
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void litematicaplus$chunkLoaded(ChunkPos pos, CallbackInfo ci) {
        GhostEngine.get().onChunkChanged(pos.x(), pos.z());
    }
}
