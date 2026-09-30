package org.lts.litematicaplus.verify;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

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

    private StateComparator() {
        int size = Block.BLOCK_STATE_REGISTRY.size();
        this.air = new boolean[size];
        this.canonical = new int[size];
        this.blockIds = new int[size];
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
        }
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
        return this.blockIds[schematicId] == this.blockIds[worldId] ? MismatchType.WRONG_STATE : MismatchType.WRONG_BLOCK;
    }

    private static <T extends Comparable<T>> BlockState copyDefault(BlockState state, BlockState defaults, Property<T> property) {
        return state.setValue(property, defaults.getValue(property));
    }
}
