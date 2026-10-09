package org.lts.litematicaplus.engine;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import net.minecraft.world.level.block.Block;
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
import org.lts.litematicaplus.render.SpecialArguments;
import org.lts.litematicaplus.render.SpecialBlocks;
import org.lts.litematicaplus.render.ExternalMap;
import org.lts.litematicaplus.render.LodDepth;
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
    private LayerRange scope;
    private SectionJob.Options options;
    private SectionJob.Options optionsWithoutGhost;
    private SectionJob.Options optionsOverlayOnly;
    private int inFlight;
    private boolean blockEntities;
    private boolean materialSwap = LitematicaPlusConfig.current().materialSwap;
    private long ticks;
    private int maxGhostIndices;
    private int maxFillIndices;
    private int maxLineIndices;
    private int failureLogs;
    private Vec3 cameraPosition;
    private final double[] heapDistances = new double[QUEUE_LIMIT];
    private final long[] heapKeys = new long[QUEUE_LIMIT];
    private final Long2ByteOpenHashMap sweepColumns = new Long2ByteOpenHashMap();
    private final ScopeTally.Scratch tallyScratch = new ScopeTally.Scratch();

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

    public boolean insideAnyVolume(int x, int y, int z) {
        for (PlacementRuntime runtime : this.runtimes.values()) {
            PlacementVolume volume = runtime.volume;
            if (volume != null && runtime.placement().enabled() && volume.paletteIndexAt(x, y, z) >= 0) {
                return true;
            }
        }
        return false;
    }

    public BlockState schematicStateAnywhere(int x, int y, int z) {
        for (PlacementRuntime runtime : this.runtimes.values()) {
            PlacementVolume volume = runtime.volume;
            if (volume != null && runtime.placement().enabled() && volume.paletteIndexAt(x, y, z) >= 0) {
                return volume.stateAt(x, y, z);
            }
        }
        return null;
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
        return category(runtime, x, y, z);
    }

    public record GhostLookup(MismatchType type, BlockState state) {
    }

    private static final GhostLookup NO_GHOST = new GhostLookup(MismatchType.NONE, null);

    public GhostLookup lookupAt(int x, int y, int z) {
        PlacementRuntime runtime = runtimeAt(x, y, z);
        if (runtime == null) {
            return NO_GHOST;
        }
        return new GhostLookup(category(runtime, x, y, z), runtime.volume.stateAt(x, y, z));
    }

    private static MismatchType category(PlacementRuntime runtime, int x, int y, int z) {
        SectionSlot slot = runtime.slots.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        if (slot == null || slot.verdict == null) {
            return MismatchType.UNKNOWN;
        }
        return slot.verdict.category(GhostSection.index(x, y, z));
    }

    public record Mismatch(BlockPos pos, MismatchType type, BlockState expected, double distanceSquared) {
    }

    private record Candidate(SectionSlot slot, double distance) {
    }

    private static final class MismatchHeap {
        private final double[] distances;
        private final SectionSlot[] slots;
        private final int[] indices;
        private final byte[] types;
        private int size;

        MismatchHeap(int capacity) {
            this.distances = new double[capacity];
            this.slots = new SectionSlot[capacity];
            this.indices = new int[capacity];
            this.types = new byte[capacity];
        }

        boolean full() {
            return this.size == this.distances.length;
        }

        double worst() {
            return this.distances[0];
        }

        void offer(double distance, SectionSlot slot, int index, byte type) {
            if (!full()) {
                int position = this.size++;
                while (position > 0) {
                    int parent = (position - 1) >> 1;
                    if (this.distances[parent] >= distance) {
                        break;
                    }
                    move(parent, position);
                    position = parent;
                }
                put(position, distance, slot, index, type);
                return;
            }
            if (distance >= this.distances[0]) {
                return;
            }
            int position = 0;
            while (true) {
                int child = position * 2 + 1;
                if (child >= this.size) {
                    break;
                }
                if (child + 1 < this.size && this.distances[child + 1] > this.distances[child]) {
                    child++;
                }
                if (this.distances[child] <= distance) {
                    break;
                }
                move(child, position);
                position = child;
            }
            put(position, distance, slot, index, type);
        }

        private void move(int from, int to) {
            this.distances[to] = this.distances[from];
            this.slots[to] = this.slots[from];
            this.indices[to] = this.indices[from];
            this.types[to] = this.types[from];
        }

        private void put(int position, double distance, SectionSlot slot, int index, byte type) {
            this.distances[position] = distance;
            this.slots[position] = slot;
            this.indices[position] = index;
            this.types[position] = type;
        }
    }

    public List<Mismatch> nearestMismatches(PlacementRuntime runtime, Vec3 from, int limit, boolean[] typeEnabled) {
        return nearestMismatches(runtime, from, limit, typeEnabled, null);
    }

    public List<Mismatch> nearestMismatches(PlacementRuntime runtime, Vec3 from, int limit, boolean[] typeEnabled,
            boolean[] paletteFilter) {
        List<Mismatch> result = new ArrayList<>();
        PlacementVolume volume = runtime == null ? null : runtime.volume;
        if (volume == null || limit <= 0) {
            return result;
        }
        boolean[] wanted = new boolean[MismatchType.VALUES.length];
        boolean any = false;
        for (MismatchType type : MismatchType.VALUES) {
            if (type.isError() && typeEnabled[type.ordinal()]) {
                wanted[type.ordinal()] = true;
                any = true;
            }
        }
        if (!any) {
            return result;
        }
        LayerRange range = this.layer;
        boolean layered = !range.unrestricted();
        List<Candidate> candidates = new ArrayList<>();
        for (SectionSlot slot : runtime.slots.values()) {
            SectionVerdict verdict = slot.verdict;
            if (verdict == null || verdict.errors() <= 0 || !hasWanted(verdict, wanted)) {
                continue;
            }
            if (layered && !range.intersects(slot.originX, slot.originY, slot.originZ, slot.originX + 15,
                    slot.originY + 15, slot.originZ + 15)) {
                continue;
            }
            if (volume.section(slot.key) != null) {
                candidates.add(new Candidate(slot, centreBoxDistanceSquared(slot, from)));
            }
        }
        candidates.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        MismatchHeap heap = new MismatchHeap(limit);
        for (Candidate candidate : candidates) {
            if (heap.full() && candidate.distance() > heap.worst()) {
                break;
            }
            SectionSlot slot = candidate.slot();
            byte[] categories = slot.verdict.categories();
            GhostSection filtered = paletteFilter == null ? null : volume.section(slot.key);
            for (int index = 0; index < GhostSection.VOLUME; index++) {
                byte category = categories[index];
                if (!wanted[category]) {
                    continue;
                }
                if (filtered != null) {
                    short value = filtered.get(index);
                    int paletteIndex = value & 0xFFFF;
                    if (value == GhostSection.OUTSIDE || paletteIndex >= paletteFilter.length || !paletteFilter[paletteIndex]) {
                        continue;
                    }
                }
                int x = slot.originX + (index & 15);
                int y = slot.originY + (index >> 8);
                int z = slot.originZ + (index >> 4 & 15);
                if (layered && !range.contains(x, y, z)) {
                    continue;
                }
                double dx = x + 0.5 - from.x;
                double dy = y + 0.5 - from.y;
                double dz = z + 0.5 - from.z;
                heap.offer(dx * dx + dy * dy + dz * dz, slot, index, category);
            }
        }
        BlockState[] palette = volume.palette();
        for (int i = 0; i < heap.size; i++) {
            SectionSlot slot = heap.slots[i];
            int index = heap.indices[i];
            GhostSection section = volume.section(slot.key);
            short value = section == null ? GhostSection.OUTSIDE : section.get(index);
            BlockState expected = value == GhostSection.OUTSIDE ? Blocks.AIR.defaultBlockState() : palette[value & 0xFFFF];
            result.add(new Mismatch(new BlockPos(slot.originX + (index & 15), slot.originY + (index >> 8),
                    slot.originZ + (index >> 4 & 15)), MismatchType.VALUES[heap.types[i]], expected, heap.distances[i]));
        }
        result.sort((a, b) -> Double.compare(a.distanceSquared(), b.distanceSquared()));
        return result;
    }

    private static boolean hasWanted(SectionVerdict verdict, boolean[] wanted) {
        int[] counts = verdict.counts();
        for (int i = 0; i < wanted.length; i++) {
            if (wanted[i] && counts[i] > 0) {
                return true;
            }
        }
        return false;
    }

    private static double centreBoxDistanceSquared(SectionSlot slot, Vec3 from) {
        double dx = axisGap(from.x, slot.originX + 0.5);
        double dy = axisGap(from.y, slot.originY + 0.5);
        double dz = axisGap(from.z, slot.originZ + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisGap(double value, double low) {
        double high = low + 15.0;
        return value < low ? low - value : value > high ? value - high : 0.0;
    }

    public void setLayer(LayerRange range) {
        LayerRange next = (range == null ? LayerRange.ALL : range).withClip(this.layer.clip());
        if (!next.equals(this.layer)) {
            this.layer = next;
            markEverythingDirty();
            refreshScope();
        }
    }

    public LayerRange.Clip clip() {
        return this.layer.clip();
    }

    public void setClip(LayerRange.Clip clip) {
        LayerRange next = this.layer.withClip(clip);
        if (!next.equals(this.layer)) {
            this.layer = next;
            markEverythingDirty();
            refreshScope();
        }
    }

    public LayerRange scope() {
        return this.scope;
    }

    private void refreshScope() {
        LayerRange desired = LitematicaPlusConfig.current().countVisibleOnly && !this.layer.unrestricted() ? this.layer : null;
        if (Objects.equals(desired, this.scope)) {
            return;
        }
        this.scope = desired;
        for (PlacementRuntime runtime : this.runtimes.values()) {
            rescope(runtime);
        }
    }

    private record TallyInput(SectionSlot slot, GhostSection section, SectionVerdict verdict) {
    }

    private void rescope(PlacementRuntime runtime) {
        runtime.resetScope();
        runtime.scope = this.scope;
        PlacementVolume volume = runtime.volume;
        if (this.scope == null || volume == null) {
            return;
        }
        List<TallyInput> inputs = new ArrayList<>(runtime.slots.size());
        for (SectionSlot slot : runtime.slots.values()) {
            inputs.add(new TallyInput(slot, volume.section(slot.key), slot.verdict));
        }
        if (inputs.isEmpty()) {
            return;
        }
        LayerRange target = this.scope;
        int generation = runtime.scopeGeneration;
        boolean[] solid = volume.solid();
        runtime.scopeReady = false;
        Minecraft minecraft = Minecraft.getInstance();
        Runnable work = () -> {
            try {
                ScopeTally.Scratch scratch = new ScopeTally.Scratch();
                ScopeTally[] tallies = new ScopeTally[inputs.size()];
                for (int i = 0; i < tallies.length; i++) {
                    if (runtime.scopeGeneration != generation) {
                        return;
                    }
                    TallyInput input = inputs.get(i);
                    tallies[i] = ScopeTally.compute(input.section(), solid, input.verdict(), target, input.slot().originX,
                            input.slot().originY, input.slot().originZ, scratch);
                }
                minecraft.execute(() -> installTallies(runtime, generation, inputs, tallies));
            } catch (Throwable exception) {
                LitematicaPlusClient.LOGGER.error("Could not count the visible part of placement '{}'",
                        runtime.placement().name(), exception);
                minecraft.execute(() -> abandonScope(runtime, generation));
            }
        };
        try {
            Workers.io().execute(work);
        } catch (RejectedExecutionException exception) {
            abandonScope(runtime, generation);
        }
    }

    private void abandonScope(PlacementRuntime runtime, int generation) {
        if (runtime.scopeGeneration != generation) {
            return;
        }
        runtime.resetScope();
        runtime.scope = null;
    }

    private void installTallies(PlacementRuntime runtime, int generation, List<TallyInput> inputs, ScopeTally[] tallies) {
        if (runtime.scopeGeneration != generation || this.runtimes.get(runtime.placement().id()) != runtime) {
            return;
        }
        for (int i = 0; i < tallies.length; i++) {
            SectionSlot slot = inputs.get(i).slot();
            if (slot.tally == null && slot.verdict == inputs.get(i).verdict() && runtime.slots.get(slot.key) == slot) {
                slot.tally = tallies[i];
                runtime.addTally(slot.tally, 1);
            }
        }
        runtime.scopeReady = true;
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
        LodDepth.close();
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
        Map<Block, Block> replacements = LitematicaPlusConfig.current().materialSwap
                ? new HashMap<>(placement.replacements()) : Map.of();
        PlacementRuntime target = runtime;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Workers.io().execute(() -> {
                if (target.buildToken != token) {
                    return;
                }
                try {
                    PlacementVolume volume = PlacementVolume.build(schematic, transform, disabled, replacements);
                    minecraft.execute(() -> install(target, token, volume));
                } catch (Throwable exception) {
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
        runtime.scope = this.scope;
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
        if (this.scope != null) {
            rescope(runtime);
        }
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
        runtime.specialSlots.clear();
        runtime.clearUrgent();
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
        boolean special = slot.specials.length > 0;
        if (special && slot.specialIndex < 0) {
            slot.specialIndex = runtime.specialSlots.size();
            runtime.specialSlots.add(slot);
        } else if (!special && slot.specialIndex >= 0) {
            int last = runtime.specialSlots.size() - 1;
            SectionSlot moved = runtime.specialSlots.get(last);
            runtime.specialSlots.set(slot.specialIndex, moved);
            moved.specialIndex = slot.specialIndex;
            runtime.specialSlots.remove(last);
            slot.specialIndex = -1;
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
        int sectionX = x >> 4;
        int sectionY = y >> 4;
        int sectionZ = z >> 4;
        int localX = x & 15;
        int localY = y & 15;
        int localZ = z & 15;
        boolean counted = false;
        for (PlacementRuntime runtime : this.runtimes.values()) {
            PlacementVolume volume = runtime.volume;
            if (volume == null || x < volume.minX() - 1 || x > volume.maxX() + 1 || y < volume.minY() - 1
                    || y > volume.maxY() + 1 || z < volume.minZ() - 1 || z > volume.maxZ() + 1) {
                continue;
            }
            if (!counted && volume.contains(x, y, z)) {
                counted = true;
                this.changes.record(System.nanoTime() / 1_000_000_000L);
            }
            touch(runtime, sectionX, sectionY, sectionZ);
            if (localX == 0) {
                touch(runtime, sectionX - 1, sectionY, sectionZ);
            } else if (localX == 15) {
                touch(runtime, sectionX + 1, sectionY, sectionZ);
            }
            if (localY == 0) {
                touch(runtime, sectionX, sectionY - 1, sectionZ);
            } else if (localY == 15) {
                touch(runtime, sectionX, sectionY + 1, sectionZ);
            }
            if (localZ == 0) {
                touch(runtime, sectionX, sectionY, sectionZ - 1);
            } else if (localZ == 15) {
                touch(runtime, sectionX, sectionY, sectionZ + 1);
            }
        }
    }

    private static void touch(PlacementRuntime runtime, int sectionX, int sectionY, int sectionZ) {
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        SectionSlot slot = runtime.slots.get(key);
        if (slot != null) {
            markDirty(runtime, slot);
            runtime.enqueueUrgent(key);
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
            forgetWorld();
            markEverythingDirty();
        }
        drain(LitematicaPlusConfig.current().meshUploadsPerFrame);
        if (current == null || minecraft.player == null || this.runtimes.isEmpty()) {
            return;
        }
        BlockStateModelSet models = refreshModels(minecraft);
        refreshOptions();
        Vec3 camera = this.cameraPosition != null ? this.cameraPosition : minecraft.player.position();
        if (this.ticks % 10L == 0L) {
            sweep(current, camera);
        }
        schedule(minecraft, current, camera, models);
    }

    public BlockStateModelSet refreshModels(Minecraft minecraft) {
        BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
        if (models != this.modelIdentity) {
            boolean reload = this.modelIdentity != null;
            this.modelIdentity = models;
            if (reload) {
                SpecialBlocks.clear();
                SpecialArguments.clear();
                for (PlacementRuntime runtime : this.runtimes.values()) {
                    computeSpecials(runtime);
                }
                markEverythingDirty();
            }
        }
        return models;
    }

    private void forgetWorld() {
        this.changes.reset();
        for (PlacementRuntime runtime : this.runtimes.values()) {
            runtime.generation++;
            if (runtime.volume != null) {
                runtime.resetTotals(runtime.volume.palette().length);
            }
            for (SectionSlot slot : runtime.slots.values()) {
                slot.verdict = null;
                slot.tally = null;
                slot.verdictRules = -1;
                slot.inFlight = false;
                slot.built = false;
                slot.ghostBuilt = false;
                slot.chunkLoaded = false;
                slot.borderIncomplete = false;
                closeSlot(runtime, slot);
            }
            if (this.scope != null && runtime.volume != null) {
                rescope(runtime);
            }
        }
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
        schedule(minecraft, current, camera, refreshModels(minecraft));
    }

    private void refreshOptions() {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (config.materialSwap != this.materialSwap) {
            this.materialSwap = config.materialSwap;
            for (PlacementRuntime runtime : new ArrayList<>(this.runtimes.values())) {
                if (!runtime.placement().replacements().isEmpty()) {
                    rebuild(runtime.placement());
                }
            }
        }
        boolean render = config.renderEnabled;
        SectionJob.Options next = new SectionJob.Options(render && config.renderGhostBlocks,
                render && config.renderOverlay, config.renderCollidingBlocks, config.lenientStates, config.looseBlocks,
                config.soilInterchangeable,
                config.trustHiddenBlocks && !Minecraft.getInstance().hasSingleplayerServer(),
                config.overlayThroughWalls, config.useWorldCache,
                config.showMissing, config.showExtra, config.showWrongBlock, config.showWrongState,
                config.overlayFillAlpha, config.overlayLineAlpha);
        boolean blockEntities = config.renderBlockEntities;
        refreshScope();
        if (!next.equals(this.options) || blockEntities != this.blockEntities) {
            boolean changed = this.options != null;
            this.options = next;
            this.blockEntities = blockEntities;
            this.optionsWithoutGhost = new SectionJob.Options(false, false, next.renderColliding(),
                    next.lenientStates(), next.looseBlocks(), next.soilSwap(), next.trustHidden(), next.throughWalls(),
                    next.worldCache(),
                    next.showMissing(), next.showExtra(), next.showWrongBlock(),
                    next.showWrongState(), next.fillAlpha(), next.lineAlpha());
            this.optionsOverlayOnly = new SectionJob.Options(false, next.overlay() && next.throughWalls(),
                    next.renderColliding(), next.lenientStates(), next.looseBlocks(), next.soilSwap(), next.trustHidden(),
                    next.throughWalls(),
                    next.worldCache(),
                    next.showMissing(), next.showExtra(), next.showWrongBlock(), next.showWrongState(), next.fillAlpha(),
                    next.lineAlpha());
            if (changed) {
                for (PlacementRuntime runtime : this.runtimes.values()) {
                    computeSpecials(runtime);
                }
                markEverythingDirty();
            }
        }
    }

    public static boolean ghostsAnywhere() {
        return !LitematicaPlusConfig.current().ghostsOnlyLoaded || TopView.active() || ExternalMap.active();
    }

    private void sweep(ClientLevel current, Vec3 camera) {
        int distance = TopView.ghostRange(LitematicaPlusConfig.current().renderDistance);
        double near = (double) distance * distance;
        int margin = Math.max(UNLOAD_MARGIN, distance / 2);
        boolean keep = ExternalMap.active() || TopView.active();
        boolean anywhere = ghostsAnywhere();
        double far = (double) (distance + margin) * (distance + margin);
        Long2ByteOpenHashMap columns = this.sweepColumns;
        columns.clear();
        for (PlacementRuntime runtime : this.runtimes.values()) {
            for (SectionSlot slot : runtime.slots.values()) {
                double range = distanceSquared(slot, camera);
                int chunkX = slot.originX >> 4;
                int chunkZ = slot.originZ >> 4;
                long column = (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
                byte known = columns.get(column);
                if (known == 0) {
                    known = current.hasChunk(chunkX, chunkZ) ? (byte) 1 : (byte) 2;
                    columns.put(column, known);
                }
                slot.chunkLoaded = known == 1;
                if (slot.ghostBuilt) {
                    if (range > far && !keep) {
                        closeSlot(runtime, slot);
                        slot.ghostBuilt = false;
                    }
                } else if (slot.built && !slot.inFlight && slot.failures <= MAX_SLOT_FAILURES && range <= near
                        && !runtime.dirty.contains(slot.key) && (anywhere || slot.chunkLoaded)
                        && (slot.hasSolid || slot.verdict != null && slot.verdict.errors() > 0)) {
                    markDirty(runtime, slot);
                }
            }
        }
        columns.clear();
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
        int rules = rules(this.options);
        int active = 0;
        for (PlacementRuntime runtime : this.runtimes.values()) {
            if (runtime.volume != null && !runtime.dirty.isEmpty() && runtime.placement().enabled()) {
                active++;
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            boolean urgentOnly = pass == 0;
            int remaining = active;
            for (PlacementRuntime runtime : this.runtimes.values()) {
                PlacementVolume volume = runtime.volume;
                if (volume == null || runtime.dirty.isEmpty() || !runtime.placement().enabled()) {
                    continue;
                }
                int free = capacity - this.inFlight;
                if (free <= 0) {
                    return;
                }
                int share = urgentOnly ? free : Math.max(1, (free + remaining - 1) / Math.max(1, remaining));
                remaining--;
                int want = Math.min(SELECT_LIMIT, share);
                int found = urgentOnly ? selectUrgent(runtime, camera, want) : selectNearest(runtime, camera, want);
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
                    boolean inRange = this.selectedDistances[i] <= ghostRange;
                    boolean ghost = inRange && (world != null || anywhere);
                    slot.chunkLoaded = world != null;
                    long[] border = world == null ? null : borders(current, SectionPos.x(slot.key),
                            SectionPos.y(slot.key), SectionPos.z(slot.key));
                    slot.borderIncomplete = world != null && this.borderIncomplete;
                    int unknownFaces = world == null ? 0 : this.unknownFaces;
                    runtime.dirty.remove(slot.key);
                    slot.inFlight = true;
                    slot.pendingGhost = ghost;
                    slot.pendingRules = rules;
                    this.inFlight++;
                    this.jobsStarted++;
                    SectionVerdict remembered = world == null && slot.verdict != null && slot.verdict.worldKnown()
                            && slot.verdictRules == rules ? slot.verdict : null;
                    SectionJob.Options jobOptions = ghost ? this.options
                            : inRange ? this.optionsOverlayOnly : this.optionsWithoutGhost;
                    SectionVerdict[] sources = jobOptions.overlay() ? new SectionVerdict[6] : null;
                    byte[][] neighbours = sources != null ? neighbourPlanes(runtime, slot.key, sources) : null;
                    slot.pendingPlanes = sources;
                    SectionJob job = new SectionJob(slot.key, runtime.generation, slot.revision, section, volume,
                            runtime.specialByPalette, world, border, unknownFaces, neighbours, remembered, this.layer,
                            current, models, fluids, minecraft.getBlockColors(), jobOptions);
                    try {
                        executor.execute(() -> this.completed.add(new Completed(runtime, SectionMesher.run(job), world)));
                    } catch (RejectedExecutionException exception) {
                        this.inFlight--;
                        slot.inFlight = false;
                        runtime.dirty.add(slot.key);
                        release(world);
                        requeue(runtime, i, found);
                        return;
                    }
                    if (this.inFlight >= capacity || System.nanoTime() > deadline) {
                        requeue(runtime, i + 1, found);
                        return;
                    }
                }
            }
        }
    }

    private static long neighbourKey(long key, int face) {
        int sectionX = SectionPos.x(key);
        int sectionY = SectionPos.y(key);
        int sectionZ = SectionPos.z(key);
        return switch (face) {
            case SectionVerdict.FACE_DOWN -> SectionPos.asLong(sectionX, sectionY - 1, sectionZ);
            case SectionVerdict.FACE_UP -> SectionPos.asLong(sectionX, sectionY + 1, sectionZ);
            case SectionVerdict.FACE_NORTH -> SectionPos.asLong(sectionX, sectionY, sectionZ - 1);
            case SectionVerdict.FACE_SOUTH -> SectionPos.asLong(sectionX, sectionY, sectionZ + 1);
            case SectionVerdict.FACE_WEST -> SectionPos.asLong(sectionX - 1, sectionY, sectionZ);
            default -> SectionPos.asLong(sectionX + 1, sectionY, sectionZ);
        };
    }

    private static byte[][] neighbourPlanes(PlacementRuntime runtime, long key, SectionVerdict[] sources) {
        byte[][] planes = null;
        for (int face = 0; face < 6; face++) {
            SectionSlot neighbour = runtime.slots.get(neighbourKey(key, face));
            if (neighbour == null || neighbour.verdict == null) {
                continue;
            }
            sources[face] = neighbour.verdict;
            byte[] categories = neighbour.verdict.categories();
            byte[] plane = new byte[256];
            for (int b = 0; b < 16; b++) {
                for (int a = 0; a < 16; a++) {
                    int index = switch (face) {
                        case SectionVerdict.FACE_DOWN -> GhostSection.index(a, 15, b);
                        case SectionVerdict.FACE_UP -> GhostSection.index(a, 0, b);
                        case SectionVerdict.FACE_NORTH -> GhostSection.index(a, b, 15);
                        case SectionVerdict.FACE_SOUTH -> GhostSection.index(a, b, 0);
                        case SectionVerdict.FACE_WEST -> GhostSection.index(15, b, a);
                        default -> GhostSection.index(0, b, a);
                    };
                    plane[a + b * 16] = categories[index];
                }
            }
            if (planes == null) {
                planes = new byte[6][];
            }
            planes[face] = plane;
        }
        return planes;
    }

    private static void refreshOverlayPlanes(PlacementRuntime runtime, SectionSlot slot, boolean ownOverlay) {
        PlacementVolume volume = runtime.volume;
        if (volume == null || slot.verdict == null) {
            return;
        }
        boolean[] solid = volume.solid();
        boolean stale = false;
        for (int face = 0; face < 6; face++) {
            SectionSlot neighbour = runtime.slots.get(neighbourKey(slot.key, face));
            if (neighbour == null) {
                continue;
            }
            int back = face ^ 1;
            if (!neighbour.inFlight && (neighbour.fill != null || neighbour.lines != null) && neighbour.verdict != null
                    && neighbour.usedPlanes != null && neighbour.usedPlanes[back] != slot.verdict
                    && planeStale(neighbour.verdict.categories(), neighbour.usedPlanes[back], slot.verdict,
                            volume.section(slot.key), solid, back)) {
                markDirty(runtime, neighbour);
            }
            if (ownOverlay && !stale && slot.usedPlanes != null && slot.usedPlanes[face] != neighbour.verdict
                    && planeStale(slot.verdict.categories(), slot.usedPlanes[face], neighbour.verdict,
                            volume.section(neighbour.key), solid, face)) {
                stale = true;
            }
        }
        if (stale) {
            markDirty(runtime, slot);
        }
    }

    private static boolean planeStale(byte[] viewer, SectionVerdict used, SectionVerdict current, GhostSection source,
            boolean[] solid, int face) {
        for (int b = 0; b < 16; b++) {
            for (int a = 0; a < 16; a++) {
                int index = sourceIndex(face, a, b);
                byte before = effectiveCategory(used, source, solid, index);
                byte after = effectiveCategory(current, source, solid, index);
                if (before == after) {
                    continue;
                }
                boolean beforeError = MismatchType.VALUES[before].isError();
                boolean afterError = MismatchType.VALUES[after].isError();
                if (!beforeError && !afterError) {
                    continue;
                }
                for (int vb = Math.max(0, b - 1); vb <= Math.min(15, b + 1); vb++) {
                    for (int va = Math.max(0, a - 1); va <= Math.min(15, a + 1); va++) {
                        byte category = viewer[viewerIndex(face, va, vb)];
                        if (beforeError && category == before || afterError && category == after) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static byte effectiveCategory(SectionVerdict verdict, GhostSection source, boolean[] solid, int index) {
        if (verdict != null) {
            return verdict.categories()[index];
        }
        short value = source == null ? GhostSection.OUTSIDE : source.get(index);
        int paletteIndex = value & 0xFFFF;
        return value != GhostSection.OUTSIDE && paletteIndex < solid.length && solid[paletteIndex]
                ? (byte) MismatchType.MISSING.ordinal() : (byte) MismatchType.NONE.ordinal();
    }

    private static int sourceIndex(int face, int a, int b) {
        return switch (face) {
            case SectionVerdict.FACE_DOWN -> GhostSection.index(a, 15, b);
            case SectionVerdict.FACE_UP -> GhostSection.index(a, 0, b);
            case SectionVerdict.FACE_NORTH -> GhostSection.index(a, b, 15);
            case SectionVerdict.FACE_SOUTH -> GhostSection.index(a, b, 0);
            case SectionVerdict.FACE_WEST -> GhostSection.index(15, b, a);
            default -> GhostSection.index(0, b, a);
        };
    }

    private static int viewerIndex(int face, int a, int b) {
        return switch (face) {
            case SectionVerdict.FACE_DOWN -> GhostSection.index(a, 0, b);
            case SectionVerdict.FACE_UP -> GhostSection.index(a, 15, b);
            case SectionVerdict.FACE_NORTH -> GhostSection.index(a, b, 0);
            case SectionVerdict.FACE_SOUTH -> GhostSection.index(a, b, 15);
            case SectionVerdict.FACE_WEST -> GhostSection.index(0, b, a);
            default -> GhostSection.index(15, b, a);
        };
    }

    private static int rules(SectionJob.Options options) {
        return options == null ? 0 : (options.lenientStates() ? 1 : 0) | (options.trustHidden() ? 2 : 0)
                | (options.looseBlocks() ? 4 : 0) | (options.soilSwap() ? 8 : 0);
    }

    private void requeue(PlacementRuntime runtime, int from, int found) {
        for (int i = from; i < found; i++) {
            if (runtime.dirty.contains(this.selectedKeys[i])) {
                runtime.enqueueUrgent(this.selectedKeys[i]);
            }
        }
    }

    private int selectUrgent(PlacementRuntime runtime, Vec3 camera, int want) {
        int found = 0;
        while (found < want && !runtime.urgent.isEmpty()) {
            found = select(runtime, runtime.dequeueUrgent(), camera, found);
        }
        return found;
    }

    private int selectNearest(PlacementRuntime runtime, Vec3 camera, int want) {
        int found = 0;
        while (found < want && !runtime.urgent.isEmpty()) {
            found = select(runtime, runtime.dequeueUrgent(), camera, found);
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
                runtime.enqueueUrgent(slot.key);
                runtime.dirty.add(slot.key);
                if (result.revision <= slot.appliedRevision) {
                    result.free();
                    continue;
                }
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
        slot.usedPlanes = slot.pendingPlanes;
        slot.pendingPlanes = null;
        slot.verdictRules = slot.pendingRules;
        slot.appliedRevision = result.revision;
        runtime.add(result.verdict);
        if (slot.tally != null) {
            runtime.addTally(slot.tally, -1);
            slot.tally = null;
        }
        if (runtime.scope != null && runtime.volume != null) {
            slot.tally = ScopeTally.compute(runtime.volume.section(slot.key), runtime.volume.solid(), result.verdict,
                    runtime.scope, slot.originX, slot.originY, slot.originZ, this.tallyScratch);
            runtime.addTally(slot.tally, 1);
        }
        refreshOverlayPlanes(runtime, slot, result.fillData() != null && result.fillVertices() >= 4
                || result.lineData() != null && result.lineVertices() >= 4);
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
