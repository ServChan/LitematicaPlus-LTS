package org.lts.litematicaplus.gui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class SettingsExtensions {
    public sealed interface Option permits Toggle, Number {
        String key();
    }

    public record Toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) implements Option {
    }

    public record Number(String key, IntSupplier getter, IntConsumer setter, int min, int max, int step)
            implements Option {
    }

    public record Page(String key, Supplier<List<Option>> options) {
    }

    private static final List<Page> PAGES = new CopyOnWriteArrayList<>();

    private SettingsExtensions() {
    }

    public static void register(Page page) {
        PAGES.removeIf(existing -> existing.key().equals(page.key()));
        PAGES.add(page);
    }

    public static List<Page> pages() {
        return List.copyOf(PAGES);
    }
}
