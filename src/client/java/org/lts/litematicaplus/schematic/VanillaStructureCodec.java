package org.lts.litematicaplus.schematic;

import java.io.IOException;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class VanillaStructureCodec {
    private VanillaStructureCodec() {
    }

    public static boolean matches(CompoundTag root) {
        return root.contains("size") && root.contains("blocks") && (root.contains("palette") || root.contains("palettes"));
    }

    public static Schematic read(CompoundTag root, String fallbackName) throws IOException {
        ListTag size = root.getListOrEmpty("size");
        int sizeX = size.getIntOr(0, 0);
        int sizeY = size.getIntOr(1, 0);
        int sizeZ = size.getIntOr(2, 0);
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IOException("Structure file has an empty size");
        }
        int dataVersion = root.getIntOr("DataVersion", 0);
        ListTag paletteTag = root.getListOrEmpty("palette");
        if (paletteTag.isEmpty()) {
            paletteTag = root.getListOrEmpty("palettes").getListOrEmpty(0);
        }
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = BlockStateNbt.read(paletteTag.getCompoundOrEmpty(i), dataVersion);
        }
        RegionBuilder builder = new RegionBuilder(fallbackName, 0, 0, 0, sizeX, sizeY, sizeZ);
        ListTag blocks = root.getListOrEmpty("blocks");
        long count = 0L;
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompoundOrEmpty(i);
            ListTag pos = block.getListOrEmpty("pos");
            int x = pos.getIntOr(0, -1);
            int y = pos.getIntOr(1, -1);
            int z = pos.getIntOr(2, -1);
            int stateIndex = block.getIntOr("state", -1);
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ
                    || stateIndex < 0 || stateIndex >= palette.length) {
                continue;
            }
            BlockState state = palette[stateIndex];
            if (state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            builder.set(x, y, z, state);
            if (!state.isAir()) {
                count++;
            }
            block.getCompound("nbt").ifPresent(nbt -> builder.blockEntity(x, y, z, nbt.copy()));
        }
        ListTag entities = root.getListOrEmpty("entities");
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entry = entities.getCompoundOrEmpty(i);
            CompoundTag nbt = entry.getCompoundOrEmpty("nbt").copy();
            entry.getList("pos").ifPresent(pos -> nbt.put("Pos", pos.copy()));
            if (!nbt.isEmpty()) {
                builder.entity(nbt);
            }
        }
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = fallbackName;
        metadata.author = root.getStringOr("author", "");
        metadata.dataVersion = dataVersion;
        metadata.totalBlocks = count;
        metadata.format = "structure";
        return new Schematic(metadata, Map.of(fallbackName, builder.build()));
    }
}
