package org.lts.litematicaplus.zones;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementLayout;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.RegionPlacement;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class ZoneManager {
    private static final ZoneManager INSTANCE = new ZoneManager();
    private static final int PROGRESS_INTERVAL_TICKS = 40;

    private record Split(PlacementVolume volume, ZonePlan plan, List<ZonePlan.Zone> zones) {
    }

    private final Map<UUID, Split> splits = new HashMap<>();
    private final Map<UUID, long[]> progress = new HashMap<>();
    private final Map<UUID, Boolean> pending = new HashMap<>();
    private int ticks;

    private ZoneManager() {
    }

    public static ZoneManager get() {
        return INSTANCE;
    }

    public List<ZonePlan.Zone> zones(SchematicPlacement placement) {
        PlacementRuntime runtime = placement == null ? null : GhostEngine.get().runtime(placement.id());
        PlacementVolume volume = runtime == null ? null : runtime.volume();
        if (volume == null || !placement.zonePlan().active()) {
            return List.of();
        }
        Split split = this.splits.get(placement.id());
        if (split == null || split.volume() != volume || !split.plan().equals(placement.zonePlan())) {
            split = new Split(volume, placement.zonePlan(), placement.zonePlan().split(volume.minX(), volume.minY(),
                    volume.minZ(), volume.maxX(), volume.maxY(), volume.maxZ()));
            this.splits.put(placement.id(), split);
            this.progress.remove(placement.id());
        }
        return split.zones();
    }

    public ZonePlan.Zone zone(SchematicPlacement placement, int index) {
        List<ZonePlan.Zone> zones = zones(placement);
        return index >= 1 && index <= zones.size() ? zones.get(index - 1) : null;
    }

    public double progress(SchematicPlacement placement, int index) {
        long[] values = this.progress.get(placement.id());
        if (values == null || index * 2 > values.length || values[index * 2 - 1] <= 0L) {
            return -1.0;
        }
        return (double) values[index * 2 - 2] / values[index * 2 - 1];
    }

    public long[] counts(SchematicPlacement placement, int index) {
        long[] values = this.progress.get(placement.id());
        if (values == null || index * 2 > values.length) {
            return null;
        }
        return new long[]{values[index * 2 - 2], values[index * 2 - 1]};
    }

    public int myZone(Minecraft minecraft, SchematicPlacement placement) {
        String self = ZoneChat.self(minecraft);
        for (Map.Entry<Integer, String> claim : placement.zoneClaims().entrySet()) {
            if (claim.getValue().equalsIgnoreCase(self)) {
                return claim.getKey();
            }
        }
        return 0;
    }

    public static String planId(SchematicPlacement placement, ZonePlan plan) {
        Schematic schematic = placement.schematic();
        if (schematic == null) {
            return "";
        }
        PlacementLayout layout = placement.layout();
        StringBuilder identity = new StringBuilder();
        identity.append(schematic.metadata().name).append('|').append(schematic.metadata().totalBlocks).append('|')
                .append(schematic.maxX() - schematic.minX()).append(',').append(schematic.maxY() - schematic.minY())
                .append(',').append(schematic.maxZ() - schematic.minZ()).append('|').append(placement.x()).append(',')
                .append(placement.y()).append(',').append(placement.z()).append('|').append(placement.rotation())
                .append('|').append(placement.mirror()).append('|').append(layout.grid());
        for (Map.Entry<String, RegionPlacement> region : new TreeMap<>(layout.regions()).entrySet()) {
            identity.append('|').append(region.getKey()).append('=').append(region.getValue());
        }
        identity.append('|').append(new TreeSet<>(placement.disabledRegions()))
                .append('|').append(plan.columns()).append('x').append(plan.rows()).append('x').append(plan.floors());
        return ZoneProtocol.planId(identity.toString());
    }

    public void tick(Minecraft minecraft) {
        ZoneChat.tick(minecraft);
        if (++this.ticks % PROGRESS_INTERVAL_TICKS != 0) {
            return;
        }
        this.splits.keySet().retainAll(ids());
        this.progress.keySet().retainAll(ids());
        for (SchematicPlacement placement : PlacementManager.get().placements()) {
            List<ZonePlan.Zone> zones = zones(placement);
            if (zones.isEmpty() || Boolean.TRUE.equals(this.pending.get(placement.id()))) {
                continue;
            }
            List<LayerRange> ranges = new ArrayList<>(zones.size());
            for (ZonePlan.Zone zone : zones) {
                ranges.add(zone.range());
            }
            UUID id = placement.id();
            Split expected = this.splits.get(id);
            this.pending.put(id, true);
            GhostEngine.get().zoneTallies(id, ranges).whenComplete((result, failure) -> minecraft.execute(() -> {
                this.pending.remove(id);
                if (result != null && this.splits.get(id) == expected) {
                    this.progress.put(id, result);
                }
            }));
        }
    }

    private List<UUID> ids() {
        List<UUID> ids = new ArrayList<>();
        for (SchematicPlacement placement : PlacementManager.get().placements()) {
            ids.add(placement.id());
        }
        return ids;
    }

    public void focus(ZonePlan.Zone zone) {
        LayerRange range = zone.range();
        ToolController.get().applyLayer(range.withClip(LayerRange.Clip.NONE));
        GhostEngine.get().setClip(range.clip());
    }

    public void unfocus() {
        ToolController.get().applyLayer(LayerRange.ALL);
        GhostEngine.get().setClip(LayerRange.Clip.NONE);
    }

    public boolean focused(ZonePlan.Zone zone) {
        return GhostEngine.get().layer().equals(zone.range());
    }

    public void claim(Minecraft minecraft, SchematicPlacement placement, int index) {
        String self = ZoneChat.self(minecraft);
        int previous = myZone(minecraft, placement);
        if (previous != 0 && previous != index) {
            release(minecraft, placement, previous);
        }
        PlacementManager.get().setZoneClaim(placement, index, self);
        ZonePlan.Zone zone = zone(placement, index);
        if (zone != null) {
            focus(zone);
        }
        broadcast(minecraft, placement, "claim", index);
    }

    public void release(Minecraft minecraft, SchematicPlacement placement, int index) {
        PlacementManager.get().setZoneClaim(placement, index, null);
        broadcast(minecraft, placement, "free", index);
    }

    public void sharePlan(Minecraft minecraft, SchematicPlacement placement) {
        ZonePlan plan = placement.zonePlan();
        if (broadcast(minecraft, placement, "plan", plan.columns(), plan.rows(), plan.floors())) {
            int mine = myZone(minecraft, placement);
            if (mine != 0) {
                broadcast(minecraft, placement, "claim", mine);
            }
            Notify.actionBar("litematicaplus.zones.plan_sent");
        }
    }

    public void askTeam(Minecraft minecraft, SchematicPlacement placement) {
        if (broadcast(minecraft, placement, "hello")) {
            Notify.actionBar("litematicaplus.zones.asked");
        }
    }

    private boolean broadcast(Minecraft minecraft, SchematicPlacement placement, String op, int... args) {
        String plan = planId(placement, placement.zonePlan());
        if (plan.isEmpty() || !placement.zonePlan().active()) {
            return false;
        }
        return ZoneChat.send(minecraft, ZoneProtocol.encode(plan, ZoneChat.self(minecraft), op, args));
    }

    void receive(Minecraft minecraft, ZoneProtocol.Message message) {
        if (message.op().equals("plan")) {
            receivePlan(minecraft, message);
            return;
        }
        SchematicPlacement placement = byPlan(message.plan());
        if (placement == null) {
            return;
        }
        int index = message.arg(0);
        boolean valid = index >= 1 && index <= placement.zonePlan().count();
        String sender = message.sender();
        switch (message.op()) {
            case "claim" -> {
                if (!valid) {
                    return;
                }
                String before = placement.zoneClaims().get(index);
                for (Map.Entry<Integer, String> claim : List.copyOf(placement.zoneClaims().entrySet())) {
                    if (claim.getValue().equalsIgnoreCase(sender) && claim.getKey() != index) {
                        PlacementManager.get().setZoneClaim(placement, claim.getKey(), null);
                    }
                }
                PlacementManager.get().setZoneClaim(placement, index, sender);
                if (before != null && before.equalsIgnoreCase(ZoneChat.self(minecraft))) {
                    Notify.chat(Component.translatable("litematicaplus.zones.taken_from_you", sender, index)
                            .withStyle(ChatFormatting.YELLOW));
                } else if (before == null || !before.equalsIgnoreCase(sender)) {
                    Notify.actionBar(Component.translatable("litematicaplus.zones.claimed_by", sender, index));
                }
            }
            case "free" -> {
                String owner = placement.zoneClaims().get(index);
                if (valid && owner != null && owner.equalsIgnoreCase(sender)) {
                    PlacementManager.get().setZoneClaim(placement, index, null);
                    Notify.actionBar(Component.translatable("litematicaplus.zones.freed_by", sender, index));
                }
            }
            case "hello" -> {
                int mine = myZone(minecraft, placement);
                if (mine != 0) {
                    broadcast(minecraft, placement, "claim", mine);
                }
            }
            default -> {
            }
        }
    }

    private void receivePlan(Minecraft minecraft, ZoneProtocol.Message message) {
        ZonePlan plan = new ZonePlan(message.arg(0), message.arg(1), message.arg(2));
        for (SchematicPlacement placement : PlacementManager.get().placements()) {
            if (!planId(placement, plan).equals(message.plan())) {
                continue;
            }
            if (!placement.zonePlan().equals(plan)) {
                PlacementManager.get().setZonePlan(placement, plan);
                Notify.chat(Component.translatable("litematicaplus.zones.plan_received", message.sender(),
                        placement.name(), plan.columns(), plan.rows(), plan.floors()).withStyle(ChatFormatting.AQUA));
            }
            return;
        }
        Notify.chat(Component.translatable("litematicaplus.zones.plan_unknown", message.sender())
                .withStyle(ChatFormatting.GRAY));
    }

    private SchematicPlacement byPlan(String plan) {
        for (SchematicPlacement placement : PlacementManager.get().placements()) {
            if (placement.zonePlan().active() && planId(placement, placement.zonePlan()).equals(plan)) {
                return placement;
            }
        }
        return null;
    }
}
