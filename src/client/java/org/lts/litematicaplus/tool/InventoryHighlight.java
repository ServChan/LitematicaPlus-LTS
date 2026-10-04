package org.lts.litematicaplus.tool;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.verify.MismatchType;

public final class InventoryHighlight {
    private static final int REFRESH_TICKS = 10;
    private static final int LIMIT = 384;
    private static final boolean[] TYPES = new boolean[MismatchType.VALUES.length];

    static {
        TYPES[MismatchType.MISSING.ordinal()] = true;
        TYPES[MismatchType.WRONG_BLOCK.ordinal()] = true;
    }

    private static List<BlockPos> positions = List.of();
    private static int ticks;

    private InventoryHighlight() {
    }

    public static List<BlockPos> positions() {
        return positions;
    }

    public static void tick(Minecraft minecraft) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!config.inventoryHighlight || !config.renderEnabled || minecraft.player == null || minecraft.level == null) {
            positions = List.of();
            ticks = 0;
            return;
        }
        if (ticks++ % REFRESH_TICKS != 0) {
            return;
        }
        boolean everything = minecraft.player.hasInfiniteMaterials();
        Reference2LongMap<Item> carried = MaterialList.countTopLevel(minecraft.player.getInventory());
        Vec3 eye = minecraft.player.getEyePosition();
        List<GhostEngine.Mismatch> found = new ArrayList<>();
        for (PlacementRuntime runtime : GhostEngine.get().runtimes()) {
            if (runtime.volume() == null || !runtime.placement().enabled()) {
                continue;
            }
            BlockState[] palette = runtime.volume().palette();
            boolean[] filter = new boolean[palette.length];
            boolean any = false;
            for (int i = 1; i < palette.length; i++) {
                Item item = InventoryHelper.itemFor(palette[i]);
                filter[i] = item != Items.AIR && (everything || carried.getLong(item) > 0L);
                any |= filter[i];
            }
            if (any) {
                found.addAll(GhostEngine.get().nearestMismatches(runtime, eye, LIMIT, TYPES, filter));
            }
        }
        found.sort(Comparator.comparingDouble(GhostEngine.Mismatch::distanceSquared));
        List<BlockPos> next = new ArrayList<>(Math.min(LIMIT, found.size()));
        for (int i = 0; i < found.size() && next.size() < LIMIT; i++) {
            next.add(found.get(i).pos());
        }
        positions = List.copyOf(next);
    }
}
