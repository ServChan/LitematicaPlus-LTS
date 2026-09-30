package org.lts.litematicaplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.loader.api.FabricLoader;
import org.lts.litematicaplus.util.AtomicFilePersistence;
import org.lts.litematicaplus.util.Workers;

public final class SessionStore {
    static final int SCHEMA_VERSION = 1;
    private static final int MAX_SCOPES = 512;
    private static final int MAX_PLACEMENTS = 256;
    private static final int MAX_BOXES = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SessionStore INSTANCE = new SessionStore(
            FabricLoader.getInstance().getConfigDir().resolve("litematica-plus-session.json"));

    public static final class PlacementData {
        public String id = "";
        public String name = "";
        public String file = "";
        public int x;
        public int y;
        public int z;
        public String rotation = "NONE";
        public String mirror = "NONE";
        public boolean enabled = true;
        public boolean locked;
        public List<String> disabledRegions = new ArrayList<>();
    }

    public static final class LayerData {
        public String mode = "ALL";
        public String axis = "Y";
        public int single;
        public int min;
        public int max;
    }

    public static final class BoxData {
        public String name = "";
        public int[] a;
        public int[] b;
    }

    public static final class AreaData {
        public String name = "";
        public List<BoxData> boxes = new ArrayList<>();
        public int selected;
    }

    public static final class ScopeData {
        public List<PlacementData> placements = new ArrayList<>();
        public String selectedPlacement = "";
        public LayerData layer = new LayerData();
        public AreaData area = new AreaData();
        public String toolMode = "";
    }

    private static final class FileData {
        int schemaVersion = SCHEMA_VERSION;
        Map<String, ScopeData> scopes = new LinkedHashMap<>();
    }

    private final Path path;
    private final Path backup;
    private FileData data = new FileData();
    private boolean readOnly;
    private boolean loaded;
    private volatile boolean retry;
    private final AtomicReference<String> pending = new AtomicReference<>();

    SessionStore(Path path) {
        this.path = path;
        this.backup = path.resolveSibling(path.getFileName() + ".bak");
    }

    public static SessionStore get() {
        return INSTANCE;
    }

    public synchronized void load() {
        this.loaded = true;
        FileData primary = read(this.path);
        if (primary != null) {
            accept(primary);
            return;
        }
        FileData recovered = read(this.backup);
        if (recovered != null) {
            try {
                AtomicFilePersistence.restoreBackup(this.path, this.backup);
            } catch (IOException exception) {
                LitematicaPlusClient.LOGGER.warn("Could not restore session backup {}", this.backup, exception);
            }
            accept(recovered);
            return;
        }
        this.data = new FileData();
    }

    public synchronized boolean readOnly() {
        return this.readOnly;
    }

    public synchronized ScopeData scope(String key) {
        if (!this.loaded) {
            load();
        }
        ScopeData scope = this.data.scopes.get(key);
        return sanitize(scope == null ? new ScopeData() : copy(scope));
    }

    public synchronized void save(String key, ScopeData scope) {
        if (key == null || key.isBlank() || this.readOnly) {
            return;
        }
        if (!this.loaded) {
            load();
        }
        ScopeData clean = sanitize(copy(scope));
        ScopeData previous = this.data.scopes.get(key);
        if (!this.retry && previous != null && GSON.toJson(previous).equals(GSON.toJson(clean))) {
            return;
        }
        this.data.scopes.remove(key);
        this.data.scopes.put(key, clean);
        while (this.data.scopes.size() > MAX_SCOPES) {
            String oldest = this.data.scopes.keySet().iterator().next();
            this.data.scopes.remove(oldest);
        }
        this.pending.set(GSON.toJson(this.data));
        try {
            Workers.writer().execute(this::writePending);
        } catch (RejectedExecutionException exception) {
            writePending();
        }
    }

