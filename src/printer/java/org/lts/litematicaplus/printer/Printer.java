package org.lts.litematicaplus.printer;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.tool.EasyPlace;
import org.lts.litematicaplus.tool.InventoryHelper;
import org.lts.litematicaplus.tool.PlacementSolver;
import org.lts.litematicaplus.tool.TickQueue;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.verify.StateComparator;

public final class Printer {
    private static final int MISSING_LIMIT = 96;
    private static final int FIX_LIMIT = 32;
    private static final int FAILURES_PER_TICK = 6;
    private static final int FAILURE_COOLDOWN_TICKS = 10;
    private static final Map<BlockPos, Long> FAILED = new HashMap<>();
    private static ClientLevel lastLevel;
    private static long actions;

    private Printer() {
    }

    public static long actions() {
        return actions;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level != lastLevel) {
            FAILED.clear();
            lastLevel = minecraft.level;
        }
        LocalPlayer player = minecraft.player;
        if (!PrinterConfig.current().enabled || player == null || minecraft.level == null || player.isSpectator()
                || player.isUsingItem() || player.isFallFlying() || !player.mayBuild()) {
            return;
        }
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!config.renderEnabled || !EasyPlace.ready(minecraft)) {
            return;
        }
        double range = Math.min(PrinterConfig.current().range, player.blockInteractionRange());
        double rangeSquared = range * range;
        long now = TickQueue.now();
        if (FAILED.size() > 512) {
            FAILED.values().removeIf(until -> until < now);
        }
        int failures = 0;
        for (GhostEngine.Mismatch target : targets(player, config)) {
            if (target.distanceSquared() > rangeSquared) {
                break;
            }
            BlockPos pos = target.pos();
            Long until = FAILED.get(pos);
            if (until != null && until >= now || !player.isWithinBlockInteractionRange(pos, 0.0)) {
                continue;
            }
            switch (EasyPlace.actAt(minecraft, pos)) {
                case ACTED -> {
                    FAILED.remove(pos);
                    actions++;
                    return;
                }
                case FAILED -> {
                    FAILED.put(pos, now + FAILURE_COOLDOWN_TICKS);
                    if (++failures >= FAILURES_PER_TICK) {
                        return;
                    }
                }
                case SKIPPED -> {
                }
            }
        }
    }

    private static List<GhostEngine.Mismatch> targets(LocalPlayer player, LitematicaPlusConfig config) {
        boolean[] missing = new boolean[MismatchType.VALUES.length];
        missing[MismatchType.MISSING.ordinal()] = true;
        boolean[] fixes = new boolean[MismatchType.VALUES.length];
        fixes[MismatchType.WRONG_STATE.ordinal()] = config.easyPlaceFinishStates;
        fixes[MismatchType.WRONG_BLOCK.ordinal()] = config.breakWrongBlocks;
        boolean anyFix = config.easyPlaceFinishStates || config.breakWrongBlocks;
        Vec3 eye = player.getEyePosition();
        Reference2LongMap<Item> carried = player.hasInfiniteMaterials() ? null
                : MaterialList.countTopLevel(player.getInventory());
        GhostEngine engine = GhostEngine.get();
        List<GhostEngine.Mismatch> found = new ArrayList<>();
        for (PlacementRuntime runtime : engine.runtimes()) {
            if (runtime.volume() == null || !runtime.placement().enabled()) {
                continue;
            }
            if (carried == null) {
                found.addAll(engine.nearestMismatches(runtime, eye, MISSING_LIMIT, missing));
            } else {
                boolean[] filter = filter(runtime.volume().palette(), carried, config);
                if (filter != null) {
                    found.addAll(engine.nearestMismatches(runtime, eye, MISSING_LIMIT, missing, filter));
                }
            }
            if (anyFix) {
                found.addAll(engine.nearestMismatches(runtime, eye, FIX_LIMIT, fixes));
            }
        }
        found.sort(Comparator.comparingDouble(GhostEngine.Mismatch::distanceSquared));
        return found;
    }

    private static boolean[] filter(BlockState[] palette, Reference2LongMap<Item> carried, LitematicaPlusConfig config) {
        boolean lenient = config.easyPlaceSubstitutes || config.looseBlocks;
        boolean[] filter = new boolean[palette.length];
        boolean any = false;
        for (int i = 1; i < palette.length; i++) {
            Item item = InventoryHelper.itemFor(palette[i]);
            if (item == Items.AIR) {
                continue;
            }
            filter[i] = lenient || carried.getLong(item) > 0L || config.soilInterchangeable
                    && StateComparator.isSoil(PlacementSolver.baseState(palette[i]).getBlock());
            any |= filter[i];
        }
        return any ? filter : null;
    }
}
