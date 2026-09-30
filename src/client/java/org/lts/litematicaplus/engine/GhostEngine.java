package org.lts.litematicaplus.engine;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.placement.PlacementTransform;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.render.MeshResult;
import org.lts.litematicaplus.render.SectionJob;
import org.lts.litematicaplus.render.SectionMesher;
import org.lts.litematicaplus.render.SpecialBlocks;
import org.lts.litematicaplus.render.ExternalMap;
import org.lts.litematicaplus.render.TopView;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.SectionVerdict;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class GhostEngine {
    private static final GhostEngine INSTANCE = new GhostEngine();
    private static final int SELECT_LIMIT = 48;
    private static final int QUEUE_LIMIT = 512;
    private static final double QUEUE_STALE_DISTANCE_SQUARED = 24.0 * 24.0;
    private static final int MAX_SLOT_FAILURES = 3;
    private static final int MAX_FAILURE_LOGS = 5;
    private static final int UNLOAD_MARGIN = 96;
    private static final long UPLOAD_BUDGET_NANOS = 1_000_000L;
    private long jobsStarted;
    private final ChangeCounter changes = new ChangeCounter();

    private record Completed(PlacementRuntime runtime, MeshResult result, BlockState[] world) {
    }

    private final Map<UUID, PlacementRuntime> runtimes = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<Completed> completed = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BlockState[]> worldPool = new ConcurrentLinkedQueue<>();
    private final long[] selectedKeys = new long[SELECT_LIMIT];
    private final double[] selectedDistances = new double[SELECT_LIMIT];
    private ClientLevel level;
    private BlockStateModelSet modelIdentity;
    private LayerRange layer = LayerRange.ALL;
    private SectionJob.Options options;
    private SectionJob.Options optionsWithoutGhost;
    private int inFlight;
    private long ticks;
    private int maxGhostIndices;
    private int maxFillIndices;
    private int maxLineIndices;
    private int failureLogs;
    private Vec3 cameraPosition;
    private final double[] heapDistances = new double[QUEUE_LIMIT];
    private final long[] heapKeys = new long[QUEUE_LIMIT];

    private GhostEngine() {
    }

    public static GhostEngine get() {
        return INSTANCE;
    }

    public Collection<PlacementRuntime> runtimes() {
        return Collections.unmodifiableCollection(this.runtimes.values());
    }

    public PlacementRuntime runtime(UUID id) {
        return this.runtimes.get(id);
    }

    public LayerRange layer() {
        return this.layer;
    }

    public int maxGhostIndices() {
        return this.maxGhostIndices;
    }

    public int maxFillIndices() {
        return this.maxFillIndices;
    }

    public int maxLineIndices() {
        return this.maxLineIndices;
    }

    public int pending() {
        int total = 0;
        for (PlacementRuntime runtime : this.runtimes.values()) {
            total += runtime.dirty.size();
        }
        return total;
    }

    public long jobsStarted() {
        return this.jobsStarted;
    }

    public int inFlight() {
        return this.inFlight;
    }

    public PlacementRuntime runtimeAt(int x, int y, int z) {
        if (!this.layer.contains(x, y, z)) {
            return null;
        }
        for (PlacementRuntime runtime : this.runtimes.values()) {
            PlacementVolume volume = runtime.volume;
            if (volume != null && runtime.placement().enabled() && volume.paletteIndexAt(x, y, z) >= 0) {
                return runtime;
            }
        }
        return null;
    }

    public BlockState schematicStateAt(int x, int y, int z) {
        PlacementRuntime runtime = runtimeAt(x, y, z);
        return runtime == null ? null : runtime.volume.stateAt(x, y, z);
    }

    public MismatchType categoryAt(int x, int y, int z) {
        PlacementRuntime runtime = runtimeAt(x, y, z);
        if (runtime == null) {
            return MismatchType.NONE;
        }
        SectionSlot slot = runtime.slots.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        if (slot == null || slot.verdict == null) {
            return MismatchType.UNKNOWN;
        }
        return slot.verdict.category(GhostSection.index(x, y, z));
    }

    public record Mismatch(BlockPos pos, MismatchType type, BlockState expected, double distanceSquared) {
    }

    public List<Mismatch> nearestMismatches(PlacementRuntime runtime, Vec3 from, int limit, boolean[] typeEnabled) {
        List<Mismatch> result = new ArrayList<>();
        PlacementVolume volume = runtime == null ? null : runtime.volume;
        if (volume == null) {
            return result;
        }
        List<SectionSlot> candidates = new ArrayList<>();
        for (SectionSlot slot : runtime.slots.values()) {
            if (slot.verdict != null && slot.verdict.errors() > 0) {
                candidates.add(slot);
            }
        }
        candidates.sort((a, b) -> Double.compare(distanceSquared(a, from), distanceSquared(b, from)));
        BlockState[] palette = volume.palette();
        for (SectionSlot slot : candidates) {
            if (result.size() >= limit * 3) {
                break;
            }
            GhostSection section = volume.section(slot.key);
            if (section == null) {
                continue;
            }
            byte[] categories = slot.verdict.categories();
            for (int index = 0; index < GhostSection.VOLUME; index++) {
                MismatchType type = MismatchType.VALUES[categories[index]];
                if (!type.isError() || !typeEnabled[type.ordinal()]) {
                    continue;
                }
                int x = slot.originX + (index & 15);
                int y = slot.originY + (index >> 8);
                int z = slot.originZ + (index >> 4 & 15);
                if (!this.layer.contains(x, y, z)) {
                    continue;
                }
                short value = section.get(index);
                BlockState expected = value == GhostSection.OUTSIDE ? Blocks.AIR.defaultBlockState() : palette[value & 0xFFFF];
                double dx = x + 0.5 - from.x;
                double dy = y + 0.5 - from.y;
                double dz = z + 0.5 - from.z;
                result.add(new Mismatch(new BlockPos(x, y, z), type, expected, dx * dx + dy * dy + dz * dz));
            }
        }
        result.sort((a, b) -> Double.compare(a.distanceSquared(), b.distanceSquared()));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    public void setLayer(LayerRange range) {
        LayerRange next = range == null ? LayerRange.ALL : range;
        if (!next.equals(this.layer)) {
            this.layer = next;
            markEverythingDirty();
        }
    }

    public void sync(List<SchematicPlacement> placements) {
        Set<UUID> alive = new HashSet<>();
        for (SchematicPlacement placement : placements) {
            alive.add(placement.id());
            if (!this.runtimes.containsKey(placement.id())) {
                this.runtimes.put(placement.id(), new PlacementRuntime(placement));
                rebuild(placement);
            }
        }
        List<UUID> removed = new ArrayList<>();
        for (UUID id : this.runtimes.keySet()) {
            if (!alive.contains(id)) {
                removed.add(id);
            }
        }
        for (UUID id : removed) {
            remove(id);
        }
    }

    public void remove(UUID id) {
        PlacementRuntime runtime = this.runtimes.remove(id);
        if (runtime != null) {
            runtime.buildToken++;
            runtime.generation++;
            freeSlots(runtime);
            runtime.volume = null;
        }
    }

    public void clear() {
        for (UUID id : new ArrayList<>(this.runtimes.keySet())) {
            remove(id);
        }
    }

    public void shutdown() {
        clear();
        Completed entry;
        while ((entry = this.completed.poll()) != null) {
            entry.result().free();
        }
    }

    public void rebuild(SchematicPlacement placement) {
        PlacementRuntime runtime = this.runtimes.get(placement.id());
        if (runtime == null) {
            runtime = new PlacementRuntime(placement);
            this.runtimes.put(placement.id(), runtime);
        }
        int token = ++runtime.buildToken;
        Schematic schematic = placement.schematic();
        if (schematic == null || !placement.enabled()) {
            runtime.building = false;
            runtime.generation++;
            freeSlots(runtime);
            runtime.volume = null;
            runtime.resetTotals(0);
            return;
        }
        runtime.building = true;
        PlacementTransform transform = placement.transform();
        Set<String> disabled = new HashSet<>(placement.disabledRegions());
        PlacementRuntime target = runtime;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Workers.io().execute(() -> {
                if (target.buildToken != token) {
                    return;
                }
                try {
                    PlacementVolume volume = PlacementVolume.build(schematic, transform, disabled);
                    minecraft.execute(() -> install(target, token, volume));
                } catch (RuntimeException | OutOfMemoryError exception) {
                    LitematicaPlusClient.LOGGER.error("Could not build placement '{}'", placement.name(), exception);
                    Component reason = Notify.describe(exception);
                    minecraft.execute(() -> {
                        if (target.buildToken == token) {
                            target.building = false;
                            placement.markFailed(reason);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException exception) {
            runtime.building = false;
        }
    }

    private void install(PlacementRuntime runtime, int token, PlacementVolume volume) {
        if (runtime.buildToken != token || this.runtimes.get(runtime.placement().id()) != runtime) {
            return;
        }
        runtime.generation++;
        freeSlots(runtime);
        runtime.volume = volume;
        runtime.building = false;
        runtime.resetTotals(volume.palette().length);
        for (Long2ObjectMap.Entry<GhostSection> entry : volume.sections().long2ObjectEntrySet()) {
            GhostSection section = entry.getValue();
            if (section.insideCount() == 0) {
                continue;
            }
            long key = entry.getLongKey();
            SectionSlot slot = new SectionSlot(key, SectionPos.x(key) << 4, SectionPos.y(key) << 4,
                    SectionPos.z(key) << 4, section.solidCount() > 0);
            runtime.slots.put(key, slot);
            runtime.dirty.add(key);
        }
        computeSpecials(runtime);
    }

    private void computeSpecials(PlacementRuntime runtime) {
        PlacementVolume volume = runtime.volume;
        if (volume == null) {
            runtime.specialByPalette = new boolean[0];
            return;
        }
        BlockState[] palette = volume.palette();
        boolean[] special = new boolean[palette.length];
        if (LitematicaPlusConfig.current().renderBlockEntities) {
            for (int i = 0; i < palette.length; i++) {
                special[i] = SpecialBlocks.isSpecial(palette[i]);
            }
        }
        runtime.specialByPalette = special;
    }

    private void freeSlots(PlacementRuntime runtime) {
        for (SectionSlot slot : runtime.slots.values()) {
            slot.closeBuffers();
        }
        runtime.slots.clear();
        runtime.dirty.clear();
        runtime.drawable.clear();
        runtime.urgent.clear();
        runtime.queueSize = 0;
        runtime.queuePos = 0;
    }

    private static void track(PlacementRuntime runtime, SectionSlot slot) {
        boolean wanted = slot.ghost != null || slot.fill != null || slot.lines != null || slot.specials.length > 0;
        if (wanted && slot.drawIndex < 0) {
            slot.drawIndex = runtime.drawable.size();
            runtime.drawable.add(slot);
        } else if (!wanted && slot.drawIndex >= 0) {
            int last = runtime.drawable.size() - 1;
            SectionSlot moved = runtime.drawable.get(last);
            runtime.drawable.set(slot.drawIndex, moved);
            moved.drawIndex = slot.drawIndex;
            runtime.drawable.remove(last);
            slot.drawIndex = -1;
        }
    }

    private static void closeSlot(PlacementRuntime runtime, SectionSlot slot) {
        slot.closeBuffers();
        track(runtime, slot);
    }

    public int changesLastMinute() {
        return this.changes.total(System.nanoTime() / 1_000_000_000L);
    }

    public void onBlockChanged(BlockPos pos) {
        if (this.runtimes.isEmpty()) {
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        long key = SectionPos.asLong(x >> 4, y >> 4, z >> 4);
        boolean counted = false;
        for (PlacementRuntime runtime : this.runtimes.values()) {
            PlacementVolume volume = runtime.volume;
            if (volume != null && volume.contains(x, y, z)) {
                if (!counted) {
                    counted = true;
                    this.changes.record(System.nanoTime() / 1_000_000_000L);
                }
                SectionSlot slot = runtime.slots.get(key);
                if (slot != null) {
                    markDirty(runtime, slot);
                    runtime.urgent.enqueue(key);
                }
            }
        }
    }

    public void onChunkChanged(int chunkX, int chunkZ) {
        if (this.runtimes.isEmpty()) {
            return;
        }
        for (PlacementRuntime runtime : this.runtimes.values()) {
            PlacementVolume volume = runtime.volume;
            if (volume == null || chunkX < (volume.minX() >> 4) - 1 || chunkX > (volume.maxX() >> 4) + 1
                    || chunkZ < (volume.minZ() >> 4) - 1 || chunkZ > (volume.maxZ() >> 4) + 1) {
                continue;
            }
            for (int sectionY = volume.minY() >> 4; sectionY <= volume.maxY() >> 4; sectionY++) {
                markDirty(runtime, runtime.slots.get(SectionPos.asLong(chunkX, sectionY, chunkZ)));
                completeBorder(runtime, runtime.slots.get(SectionPos.asLong(chunkX - 1, sectionY, chunkZ)));
                completeBorder(runtime, runtime.slots.get(SectionPos.asLong(chunkX + 1, sectionY, chunkZ)));
                completeBorder(runtime, runtime.slots.get(SectionPos.asLong(chunkX, sectionY, chunkZ - 1)));
                completeBorder(runtime, runtime.slots.get(SectionPos.asLong(chunkX, sectionY, chunkZ + 1)));
            }
        }
    }

    public void markEverythingDirty() {
        for (PlacementRuntime runtime : this.runtimes.values()) {
            for (SectionSlot slot : runtime.slots.values()) {
                markDirty(runtime, slot);
            }
        }
    }

    private static void completeBorder(PlacementRuntime runtime, SectionSlot slot) {
        if (slot != null && slot.borderIncomplete) {
            markDirty(runtime, slot);
        }
    }

    private static void markDirty(PlacementRuntime runtime, SectionSlot slot) {
        if (slot != null) {
            slot.revision++;
            runtime.dirty.add(slot.key);
        }
    }

    public void tick(Minecraft minecraft) {
        this.ticks++;
        ClientLevel current = minecraft.level;
        if (current != this.level) {
            this.level = current;
            this.cameraPosition = null;
            markEverythingDirty();
        }
        drain(LitematicaPlusConfig.current().meshUploadsPerFrame);
        if (current == null || minecraft.player == null || this.runtimes.isEmpty()) {
            return;
        }
        BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
        if (models != this.modelIdentity) {
            boolean reload = this.modelIdentity != null;
            this.modelIdentity = models;
            if (reload) {
                SpecialBlocks.clear();
                for (PlacementRuntime runtime : this.runtimes.values()) {
                    computeSpecials(runtime);
                }
                markEverythingDirty();
            }
        }
        refreshOptions();
        Vec3 camera = this.cameraPosition != null ? this.cameraPosition : minecraft.player.position();
        if (this.ticks % 10L == 0L) {
            sweep(current, camera);
        }
        schedule(minecraft, current, camera, models);
    }

    public void frame(Minecraft minecraft, Vec3 camera) {
        this.cameraPosition = camera;
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        drain(config.meshUploadsPerFrame);
        ClientLevel current = minecraft.level;
        if (current == null || current != this.level || minecraft.player == null || this.options == null
                || this.modelIdentity == null || this.runtimes.isEmpty()) {
            return;
        }
        schedule(minecraft, current, camera, this.modelIdentity);
    }

    private void refreshOptions() {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        boolean render = config.renderEnabled;
        SectionJob.Options next = new SectionJob.Options(render && config.renderGhostBlocks,
                render && config.renderOverlay, config.renderCollidingBlocks, config.lenientStates,
                config.trustHiddenBlocks && !Minecraft.getInstance().hasSingleplayerServer(),
                config.overlayThroughWalls, config.useWorldCache,
                config.showMissing, config.showExtra, config.showWrongBlock, config.showWrongState,
                config.overlayFillAlpha, config.overlayLineAlpha);
        if (!next.equals(this.options)) {
            boolean changed = this.options != null;
            this.options = next;
            this.optionsWithoutGhost = new SectionJob.Options(false, false, next.renderColliding(),
                    next.lenientStates(), next.trustHidden(), next.throughWalls(), next.worldCache(), next.showMissing(), next.showExtra(), next.showWrongBlock(),
                    next.showWrongState(), next.fillAlpha(), next.lineAlpha());
            if (changed) {
                for (PlacementRuntime runtime : this.runtimes.values()) {
                    computeSpecials(runtime);
                }
                markEverythingDirty();
            }
        }
    }

    public static boolean ghostsAnywhere() {
        return !LitematicaPlusConfig.current().ghostsOnlyLoaded || TopView.active();
    }

    private void sweep(ClientLevel current, Vec3 camera) {
        int distance = TopView.ghostRange(LitematicaPlusConfig.current().renderDistance);
        double near = (double) distance * distance;
        int margin = Math.max(UNLOAD_MARGIN, distance / 2);
        boolean keep = ExternalMap.active() || TopView.active();
        boolean anywhere = ghostsAnywhere();
        double far = (double) (distance + margin) * (distance + margin);
        for (PlacementRuntime runtime : this.runtimes.values()) {
            for (SectionSlot slot : runtime.slots.values()) {
                double range = distanceSquared(slot, camera);
                slot.chunkLoaded = current.hasChunk(slot.originX >> 4, slot.originZ >> 4);
                if (slot.ghostBuilt) {
                    if (range > far && !keep) {
                        closeSlot(runtime, slot);
                        slot.ghostBuilt = false;
                    }
                } else if (slot.built && !slot.inFlight && range <= near && !runtime.dirty.contains(slot.key)
                        && (anywhere || slot.chunkLoaded) && (slot.hasSolid || slot.verdict != null && slot.verdict.errors() > 0)) {
                    markDirty(runtime, slot);
                }
            }
        }
    }

    private static double distanceSquared(SectionSlot slot, Vec3 camera) {
        double dx = slot.originX + 8.0 - camera.x;
        double dy = slot.originY + 8.0 - camera.y;
        double dz = slot.originZ + 8.0 - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void schedule(Minecraft minecraft, ClientLevel current, Vec3 camera, BlockStateModelSet models) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        int threads = config.resolvedWorkerThreads();
        int capacity = threads * 4;
        if (this.inFlight >= capacity) {
            return;
        }
        ThreadPoolExecutor executor = Workers.mesh(threads);
        FluidStateModelSet fluids = minecraft.getModelManager().getFluidStateModelSet();
        long deadline = System.nanoTime() + config.snapshotBudgetMicros * 1000L;
        int configuredRange = TopView.ghostRange(config.renderDistance);
        double ghostRange = (double) configuredRange * configuredRange;
        boolean anywhere = ghostsAnywhere();
        for (PlacementRuntime runtime : this.runtimes.values()) {
            PlacementVolume volume = runtime.volume;
            if (volume == null || runtime.dirty.isEmpty() || !runtime.placement().enabled()) {
                continue;
            }
            int want = Math.min(SELECT_LIMIT, capacity - this.inFlight);
            if (want <= 0) {
                return;
            }
            int found = selectNearest(runtime, camera, want);
            for (int i = 0; i < found; i++) {
                SectionSlot slot = runtime.slots.get(this.selectedKeys[i]);
                if (slot == null) {
                    runtime.dirty.remove(this.selectedKeys[i]);
                    continue;
                }
                if (slot.inFlight || !runtime.dirty.contains(slot.key)) {
                    continue;
                }
                GhostSection section = volume.section(slot.key);
                if (section == null) {
                    runtime.dirty.remove(slot.key);
                    continue;
                }
                BlockState[] world = snapshot(current, SectionPos.x(slot.key), SectionPos.y(slot.key),
                        SectionPos.z(slot.key));
                boolean ghost = this.selectedDistances[i] <= ghostRange && (world != null || anywhere);
                slot.chunkLoaded = world != null;
                long[] border = world == null ? null : borders(current, SectionPos.x(slot.key), SectionPos.y(slot.key),
                        SectionPos.z(slot.key));
                slot.borderIncomplete = world != null && this.borderIncomplete;
                int unknownFaces = world == null ? 0 : this.unknownFaces;
                runtime.dirty.remove(slot.key);
                slot.inFlight = true;
                slot.pendingGhost = ghost;
                this.inFlight++;
                this.jobsStarted++;
                SectionVerdict remembered = world == null && slot.verdict != null && slot.verdict.worldKnown()
                        ? slot.verdict : null;
                SectionJob job = new SectionJob(slot.key, runtime.generation, slot.revision, section, volume,
                        runtime.specialByPalette, world, border, unknownFaces, remembered, this.layer, current, models,
                        fluids,
                        minecraft.getBlockColors(), ghost ? this.options : this.optionsWithoutGhost);
                try {
                    executor.execute(() -> this.completed.add(new Completed(runtime, SectionMesher.run(job), world)));
                } catch (RejectedExecutionException exception) {
                    this.inFlight--;
                    slot.inFlight = false;
                    runtime.dirty.add(slot.key);
                    release(world);
                    return;
                }
                if (this.inFlight >= capacity || System.nanoTime() > deadline) {
                    return;
                }
            }
        }
    }

    private int selectNearest(PlacementRuntime runtime, Vec3 camera, int want) {
        int found = 0;
        while (found < want && !runtime.urgent.isEmpty()) {
            found = select(runtime, runtime.urgent.dequeueLong(), camera, found);
        }
        if (found >= want) {
            return found;
        }
        double dx = camera.x - runtime.queueX;
        double dy = camera.y - runtime.queueY;
        double dz = camera.z - runtime.queueZ;
        if (runtime.queuePos >= runtime.queueSize || dx * dx + dy * dy + dz * dz > QUEUE_STALE_DISTANCE_SQUARED) {
            refillQueue(runtime, camera);
        }
        while (found < want && runtime.queuePos < runtime.queueSize) {
            found = select(runtime, runtime.queue[runtime.queuePos++], camera, found);
        }
        return found;
    }

    private int select(PlacementRuntime runtime, long key, Vec3 camera, int found) {
        SectionSlot slot = runtime.slots.get(key);
        if (slot == null || slot.inFlight || !runtime.dirty.contains(key)) {
            return found;
        }
        this.selectedKeys[found] = key;
        this.selectedDistances[found] = distanceSquared(slot, camera);
        return found + 1;
    }

    private void refillQueue(PlacementRuntime runtime, Vec3 camera) {
        int size = 0;
        LongIterator iterator = runtime.dirty.iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            SectionSlot slot = runtime.slots.get(key);
            if (slot == null) {
                iterator.remove();
                continue;
            }
            if (slot.inFlight) {
                continue;
            }
            double range = distanceSquared(slot, camera);
            if (size < QUEUE_LIMIT) {
                int position = size++;
                while (position > 0 && this.heapDistances[(position - 1) >> 1] < range) {
                    int parent = (position - 1) >> 1;
                    this.heapDistances[position] = this.heapDistances[parent];
                    this.heapKeys[position] = this.heapKeys[parent];
                    position = parent;
                }
                this.heapDistances[position] = range;
                this.heapKeys[position] = key;
            } else if (range < this.heapDistances[0]) {
                siftDown(range, key, size);
            }
        }
        if (runtime.queue.length < size) {
            runtime.queue = new long[QUEUE_LIMIT];
        }
        runtime.queueSize = size;
        runtime.queuePos = 0;
        runtime.queueX = camera.x;
        runtime.queueY = camera.y;
        runtime.queueZ = camera.z;
        for (int remaining = size; remaining > 0; remaining--) {
            runtime.queue[remaining - 1] = this.heapKeys[0];
            int last = remaining - 1;
            if (last > 0) {
                siftDown(this.heapDistances[last], this.heapKeys[last], last);
            }
        }
    }

    private void siftDown(double range, long key, int size) {
        int position = 0;
        while (true) {
            int child = position * 2 + 1;
            if (child >= size) {
                break;
            }
            if (child + 1 < size && this.heapDistances[child + 1] > this.heapDistances[child]) {
                child++;
            }
            if (this.heapDistances[child] <= range) {
                break;
            }
            this.heapDistances[position] = this.heapDistances[child];
            this.heapKeys[position] = this.heapKeys[child];
            position = child;
        }
        this.heapDistances[position] = range;
        this.heapKeys[position] = key;
    }

    private boolean borderIncomplete;
    private int unknownFaces;

    private LevelChunkSection neighbor(ClientLevel current, long[] bits, int face, int sectionX, int sectionY,
            int sectionZ) {
        LevelChunk chunk = current.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            SectionVerdict.closeBorderFace(bits, face);
            this.borderIncomplete = true;
            this.unknownFaces |= 1 << face;
            return null;
        }
        if (sectionY < chunk.getMinSectionY()) {
            SectionVerdict.closeBorderFace(bits, face);
            return null;
        }
        if (sectionY > chunk.getMaxSectionY()) {
            return null;
        }
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
        return section == null || section.hasOnlyAir() ? null : section;
    }

    private long[] borders(ClientLevel current, int sectionX, int sectionY, int sectionZ) {
        long[] bits = new long[SectionVerdict.BORDER_LONGS];
        this.borderIncomplete = false;
        this.unknownFaces = 0;
        for (int face = 0; face < 6; face++) {
            LevelChunkSection section = switch (face) {
                case SectionVerdict.FACE_DOWN -> neighbor(current, bits, face, sectionX, sectionY - 1, sectionZ);
                case SectionVerdict.FACE_UP -> neighbor(current, bits, face, sectionX, sectionY + 1, sectionZ);
                case SectionVerdict.FACE_NORTH -> neighbor(current, bits, face, sectionX, sectionY, sectionZ - 1);
                case SectionVerdict.FACE_SOUTH -> neighbor(current, bits, face, sectionX, sectionY, sectionZ + 1);
                case SectionVerdict.FACE_WEST -> neighbor(current, bits, face, sectionX - 1, sectionY, sectionZ);
                default -> neighbor(current, bits, face, sectionX + 1, sectionY, sectionZ);
            };
            if (section == null) {
                continue;
            }
            int fixed = (face & 1) == 0 ? 15 : 0;
            for (int b = 0; b < 16; b++) {
                for (int a = 0; a < 16; a++) {
                    BlockState state = face < 2 ? section.getBlockState(a, fixed, b)
                            : face < 4 ? section.getBlockState(a, b, fixed) : section.getBlockState(fixed, b, a);
                    SectionVerdict.markBorder(bits, face, a, b, state);
                }
            }
        }
        return bits;
    }

    private BlockState[] snapshot(ClientLevel current, int sectionX, int sectionY, int sectionZ) {
        LevelChunk chunk = current.getChunkSource().getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return null;
        }
        BlockState[] target = this.worldPool.poll();
        if (target == null) {
            target = new BlockState[GhostSection.VOLUME];
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        if (sectionY < chunk.getMinSectionY() || sectionY > chunk.getMaxSectionY()) {
            Arrays.fill(target, air);
            return target;
        }
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
        if (section == null || section.hasOnlyAir()) {
            Arrays.fill(target, air);
            return target;
        }
        int index = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    target[index++] = section.getBlockState(x, y, z);
                }
            }
        }
        return target;
    }

    private void release(BlockState[] world) {
        if (world != null && this.worldPool.size() < 64) {
            this.worldPool.add(world);
        }
    }

    public void drain(int limit) {
        int processed = 0;
        long deadline = System.nanoTime() + UPLOAD_BUDGET_NANOS;
        Completed entry;
        while (processed < limit && (processed == 0 || System.nanoTime() < deadline)
                && (entry = this.completed.poll()) != null) {
            processed++;
            this.inFlight--;
            release(entry.world());
            PlacementRuntime runtime = entry.runtime();
            MeshResult result = entry.result();
            SectionSlot slot = runtime.slots.get(result.sectionKey);
            if (result.generation != runtime.generation || slot == null) {
                result.free();
                continue;
            }
            slot.inFlight = false;
            if (result.failure != null) {
                if (this.failureLogs < MAX_FAILURE_LOGS) {
                    this.failureLogs++;
                    LitematicaPlusClient.LOGGER.error("Schematic section meshing failed", result.failure);
                }
                result.free();
                if (++slot.failures <= MAX_SLOT_FAILURES) {
                    runtime.dirty.add(slot.key);
                }
                continue;
            }
            if (result.revision != slot.revision) {
                result.free();
                runtime.urgent.enqueue(slot.key);
                continue;
            }
            slot.failures = 0;
            try {
                apply(runtime, slot, result);
            } catch (RuntimeException exception) {
                closeSlot(runtime, slot);
                if (this.failureLogs < MAX_FAILURE_LOGS) {
                    this.failureLogs++;
                    LitematicaPlusClient.LOGGER.error("Could not upload a schematic section", exception);
                }
            }
        }
    }

    private void apply(PlacementRuntime runtime, SectionSlot slot, MeshResult result) {
        if (slot.verdict != null) {
            runtime.subtract(slot.verdict);
        }
        slot.verdict = result.verdict;
        runtime.add(result.verdict);
        slot.closeBuffers();
        GpuDevice device = RenderSystem.getDevice();
        try {
            if (result.ghostData() != null && result.ghostVertices() >= 4) {
                slot.ghost = device.createBuffer(() -> "LitematicaPlus ghost section", GpuBuffer.USAGE_VERTEX,
                        result.ghostData());
                slot.ghostIndices = result.ghostVertices() / 4 * 6;
                this.maxGhostIndices = Math.max(this.maxGhostIndices, slot.ghostIndices);
            }
            if (result.fillData() != null && result.fillVertices() >= 4) {
                slot.fill = device.createBuffer(() -> "LitematicaPlus overlay fill", GpuBuffer.USAGE_VERTEX,
                        result.fillData());
                slot.fillIndices = result.fillVertices() / 4 * 6;
                this.maxFillIndices = Math.max(this.maxFillIndices, slot.fillIndices);
            }
            if (result.lineData() != null && result.lineVertices() >= 4) {
                slot.lines = device.createBuffer(() -> "LitematicaPlus overlay lines", GpuBuffer.USAGE_VERTEX,
                        result.lineData());
                slot.lineIndices = result.lineVertices() / 4 * 6;
                this.maxLineIndices = Math.max(this.maxLineIndices, slot.lineIndices);
            }
            slot.specials = result.specials;
        } finally {
            result.free();
        }
        slot.built = true;
        slot.ghostBuilt = slot.pendingGhost;
        track(runtime, slot);
    }
}
