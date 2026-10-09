package org.lts.litematicaplus.placement;

import java.util.Map;
import org.lts.litematicaplus.schematic.SchematicRegion;

public record PlacementLayout(PlacementTransform transform, Map<String, RegionPlacement> regions, PlacementGrid grid) {
    public PlacementLayout {
        regions = regions == null ? Map.of() : Map.copyOf(regions);
        grid = grid == null ? PlacementGrid.NONE : grid;
    }

    public static PlacementLayout of(PlacementTransform transform) {
        return new PlacementLayout(transform, Map.of(), PlacementGrid.NONE);
    }

    public RegionPlacement region(String name) {
        return this.regions.getOrDefault(name, RegionPlacement.IDENTITY);
    }

    public RegionMapping mapping(SchematicRegion region, int shiftX, int shiftY, int shiftZ) {
        return new RegionMapping(this.transform, region(region.name()), region.posX(), region.posY(), region.posZ(),
                shiftX, shiftY, shiftZ);
    }

    public RegionMapping mapping(SchematicRegion region) {
        return mapping(region, 0, 0, 0);
    }
}
