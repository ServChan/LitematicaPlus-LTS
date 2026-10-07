package org.lts.litematicaplus.placement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.SessionStore;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.schematic.LitematicCodec;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.share.SyncmaticaClient;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;

public final class PlacementManager {
    private static final PlacementManager INSTANCE = new PlacementManager();

    private final List<SchematicPlacement> placements = new ArrayList<>();
    private final Map<String, CachedSchematic> cache = new ConcurrentHashMap<>();
    private UUID selected;
    private Runnable changeListener = () -> { };

    private record CachedSchematic(long size, long modified, CompletableFuture<Schematic> result) {
        boolean matches(long size, long modified) {
            return this.size == size && this.modified == modified && !this.result.isCompletedExceptionally();
        }
    }

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
        try {
            Path path = Path.of(file);
            return path.isAbsolute() ? path.normalize() : schematicsDirectory().resolve(file).normalize();
        } catch (InvalidPathException exception) {
            return schematicsDirectory().resolve("invalid-path");
        }
    }

    public void invalidate(Path file) {
        this.cache.remove(cacheKey(file));
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

    public SchematicPlacement announcedSelection() {
        return LitematicaPlusConfig.current().materialHelperFollowsRender ? visibleSelection() : selected();
    }

    public SchematicPlacement visibleSelection() {
        SchematicPlacement placement = selected();
        return placement != null && placement.enabled() && LitematicaPlusConfig.current().renderEnabled ? placement : null;
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
        return add(new SchematicPlacement(UUID.randomUUID(), uniqueName(name), relativize(file), origin));
    }

    public SchematicPlacement createShared(Path file, String name, BlockPos origin, Rotation rotation, Mirror mirror,
            UUID serverId) {
        SchematicPlacement placement = new SchematicPlacement(UUID.randomUUID(), uniqueName(name.isBlank()
                ? SchematicIo.baseName(file) : name), relativize(file), origin);
        placement.setRotation(rotation);
        placement.setMirror(mirror);
        placement.setLocked(true);
        placement.setServerId(serverId);
        return add(placement);
    }

    private SchematicPlacement add(SchematicPlacement placement) {
        this.placements.add(placement);
        this.selected = placement.id();
        GhostEngine.get().sync(this.placements);
        load(placement, false);
        this.changeListener.run();
        return placement;
    }

    public SchematicPlacement byId(UUID id) {
        if (id == null) {
            return null;
        }
        for (SchematicPlacement placement : this.placements) {
            if (placement.id().equals(id)) {
                return placement;
            }
        }
        return null;
    }

    public void link(SchematicPlacement placement, UUID serverId) {
        placement.setServerId(serverId);
        if (serverId != null) {
            placement.setLocked(true);
        }
        this.changeListener.run();
    }

    public void forceLocked(SchematicPlacement placement, boolean locked) {
        placement.setLocked(locked);
        this.changeListener.run();
    }

    public void applyShared(SchematicPlacement placement, int x, int y, int z, Rotation rotation, Mirror mirror) {
        boolean geometry = placement.x() != x || placement.y() != y || placement.z() != z
                || placement.rotation() != rotation || placement.mirror() != mirror;
        placement.setOrigin(x, y, z);
        placement.setRotation(rotation);
        placement.setMirror(mirror);
        placement.setLocked(true);
        if (geometry) {
            GhostEngine.get().rebuild(placement);
        }
        this.changeListener.run();
    }

    public void remove(SchematicPlacement placement) {
        if (this.placements.contains(placement)) {
            SyncmaticaClient.get().forget(placement);
        }
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
        for (SchematicPlacement placement : this.placements) {
            SyncmaticaClient.get().forget(placement);
        }
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
        if (placement.locked() != locked && !SyncmaticaClient.get().allowLockChange(placement, locked)) {
            this.changeListener.run();
            return;
        }
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

    public void setReplacement(SchematicPlacement placement, Block from, Block to) {
        boolean changed = to == null || to == from ? placement.replacements().remove(from) != null
                : placement.replacements().put(from, to) != to;
        if (changed) {
            geometryChanged(placement);
        }
    }

    public void clearReplacements(SchematicPlacement placement) {
        if (!placement.replacements().isEmpty()) {
            placement.replacements().clear();
            geometryChanged(placement);
        }
    }

    private static Block blockById(String id) {
        Identifier parsed = id == null ? null : Identifier.tryParse(id);
        return parsed == null ? null : BuiltInRegistries.BLOCK.getOptional(parsed).orElse(null);
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
        int generation = placement.markLoading();
        CompletableFuture<Schematic> future = CompletableFuture.supplyAsync(() -> {
            try {
                return fetch(path, key);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, Workers.io());
        Minecraft minecraft = Minecraft.getInstance();
        future.whenComplete((schematic, failure) -> minecraft.execute(() -> {
            if (!this.placements.contains(placement) || placement.loadGeneration() != generation) {
                return;
            }
            if (failure != null || schematic == null) {
                LitematicaPlusClient.LOGGER.warn("Could not load schematic {}: {}", path,
                        failure == null ? "no data" : String.valueOf(failure.getMessage()));
                placement.markFailed(Notify.describe(failure));
            } else {
                placement.markReady(schematic);
                warnAboutContent(placement, schematic);
            }
            GhostEngine.get().rebuild(placement);
            this.changeListener.run();
        }));
    }

    private Schematic fetch(Path path, String key) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        long size = attributes.size();
        long modified = attributes.lastModifiedTime().toMillis();
        CachedSchematic fresh = new CachedSchematic(size, modified, new CompletableFuture<>());
        CachedSchematic entry = this.cache.compute(key, (ignored, previous) ->
                previous != null && previous.matches(size, modified) ? previous : fresh);
        if (entry == fresh) {
            try {
                fresh.result().complete(SchematicIo.read(path));
            } catch (IOException | RuntimeException | Error exception) {
                this.cache.remove(key, fresh);
                fresh.result().completeExceptionally(exception);
                throw exception;
            }
        }
        return entry.result().join();
    }

    private static void warnAboutContent(SchematicPlacement placement, Schematic schematic) {
        int unknown = schematic.metadata().unknownStates;
        if (unknown > 0) {
            LitematicaPlusClient.LOGGER.warn("Schematic {} has {} block states this game version does not know",
                    placement.file(), unknown);
            Notify.chat(Component.translatable("litematicaplus.message.unknown_blocks", placement.name(), unknown));
        } else if (schematic.metadata().dataVersion > BlockStateNbt.currentDataVersion()
                || "litematic".equals(schematic.metadata().format)
                && schematic.metadata().schematicVersion > LitematicCodec.SCHEMATIC_VERSION) {
            Notify.chat(Component.translatable("litematicaplus.message.newer_schematic", placement.name()));
        }
    }

    private void pruneCache() {
        List<String> used = new ArrayList<>();
        for (SchematicPlacement placement : this.placements) {
            used.add(cacheKey(resolve(placement.file())));
        }
        this.cache.keySet().removeIf(key -> !used.contains(key));
    }

    private static String cacheKey(Path path) {
        return path.toAbsolutePath().normalize().toString();
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
            placement.setServerId(parseUuid(entry.serverId));
            placement.disabledRegions().addAll(entry.disabledRegions);
            for (Map.Entry<String, String> pair : entry.replacements.entrySet()) {
                Block from = blockById(pair.getKey());
                Block to = blockById(pair.getValue());
                if (from != null && to != null && from != to) {
                    placement.replacements().put(from, to);
                }
            }
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
            entry.serverId = placement.serverId() == null ? "" : placement.serverId().toString();
            entry.disabledRegions = new ArrayList<>(placement.disabledRegions());
            for (Map.Entry<Block, Block> pair : placement.replacements().entrySet()) {
                entry.replacements.put(BuiltInRegistries.BLOCK.getKey(pair.getKey()).toString(),
                        BuiltInRegistries.BLOCK.getKey(pair.getValue()).toString());
            }
            data.placements.add(entry);
        }
        data.selectedPlacement = this.selected == null ? "" : this.selected.toString();
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return fallback;
        }
    }
}
