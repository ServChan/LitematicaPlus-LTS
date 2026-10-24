package org.lts.litematicaplus.placement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import org.lts.litematicaplus.schematic.EmbeddedPlacement;
import org.lts.litematicaplus.schematic.LitematicCodec;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.share.SharedPlacement;
import org.lts.litematicaplus.share.SyncmaticaClient;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;
import org.lts.litematicaplus.zones.ZonePlan;

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
            UUID serverId, List<SharedPlacement.RegionShift> shifts) {
        SchematicPlacement placement = new SchematicPlacement(UUID.randomUUID(), uniqueName(name.isBlank()
                ? SchematicIo.baseName(file) : name), relativize(file), origin);
        placement.setRotation(rotation);
        placement.setMirror(mirror);
        placement.setLocked(true);
        placement.setServerId(serverId);
        placement.setPendingShifts(List.copyOf(shifts));
        return add(placement);
    }

    public SchematicPlacement createEmbedded(Path file, EmbeddedPlacement saved) {
        SchematicPlacement placement = new SchematicPlacement(UUID.randomUUID(), uniqueName(SchematicIo.baseName(file)),
                relativize(file), new BlockPos(saved.x(), saved.y(), saved.z()));
        placement.setRotation(saved.rotation());
        placement.setMirror(saved.mirror());
        placement.regionPlacements().putAll(saved.regions());
        placement.disabledRegions().addAll(saved.disabledRegions());
        placement.setGrid(saved.grid());
        placement.setZonePlan(new ZonePlan(saved.zoneColumns(), saved.zoneRows(), saved.zoneFloors()));
        for (Map.Entry<String, String> pair : saved.replacements().entrySet()) {
            Block from = blockById(pair.getKey());
            Block to = blockById(pair.getValue());
            if (from != null && to != null && from != to) {
                placement.replacements().put(from, to);
            }
        }
        return add(placement);
    }

    public static EmbeddedPlacement embedded(SchematicPlacement placement, boolean workSettings, String dimension,
            String world) {
        Map<String, String> replacements = new LinkedHashMap<>();
        if (workSettings) {
            for (Map.Entry<Block, Block> pair : placement.replacements().entrySet()) {
                replacements.put(BuiltInRegistries.BLOCK.getKey(pair.getKey()).toString(),
                        BuiltInRegistries.BLOCK.getKey(pair.getValue()).toString());
            }
        }
        ZonePlan zones = workSettings ? placement.zonePlan() : ZonePlan.NONE;
        return new EmbeddedPlacement(placement.x(), placement.y(), placement.z(), placement.rotation(),
                placement.mirror(), dimension, world, placement.regionPlacements(), placement.disabledRegions(),
                placement.grid(), replacements, zones.columns(), zones.rows(), zones.floors());
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

    public void applyShared(SchematicPlacement placement, int x, int y, int z, Rotation rotation, Mirror mirror,
            List<SharedPlacement.RegionShift> shifts) {
        boolean geometry = placement.x() != x || placement.y() != y || placement.z() != z
                || placement.rotation() != rotation || placement.mirror() != mirror;
        placement.setOrigin(x, y, z);
        placement.setRotation(rotation);
        placement.setMirror(mirror);
        placement.setLocked(true);
        placement.setPendingShifts(List.copyOf(shifts));
        geometry |= resolveShifts(placement);
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

    public void setRegionPlacement(SchematicPlacement placement, String region, RegionPlacement value) {
        if (placement.locked()) {
            return;
        }
        RegionPlacement current = placement.regionPlacement(region);
        if (current.equals(value)) {
            return;
        }
        if (value == null || value.isIdentity()) {
            placement.regionPlacements().remove(region);
        } else {
            placement.regionPlacements().put(region, value);
        }
        geometryChanged(placement);
    }

    public void resetRegionPlacements(SchematicPlacement placement) {
        if (!placement.locked() && !placement.regionPlacements().isEmpty()) {
            placement.regionPlacements().clear();
            geometryChanged(placement);
        }
    }

    public void setGrid(SchematicPlacement placement, PlacementGrid grid) {
        if (placement.locked() || placement.grid().equals(grid)) {
            return;
        }
        placement.setGrid(grid);
        geometryChanged(placement);
    }

    public static List<SharedPlacement.RegionShift> sharedShifts(SchematicPlacement placement) {
        Schematic schematic = placement.schematic();
        if (schematic == null) {
            List<SharedPlacement.RegionShift> pending = placement.pendingShifts();
            return pending == null ? List.of() : pending;
        }
        List<SharedPlacement.RegionShift> shifts = new ArrayList<>();
        for (Map.Entry<String, RegionPlacement> entry : placement.regionPlacements().entrySet()) {
            SchematicRegion region = schematic.regions().get(entry.getKey());
            RegionPlacement value = entry.getValue();
            if (region != null && !value.isIdentity()) {
                shifts.add(new SharedPlacement.RegionShift(region.name(), region.posX() + value.offsetX(),
                        region.posY() + value.offsetY(), region.posZ() + value.offsetZ(), value.rotation().ordinal(),
                        value.mirror().ordinal()));
            }
        }
        return shifts;
    }

    private static boolean resolveShifts(SchematicPlacement placement) {
        Schematic schematic = placement.schematic();
        List<SharedPlacement.RegionShift> shifts = placement.pendingShifts();
        if (schematic == null || shifts == null) {
            return false;
        }
        placement.setPendingShifts(null);
        Map<String, RegionPlacement> before = Map.copyOf(placement.regionPlacements());
        placement.regionPlacements().clear();
        for (SharedPlacement.RegionShift shift : shifts) {
            SchematicRegion region = schematic.regions().get(shift.name());
            if (region == null) {
                continue;
            }
            RegionPlacement value = new RegionPlacement(shift.x() - region.posX(), shift.y() - region.posY(),
                    shift.z() - region.posZ(), Rotation.values()[Math.floorMod(shift.rotation(), 4)],
                    Mirror.values()[Math.floorMod(shift.mirror(), 3)]);
            if (!value.isIdentity()) {
                placement.regionPlacements().put(region.name(), value);
            }
        }
        return !before.equals(placement.regionPlacements());
    }

    public void setZonePlan(SchematicPlacement placement, ZonePlan plan) {
        if (placement.zonePlan().equals(plan)) {
            return;
        }
        placement.setZonePlan(plan);
        placement.zoneClaims().clear();
        this.changeListener.run();
    }

    public void setZoneClaim(SchematicPlacement placement, int zone, String player) {
        boolean changed = player == null ? placement.zoneClaims().remove(zone) != null
                : !player.equals(placement.zoneClaims().put(zone, player));
        if (changed) {
            this.changeListener.run();
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
                if (failure == null) {
                    LitematicaPlusClient.LOGGER.warn("Could not load schematic {}; it has no data", path);
                } else {
                    LitematicaPlusClient.LOGGER.warn("Could not load schematic {}", path, failure);
                }
                placement.markFailed(Notify.describe(failure));
            } else {
                placement.markReady(schematic);
                resolveShifts(placement);
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
            LitematicaPlusClient.LOGGER.warn("Schematic {} has {} block states this game version does not know; they are skipped",
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
            if (entry.regions != null) {
                for (Map.Entry<String, SessionStore.RegionData> pair : entry.regions.entrySet()) {
                    SessionStore.RegionData region = pair.getValue();
                    if (region == null) {
                        continue;
                    }
                    RegionPlacement value = new RegionPlacement(region.x, region.y, region.z,
                            parse(Rotation.class, region.rotation, Rotation.NONE),
                            parse(Mirror.class, region.mirror, Mirror.NONE));
                    if (!value.isIdentity()) {
                        placement.regionPlacements().put(pair.getKey(), value);
                    }
                }
            }
            placement.setZonePlan(new ZonePlan(entry.zoneColumns, entry.zoneRows, entry.zoneFloors));
            if (entry.zoneClaims != null) {
                for (Map.Entry<String, String> claim : entry.zoneClaims.entrySet()) {
                    try {
                        int zone = Integer.parseInt(claim.getKey());
                        if (zone >= 1 && zone <= placement.zonePlan().count() && claim.getValue() != null
                                && !claim.getValue().isBlank()) {
                            placement.zoneClaims().put(zone, claim.getValue());
                        }
                    } catch (NumberFormatException exception) {
                        continue;
                    }
                }
            }
            if (entry.grid != null) {
                SessionStore.GridData grid = entry.grid;
                placement.setGrid(new PlacementGrid(grid.negativeX, grid.positiveX, grid.negativeY, grid.positiveY,
                        grid.negativeZ, grid.positiveZ, grid.stepX, grid.stepY, grid.stepZ));
            }
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
            for (Map.Entry<String, RegionPlacement> pair : placement.regionPlacements().entrySet()) {
                SessionStore.RegionData region = new SessionStore.RegionData();
                region.x = pair.getValue().offsetX();
                region.y = pair.getValue().offsetY();
                region.z = pair.getValue().offsetZ();
                region.rotation = pair.getValue().rotation().name();
                region.mirror = pair.getValue().mirror().name();
                entry.regions.put(pair.getKey(), region);
            }
            entry.zoneColumns = placement.zonePlan().columns();
            entry.zoneRows = placement.zonePlan().rows();
            entry.zoneFloors = placement.zonePlan().floors();
            for (Map.Entry<Integer, String> claim : placement.zoneClaims().entrySet()) {
                entry.zoneClaims.put(Integer.toString(claim.getKey()), claim.getValue());
            }
            PlacementGrid grid = placement.grid();
            entry.grid.negativeX = grid.negativeX();
            entry.grid.positiveX = grid.positiveX();
            entry.grid.negativeY = grid.negativeY();
            entry.grid.positiveY = grid.positiveY();
            entry.grid.negativeZ = grid.negativeZ();
            entry.grid.positiveZ = grid.positiveZ();
            entry.grid.stepX = grid.stepX();
            entry.grid.stepY = grid.stepY();
            entry.grid.stepZ = grid.stepZ();
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
