package org.lts.litematicaplus.mhud;

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
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.util.AtomicFilePersistence;
import org.lts.litematicaplus.util.Workers;

public final class MaterialHudConfig {
    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile MaterialHudConfig current = new MaterialHudConfig();

    public int version = CURRENT_VERSION;
    public boolean enabled = false;
    public boolean panelVisible = true;
    public boolean onlyMissing = true;
    public boolean sortByName = false;
    public boolean showProgress = true;
    public boolean showShortfall = false;
    public boolean countInBoxes = false;
    public int itemsPerPage = 8;
    public int scalePercent = 100;
    public float x = 1.0F;
    public float y = 0.0F;
    public boolean besideContainer = true;
    public boolean dockRight = true;
    public boolean transferButton = true;
    public boolean transferAuto = false;
    public boolean pinAcrossDimensions = true;
    public int searchRadius = 96;
    public boolean searchItemFrames = true;
    public boolean searchFollowsPage = true;
    public boolean skipOwned = true;
    public boolean searchMarkers = true;
    public boolean highlightLabels = true;
    public int highlightGlow = 60;
    public boolean ownColor = false;
    public int hue = 190;
    public boolean highlightInContainer = true;
    public boolean countEntities = true;
    public boolean ghostInventoryOnly = false;
    public boolean searchAllChests = false;

    public static MaterialHudConfig current() {
        return current;
    }

    public static void load() {
        Path path = path();
        MaterialHudConfig loaded = read(path);
        if (loaded == null) {
            loaded = read(backup(path));
        }
        current = sanitize(loaded == null ? new MaterialHudConfig() : loaded);
    }

    public static void update(Consumer<MaterialHudConfig> change) {
        MaterialHudConfig next = copy(current);
        change.accept(next);
        current = sanitize(next);
        try {
            Workers.writer().execute(MaterialHudConfig::write);
        } catch (RejectedExecutionException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not queue the Material HUD settings for saving", exception);
        }
    }

    private static void write() {
        Path path = path();
        try {
            AtomicFilePersistence.write(path, backup(path), GSON.toJson(current));
        } catch (IOException exception) {
            LitematicaPlusClient.LOGGER.error("Could not save the Material HUD settings {}", path, exception);
        }
    }

    private static MaterialHudConfig read(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, MaterialHudConfig.class);
        } catch (IOException | JsonParseException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not read the Material HUD settings {}; using defaults", path, exception);
            return null;
        }
    }

    static MaterialHudConfig sanitize(MaterialHudConfig config) {
        config.version = CURRENT_VERSION;
        config.itemsPerPage = clamp(config.itemsPerPage, 1, 30);
        config.scalePercent = clamp(config.scalePercent, 50, 300);
        config.searchRadius = clamp(config.searchRadius, 16, 256);
        config.highlightGlow = clamp(config.highlightGlow, 10, 100);
        config.hue = clamp(config.hue, 0, 359);
        config.x = Float.isFinite(config.x) ? Math.max(0.0F, Math.min(1.0F, config.x)) : 1.0F;
        config.y = Float.isFinite(config.y) ? Math.max(0.0F, Math.min(1.0F, config.y)) : 0.0F;
        return config;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private static MaterialHudConfig copy(MaterialHudConfig source) {
        return GSON.fromJson(GSON.toJson(source), MaterialHudConfig.class);
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("litematicaplus-material-hud.json");
    }

    private static Path backup(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }
}
