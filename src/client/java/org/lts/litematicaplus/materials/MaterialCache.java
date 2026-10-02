package org.lts.litematicaplus.materials;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.tool.TickQueue;

public final class MaterialCache {
    private static final long TTL_TICKS = 5L;

    public record Snapshot(SchematicPlacement placement, List<MaterialList.Entry> entries,
            Map<Item, MaterialList.Entry> byItem) {
        public static final Snapshot EMPTY = new Snapshot(null, List.of(), Map.of());

        public MaterialList.Entry entry(Item item) {
            return this.byItem.get(item);
        }
    }

    private static Snapshot current = Snapshot.EMPTY;
    private static long computedAt;
    private static boolean stale = true;
    private static UUID computedFor;

    private MaterialCache() {
    }

    public static void invalidate() {
        stale = true;
    }

    public static Snapshot get() {
        Minecraft minecraft = Minecraft.getInstance();
        SchematicPlacement placement = PlacementManager.get().selected();
        UUID id = placement == null ? null : placement.id();
        long now = TickQueue.now();
        if (!stale && id != null && id.equals(computedFor) && now >= computedAt && now - computedAt < TTL_TICKS) {
            return current;
        }
        stale = false;
        computedAt = now;
        computedFor = id;
        current = compute(minecraft, placement);
        return current;
    }

    private static Snapshot compute(Minecraft minecraft, SchematicPlacement placement) {
        if (placement == null || minecraft.player == null) {
            return Snapshot.EMPTY;
        }
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        if (runtime == null || runtime.volume() == null) {
            return new Snapshot(placement, List.of(), Map.of());
        }
        List<MaterialList.Entry> entries = MaterialList.compute(runtime, minecraft.player.getInventory(),
                MaterialList.Sort.SHORTFALL);
        Reference2ObjectOpenHashMap<Item, MaterialList.Entry> byItem = new Reference2ObjectOpenHashMap<>();
        for (MaterialList.Entry entry : entries) {
            byItem.put(entry.item(), entry);
        }
        return new Snapshot(placement, List.copyOf(entries), byItem);
    }
}
