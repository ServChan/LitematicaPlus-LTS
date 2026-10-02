package org.lts.litematicaplus.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int litematicaplus$leftPos();

    @Accessor("topPos")
    int litematicaplus$topPos();

    @Accessor("imageWidth")
    int litematicaplus$imageWidth();

    @Accessor("imageHeight")
    int litematicaplus$imageHeight();
}
