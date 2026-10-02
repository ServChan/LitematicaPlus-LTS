package org.lts.litematicaplus.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("yRotLast")
    float litematicaplus$getYRotLast();

    @Accessor("yRotLast")
    void litematicaplus$setYRotLast(float value);

    @Accessor("xRotLast")
    float litematicaplus$getXRotLast();

    @Accessor("xRotLast")
    void litematicaplus$setXRotLast(float value);

    @Accessor("lastOnGround")
    void litematicaplus$setLastOnGround(boolean value);

    @Accessor("lastHorizontalCollision")
    void litematicaplus$setLastHorizontalCollision(boolean value);
}
