package org.lts.litematicaplus.placement;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.SessionStore;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.util.Workers;

public final class PlacementManager {
    private static final PlacementManager INSTANCE = new PlacementManager();

    private final List<SchematicPlacement> placements = new ArrayList<>();
    private final Map<String, CompletableFuture<Schematic>> cache = new ConcurrentHashMap<>();
    private UUID selected;
    private Runnable changeListener = () -> { };

    private PlacementManager() {
    }

    public static PlacementManager get() {
        return INSTANCE;
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener == null ? () -> { } : listener;
    }

    public static Path schematicsDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(LitematicaPlusConfig.current().schematicsDirectory)
                .toAbsolutePath().normalize();
    }

    public static Path resolve(String file) {
        Path path = Path.of(file);
        return path.isAbsolute() ? path.normalize() : schematicsDirectory().resolve(file).normalize();
    }

    public static String relativize(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Path root = schematicsDirectory();
        if (absolute.startsWith(root)) {
            return root.relativize(absolute).toString().replace('\\', '/');
        }
        return absolute.toString();
    }

    public List<SchematicPlacement> placements() {
        return Collections.unmodifiableList(this.placements);
    }

    public SchematicPlacement selected() {
        if (this.selected == null) {
            return null;
        }
        for (SchematicPlacement placement : this.placements) {
            if (placement.id().equals(this.selected)) {
                return placement;
            }
        }
        return null;
    }

    public void select(SchematicPlacement placement) {
        this.selected = placement == null ? null : placement.id();
        this.changeListener.run();
    }

    public SchematicPlacement create(Path file, BlockPos origin) {
        String name = SchematicIo.baseName(file);
        SchematicPlacement placement = new SchematicPlacement(UUID.randomUUID(), uniqueName(name), relativize(file), origin);
        this.placements.add(placement);
        this.selected = placement.id();
        GhostEngine.get().sync(this.placements);
        load(placement, false);
        this.changeListener.run();
        return placement;
    }

    public void remove(SchematicPlacement placement) {
        if (this.placements.remove(placement)) {
            if (placement.id().equals(this.selected)) {
                this.selected = this.placements.isEmpty() ? null : this.placements.get(this.placements.size() - 1).id();
            }
            GhostEngine.get().remove(placement.id());
            pruneCache();
            this.changeListener.run();
        }
    }

    public void removeAll() {
        this.placements.clear();
        this.selected = null;
        GhostEngine.get().clear();
        this.cache.clear();
        this.changeListener.run();
    }

    public void move(SchematicPlacement placement, int x, int y, int z) {
        if (placement.locked() || placement.x() == x && placement.y() == y && placement.z() == z) {
            return;
        }
        placement.setOrigin(x, y, z);
        geometryChanged(placement);
    }

    public void rotate(SchematicPlacement placement, Rotation rotation) {
        if (placement.locked() || placement.rotation() == rotation) {
            return;
        }
        placement.setRotation(rotation);
        geometryChanged(placement);
    }

    public void mirror(SchematicPlacement placement, Mirror mirror) {
        if (placement.locked() || placement.mirror() == mirror) {
            return;
        }
        placement.setMirror(mirror);
        geometryChanged(placement);
    }

    public void setEnabled(SchematicPlacement placement, boolean enabled) {
        if (placement.enabled() != enabled) {
            placement.setEnabled(enabled);
            geometryChanged(placement);
        }
    }

    public void setLocked(SchematicPlacement placement, boolean locked) {
        placement.setLocked(locked);
        this.changeListener.run();
    }

    public void rename(SchematicPlacement placement, String name) {
        String clean = name == null ? "" : name.strip();
        if (!clean.isEmpty()) {
            placement.setName(clean.length() > 96 ? clean.substring(0, 96) : clean);
            this.changeListener.run();
        }
    }

    public void toggleRegion(SchematicPlacement placement, String region) {
        if (placement.locked()) {
            return;
        }
        if (!placement.disabledRegions().remove(region)) {
            placement.disabledRegions().add(region);
        }
        geometryChanged(placement);
    }

    public void reload(SchematicPlacement placement) {
        load(placement, true);
    }

    private void geometryChanged(SchematicPlacement placement) {
        GhostEngine.get().rebuild(placement);
        this.changeListener.run();
    }

    private void load(SchematicPlacement placement, boolean force) {
        Path path = resolve(placement.file());
        String key = cacheKey(path);
        if (force) {
            this.cache.remove(key);
        }
        placement.markLoading();
        CompletableFuture<Schematic> future = this.cache.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
            try {
                return SchematicIo.read(path);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, Workers.io()));
        Minecraft minecraft = Minecraft.getInstance();
        future.whenComplete((schematic, failure) -> minecraft.execute(() -> {
            if (!this.placements.contains(placement)) {
                return;
            }
            if (failure != null || schematic == null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause() : failure;
                String message = cause == null ? "unknown error" : String.valueOf(cause.getMessage());
                LitematicaPlusClient.LOGGER.warn("Could not load schematic {}: {}", path, message);
                this.cache.remove(key, future);
                placement.markFailed(message);
            } else {
                placement.markReady(schematic);
            }
            GhostEngine.get().rebuild(placement);
            this.changeListener.run();
        }));
    }

    private void pruneCache() {
        List<String> used = new ArrayList<>();
        for (SchematicPlacement placement : this.placements) {
            used.add(cacheKey(resolve(placement.file())));
        }
        this.cache.keySet().removeIf(key -> !used.contains(key));
    }

    private static String cacheKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private String uniqueName(String base) {
        String candidate = base;
        int counter = 2;
        while (nameTaken(candidate)) {
            candidate = base + " (" + counter++ + ")";
        }
        return candidate;
    }

    private boolean nameTaken(String name) {
        for (SchematicPlacement placement : this.placements) {
            if (placement.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void restore(SessionStore.ScopeData data) {
        this.placements.clear();
        this.selected = null;
        GhostEngine.get().clear();
        this.cache.clear();
        for (SessionStore.PlacementData entry : data.placements) {
            UUID id;
            try {
                id = UUID.fromString(entry.id);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            SchematicPlacement placement = new SchematicPlacement(id, entry.name, entry.file,
                    new BlockPos(entry.x, entry.y, entry.z));
            placement.setRotation(parse(Rotation.class, entry.rotation, Rotation.NONE));
            placement.setMirror(parse(Mirror.class, entry.mirror, Mirror.NONE));
            placement.setEnabled(entry.enabled);
            placement.setLocked(entry.locked);
            placement.disabledRegions().addAll(entry.disabledRegions);
            this.placements.add(placement);
            if (entry.id.equals(data.selectedPlacement)) {
                this.selected = id;
            }
        }
        GhostEngine.get().sync(this.placements);
        for (SchematicPlacement placement : this.placements) {
            load(placement, false);
        }
    }

    public void snapshot(SessionStore.ScopeData data) {
        data.placements.clear();
        for (SchematicPlacement placement : this.placements) {
            SessionStore.PlacementData entry = new SessionStore.PlacementData();
            entry.id = placement.id().toString();
            entry.name = placement.name();
            entry.file = placement.file();
            entry.x = placement.x();
            entry.y = placement.y();
            entry.z = placement.z();
            entry.rotation = placement.rotation().name();
            entry.mirror = placement.mirror().name();
            entry.enabled = placement.enabled();
            entry.locked = placement.locked();
            entry.disabledRegions = new ArrayList<>(placement.disabledRegions());
            data.placements.add(entry);
        }
        data.selectedPlacement = this.selected == null ? "" : this.selected.toString();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return fallback;
        }
    }
}
