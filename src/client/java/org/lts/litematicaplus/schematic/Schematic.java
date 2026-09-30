package org.lts.litematicaplus.schematic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Schematic {
    private final SchematicMetadata metadata;
    private final Map<String, SchematicRegion> regions;

    public Schematic(SchematicMetadata metadata, Map<String, SchematicRegion> regions) {
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("Schematic has no regions");
        }
        this.metadata = metadata;
        this.regions = Collections.unmodifiableMap(new LinkedHashMap<>(regions));
        refreshDerivedMetadata();
    }

    public SchematicMetadata metadata() {
        return this.metadata;
    }

    public Map<String, SchematicRegion> regions() {
        return this.regions;
    }

    public int minX() {
        int value = Integer.MAX_VALUE;
        for (SchematicRegion region : this.regions.values()) {
            value = Math.min(value, region.minX());
        }
        return value;
    }

    public int minY() {
        int value = Integer.MAX_VALUE;
        for (SchematicRegion region : this.regions.values()) {
            value = Math.min(value, region.minY());
        }
        return value;
    }

    public int minZ() {
        int value = Integer.MAX_VALUE;
        for (SchematicRegion region : this.regions.values()) {
            value = Math.min(value, region.minZ());
        }
        return value;
    }

    public int maxX() {
        int value = Integer.MIN_VALUE;
        for (SchematicRegion region : this.regions.values()) {
            value = Math.max(value, region.minX() + region.absX() - 1);
        }
        return value;
    }

    public int maxY() {
        int value = Integer.MIN_VALUE;
        for (SchematicRegion region : this.regions.values()) {
            value = Math.max(value, region.minY() + region.absY() - 1);
        }
        return value;
    }

    public int maxZ() {
        int value = Integer.MIN_VALUE;
        for (SchematicRegion region : this.regions.values()) {
            value = Math.max(value, region.minZ() + region.absZ() - 1);
        }
        return value;
    }

    private void refreshDerivedMetadata() {
        this.metadata.regionCount = this.regions.size();
        long volume = 0L;
        for (SchematicRegion region : this.regions.values()) {
            volume += region.volume();
        }
        this.metadata.totalVolume = volume;
        this.metadata.enclosingX = maxX() - minX() + 1;
        this.metadata.enclosingY = maxY() - minY() + 1;
        this.metadata.enclosingZ = maxZ() - minZ() + 1;
        if (this.metadata.totalBlocks <= 0L) {
            long blocks = 0L;
            for (SchematicRegion region : this.regions.values()) {
                blocks += region.countNonAir();
            }
            this.metadata.totalBlocks = blocks;
        }
    }
}
