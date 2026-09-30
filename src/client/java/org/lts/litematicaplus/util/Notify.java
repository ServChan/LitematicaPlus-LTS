package org.lts.litematicaplus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Notify {
    private Notify() {
    }

    public static void actionBar(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null && minecraft.gui.hud != null) {
            minecraft.gui.hud.setOverlayMessage(message, false);
        }
    }

    public static void actionBar(String key, Object... args) {
        actionBar(Component.translatable(key, args));
    }
}
