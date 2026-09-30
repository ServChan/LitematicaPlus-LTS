package org.lts.litematicaplus.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.placement.PlacementTransform;
import org.lts.litematicaplus.schematic.BitPackedArray;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.util.LocalizedException;

public final class PlacementVolume {
    public static final int MAX_PALETTE = 0xFFFE;

    private final BlockState[] palette;
    private final int[] globalIds;
    private final boolean[] solid;
    private final Long2ObjectOpenHashMap<GhostSection> sections;
    private final Long2ObjectOpenHashMap<CompoundTag> blockEntities;
    private final List<RegionBox> regionBoxes;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final long totalBlocks;
    private final long[] totalByPalette;
    private final int dataVersion;

    public record RegionBox(String name, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private PlacementVolume(BlockState[] palette, Long2ObjectOpenHashMap<GhostSection> sections,
            Long2ObjectOpenHashMap<CompoundTag> blockEntities, List<RegionBox> regionBoxes, int dataVersion) {
        this.palette = palette;
        this.dataVersion = dataVersion;
        this.globalIds = new int[palette.length];
        this.solid = new boolean[palette.length];
        for (int i = 0; i < palette.length; i++) {
            this.globalIds[i] = Block.getId(palette[i]);
            this.solid[i] = !palette[i].isAir();
        }
        this.sections = sections;
        this.blockEntities = blockEntities;
        this.regionBoxes = List.copyOf(regionBoxes);
        int lowX = Integer.MAX_VALUE;
        int lowY = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int highY = Integer.MIN_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (RegionBox box : regionBoxes) {
            lowX = Math.min(lowX, box.minX());
            lowY = Math.min(lowY, box.minY());
            lowZ = Math.min(lowZ, box.minZ());
            highX = Math.max(highX, box.maxX());
            highY = Math.max(highY, box.maxY());
            highZ = Math.max(highZ, box.maxZ());
        }
        this.minX = lowX;
        this.minY = lowY;
        this.minZ = lowZ;
        this.maxX = highX;
        this.maxY = highY;
        this.maxZ = highZ;
        long blocks = 0L;
        this.totalByPalette = new long[palette.length];
        for (Long2ObjectMap.Entry<GhostSection> entry : sections.long2ObjectEntrySet()) {
            GhostSection section = entry.getValue();
            section.recount(this.solid);
            if (section.isFullAir()) {
                entry.setValue(GhostSection.sharedFullAir());
                continue;
            }
            blocks += section.solidCount();
            if (section.solidCount() > 0) {
                for (short value : section.raw()) {
                    if (value != GhostSection.OUTSIDE) {
                        this.totalByPalette[value & 0xFFFF]++;
                    }
                }
            }
        }
        this.totalByPalette[0] = 0L;
        this.totalBlocks = blocks;
    }

    public int dataVersion() {
        return this.dataVersion;
    }

    public long[] totalByPalette() {
        return this.totalByPalette;
    }

    public static PlacementVolume build(Schematic schematic, PlacementTransform transform, Set<String> disabledRegions) {
        List<BlockState> palette = new ArrayList<>();
        Reference2IntMap<BlockState> lookup = new Reference2IntOpenHashMap<>();
        lookup.defaultReturnValue(-1);
        BlockState air = Blocks.AIR.defaultBlockState();
        palette.add(air);
        lookup.put(air, 0);
        Long2ObjectOpenHashMap<GhostSection> sections = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<CompoundTag> blockEntities = new Long2ObjectOpenHashMap<>();
        List<RegionBox> boxes = new ArrayList<>();
        for (SchematicRegion region : schematic.regions().values()) {
            if (disabledRegions != null && disabledRegions.contains(region.name())) {
                continue;
            }
            BlockState[] regionPalette = region.palette();
            short[] mapped = new short[regionPalette.length];
            boolean[] skip = new boolean[regionPalette.length];
            for (int i = 0; i < regionPalette.length; i++) {
                if (regionPalette[i].is(Blocks.STRUCTURE_VOID)) {
                    skip[i] = true;
                    continue;
                }
                BlockState state = transform.apply(regionPalette[i]);
                int index = lookup.getInt(state);
                if (index < 0) {
                    if (palette.size() >= MAX_PALETTE) {
                        throw new UncheckedIOException(new LocalizedException("litematicaplus.error.too_many_states",
                                "Placement uses more than " + MAX_PALETTE + " block states"));
                    }
                    index = palette.size();
                    palette.add(state);
                    lookup.put(state, index);
                }
                mapped[i] = (short) index;
            }
            int baseX = region.minX();
            int baseY = region.minY();
            int baseZ = region.minZ();
            int sizeX = region.absX();
            int sizeY = region.absY();
            int sizeZ = region.absZ();
            BitPackedArray states = region.states();
            long lastKey = Long.MIN_VALUE;
            GhostSection lastSection = null;
            long cell = 0L;
            for (int y = 0; y < sizeY; y++) {
                int worldY = transform.worldY(baseY + y);
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++, cell++) {
                        int id = states.get(cell);
                        if (id >= mapped.length || skip[id]) {
                            continue;
                        }
                        int worldX = transform.worldX(baseX + x, baseZ + z);
                        int worldZ = transform.worldZ(baseX + x, baseZ + z);
                        long key = SectionPos.asLong(worldX >> 4, worldY >> 4, worldZ >> 4);
                        if (key != lastKey) {
                            lastSection = sections.get(key);
                            if (lastSection == null) {
                                lastSection = new GhostSection();
                                sections.put(key, lastSection);
                            }
                            lastKey = key;
                        }
                        lastSection.set(GhostSection.index(worldX, worldY, worldZ), mapped[id]);
                    }
                }
            }
            for (Long2ObjectMap.Entry<CompoundTag> entry : region.blockEntities().long2ObjectEntrySet()) {
                int localX = baseX + SchematicRegion.localKeyX(entry.getLongKey());
                int localY = baseY + SchematicRegion.localKeyY(entry.getLongKey());
                int localZ = baseZ + SchematicRegion.localKeyZ(entry.getLongKey());
                blockEntities.put(BlockPos.asLong(transform.worldX(localX, localZ), transform.worldY(localY),
                        transform.worldZ(localX, localZ)), entry.getValue());
            }
            int cornerAX = transform.worldX(baseX, baseZ);
            int cornerAZ = transform.worldZ(baseX, baseZ);
            int cornerBX = transform.worldX(baseX + sizeX - 1, baseZ + sizeZ - 1);
            int cornerBZ = transform.worldZ(baseX + sizeX - 1, baseZ + sizeZ - 1);
            boxes.add(new RegionBox(region.name(),
                    Math.min(cornerAX, cornerBX), transform.worldY(baseY), Math.min(cornerAZ, cornerBZ),
                    Math.max(cornerAX, cornerBX), transform.worldY(baseY + sizeY - 1), Math.max(cornerAZ, cornerBZ)));
        }
        if (boxes.isEmpty()) {
            boxes.add(new RegionBox("", transform.originX(), transform.originY(), transform.originZ(),
                    transform.originX(), transform.originY(), transform.originZ()));
        }
        return new PlacementVolume(palette.toArray(BlockState[]::new), sections, blockEntities, boxes,
                schematic.metadata().dataVersion);
    }

    public BlockState[] palette() {
        return this.palette;
    }

    public int[] globalIds() {
        return this.globalIds;
    }

    public boolean[] solid() {
        return this.solid;
    }

    public Long2ObjectOpenHashMap<GhostSection> sections() {
        return this.sections;
    }

    public GhostSection section(long key) {
        return this.sections.get(key);
    }

    public Long2ObjectOpenHashMap<CompoundTag> blockEntities() {
        return this.blockEntities;
    }

    public List<RegionBox> regionBoxes() {
        return this.regionBoxes;
    }

    public int minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public int minZ() {
        return this.minZ;
    }

    public int maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public int maxZ() {
        return this.maxZ;
    }

    public long totalBlocks() {
        return this.totalBlocks;
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    public int paletteIndexAt(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return -1;
        }
        GhostSection section = this.sections.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        if (section == null) {
            return -1;
        }
        short value = section.get(x, y, z);
        return value == GhostSection.OUTSIDE ? -1 : value & 0xFFFF;
    }

    public BlockState stateAt(int x, int y, int z) {
        int index = paletteIndexAt(x, y, z);
        return index < 0 ? Blocks.AIR.defaultBlockState() : this.palette[index];
    }}
