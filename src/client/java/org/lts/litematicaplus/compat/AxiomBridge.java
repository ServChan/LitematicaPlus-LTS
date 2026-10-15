package org.lts.litematicaplus.compat;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.world.GhostSection;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class AxiomBridge {
    public static final String MOD_ID = "axiom";
    public static final long MAX_BLOCKS = 1L << 24;

    public record Bounds(BlockPos min, BlockPos max) {
    }

    public record Copied(Map<Long, BlockState> blocks, Map<Long, CompoundTag> blockEntities, List<CompoundTag> entities,
            String name) {
    }

    public record Prepared(Object region, Object blockEntities, List<CompoundTag> entities, boolean containsAir,
            BlockPos target, int blocks, int skippedEntities) {
    }

    private static final Api API = Api.load();
    private static final Map<Block, String> BLOCK_ENTITY_IDS = new HashMap<>();

    private AxiomBridge() {
    }

    public static boolean installed() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static boolean available() {
        return API != null;
    }

    public static boolean active() {
        return API != null && API.call(API.isAxiomActive, null) instanceof Boolean value && value;
    }

    public static boolean editorActive() {
        if (API == null) {
            return false;
        }
        return API.call(API.editorActive, null) instanceof Boolean value && value;
    }

    public static boolean pausesInput() {
        return API != null && LitematicaPlusConfig.current().axiomEditorPause && editorActive();
    }

    public static Path blueprintsDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("axiom").resolve("blueprints").toAbsolutePath().normalize();
    }

    public static Bounds selection() {
        if (API == null) {
            return null;
        }
        Object buffer = API.call(API.getSelectionBuffer, null);
        if (buffer == null || Boolean.TRUE.equals(API.call(API.bufferIsEmpty, buffer))) {
            return null;
        }
        Object min = API.call(API.bufferMin, buffer);
        Object max = API.call(API.bufferMax, buffer);
        return min instanceof BlockPos low && max instanceof BlockPos high ? new Bounds(low.immutable(), high.immutable()) : null;
    }

    public static boolean select(List<Bounds> boxes) {
        if (API == null || boxes.isEmpty()) {
            return false;
        }
        try {
            Bounds first = boxes.getFirst();
            Object buffer = API.aabbConstructor.newInstance(first.min(), first.max());
            API.setBufferWithHistory.invoke(null, buffer);
            for (int i = 1; i < boxes.size(); i++) {
                API.addAabb.invoke(null, boxes.get(i).min(), boxes.get(i).max());
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            API.report(exception);
            return false;
        }
    }

    public static Copied copyClipboard() {
        if (API == null) {
            return null;
        }
        Object clipboard = API.call(API.getClipboard, API.clipboard);
        if (clipboard == null) {
            return null;
        }
        Object region = API.call(API.clipboardRegion, clipboard);
        if (region == null || Boolean.TRUE.equals(API.call(API.regionIsEmpty, region))) {
            return null;
        }
        Map<Long, BlockState> blocks = new HashMap<>();
        Object consumer = Proxy.newProxyInstance(API.positionConsumer.getClassLoader(), new Class<?>[] {API.positionConsumer},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> "AxiomClipboardReader";
                        };
                    }
                    if (args != null && args.length == 4 && args[3] instanceof BlockState state) {
                        blocks.put(BlockPos.asLong((int) args[0], (int) args[1], (int) args[2]), state);
                    }
                    return null;
                });
        try {
            API.forEachEntry.invoke(region, consumer);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            API.report(exception);
            return null;
        }
        Map<Long, CompoundTag> blockEntities = new HashMap<>();
        if (API.call(API.clipboardBlockEntities, clipboard) instanceof Long2ObjectMap<?> compressed) {
            for (Long2ObjectMap.Entry<?> entry : compressed.long2ObjectEntrySet()) {
                if (API.call(API.decompress, entry.getValue()) instanceof CompoundTag tag) {
                    blockEntities.put(entry.getLongKey(), tag.copy());
                }
            }
        }
        List<CompoundTag> entities = new ArrayList<>();
        if (API.call(API.clipboardEntities, clipboard) instanceof List<?> list) {
            for (Object entity : list) {
                if (entity instanceof CompoundTag tag) {
                    entities.add(tag.copy());
                }
            }
        }
        Object name = API.call(API.clipboardName, clipboard);
        return new Copied(blocks, blockEntities, entities, name instanceof String text ? text : "");
    }

    public static Schematic toSchematic(Copied copied, String name, String author) {
        if (copied.blocks().isEmpty()) {
            return null;
        }
        int lowX = Integer.MAX_VALUE;
        int lowY = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int highY = Integer.MIN_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (long key : copied.blocks().keySet()) {
            lowX = Math.min(lowX, BlockPos.getX(key));
            lowY = Math.min(lowY, BlockPos.getY(key));
            lowZ = Math.min(lowZ, BlockPos.getZ(key));
            highX = Math.max(highX, BlockPos.getX(key));
            highY = Math.max(highY, BlockPos.getY(key));
            highZ = Math.max(highZ, BlockPos.getZ(key));
        }
        RegionBuilder builder = new RegionBuilder(name, 0, 0, 0, highX - lowX + 1, highY - lowY + 1, highZ - lowZ + 1);
        builder.fill(Blocks.STRUCTURE_VOID.defaultBlockState());
        long count = 0L;
        for (Map.Entry<Long, BlockState> entry : copied.blocks().entrySet()) {
            long key = entry.getKey();
            BlockState state = entry.getValue();
            if (state.is(Blocks.VOID_AIR)) {
                continue;
            }
            int x = BlockPos.getX(key) - lowX;
            int y = BlockPos.getY(key) - lowY;
            int z = BlockPos.getZ(key) - lowZ;
            builder.set(x, y, z, state);
            if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID)) {
                count++;
            }
            CompoundTag tile = copied.blockEntities().get(key);
            String id = tile == null ? null : blockEntityId(state);
            if (id != null) {
                CompoundTag data = tile.copy();
                data.putString("id", id);
                builder.blockEntity(x, y, z, data);
            }
        }
        for (CompoundTag entity : copied.entities()) {
            ListTag pos = entity.getListOrEmpty("Pos");
            if (pos.size() < 3 || entity.getStringOr("id", "").isEmpty()) {
                continue;
            }
            CompoundTag tag = entity.copy();
            tag.put("Pos", doubles(pos.getDoubleOr(0, 0.0) - lowX, pos.getDoubleOr(1, 0.0) - lowY,
                    pos.getDoubleOr(2, 0.0) - lowZ));
            int[] hanging = tag.getIntArray("block_pos").orElse(null);
            if (hanging != null && hanging.length == 3) {
                tag.putIntArray("block_pos", new int[] {hanging[0] - lowX, hanging[1] - lowY, hanging[2] - lowZ});
            }
            tag.remove("UUID");
            builder.entity(tag);
        }
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = name;
        metadata.author = author;
        metadata.totalBlocks = count;
        metadata.timeCreated = System.currentTimeMillis();
        metadata.timeModified = metadata.timeCreated;
        metadata.dataVersion = BlockStateNbt.currentDataVersion();
        return new Schematic(metadata, Map.of(name, builder.build()));
    }

    public static Prepared prepare(PlacementVolume volume, LayerRange layer, boolean withEntities) throws ReflectiveOperationException {
        if (API == null) {
            throw new IllegalStateException("Axiom is not available");
        }
        Object region = API.regionConstructor.newInstance();
        int originX = volume.minX();
        int originY = volume.minY();
        int originZ = volume.minZ();
        BlockState[] palette = volume.palette();
        boolean containsAir = false;
        int blocks = 0;
        for (Long2ObjectMap.Entry<GhostSection> entry : volume.sections().long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int baseX = SectionPos.x(key) << 4;
            int baseY = SectionPos.y(key) << 4;
            int baseZ = SectionPos.z(key) << 4;
            if (!layer.unrestricted() && !layer.intersects(baseX, baseY, baseZ, baseX + 15, baseY + 15, baseZ + 15)) {
                continue;
            }
            short[] raw = entry.getValue().raw();
            for (int index = 0; index < GhostSection.VOLUME; index++) {
                short value = raw[index];
                if (value == GhostSection.OUTSIDE) {
                    continue;
                }
                int x = baseX + (index & 15);
                int y = baseY + (index >> 8);
                int z = baseZ + (index >> 4 & 15);
                if (!layer.contains(x, y, z)) {
                    continue;
                }
                BlockState state = palette[value & 0xFFFF];
                if (state.is(Blocks.STRUCTURE_VOID)) {
                    continue;
                }
                if (++blocks > MAX_BLOCKS) {
                    throw new IllegalStateException("Too many blocks for one Axiom placement");
                }
                containsAir |= state.isAir();
                API.addBlock.invoke(region, x - originX, y - originY, z - originZ, state);
            }
        }
        if (blocks == 0) {
            return null;
        }
        API.dirtyAll.invoke(region);
        Long2ObjectOpenHashMap<Object> compressed = new Long2ObjectOpenHashMap<>();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (Long2ObjectMap.Entry<CompoundTag> entry : volume.blockEntities().long2ObjectEntrySet()) {
            int x = BlockPos.getX(entry.getLongKey());
            int y = BlockPos.getY(entry.getLongKey());
            int z = BlockPos.getZ(entry.getLongKey());
            if (!layer.contains(x, y, z) || !volume.stateAt(x, y, z).hasBlockEntity()) {
                continue;
            }
            CompoundTag data = BlockStateNbt.fixBlockEntity(entry.getValue(), volume.dataVersion()).copy();
            data.remove("x");
            data.remove("y");
            data.remove("z");
            data.remove("id");
            buffer.reset();
            compressed.put(BlockPos.asLong(x - originX, y - originY, z - originZ), API.compress.invoke(null, data, buffer));
        }
        List<CompoundTag> entities = new ArrayList<>();
        int skipped = 0;
        if (withEntities) {
            for (PlacementVolume.EntitySeed seed : volume.entities()) {
                if (!layer.contains((int) Math.floor(seed.x()), (int) Math.floor(seed.y()), (int) Math.floor(seed.z()))) {
                    continue;
                }
                if (seed.rotation() != Rotation.NONE || seed.mirror() != Mirror.NONE) {
                    skipped++;
                    continue;
                }
                CompoundTag tag = seed.tag().copy();
                double shiftX = seed.x() - tag.getListOrEmpty("Pos").getDoubleOr(0, seed.x());
                double shiftY = seed.y() - tag.getListOrEmpty("Pos").getDoubleOr(1, seed.y());
                double shiftZ = seed.z() - tag.getListOrEmpty("Pos").getDoubleOr(2, seed.z());
                tag.put("Pos", doubles(seed.x() - originX, seed.y() - originY, seed.z() - originZ));
                int[] hanging = tag.getIntArray("block_pos").orElse(null);
                if (hanging != null && hanging.length == 3) {
                    tag.putIntArray("block_pos", new int[] {(int) Math.round(hanging[0] + shiftX) - originX,
                            (int) Math.round(hanging[1] + shiftY) - originY, (int) Math.round(hanging[2] + shiftZ) - originZ});
                }
                tag.remove("UUID");
                entities.add(tag);
            }
        }
        BlockPos min = (BlockPos) API.regionMin.invoke(region);
        BlockPos max = (BlockPos) API.regionMax.invoke(region);
        BlockPos target = new BlockPos(originX - (-(min.getX() + max.getX())) / 2,
                originY - (-(min.getY() + max.getY())) / 2, originZ - (-(min.getZ() + max.getZ())) / 2);
        return new Prepared(region, compressed, entities, containsAir, target, blocks, skipped);
    }

    public static boolean startPlacement(Prepared prepared, String description) {
        if (API == null) {
            return false;
        }
        try {
            API.startPlacement.invoke(API.placement, prepared.target(), prepared.region(), prepared.blockEntities(),
                    prepared.entities(), prepared.containsAir(), description);
            return Boolean.TRUE.equals(API.isPlacing.invoke(API.placement));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            API.report(exception);
            return false;
        }
    }

    public static boolean toClipboard(Prepared prepared, String name) {
        if (API == null) {
            return false;
        }
        try {
            API.setClipboard.invoke(API.clipboard, prepared.region(), prepared.blockEntities(), prepared.entities(), name,
                    0.0F, prepared.containsAir());
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            API.report(exception);
            return false;
        }
    }

    private static synchronized String blockEntityId(BlockState state) {
        return BLOCK_ENTITY_IDS.computeIfAbsent(state.getBlock(), block -> {
            if (!state.hasBlockEntity()) {
                return null;
            }
            for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
                if (type.isValid(state)) {
                    return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).toString();
                }
            }
            return null;
        });
    }

    private static ListTag doubles(double x, double y, double z) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(x));
        list.add(DoubleTag.valueOf(y));
        list.add(DoubleTag.valueOf(z));
        return list;
    }

    private record Api(Method isAxiomActive, Method editorActive, Method getSelectionBuffer, Method bufferIsEmpty,
            Method bufferMin, Method bufferMax, Constructor<?> aabbConstructor, Method setBufferWithHistory, Method addAabb,
            Object clipboard, Method getClipboard, Method setClipboard, Method clipboardRegion,
            Method clipboardBlockEntities, Method clipboardEntities, Method clipboardName, Class<?> positionConsumer,
            Method forEachEntry, Method regionIsEmpty, Constructor<?> regionConstructor, Method addBlock, Method dirtyAll,
            Method regionMin, Method regionMax, Method compress, Method decompress, Object placement,
            Method startPlacement, Method isPlacing) {
        static Api load() {
            if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
                return null;
            }
            try {
                ClassLoader loader = AxiomBridge.class.getClassLoader();
                Class<?> client = Class.forName("com.moulberry.axiom.AxiomClient", false, loader);
                Class<?> editor = Class.forName("com.moulberry.axiom.editor.EditorUI", false, loader);
                Class<?> selection = Class.forName("com.moulberry.axiom.clipboard.Selection", false, loader);
                Class<?> buffer = Class.forName("com.moulberry.axiom.clipboard.SelectionBuffer", false, loader);
                Class<?> aabb = Class.forName("com.moulberry.axiom.clipboard.SelectionBuffer$AABB", false, loader);
                Class<?> clipboard = Class.forName("com.moulberry.axiom.clipboard.Clipboard", false, loader);
                Class<?> clipboardObject = Class.forName("com.moulberry.axiom.clipboard.ClipboardObject", false, loader);
                Class<?> region = Class.forName("com.moulberry.axiom.render.regions.ChunkedBlockRegion", false, loader);
                Class<?> consumer = Class.forName("com.moulberry.axiom.collections.PositionConsumer", false, loader);
                Class<?> compressed = Class.forName("com.moulberry.axiom.world_modification.CompressedBlockEntity", false, loader);
                Class<?> placement = Class.forName("com.moulberry.axiom.clipboard.Placement", false, loader);
                Field clipboardInstance = clipboard.getField("INSTANCE");
                Field placementInstance = placement.getField("INSTANCE");
                return new Api(client.getMethod("isAxiomActive"), editor.getMethod("isActive"),
                        selection.getMethod("getSelectionBuffer"), buffer.getMethod("isEmpty"), buffer.getMethod("min"),
                        buffer.getMethod("max"), aabb.getConstructor(BlockPos.class, BlockPos.class),
                        selection.getMethod("setBufferWithHistory", buffer),
                        selection.getMethod("addAABB", BlockPos.class, BlockPos.class), clipboardInstance.get(null),
                        clipboard.getMethod("getClipboard"), clipboard.getMethod("setClipboard", region, Long2ObjectMap.class,
                                List.class, String.class, float.class, boolean.class),
                        clipboardObject.getMethod("blockRegion"), clipboardObject.getMethod("blockEntities"),
                        clipboardObject.getMethod("entities"), clipboardObject.getMethod("name"), consumer,
                        region.getMethod("forEachEntry", consumer), region.getMethod("isEmpty"), region.getConstructor(),
                        region.getMethod("addBlockWithoutDirty", int.class, int.class, int.class, BlockState.class),
                        region.getMethod("dirtyAll"), region.getMethod("min"), region.getMethod("max"),
                        compressed.getMethod("compress", CompoundTag.class, ByteArrayOutputStream.class),
                        compressed.getMethod("decompress"), placementInstance.get(null),
                        placement.getMethod("startPlacement", BlockPos.class, region, Long2ObjectMap.class, List.class,
                                boolean.class, String.class),
                        placement.getMethod("isPlacing"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                Addons.report(Addon.AXIOM, "litematicaplus.addon.problem.unreadable", exception);
                return null;
            }
        }

        Object call(Method method, Object target) {
            try {
                return method.invoke(target);
            } catch (InvocationTargetException exception) {
                report(exception.getCause() == null ? exception : exception.getCause());
                return null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                report(exception);
                return null;
            }
        }

        void report(Throwable failure) {
            Throwable cause = failure instanceof InvocationTargetException wrapped && wrapped.getCause() != null
                    ? wrapped.getCause() : failure;
            Addons.report(Addon.AXIOM, "litematicaplus.addon.problem.call_failed", cause);
        }
    }
}
