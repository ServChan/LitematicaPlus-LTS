package org.lts.litematicaplus.schematic;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.util.LocalizedException;

public final class SpongeCodec {
    private static final int MAX_PALETTE_ID = 1 << 20;

    private SpongeCodec() {
    }

    public static boolean matches(CompoundTag root) {
        CompoundTag body = body(root);
        return body.contains("Width") && body.contains("Height") && body.contains("Length")
                && (body.contains("Palette") || body.contains("Blocks"));
    }

    public static Schematic read(CompoundTag root, String fallbackName) throws IOException {
        CompoundTag body = body(root);
        int width = body.getShortOr("Width", (short) 0) & 0xFFFF;
        int height = body.getShortOr("Height", (short) 0) & 0xFFFF;
        int length = body.getShortOr("Length", (short) 0) & 0xFFFF;
        if (width == 0 || height == 0 || length == 0) {
            throw new LocalizedException("litematicaplus.error.empty_schematic", "Sponge schematic has an empty size");
        }
        CompoundTag blocks = body.getCompound("Blocks").orElse(body);
        CompoundTag paletteTag = blocks.getCompoundOrEmpty("Palette");
        byte[] data = blocks.getByteArray("Data").or(() -> blocks.getByteArray("BlockData")).orElse(null);
        if (paletteTag.isEmpty() || data == null) {
            throw new LocalizedException("litematicaplus.error.empty_schematic", "Sponge schematic has no block data");
        }
        long volume = (long) width * height * length;
        if (volume > LitematicCodec.MAX_REGION_VOLUME) {
            throw new LocalizedException("litematicaplus.error.too_large", "Sponge schematic is too large: " + volume + " blocks");
        }
        if (data.length < volume) {
            throw new LocalizedException("litematicaplus.error.corrupt", "Sponge schematic block data is truncated: " + data.length + " bytes for "
                    + volume + " blocks");
        }
        int dataVersion = body.getIntOr("DataVersion", 0);
        int maxId = 0;
        for (String key : paletteTag.keySet()) {
            int id = paletteTag.getIntOr(key, -1);
            if (id < 0 || id > MAX_PALETTE_ID) {
                throw new LocalizedException("litematicaplus.error.corrupt", "Sponge schematic has an invalid palette id " + id);
            }
            maxId = Math.max(maxId, id);
        }
        BlockState[] palette = new BlockState[maxId + 1];
        BlockState air = Blocks.AIR.defaultBlockState();
        Arrays.fill(palette, air);
        BlockStateNbt.Unresolved unresolved = new BlockStateNbt.Unresolved();
        for (String key : paletteTag.keySet()) {
            palette[paletteTag.getIntOr(key, 0)] = BlockStateNbt.parse(key, dataVersion, unresolved);
        }
        RegionBuilder builder = new RegionBuilder(fallbackName, 0, 0, 0, width, height, length);
        long count = 0L;
        int cursor = 0;
        for (long index = 0L; index < volume && cursor < data.length; index++) {
            int value = 0;
            int shift = 0;
            while (cursor < data.length) {
                byte next = data[cursor++];
                value |= (next & 0x7F) << shift;
                if ((next & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 28) {
                    throw new LocalizedException("litematicaplus.error.corrupt", "Sponge schematic has a malformed block index");
                }
            }
            BlockState state = value >= 0 && value < palette.length ? palette[value] : air;
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            int x = (int) (index % width);
            int z = (int) (index / width % length);
            int y = (int) (index / ((long) width * length));
            builder.set(x, y, z, state);
            count++;
        }
        ListTag tiles = blocks.getList("BlockEntities").or(() -> body.getList("BlockEntities"))
                .or(() -> body.getList("TileEntities")).orElseGet(ListTag::new);
        for (int i = 0; i < tiles.size(); i++) {
            CompoundTag tile = tiles.getCompoundOrEmpty(i);
            int[] pos = tile.getIntArray("Pos").orElse(null);
            if (pos == null || pos.length < 3 || pos[0] < 0 || pos[1] < 0 || pos[2] < 0
                    || pos[0] >= width || pos[1] >= height || pos[2] >= length) {
                continue;
            }
            CompoundTag nbt = tile.getCompound("Data").map(CompoundTag::copy).orElseGet(() -> {
                CompoundTag copy = tile.copy();
                copy.remove("Pos");
                copy.remove("Id");
                return copy;
            });
            String id = tile.getStringOr("Id", tile.getStringOr("id", ""));
            if (!id.isEmpty()) {
                nbt.putString("id", id);
            }
            builder.blockEntity(pos[0], pos[1], pos[2], nbt);
        }
        SchematicMetadata metadata = new SchematicMetadata();
        CompoundTag meta = body.getCompoundOrEmpty("Metadata");
        metadata.name = meta.getStringOr("Name", fallbackName);
        metadata.author = meta.getStringOr("Author", "");
        metadata.timeCreated = meta.getLongOr("Date", 0L);
        metadata.dataVersion = dataVersion;
        metadata.schematicVersion = body.getIntOr("Version", 0);
        metadata.totalBlocks = count;
        metadata.unknownStates = unresolved.count();
        metadata.format = "sponge";
        return new Schematic(metadata, Map.of(fallbackName, builder.build()));
    }

    private static CompoundTag body(CompoundTag root) {
        return root.getCompound("Schematic").orElse(root);
    }
}