    synchronized void saveBlocking(String key, ScopeData scope) {
        if (key == null || key.isBlank() || this.readOnly) {
            return;
        }
        this.loaded = true;
        this.data.scopes.put(key, sanitize(copy(scope)));
        String json = GSON.toJson(this.data);
        synchronized (this.path) {
            this.pending.set(null);
            writeNow(json);
        }
    }

    private void writePending() {
        synchronized (this.path) {
            String json = this.pending.getAndSet(null);
            if (json != null) {
                writeNow(json);
            }
        }
    }

    private void writeNow(String json) {
        synchronized (this.path) {
            try {
                AtomicFilePersistence.write(this.path, this.backup, json);
                this.retry = false;
            } catch (IOException exception) {
                this.retry = true;
                LitematicaPlusClient.LOGGER.error("Could not save session {}", this.path, exception);
            }
        }
    }

    private void accept(FileData loadedData) {
        if (loadedData.schemaVersion > SCHEMA_VERSION) {
            this.readOnly = true;
            LitematicaPlusClient.LOGGER.warn("Session file {} was written by a newer version; keeping it read-only", this.path);
        }
        if (loadedData.scopes == null) {
            loadedData.scopes = new LinkedHashMap<>();
        }
        loadedData.scopes.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        for (Map.Entry<String, ScopeData> entry : loadedData.scopes.entrySet()) {
            entry.setValue(sanitize(entry.getValue()));
        }
        loadedData.schemaVersion = Math.max(loadedData.schemaVersion, SCHEMA_VERSION);
        this.data = loadedData;
    }

    private static FileData read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, FileData.class);
        } catch (IOException | RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not read session file {}", file, exception);
            return null;
        }
    }

    private static ScopeData copy(ScopeData scope) {
        return GSON.fromJson(GSON.toJson(scope), ScopeData.class);
    }

    static ScopeData sanitize(ScopeData scope) {
        if (scope.placements == null) {
            scope.placements = new ArrayList<>();
        }
        scope.placements.removeIf(entry -> entry == null || entry.id == null || entry.id.isBlank()
                || entry.file == null || entry.file.isBlank());
        if (scope.placements.size() > MAX_PLACEMENTS) {
            scope.placements = new ArrayList<>(scope.placements.subList(0, MAX_PLACEMENTS));
        }
        for (PlacementData entry : scope.placements) {
            entry.name = limit(entry.name, 96);
            entry.file = limit(entry.file, 4096);
            entry.rotation = entry.rotation == null ? "NONE" : entry.rotation;
            entry.mirror = entry.mirror == null ? "NONE" : entry.mirror;
            if (entry.disabledRegions == null) {
                entry.disabledRegions = new ArrayList<>();
            }
            entry.disabledRegions.removeIf(name -> name == null);
        }
        scope.selectedPlacement = scope.selectedPlacement == null ? "" : scope.selectedPlacement;
        scope.toolMode = scope.toolMode == null ? "" : scope.toolMode;
        if (scope.layer == null) {
            scope.layer = new LayerData();
        }
        scope.layer.mode = scope.layer.mode == null ? "ALL" : scope.layer.mode;
        scope.layer.axis = scope.layer.axis == null ? "Y" : scope.layer.axis;
        if (scope.area == null) {
            scope.area = new AreaData();
        }
        scope.area.name = limit(scope.area.name, 96);
        if (scope.area.boxes == null) {
            scope.area.boxes = new ArrayList<>();
        }
        scope.area.boxes.removeIf(box -> box == null || box.a != null && box.a.length != 3
                || box.b != null && box.b.length != 3);
        if (scope.area.boxes.size() > MAX_BOXES) {
            scope.area.boxes = new ArrayList<>(scope.area.boxes.subList(0, MAX_BOXES));
        }
        for (BoxData box : scope.area.boxes) {
            box.name = limit(box.name, 64);
        }
        scope.area.selected = Math.max(0, Math.min(scope.area.selected, Math.max(0, scope.area.boxes.size() - 1)));
        return scope;
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
