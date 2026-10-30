package org.lts.litematicaplus.tool;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class LayerAdvance {
    private static final int CHECK_TICKS = 20;
    private static final int LOOK_AHEAD = 32;
    private static int ticks;
    private static boolean checking;
    private static boolean enabled;
    private static LayerRange armed;

    private LayerAdvance() {
    }

    public static void tick(Minecraft minecraft) {
        if (!LitematicaPlusConfig.current().autoLayerAdvance) {
            enabled = false;
            armed = null;
            return;
        }
        if (++ticks % CHECK_TICKS != 0 || checking || minecraft.player == null) {
            return;
        }
        SchematicPlacement placement = PlacementManager.get().visibleSelection();
        GhostEngine engine = GhostEngine.get();
        PlacementRuntime runtime = placement == null ? null : engine.runtime(placement.id());
        PlacementVolume volume = runtime == null ? null : runtime.volume();
        LayerRange layer = engine.layer();
        if (volume == null || layer.isAll() || layer.mode() == LayerRange.Mode.ALL_ABOVE
                || runtime.pendingSections() > 0 || engine.inFlight() > 0) {
            return;
        }
        if (!enabled) {
            enabled = true;
            armed = layer;
        }
        List<LayerRange> ranges = new ArrayList<>(LOOK_AHEAD + 1);
        for (int step = 0; step <= LOOK_AHEAD; step++) {
            ranges.add(layer.shifted(step));
        }
        checking = true;
        engine.zoneTallies(placement.id(), ranges).whenComplete((tallies, failure) -> minecraft.execute(() -> {
            checking = false;
            if (failure == null && tallies != null && engine.layer().equals(layer)) {
                advance(layer, tallies, top(volume, layer));
            }
        }));
    }

    private static int top(PlacementVolume volume, LayerRange layer) {
        return switch (layer.axis()) {
            case X -> volume.maxX();
            case Y -> volume.maxY();
            case Z -> volume.maxZ();
        };
    }

    private static void moveTo(LayerRange next) {
        armed = next;
        ToolController.get().applyLayer(next);
    }

    static int nextStep(long[] tallies, int steps) {
        if (tallies[1] > 0L && tallies[0] < tallies[1]) {
            return 0;
        }
        for (int step = 1; step <= steps; step++) {
            long correct = tallies[step * 2];
            long total = tallies[step * 2 + 1];
            if (total > 0L && correct < total) {
                return step;
            }
        }
        return -1;
    }

    private static void advance(LayerRange layer, long[] tallies, int top) {
        int step = nextStep(tallies, LOOK_AHEAD);
        if (step == 0) {
            armed = layer;
            return;
        }
        if (!layer.equals(armed)) {
            return;
        }
        int front = layer.mode() == LayerRange.Mode.RANGE ? layer.max() : layer.single();
        if (step > 0) {
            if (front + step <= top) {
                moveTo(layer.shifted(step));
                return;
            }
        } else if (front + LOOK_AHEAD < top) {
            moveTo(layer.shifted(LOOK_AHEAD));
            return;
        }
        armed = null;
        Notify.actionBar("litematicaplus.message.layers_done");
    }
}
