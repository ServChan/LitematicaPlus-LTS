package org.lts.litematicaplus.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.placement.PlacementGrid;
import org.lts.litematicaplus.placement.PlacementLayout;
import org.lts.litematicaplus.placement.PlacementTransform;
import org.lts.litematicaplus.placement.RegionMapping;
import org.lts.litematicaplus.schematic.BitPackedArray;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicRegion;
import org.lts.litematicaplus.util.LocalizedException;
import org.lts.litematicaplus.verify.StateComparator;

public final class PlacementVolume {
    public static final int MAX_PALETTE = 0xFFFE;

    private final BlockState[] palette;
    private final int[] globalIds;
    private final boolean[] solid;
    private final Long2ObjectOpenHashMap<GhostSection> sections;
    private final Long2ObjectOpenHashMap<CompoundTag> blockEntities;
    private final List<RegionBox> regionBoxes;
    private final List<EntitySeed> entities;
    private final PlacementLayout layout;
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

    public record EntitySeed(CompoundTag tag, double x, double y, double z, Rotation rotation, Mirror mirror,
            boolean assisted) {
    }

    public static final int MAX_ENTITIES = 4096;
    public static final long MAX_GRID_CELLS = 1L << 28;
    private static final Set<String> SUPPORTED_ENTITIES = Set.of("minecraft:item_frame", "minecraft:glow_item_frame",
            "minecraft:painting", "minecraft:armor_stand", "ItemFrame", "Painting", "ArmorStand", "item_frame",
            "glow_item_frame", "painting", "armor_stand");

    private PlacementVolume(BlockState[] palette, Long2ObjectOpenHashMap<GhostSection> sections,
            Long2ObjectOpenHashMap<CompoundTag> blockEntities, List<RegionBox> regionBoxes, List<EntitySeed> entities,
            PlacementLayout layout, int dataVersion) {
        this.palette = palette;
        this.entities = List.copyOf(entities);
        this.layout = layout;
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
        return build(schematic, PlacementLayout.of(transform), disabledRegions, Map.of());
    }

    public static PlacementVolume build(Schematic schematic, PlacementTransform transform, Set<String> disabledRegions,
            Map<Block, Block> replacements) {
        return build(schematic, PlacementLayout.of(transform), disabledRegions, replacements);
    }

