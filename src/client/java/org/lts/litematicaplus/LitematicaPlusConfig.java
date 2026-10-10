package org.lts.litematicaplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.loader.api.FabricLoader;
import org.lts.litematicaplus.util.AtomicFilePersistence;
import org.lts.litematicaplus.util.Workers;

public final class LitematicaPlusConfig {
    private static final int CURRENT_CONFIG_VERSION = 5;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile LitematicaPlusConfig current = defaults();
    private static final AtomicReference<Pending> PENDING = new AtomicReference<>();

    private record Pending(LitematicaPlusConfig config, List<CompletableFuture<Boolean>> results) {
    }

    public enum PasteReplace {
        ALL,
        NON_AIR_ONLY,
        INTO_AIR_ONLY;

        public PasteReplace next() {
            PasteReplace[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum PlacementPreset {
        BALANCED(2),
        SAFE(4),
        FAST(1),
        CUSTOM(-1);

        private final int ticks;

        PlacementPreset(int ticks) {
            this.ticks = ticks;
        }

        public int ticks(int custom) {
            return this == CUSTOM ? Math.max(0, custom) : this.ticks;
        }

        public PlacementPreset next() {
            PlacementPreset[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public int configVersion = CURRENT_CONFIG_VERSION;
    public boolean renderEnabled = true;
    public boolean renderGhostBlocks = true;
    public boolean renderOverlay = true;
    public boolean renderPlacementBoxes = true;
    public boolean renderSelectionBoxes = true;
    public boolean overlayThroughWalls = false;
    public boolean renderCollidingBlocks = false;
    public boolean renderBlockEntities = true;
    public float ghostAlpha = 0.65F;
    public float overlayFillAlpha = 0.22F;
    public float overlayLineAlpha = 0.9F;
    public int renderDistance = 320;
    public int workerThreads = 0;
    public int meshUploadsPerFrame = 24;
    public int snapshotBudgetMicros = 600;
    public boolean showMissing = true;
    public boolean showExtra = true;
    public boolean showWrongBlock = true;
    public boolean showWrongState = true;
    public boolean lenientStates = true;
    public boolean looseBlocks = false;
    public boolean soilInterchangeable = true;
    public boolean toolUseGuard = true;
    public boolean hudEnabled = true;
    public int hudMaterialLines = 6;
    public boolean hudRightSide = false;
    public float hudX = 0.0F;
    public float hudY = 0.0F;
    public boolean toolEnabled = true;
    public String toolItem = "minecraft:stick";
    public boolean easyPlaceEnabled = false;
    public int easyPlaceDelayTicks = 2;
    public boolean easyPlaceStrictOrientation = true;
    public boolean easyPlaceAirPlacement = true;
    public PlacementPreset easyPlacePreset = PlacementPreset.BALANCED;
    public boolean easyPlaceJitter = false;
    public boolean easyPlaceHold = true;
    public boolean easyPlaceFakeRotation = true;
    public int easyPlaceRotationLeadTicks = 1;
    public boolean easyPlaceRotationRevert = true;
    public boolean easyPlaceAllowInteraction = true;
    public boolean easyPlaceFinishStates = true;
    public boolean easyPlaceIgnite = true;
    public boolean easyPlaceWaterlog = false;
    public boolean easyPlaceObserverDetect = false;
    public boolean easyPlaceIgnoreItemData = false;
    public boolean easyPlaceSubstitutes = false;
    public boolean easyPlaceDiagnostics = true;
    public boolean easyPlaceRestrictArea = true;
    public boolean excavationGuard = false;
    public boolean excavationGuardOutside = true;
    public boolean excavationGuardCorrect = true;
    public boolean excavationGuardHint = true;
    public boolean autoToolSwitch = false;
    public boolean terrainAutoReplace = false;
    public boolean breakWrongBlocks = false;
    public boolean entityPlacement = true;
    public boolean noteTuningHud = true;
    public boolean hudDiagnostics = false;
    public boolean trustHiddenBlocks = true;
    public boolean useWorldCache = true;
    public boolean ghostsOnlyLoaded = true;
    public boolean materialHelper = true;
    public boolean materialPanel = true;
    public boolean materialPanelCollapsed = false;
    public boolean materialExactTake = true;
    public boolean materialTooltips = true;
    public boolean materialHelperFollowsRender = true;
    public boolean enderChestMaterials = true;
    public boolean materialSwap = true;
    public boolean countVisibleOnly = true;
    public boolean schematicPreview = true;
    public boolean previewModels = true;
    public boolean placementRestriction = false;
    public boolean pickBlockEnabled = true;
    public int lockedHotbarSlots = 0;
    public Map<String, Integer> hotkeyModifiers = new HashMap<>();
    public boolean inventoryHighlight = false;
    public int pasteCommandsPerTick = 8;
    public PasteReplace pasteReplace = PasteReplace.ALL;
    public boolean pasteBlockEntityData = true;
    public boolean pasteEntities = true;
    public boolean syncmaticaEnabled = true;
    public String schematicsDirectory = "schematics";
    public String defaultAuthor = "";
    public String zoneTeam = "";
    public String zoneTransport = "MESSAGE";
    public String zoneCommand = "";
    public boolean zoneHideMessages = true;

    public static void load() {
        Path configPath = configPath();
        LitematicaPlusConfig primary = read(configPath);
        if (primary != null) {
            if (primary.configVersion > CURRENT_CONFIG_VERSION) {
                LitematicaPlusClient.LOGGER.warn("Config {} was written by a newer version; unknown options will be dropped on save",
                        configPath);
            }
            boolean migrated = primary.configVersion < CURRENT_CONFIG_VERSION;
            current = sanitize(migrate(primary));
            if (migrated) {
                try {
                    writeAtomically(configPath, current, true);
                } catch (IOException exception) {
                    LitematicaPlusClient.LOGGER.error("Could not save migrated config {}", configPath, exception);
                }
            }
            return;
        }
        LitematicaPlusConfig backup = read(backupPath(configPath));
        current = sanitize(backup == null ? defaults() : migrate(backup));
        try {
            AtomicFilePersistence.quarantine(configPath);
            writeAtomically(configPath, current, false);
        } catch (IOException exception) {
            LitematicaPlusClient.LOGGER.error("Could not create config {}", configPath, exception);
        }
    }

    static LitematicaPlusConfig load(Path path) {
        LitematicaPlusConfig loaded = read(path);
        if (loaded == null) {
            loaded = read(backupPath(path));
        }
        return sanitize(loaded == null ? defaults() : migrate(loaded));
    }

    public static LitematicaPlusConfig current() {
        return current;
    }

    public static LitematicaPlusConfig currentCopy() {
        return copyOf(current);
    }

    public static CompletableFuture<Boolean> save(LitematicaPlusConfig config) {
        LitematicaPlusConfig clean = sanitize(copyOf(config));
        current = clean;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        PENDING.accumulateAndGet(new Pending(clean, List.of(result)), (previous, next) -> {
            if (previous == null) {
                return next;
            }
            List<CompletableFuture<Boolean>> merged = new ArrayList<>(previous.results());
            merged.addAll(next.results());
            return new Pending(next.config(), merged);
        });
        try {
            Workers.writer().execute(LitematicaPlusConfig::writePending);
        } catch (RejectedExecutionException exception) {
            writePending();
        }
        return result;
    }

    private static void writePending() {
        Pending pending = PENDING.getAndSet(null);
        if (pending == null) {
            return;
        }
        Path configPath = configPath();
        boolean written = false;
        try {
            writeAtomically(configPath, pending.config(), true);
            written = true;
        } catch (IOException | RuntimeException exception) {
            LitematicaPlusClient.LOGGER.error("Could not save config {}", configPath, exception);
        } finally {
            for (CompletableFuture<Boolean> result : pending.results()) {
                result.complete(written);
            }
        }
    }

    public int placementDelayTicks() {
        return this.easyPlacePreset.ticks(this.easyPlaceDelayTicks);
    }

    public boolean hotbarSlotLocked(int slot) {
        return slot >= 0 && slot < 9 && (this.lockedHotbarSlots & 1 << slot) != 0;
    }

    public int resolvedWorkerThreads() {
        if (this.workerThreads > 0) {
            return this.workerThreads;
        }
        return Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() / 2));
    }

    static void writeAtomically(Path path, LitematicaPlusConfig config, boolean backupExisting) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(sanitize(copyOf(config)), writer);
            }
            if (backupExisting && Files.isRegularFile(path)) {
                Files.copy(path, backupPath(path), StandardCopyOption.REPLACE_EXISTING);
            }
            AtomicFilePersistence.replace(temporary, path);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static LitematicaPlusConfig migrate(LitematicaPlusConfig config) {
        if (config.configVersion < 2) {
            config.pasteCommandsPerTick = Math.min(config.pasteCommandsPerTick, 8);
        }
        if (config.configVersion < 3) {
            config.easyPlaceAirPlacement = true;
            config.easyPlacePreset = config.easyPlaceDelayTicks == 2 ? PlacementPreset.BALANCED : PlacementPreset.CUSTOM;
        }
        if (config.configVersion < 4) {
            if (config.renderDistance == 192) {
                config.renderDistance = 320;
            }
            if (config.snapshotBudgetMicros == 1500) {
                config.snapshotBudgetMicros = 600;
            }
        }
        if (config.configVersion < 5) {
            config.hudX = config.hudRightSide ? 1.0F : 0.0F;
            config.hudY = 0.0F;
            config.hudRightSide = false;
        }
        return config;
    }

    static LitematicaPlusConfig sanitize(LitematicaPlusConfig config) {
        config.configVersion = CURRENT_CONFIG_VERSION;
        config.ghostAlpha = (float) clamp(config.ghostAlpha, 0.2, 1.0);
        config.overlayFillAlpha = (float) clamp(config.overlayFillAlpha, 0.0, 1.0);
        config.overlayLineAlpha = (float) clamp(config.overlayLineAlpha, 0.0, 1.0);
        config.renderDistance = clamp(config.renderDistance, 32, 2048);
        config.workerThreads = clamp(config.workerThreads, 0, 16);
        config.meshUploadsPerFrame = clamp(config.meshUploadsPerFrame, 1, 256);
        config.snapshotBudgetMicros = clamp(config.snapshotBudgetMicros, 200, 10000);
        config.hudMaterialLines = clamp(config.hudMaterialLines, 0, 16);
        config.hudX = (float) clamp(config.hudX, 0.0, 1.0);
        config.hudY = (float) clamp(config.hudY, 0.0, 1.0);
        config.lockedHotbarSlots &= 0x1FF;
        config.zoneTeam = config.zoneTeam == null ? "" : config.zoneTeam.length() > 512 ? config.zoneTeam.substring(0, 512) : config.zoneTeam;
        config.zoneCommand = config.zoneCommand == null ? "" : config.zoneCommand.length() > 128 ? config.zoneCommand.substring(0, 128) : config.zoneCommand;
        config.zoneTransport = config.zoneTransport == null ? "MESSAGE" : config.zoneTransport;
        Map<String, Integer> modifiers = new HashMap<>();
        if (config.hotkeyModifiers != null) {
            config.hotkeyModifiers.forEach((name, mask) -> {
                if (name != null && mask != null && (mask & 7) != 0) {
                    modifiers.put(name, mask & 7);
                }
            });
        }
        config.hotkeyModifiers = modifiers;
        config.easyPlaceDelayTicks = clamp(config.easyPlaceDelayTicks, 0, 20);
        config.pasteCommandsPerTick = clamp(config.pasteCommandsPerTick, 1, 16);
        config.easyPlaceRotationLeadTicks = clamp(config.easyPlaceRotationLeadTicks, 0, 3);
        if (config.easyPlacePreset == null) {
            config.easyPlacePreset = PlacementPreset.BALANCED;
        }
        if (config.pasteReplace == null) {
            config.pasteReplace = PasteReplace.ALL;
        }
        config.toolItem = normalizeId(config.toolItem, "minecraft:stick");
        if (config.toolItem.equals("minecraft:air")) {
            config.toolItem = "minecraft:stick";
        }
        config.schematicsDirectory = normalizeDirectory(config.schematicsDirectory);
        config.defaultAuthor = config.defaultAuthor == null ? "" : trim(config.defaultAuthor.strip(), 64);
        return config;
    }

    private static String normalizeId(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String clean = value.strip().toLowerCase(java.util.Locale.ROOT);
        if (clean.isEmpty() || !clean.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?")) {
            return fallback;
        }
        return clean.contains(":") ? clean : "minecraft:" + clean;
    }

    private static String normalizeDirectory(String value) {
        if (value == null) {
            return "schematics";
        }
        String clean = value.strip().replace('\\', '/');
        if (clean.isEmpty() || clean.contains("..") || clean.length() > 260) {
            return "schematics";
        }
        try {
            Path.of(clean);
        } catch (InvalidPathException exception) {
            return "schematics";
        }
        return clean;
    }

    private static String trim(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static LitematicaPlusConfig read(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, LitematicaPlusConfig.class);
        } catch (IOException | RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not read config {}", path, exception);
            return null;
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("litematicaplus.json");
    }

    private static Path backupPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }

    private static LitematicaPlusConfig defaults() {
        return new LitematicaPlusConfig();
    }

    private static LitematicaPlusConfig copyOf(LitematicaPlusConfig source) {
        return GSON.fromJson(GSON.toJson(source), LitematicaPlusConfig.class);
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private static double clamp(double value, double low, double high) {
        return Double.isFinite(value) ? Math.max(low, Math.min(high, value)) : low;
    }
}
