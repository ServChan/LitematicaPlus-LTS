package org.lts.litematicaplus.schematic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.placement.PlacementGrid;
import org.lts.litematicaplus.placement.RegionPlacement;

public record EmbeddedPlacement(int x, int y, int z, Rotation rotation, Mirror mirror, String dimension, String world,
        Map<String, RegionPlacement> regions, Set<String> disabledRegions, PlacementGrid grid,
        Map<String, String> replacements, int zoneColumns, int zoneRows, int zoneFloors) {
    public static final int VERSION = 1;
    public static final String CONTAINER_KEY = "LitematicaPlus";
    public static final String PLACEMENT_KEY = "Placement";
    private static final int MAX_COORDINATE = 30000000;
    private static final int MAX_ENTRIES = 4096;

    public EmbeddedPlacement {
        x = clamp(x);
        y = clamp(y);
        z = clamp(z);
        rotation = rotation == null ? Rotation.NONE : rotation;
        mirror = mirror == null ? Mirror.NONE : mirror;
        dimension = dimension == null ? "" : dimension;
        world = world == null ? "" : world;
        regions = regions == null ? Map.of() : Map.copyOf(regions);
        disabledRegions = disabledRegions == null ? Set.of() : Set.copyOf(disabledRegions);
        grid = grid == null ? PlacementGrid.NONE : grid;
        replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
        zoneColumns = Math.max(1, zoneColumns);
        zoneRows = Math.max(1, zoneRows);
        zoneFloors = Math.max(1, zoneFloors);
    }

    public static EmbeddedPlacement at(int x, int y, int z, Rotation rotation, Mirror mirror, String dimension,
            String world) {
        return new EmbeddedPlacement(x, y, z, rotation, mirror, dimension, world, Map.of(), Set.of(),
                PlacementGrid.NONE, Map.of(), 1, 1, 1);
    }

    private static int clamp(int value) {
        return Math.max(-MAX_COORDINATE, Math.min(MAX_COORDINATE, value));
    }

    public boolean hasWorkSettings() {
        return !this.replacements.isEmpty() || this.zoneColumns * this.zoneRows * this.zoneFloors > 1;
    }

    public boolean matchesDimension(String current) {
        return this.dimension.isEmpty() || this.dimension.equals(current);
    }

    public boolean matchesWorld(String current) {
        return this.world.isEmpty() || this.world.equals(current);
    }

    public static EmbeddedPlacement readFrom(CompoundTag metadata) {
        CompoundTag container = metadata.getCompound(CONTAINER_KEY).orElse(null);
        CompoundTag tag = container == null ? null : container.getCompound(PLACEMENT_KEY).orElse(null);
        return tag == null ? null : read(tag);
    }

    public static void writeTo(CompoundTag metadata, EmbeddedPlacement placement) {
        CompoundTag container = metadata.getCompound(CONTAINER_KEY).orElseGet(CompoundTag::new);
        if (placement == null) {
            container.remove(PLACEMENT_KEY);
        } else {
            container.put(PLACEMENT_KEY, placement.write());
        }
        if (container.isEmpty()) {
            metadata.remove(CONTAINER_KEY);
        } else {
            metadata.put(CONTAINER_KEY, container);
        }
    }

    public static EmbeddedPlacement read(CompoundTag tag) {
        CompoundTag origin = tag.getCompound("Origin").orElse(null);
        if (origin == null) {
            return null;
        }
        Map<String, RegionPlacement> regions = new LinkedHashMap<>();
        CompoundTag regionsTag = tag.getCompoundOrEmpty("Regions");
        for (String name : regionsTag.keySet()) {
            if (regions.size() >= MAX_ENTRIES) {
                break;
            }
            CompoundTag region = regionsTag.getCompoundOrEmpty(name);
            RegionPlacement value = new RegionPlacement(region.getIntOr("x", 0), region.getIntOr("y", 0),
                    region.getIntOr("z", 0), rotation(region.getStringOr("Rotation", "")),
                    mirror(region.getStringOr("Mirror", "")));
            if (!value.isIdentity()) {
                regions.put(name, value);
            }
        }
        Set<String> disabled = new LinkedHashSet<>();
        ListTag disabledTag = tag.getListOrEmpty("DisabledRegions");
        for (int index = 0; index < disabledTag.size() && disabled.size() < MAX_ENTRIES; index++) {
            disabledTag.getString(index).filter(name -> !name.isBlank()).ifPresent(disabled::add);
        }
        CompoundTag gridTag = tag.getCompoundOrEmpty("Grid");
        int[] repeat = gridTag.getIntArray("Repeat").orElse(new int[0]);
        int[] step = gridTag.getIntArray("Step").orElse(new int[0]);
        PlacementGrid grid = new PlacementGrid(at(repeat, 0), at(repeat, 1), at(repeat, 2), at(repeat, 3),
                at(repeat, 4), at(repeat, 5), at(step, 0), at(step, 1), at(step, 2));
        Map<String, String> replacements = new LinkedHashMap<>();
        CompoundTag replacementsTag = tag.getCompoundOrEmpty("Replacements");
        for (String from : replacementsTag.keySet()) {
            if (replacements.size() >= MAX_ENTRIES) {
                break;
            }
            replacementsTag.getString(from).filter(to -> !to.isBlank()).ifPresent(to -> replacements.put(from, to));
        }
        CompoundTag zones = tag.getCompoundOrEmpty("Zones");
        return new EmbeddedPlacement(origin.getIntOr("x", 0), origin.getIntOr("y", 0), origin.getIntOr("z", 0),
                rotation(tag.getStringOr("Rotation", "")), mirror(tag.getStringOr("Mirror", "")),
                tag.getStringOr("Dimension", ""), tag.getStringOr("World", ""), regions, disabled, grid, replacements,
                zones.getIntOr("Columns", 1), zones.getIntOr("Rows", 1), zones.getIntOr("Floors", 1));
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        CompoundTag origin = new CompoundTag();
        origin.putInt("x", this.x);
        origin.putInt("y", this.y);
        origin.putInt("z", this.z);
        tag.put("Origin", origin);
        tag.putString("Rotation", this.rotation.name());
        tag.putString("Mirror", this.mirror.name());
        if (!this.dimension.isEmpty()) {
            tag.putString("Dimension", this.dimension);
        }
        if (!this.world.isEmpty()) {
            tag.putString("World", this.world);
        }
        if (!this.regions.isEmpty()) {
            CompoundTag regionsTag = new CompoundTag();
            for (Map.Entry<String, RegionPlacement> entry : new TreeMap<>(this.regions).entrySet()) {
                RegionPlacement value = entry.getValue();
                CompoundTag region = new CompoundTag();
                region.putInt("x", value.offsetX());
                region.putInt("y", value.offsetY());
                region.putInt("z", value.offsetZ());
                region.putString("Rotation", value.rotation().name());
                region.putString("Mirror", value.mirror().name());
                regionsTag.put(entry.getKey(), region);
            }
            tag.put("Regions", regionsTag);
        }
        if (!this.disabledRegions.isEmpty()) {
            ListTag disabled = new ListTag();
            List<String> sorted = new ArrayList<>(this.disabledRegions);
            sorted.sort(null);
            for (String name : sorted) {
                disabled.add(StringTag.valueOf(name));
            }
            tag.put("DisabledRegions", disabled);
        }
        if (!this.grid.equals(PlacementGrid.NONE)) {
            CompoundTag gridTag = new CompoundTag();
            gridTag.putIntArray("Repeat", new int[] {this.grid.negativeX(), this.grid.positiveX(), this.grid.negativeY(),
                    this.grid.positiveY(), this.grid.negativeZ(), this.grid.positiveZ()});
            gridTag.putIntArray("Step", new int[] {this.grid.stepX(), this.grid.stepY(), this.grid.stepZ()});
            tag.put("Grid", gridTag);
        }
        if (!this.replacements.isEmpty()) {
            CompoundTag replacementsTag = new CompoundTag();
            new TreeMap<>(this.replacements).forEach(replacementsTag::putString);
            tag.put("Replacements", replacementsTag);
        }
        if (this.zoneColumns * this.zoneRows * this.zoneFloors > 1) {
            CompoundTag zones = new CompoundTag();
            zones.putInt("Columns", this.zoneColumns);
            zones.putInt("Rows", this.zoneRows);
            zones.putInt("Floors", this.zoneFloors);
            tag.put("Zones", zones);
        }
        return tag;
    }

    private static int at(int[] values, int index) {
        return index < values.length ? values[index] : 0;
    }

    private static Rotation rotation(String name) {
        for (Rotation value : Rotation.values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return Rotation.NONE;
    }

    private static Mirror mirror(String name) {
        for (Mirror value : Mirror.values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return Mirror.NONE;
    }
}
