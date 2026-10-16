package org.lts.litematicaplus.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StateComparator {
    private static final Set<String> VOLATILE_PROPERTIES = Set.of(
            "distance", "persistent", "power", "triggered", "enabled", "snowy", "unstable",
            "locked", "occupied", "has_record", "has_book", "has_bottle_0", "has_bottle_1", "has_bottle_2",
            "bloom", "shrieking", "can_summon", "crafting", "age", "stage", "moisture", "honey_level", "dusted",
            "extended", "sculk_sensor_phase", "tilt", "attached", "disarmed", "vault_state", "trial_spawner_state",
            "ominous", "drag", "cracked", "hatch");

    private static volatile StateComparator instance;

    private final boolean[] air;
    private final int[] canonical;
    private final int[] blockIds;
    private final int[] groupExact;
    private final int[] groupLenient;
    private final int[] groupBlocks;
    private final boolean[] soil;

    private StateComparator() {
        int size = Block.BLOCK_STATE_REGISTRY.size();
        this.air = new boolean[size];
        this.canonical = new int[size];
        this.blockIds = new int[size];
        this.groupExact = new int[size];
        this.groupLenient = new int[size];
        this.groupBlocks = new int[size];
        this.soil = new boolean[size];
        for (int id = 0; id < size; id++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
            if (state == null) {
                this.canonical[id] = id;
                continue;
            }
            this.air[id] = state.isAir();
            this.blockIds[id] = BuiltInRegistries.BLOCK.getId(state.getBlock());
            BlockState defaults = state.getBlock().defaultBlockState();
            BlockState normalized = state;
            for (Property<?> property : state.getProperties()) {
                if (isVolatile(state.getBlock(), property.getName())) {
                    normalized = copyDefault(normalized, defaults, property);
                }
            }
            this.canonical[id] = Block.getId(normalized);
            this.soil[id] = isSoil(state.getBlock());
        }
        Map<String, Block> representatives = new HashMap<>();
        Map<Block, String> keys = new HashMap<>();
        for (int id = 0; id < size; id++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
            this.groupExact[id] = id;
            this.groupBlocks[id] = -1;
            if (state == null) {
                continue;
            }
            String key = keys.computeIfAbsent(state.getBlock(), block -> {
                String computed = groupKey(block);
                return computed == null ? "" : computed;
            });
            if (key.isEmpty()) {
                continue;
            }
            Block representative = representatives.computeIfAbsent(key, ignored -> state.getBlock());
            BlockState mapped = representative == state.getBlock() ? state : transfer(state, representative);
            if (mapped != null) {
                this.groupExact[id] = Block.getId(mapped);
                this.groupBlocks[id] = BuiltInRegistries.BLOCK.getId(representative);
            }
        }
        for (int id = 0; id < size; id++) {
            int exact = this.groupExact[id];
            this.groupLenient[id] = exact >= 0 && exact < size ? this.canonical[exact] : id;
        }
    }

    public static boolean isSoil(Block block) {
        return block == Blocks.DIRT || block == Blocks.GRASS_BLOCK;
    }

    static boolean groupable(Block block) {
        BlockState state = block.defaultBlockState();
        return !state.isAir() && !(block instanceof LiquidBlock) && !state.hasBlockEntity() && !state.isSignalSource()
                && !(block instanceof BaseRailBlock) && !(block instanceof DiodeBlock)
                && !(block instanceof RedstoneWireBlock) && !(block instanceof PistonBaseBlock)
                && !(block instanceof PistonHeadBlock) && !(block instanceof MovingPistonBlock)
                && !(block instanceof ObserverBlock) && !(block instanceof DispenserBlock)
                && !(block instanceof HopperBlock) && !(block instanceof NoteBlock) && !(block instanceof TntBlock)
                && !(block instanceof RedstoneLampBlock) && !(block instanceof TargetBlock) && !(block instanceof WallBlock)
                && block != Blocks.IRON_DOOR && block != Blocks.IRON_TRAPDOOR && block != Blocks.STRUCTURE_VOID
                && block != Blocks.BARRIER && block != Blocks.LIGHT;
    }

    static String groupKey(Block block) {
        if (!groupable(block)) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        List<String> properties = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            properties.add(property.getName() + "=" + property.getPossibleValues());
        }
        properties.sort(String::compareTo);
        StringBuilder key = new StringBuilder().append(properties);
        try {
            VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            key.append('|').append(shape.isEmpty() ? "empty" : Block.isShapeFullBlock(shape) ? "full" : shape.toAabbs());
        } catch (RuntimeException exception) {
            return null;
        }
        key.append('|').append(state.canOcclude()).append('|').append(state.canBeReplaced())
                .append('|').append(state.getRenderShape()).append('|').append(block instanceof FallingBlock)
                .append('|').append(state.getLightEmission() > 0).append('|').append(block.getFriction())
                .append('|').append(block.getSpeedFactor()).append('|').append(block.getJumpFactor());
        return key.toString();
    }

    private static BlockState transfer(BlockState state, Block into) {
        BlockState result = into.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            Property<?> target = into.getStateDefinition().getProperty(property.getName());
            BlockState next = target == null ? null : assign(result, target, state.getValue(property));
            if (next == null) {
                return null;
            }
            result = next;
        }
        return result;
    }

    public static BlockState transferProperties(BlockState state, Block into) {
        BlockState result = into.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            Property<?> target = into.getStateDefinition().getProperty(property.getName());
            BlockState next = target == null ? null : assign(result, target, state.getValue(property));
            if (next != null) {
                result = next;
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState assign(BlockState state, Property<T> property, Comparable<?> value) {
        if (!property.getValueClass().isInstance(value)) {
            return null;
        }
        T cast = property.getValueClass().cast(value);
        return property.getPossibleValues().contains(cast) ? state.setValue(property, cast) : null;
    }

    public boolean sameGroup(BlockState schematic, BlockState world) {
        int a = Block.getId(schematic);
        int b = Block.getId(world);
        return a >= 0 && b >= 0 && a < this.groupBlocks.length && b < this.groupBlocks.length
                && this.groupBlocks[a] >= 0 && this.groupBlocks[a] == this.groupBlocks[b];
    }

    public boolean sameGroup(Block schematic, Block world) {
        return sameGroup(schematic.defaultBlockState(), world.defaultBlockState());
    }

    static boolean isVolatile(Block block, String property) {
        return switch (property) {
            case "powered" -> !(block instanceof LeverBlock);
            case "lit" -> !(block instanceof AbstractCandleBlock) && !(block instanceof CampfireBlock);
            case "open" -> block instanceof BarrelBlock;
            case "level" -> block instanceof ComposterBlock;
            default -> VOLATILE_PROPERTIES.contains(property);
        };
    }

    public static StateComparator get() {
        StateComparator current = instance;
        if (current == null || current.air.length != Block.BLOCK_STATE_REGISTRY.size()) {
            synchronized (StateComparator.class) {
                current = instance;
                if (current == null || current.air.length != Block.BLOCK_STATE_REGISTRY.size()) {
                    current = new StateComparator();
                    instance = current;
                }
            }
        }
        return current;
    }

    public int size() {
        return this.air.length;
    }

    public boolean isAir(int stateId) {
        return stateId < 0 || stateId >= this.air.length || this.air[stateId];
    }

    public MismatchType compare(int schematicId, int worldId, boolean lenient) {
        return compare(schematicId, worldId, lenient, false, false);
    }

    public MismatchType compare(int schematicId, int worldId, boolean lenient, boolean loose) {
        return compare(schematicId, worldId, lenient, loose, false);
    }

    public MismatchType compare(int schematicId, int worldId, boolean lenient, boolean loose, boolean soilSwap) {
        boolean schematicAir = isAir(schematicId);
        boolean worldAir = isAir(worldId);
        if (schematicAir) {
            return worldAir ? MismatchType.NONE : MismatchType.EXTRA;
        }
        if (worldAir) {
            return MismatchType.MISSING;
        }
        if (schematicId == worldId) {
            return MismatchType.CORRECT;
        }
        if (worldId >= this.canonical.length || schematicId >= this.canonical.length) {
            return MismatchType.WRONG_BLOCK;
        }
        if (lenient && this.canonical[schematicId] == this.canonical[worldId]) {
            return MismatchType.CORRECT;
        }
        if (this.blockIds[schematicId] == this.blockIds[worldId]) {
            return MismatchType.WRONG_STATE;
        }
        if (soilSwap && this.soil[schematicId] && this.soil[worldId]) {
            return MismatchType.CORRECT;
        }
        if (loose && this.groupBlocks[schematicId] >= 0 && this.groupBlocks[schematicId] == this.groupBlocks[worldId]) {
            int[] group = lenient ? this.groupLenient : this.groupExact;
            return group[schematicId] == group[worldId] ? MismatchType.CORRECT : MismatchType.WRONG_STATE;
        }
        return MismatchType.WRONG_BLOCK;
    }

    private static <T extends Comparable<T>> BlockState copyDefault(BlockState state, BlockState defaults, Property<T> property) {
        return state.setValue(property, defaults.getValue(property));
    }
}
