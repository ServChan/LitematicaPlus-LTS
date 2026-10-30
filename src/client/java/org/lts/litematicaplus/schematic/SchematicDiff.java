package org.lts.litematicaplus.schematic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.util.LocalizedException;

public final class SchematicDiff {
    public static final long MAX_VOLUME = 1L << 25;

    public enum Change {
        ADDED,
        REMOVED,
        REPLACED,
        RESTATED
    }

    public record Row(Change change, Block from, Block to, long count) {
    }

    public record Result(long same, long added, long removed, long replaced, long restated, List<Row> rows,
            Schematic changes) {
        public long total() {
            return this.added + this.removed + this.replaced + this.restated;
        }
    }

    private record Key(Change change, Block from, Block to) {
    }

    private SchematicDiff() {
    }

    public static Result compare(Schematic before, Schematic after, String name) throws IOException {
        int minX = Math.min(before.minX(), after.minX());
        int minY = Math.min(before.minY(), after.minY());
        int minZ = Math.min(before.minZ(), after.minZ());
        int sizeX = Math.max(before.maxX(), after.maxX()) - minX + 1;
        int sizeY = Math.max(before.maxY(), after.maxY()) - minY + 1;
        int sizeZ = Math.max(before.maxZ(), after.maxZ()) - minZ + 1;
        if ((long) sizeX * sizeY * sizeZ > MAX_VOLUME) {
            throw new LocalizedException("litematicaplus.error.compare_too_large", "Schematics are too large to compare");
        }
        SchematicExport.Merged first = SchematicExport.merge(before, MAX_VOLUME);
        SchematicExport.Merged second = SchematicExport.merge(after, MAX_VOLUME);
        int firstX = before.minX() - minX;
        int firstY = before.minY() - minY;
        int firstZ = before.minZ() - minZ;
        int secondX = after.minX() - minX;
        int secondY = after.minY() - minY;
        int secondZ = after.minZ() - minZ;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState outside = Blocks.STRUCTURE_VOID.defaultBlockState();
        RegionBuilder builder = new RegionBuilder("Changes", 0, 0, 0, sizeX, sizeY, sizeZ);
        builder.fill(outside);
        Map<Key, long[]> counts = new HashMap<>();
        long same = 0L;
        long[] totals = new long[Change.values().length];
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockState from = cell(first, x - firstX, y - firstY, z - firstZ, air);
                    BlockState to = cell(second, x - secondX, y - secondY, z - secondZ, air);
                    if (from == to) {
                        if (!from.isAir()) {
                            same++;
                        }
                        continue;
                    }
                    Change change = from.isAir() ? Change.ADDED : to.isAir() ? Change.REMOVED
                            : from.getBlock() == to.getBlock() ? Change.RESTATED : Change.REPLACED;
                    totals[change.ordinal()]++;
                    counts.computeIfAbsent(new Key(change, from.getBlock(), to.getBlock()), key -> new long[1])[0]++;
                    if (change != Change.REMOVED) {
                        builder.set(x, y, z, to);
                    }
                }
            }
        }
        List<Row> rows = new ArrayList<>(counts.size());
        counts.forEach((key, value) -> rows.add(new Row(key.change(), key.from(), key.to(), value[0])));
        rows.sort(Comparator.comparingLong(Row::count).reversed());
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = name;
        metadata.dataVersion = BlockStateNbt.currentDataVersion();
        Schematic changes = new Schematic(metadata, Map.of("Changes", builder.build()));
        return new Result(same, totals[Change.ADDED.ordinal()], totals[Change.REMOVED.ordinal()],
                totals[Change.REPLACED.ordinal()], totals[Change.RESTATED.ordinal()], List.copyOf(rows), changes);
    }

    private static BlockState cell(SchematicExport.Merged merged, int x, int y, int z, BlockState air) {
        if (x < 0 || y < 0 || z < 0 || x >= merged.sizeX() || y >= merged.sizeY() || z >= merged.sizeZ()) {
            return air;
        }
        int value = merged.cells()[(int) merged.index(x, y, z)];
        if (value == SchematicExport.OUTSIDE) {
            return air;
        }
        BlockState state = merged.palette()[value];
        return state.is(Blocks.STRUCTURE_VOID) ? air : state;
    }
}
