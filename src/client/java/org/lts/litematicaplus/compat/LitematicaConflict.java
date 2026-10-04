package org.lts.litematicaplus.compat;

import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.gui.LitematicaConflictScreen;
import org.lts.litematicaplus.util.Notify;

public final class LitematicaConflict {
    private static final String LITEMATICA = "litematica";
    private static String version;
    private static boolean screenShown;
    private static boolean chatShown;

    private LitematicaConflict() {
    }

    public static Optional<String> litematicaVersion() {
        return FabricLoader.getInstance().getModContainer(LITEMATICA)
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString());
    }

    public static void register() {
        Optional<String> found = litematicaVersion();
        if (found.isEmpty()) {
            return;
        }
        version = found.get();
        LitematicaPlusClient.LOGGER.warn("Litematica {} is loaded together with Litematica Plus; Litematica Plus replaces it, "
                + "keep only one of them in the mods folder", version);
        ClientTickEvents.END_CLIENT_TICK.register(LitematicaConflict::tick);
    }

    private static void tick(Minecraft client) {
        if (screenShown && chatShown) {
            return;
        }
        Screen screen = client.gui.screen();
        if (!screenShown && client.level == null && client.gui.overlay() == null && screen instanceof TitleScreen) {
            screenShown = true;
            client.gui.setScreen(new LitematicaConflictScreen(screen, version));
            return;
        }
        if (!chatShown && client.level != null && client.player != null && screen == null) {
            chatShown = true;
            screenShown = true;
            Notify.chat(Component.translatable("litematicaplus.conflict.chat", version).copy()
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
