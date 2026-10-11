package org.lts.litematicaplus.printer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import org.lts.litematicaplus.util.AtomicFilePersistence;
import org.lts.litematicaplus.util.Workers;

public final class PrinterConfig {
    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 6;
    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile PrinterConfig current = new PrinterConfig();

    public int version = CURRENT_VERSION;
    public boolean enabled;
    public int range = 4;

    public static PrinterConfig current() {
        return current;
    }

    public static void load() {
        Path path = path();
        PrinterConfig loaded = read(path);
        if (loaded == null) {
            loaded = read(backup(path));
        }
        current = sanitize(loaded == null ? new PrinterConfig() : loaded);
    }

    public static void update(Consumer<PrinterConfig> change) {
        PrinterConfig next = copy(current);
        change.accept(next);
        current = sanitize(next);
        try {
            Workers.writer().execute(PrinterConfig::write);
        } catch (RejectedExecutionException exception) {
            PrinterClient.LOGGER.warn("Could not queue the printer settings for saving", exception);
        }
    }

    private static void write() {
        Path path = path();
        try {
            AtomicFilePersistence.write(path, backup(path), GSON.toJson(current));
        } catch (IOException exception) {
            PrinterClient.LOGGER.error("Could not save printer settings {}", path, exception);
        }
    }

    private static PrinterConfig read(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, PrinterConfig.class);
        } catch (IOException | JsonParseException exception) {
            PrinterClient.LOGGER.warn("Could not read printer settings {}; using defaults", path, exception);
            return null;
        }
    }

    private static PrinterConfig sanitize(PrinterConfig config) {
        config.version = CURRENT_VERSION;
        config.range = Math.max(MIN_RANGE, Math.min(MAX_RANGE, config.range));
        return config;
    }

    private static PrinterConfig copy(PrinterConfig source) {
        return GSON.fromJson(GSON.toJson(source), PrinterConfig.class);
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("litematicaplus-printer.json");
    }

    private static Path backup(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }
}
