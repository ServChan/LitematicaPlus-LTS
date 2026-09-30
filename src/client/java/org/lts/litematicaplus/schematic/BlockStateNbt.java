package org.lts.litematicaplus.schematic;

import com.mojang.datafixers.DSL;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public final class BlockStateNbt {
    public static final class Unresolved {
        private int count;

        public int count() {
            return this.count;
        }

        void add() {
            this.count++;
        }
    }

    private BlockStateNbt() {
    }

    public static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    public static BlockState read(CompoundTag tag, int dataVersion) {
        return read(tag, dataVersion, null);
    }

    public static BlockState read(CompoundTag tag, int dataVersion, Unresolved unresolved) {
        CompoundTag fixed = tag;
        int current = currentDataVersion();
        if (dataVersion > 0 && dataVersion < current) {
            try {
                Tag updated = DataFixers.getDataFixer()
                        .update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, tag.copy()), dataVersion, current)
                        .getValue();
                if (updated instanceof CompoundTag compound) {
                    fixed = compound;
                }
            } catch (RuntimeException exception) {
                fixed = tag;
            }
        }
        return read(fixed, unresolved);
    }

    public static BlockState read(CompoundTag tag) {
        return read(tag, null);
    }

    public static BlockState read(CompoundTag tag, Unresolved unresolved) {
        String name = tag.getString("Name").or(() -> tag.getString("id")).orElse("");
        Identifier id = Identifier.tryParse(name);
        Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty()) {
            if (unresolved != null && !name.isBlank()) {
                unresolved.add();
                return Blocks.STRUCTURE_VOID.defaultBlockState();
            }
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = block.get().defaultBlockState();
        CompoundTag properties = tag.getCompound("Properties").or(() -> tag.getCompound("properties")).orElse(null);
        if (properties == null) {
            return state;
        }
        StateDefinition<Block, BlockState> definition = block.get().getStateDefinition();
        for (String key : properties.keySet()) {
            Property<?> property = definition.getProperty(key);
            if (property != null) {
                state = apply(state, property, properties.getStringOr(key, ""));
            }
        }
        return state;
    }

    public static BlockState parse(String text) {
        return read(toTag(text), null);
    }

    public static BlockState parse(String text, int dataVersion, Unresolved unresolved) {
        return read(toTag(text), dataVersion, unresolved);
    }

    public static CompoundTag toTag(String text) {
        String trimmed = text.trim();
        int bracket = trimmed.indexOf('[');
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", bracket < 0 ? trimmed : trimmed.substring(0, bracket));
        if (bracket < 0 || !trimmed.endsWith("]")) {
            return tag;
        }
        CompoundTag properties = new CompoundTag();
        for (String pair : trimmed.substring(bracket + 1, trimmed.length() - 1).split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                properties.putString(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
        if (!properties.isEmpty()) {
            tag.put("Properties", properties);
        }
        return tag;
    }

    public static CompoundTag fixBlockEntity(CompoundTag tag, int dataVersion) {
        return fix(References.BLOCK_ENTITY, tag, dataVersion);
    }

    public static CompoundTag fixEntity(CompoundTag tag, int dataVersion) {
        return fix(References.ENTITY, tag, dataVersion);
    }

    private static CompoundTag fix(DSL.TypeReference type, CompoundTag tag, int dataVersion) {
        int current = currentDataVersion();
        if (dataVersion <= 0 || dataVersion >= current || !tag.contains("id")) {
            return tag;
        }
        try {
            Tag updated = DataFixers.getDataFixer()
                    .update(type, new Dynamic<>(NbtOps.INSTANCE, tag.copy()), dataVersion, current)
                    .getValue();
            return updated instanceof CompoundTag compound ? compound : tag;
        } catch (RuntimeException exception) {
            return tag;
        }
    }

    public static CompoundTag write(BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (!state.isSingletonState()) {
            CompoundTag properties = new CompoundTag();
            state.getValues().forEach(value -> properties.putString(value.property().getName(), value.valueName()));
            tag.put("Properties", properties);
        }
        return tag;
    }

    public static String format(BlockState state) {
        StringBuilder builder = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (!state.isSingletonState()) {
            builder.append('[');
            boolean[] first = {true};
            state.getValues().forEach(value -> {
                if (!first[0]) {
                    builder.append(',');
                }
                first[0] = false;
                builder.append(value.property().getName()).append('=').append(value.valueName());
            });
            builder.append(']');
        }
        return builder.toString();
    }

    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
    }
}
