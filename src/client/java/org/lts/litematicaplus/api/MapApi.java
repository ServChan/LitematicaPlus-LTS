package org.lts.litematicaplus.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.gui.PlacementEditorScreen;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.world.PlacementVolume;

public final class MapApi {
    public static final int VERSION = 1;
    public static final int FIELD_ID = 0;
    public static final int FIELD_NAME = 1;
    public static final int FIELD_MIN_X = 2;
    public static final int FIELD_MIN_Y = 3;
    public static final int FIELD_MIN_Z = 4;
    public static final int FIELD_MAX_X = 5;
    public static final int FIELD_MAX_Y = 6;
    public static final int FIELD_MAX_Z = 7;
    public static final int FIELD_PROGRESS = 8;
    public static final int FIELD_SELECTED = 9;
    public static final int FIELD_SHOWN = 10;
    public static final int FIELD_COUNT = 11;

    private MapApi() {
    }

    public static List<Object[]> placements() {
        PlacementManager manager = PlacementManager.get();
        SchematicPlacement selected = manager.selected();
        List<Object[]> result = new ArrayList<>();
        for (SchematicPlacement placement : manager.placements()) {
            Schematic schematic = placement.schematic();
            if (schematic == null) {
                continue;
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (PlacementVolume.RegionBox box : PlacementVolume.regionBoxes(schematic, placement.transform(),
                    placement.disabledRegions())) {
                minX = Math.min(minX, box.minX());
                minY = Math.min(minY, box.minY());
                minZ = Math.min(minZ, box.minZ());
                maxX = Math.max(maxX, box.maxX());
                maxY = Math.max(maxY, box.maxY());
                maxZ = Math.max(maxZ, box.maxZ());
            }
            if (minX > maxX) {
                continue;
            }
            PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
            double progress = runtime == null || runtime.volume() == null ? -1.0 : runtime.progress();
            Object[] row = new Object[FIELD_COUNT];
            row[FIELD_ID] = placement.id().toString();
            row[FIELD_NAME] = placement.name();
            row[FIELD_MIN_X] = minX;
            row[FIELD_MIN_Y] = minY;
            row[FIELD_MIN_Z] = minZ;
            row[FIELD_MAX_X] = maxX + 1;
            row[FIELD_MAX_Y] = maxY + 1;
            row[FIELD_MAX_Z] = maxZ + 1;
            row[FIELD_PROGRESS] = progress;
            row[FIELD_SELECTED] = placement == selected;
            row[FIELD_SHOWN] = placement.enabled();
            result.add(row);
        }
        return result;
    }

    public static boolean select(String id) {
        SchematicPlacement placement = find(id);
        if (placement == null) {
            return false;
        }
        PlacementManager.get().select(placement);
        return true;
    }

    public static boolean openEditor(String id) {
        SchematicPlacement placement = find(id);
        Minecraft minecraft = Minecraft.getInstance();
        if (placement == null || minecraft.player == null) {
            return false;
        }
        PlacementManager.get().select(placement);
        minecraft.gui.setScreen(new PlacementEditorScreen(null, placement));
        return true;
    }

    private static SchematicPlacement find(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
        for (SchematicPlacement placement : PlacementManager.get().placements()) {
            if (placement.id().equals(uuid)) {
                return placement;
            }
        }
        return null;
    }
}
