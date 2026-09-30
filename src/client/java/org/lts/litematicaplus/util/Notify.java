package org.lts.litematicaplus.util;

import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
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

    public static void chat(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null && minecraft.gui.hud != null) {
            minecraft.gui.hud.getChat().addClientSystemMessage(message);
        }
    }

    public static Component describe(Throwable failure) {
        Throwable cause = failure;
        while (cause != null && !(cause instanceof LocalizedException) && cause.getCause() != null
                && (cause instanceof CompletionException || cause instanceof ExecutionException
                || cause instanceof UncheckedIOException)) {
            cause = cause.getCause();
        }
        if (cause instanceof LocalizedException localized) {
            return Component.translatable(localized.key(), localized.args());
        }
        if (cause instanceof NoSuchFileException missing) {
            return Component.translatable("litematicaplus.error.file_missing", String.valueOf(missing.getFile()));
        }
        if (cause instanceof OutOfMemoryError) {
            return Component.translatable("litematicaplus.error.out_of_memory");
        }
        String message = cause == null ? null : cause.getMessage();
        return message == null || message.isBlank() ? Component.translatable("litematicaplus.error.unknown")
                : Component.literal(message);
    }

    public static void actionBar(String key, Object... args) {
        actionBar(Component.translatable(key, args));
    }
}