    public static PlacementVolume build(Schematic schematic, PlacementLayout layout, Set<String> disabledRegions,
            Map<Block, Block> replacements) {
        PlacementTransform transform = layout.transform();
        List<BlockState> palette = new ArrayList<>();
        Reference2IntMap<BlockState> lookup = new Reference2IntOpenHashMap<>();
        lookup.defaultReturnValue(-1);
        BlockState air = Blocks.AIR.defaultBlockState();
        palette.add(air);
        lookup.put(air, 0);
        Long2ObjectOpenHashMap<GhostSection> sections = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<CompoundTag> blockEntities = new Long2ObjectOpenHashMap<>();
        List<RegionBox> boxes = new ArrayList<>();
        List<EntitySeed> entities = new ArrayList<>();
        int dataVersion = schematic.metadata().dataVersion;
        List<SchematicRegion> regions = new ArrayList<>();
        List<short[]> mappedPalettes = new ArrayList<>();
        List<boolean[]> skipped = new ArrayList<>();
        long cellsPerCopy = 0L;
        for (SchematicRegion region : schematic.regions().values()) {
            if (disabledRegions != null && disabledRegions.contains(region.name())) {
                continue;
            }
            RegionMapping mapping = layout.mapping(region);
            BlockState[] regionPalette = region.palette();
            short[] mapped = new short[regionPalette.length];
            boolean[] skip = new boolean[regionPalette.length];
            for (int i = 0; i < regionPalette.length; i++) {
                if (regionPalette[i].is(Blocks.STRUCTURE_VOID)) {
                    skip[i] = true;
                    continue;
                }
                BlockState state = mapping.apply(regionPalette[i]);
                Block replacement = replacements.get(state.getBlock());
                if (replacement != null) {
                    state = StateComparator.transferProperties(state, replacement);
                }
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
            regions.add(region);
            mappedPalettes.add(mapped);
            skipped.add(skip);
            cellsPerCopy += region.volume();
        }
        PlacementGrid grid = layout.grid();
        int[] step = gridSteps(layout, regions);
        long copies = grid.copies();
        if (copies > PlacementGrid.MAX_COPIES || copies * cellsPerCopy > MAX_GRID_CELLS) {
            throw new UncheckedIOException(new LocalizedException("litematicaplus.error.grid_too_large",
                    "Grid is too large: " + copies + " copies of " + cellsPerCopy + " blocks"));
        }
        for (int gridY = -grid.negativeY(); gridY <= grid.positiveY(); gridY++) {
            for (int gridZ = -grid.negativeZ(); gridZ <= grid.positiveZ(); gridZ++) {
                for (int gridX = -grid.negativeX(); gridX <= grid.positiveX(); gridX++) {
                    int shiftX = gridX * step[0];
                    int shiftY = gridY * step[1];
                    int shiftZ = gridZ * step[2];
                    for (int r = 0; r < regions.size(); r++) {
                        SchematicRegion region = regions.get(r);
                        RegionMapping mapping = layout.mapping(region, shiftX, shiftY, shiftZ);
                        fill(region, mapping, mappedPalettes.get(r), skipped.get(r), sections);
                        for (Long2ObjectMap.Entry<CompoundTag> entry : region.blockEntities().long2ObjectEntrySet()) {
                            int localX = region.minX() + SchematicRegion.localKeyX(entry.getLongKey());
                            int localY = region.minY() + SchematicRegion.localKeyY(entry.getLongKey());
                            int localZ = region.minZ() + SchematicRegion.localKeyZ(entry.getLongKey());
                            blockEntities.put(BlockPos.asLong(mapping.worldX(localX, localZ), mapping.worldY(localY),
                                    mapping.worldZ(localX, localZ)), entry.getValue());
                        }
                        collectEntities(region, mapping, dataVersion, entities);
                        boxes.add(regionBox(region, mapping));
                    }
                }
            }
        }
        if (boxes.isEmpty()) {
            boxes.add(new RegionBox("", transform.originX(), transform.originY(), transform.originZ(),
                    transform.originX(), transform.originY(), transform.originZ()));
        }
        return new PlacementVolume(palette.toArray(BlockState[]::new), sections, blockEntities, boxes, entities,
                layout, dataVersion);
    }

    private static void fill(SchematicRegion region, RegionMapping mapping, short[] mapped, boolean[] skip,
            Long2ObjectOpenHashMap<GhostSection> sections) {
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
            int worldY = mapping.worldY(baseY + y);
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, cell++) {
                    int id = states.get(cell);
                    if (id >= mapped.length || skip[id]) {
                        continue;
                    }
                    int worldX = mapping.worldX(baseX + x, baseZ + z);
                    int worldZ = mapping.worldZ(baseX + x, baseZ + z);
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
    }

    public static int[] gridSteps(PlacementLayout layout, List<SchematicRegion> regions) {
        PlacementGrid grid = layout.grid();
        int lowX = Integer.MAX_VALUE;
        int lowY = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int highY = Integer.MIN_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (SchematicRegion region : regions) {
            RegionBox box = regionBox(region, layout.mapping(region));
            lowX = Math.min(lowX, box.minX());
            lowY = Math.min(lowY, box.minY());
            lowZ = Math.min(lowZ, box.minZ());
            highX = Math.max(highX, box.maxX());
            highY = Math.max(highY, box.maxY());
            highZ = Math.max(highZ, box.maxZ());
        }
        int autoX = regions.isEmpty() ? 1 : highX - lowX + 1;
        int autoY = regions.isEmpty() ? 1 : highY - lowY + 1;
        int autoZ = regions.isEmpty() ? 1 : highZ - lowZ + 1;
        return new int[]{grid.stepX() > 0 ? grid.stepX() : autoX, grid.stepY() > 0 ? grid.stepY() : autoY,
                grid.stepZ() > 0 ? grid.stepZ() : autoZ};
    }

    private static void collectEntities(SchematicRegion region, RegionMapping mapping, int dataVersion,
            List<EntitySeed> out) {
        for (CompoundTag tag : region.entities()) {
            if (out.size() >= MAX_ENTITIES) {
                return;
            }
            String id = tag.getStringOr("id", "");
            ListTag position = tag.getListOrEmpty("Pos");
            if (id.isEmpty() || position.size() < 3) {
                continue;
            }
            Vec3 world = mapping.worldPoint(region.posX() + position.getDoubleOr(0, 0.0),
                    region.posY() + position.getDoubleOr(1, 0.0), region.posZ() + position.getDoubleOr(2, 0.0));
            out.add(new EntitySeed(BlockStateNbt.fixEntity(tag, dataVersion), world.x, world.y, world.z,
                    mapping.rotation(), mapping.mirror(), SUPPORTED_ENTITIES.contains(id)));
        }
    }

    public List<EntitySeed> entities() {
        return this.entities;
    }

    public PlacementTransform transform() {
        return this.layout.transform();
    }

    public PlacementLayout layout() {
        return this.layout;
    }

    public static RegionBox regionBox(SchematicRegion region, PlacementTransform transform) {
        return regionBox(region, PlacementLayout.of(transform).mapping(region));
    }

    public static RegionBox regionBox(SchematicRegion region, RegionMapping mapping) {
        int baseX = region.minX();
        int baseY = region.minY();
        int baseZ = region.minZ();
        int cornerAX = mapping.worldX(baseX, baseZ);
        int cornerAZ = mapping.worldZ(baseX, baseZ);
        int cornerBX = mapping.worldX(baseX + region.absX() - 1, baseZ + region.absZ() - 1);
        int cornerBZ = mapping.worldZ(baseX + region.absX() - 1, baseZ + region.absZ() - 1);
        int cornerAY = mapping.worldY(baseY);
        int cornerBY = mapping.worldY(baseY + region.absY() - 1);
        return new RegionBox(region.name(), Math.min(cornerAX, cornerBX), Math.min(cornerAY, cornerBY),
                Math.min(cornerAZ, cornerBZ), Math.max(cornerAX, cornerBX), Math.max(cornerAY, cornerBY),
                Math.max(cornerAZ, cornerBZ));
    }

    public static List<RegionBox> regionBoxes(Schematic schematic, PlacementTransform transform, Set<String> disabled) {
        return regionBoxes(schematic, PlacementLayout.of(transform), disabled);
    }

    public static List<RegionBox> regionBoxes(Schematic schematic, PlacementLayout layout, Set<String> disabled) {
        List<SchematicRegion> regions = new ArrayList<>();
        for (SchematicRegion region : schematic.regions().values()) {
            if (disabled == null || !disabled.contains(region.name())) {
                regions.add(region);
            }
        }
        List<RegionBox> boxes = new ArrayList<>();
        PlacementGrid grid = layout.grid();
        if (grid.copies() > PlacementGrid.MAX_COPIES) {
            grid = PlacementGrid.NONE;
        }
        int[] step = gridSteps(layout, regions);
        for (int gridY = -grid.negativeY(); gridY <= grid.positiveY(); gridY++) {
            for (int gridZ = -grid.negativeZ(); gridZ <= grid.positiveZ(); gridZ++) {
                for (int gridX = -grid.negativeX(); gridX <= grid.positiveX(); gridX++) {
                    for (SchematicRegion region : regions) {
                        boxes.add(regionBox(region, layout.mapping(region, gridX * step[0], gridY * step[1],
                                gridZ * step[2])));
                    }
                }
            }
        }
        return boxes;
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
