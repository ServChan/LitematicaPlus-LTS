package org.lts.litematicaplus.mhud;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.lts.litematicaplus.materials.MaterialList;

public final class StorageSearch {
    private static final int[] PALETTE = {0xFF00E5FF, 0xFFFFD740, 0xFF69F0AE, 0xFFFF80AB, 0xFFB388FF, 0xFFFFAB40,
        0xFF40C4FF, 0xFFEEFF41};
    private static final int REFRESH_TICKS = 10;
    private static final StorageSearch INSTANCE = new StorageSearch();

    public record Match(BlockPos pos, BlockPos pair, List<Integer> colors) {
    }

    private final LinkedHashMap<Item, Integer> selected = new LinkedHashMap<>();
    private final HashMap<Integer, String> labels = new HashMap<>();
    private List<Match> matches = List.of();
    private boolean active;
    private int ticks;
    private boolean refreshRequested;
    private long seenRevision = -1L;

    private StorageSearch() {
    }

    public static StorageSearch get() {
        return INSTANCE;
    }

    public boolean active() {
        return this.active;
    }

    public boolean empty() {
        return this.selected.isEmpty();
    }

    public Integer colorFor(Item item) {
        return this.selected.get(item);
    }

    public String labelFor(int color) {
        return this.labels.getOrDefault(color, "");
    }

    public static int highlightFrames() {
        return StorageHighlight.frames();
    }

    public List<Match> matches() {
        return this.matches;
    }

    public void clear() {
        this.active = false;
        this.selected.clear();
        this.labels.clear();
        this.matches = List.of();
    }

    public void searchAll(List<Item> items) {
        LinkedHashMap<Item, Integer> next = new LinkedHashMap<>();
        HashMap<Integer, String> nextLabels = new HashMap<>();
        for (Item item : items) {
            if (next.containsKey(item)) {
                continue;
            }
            Integer color = this.selected.get(item);
            if (color == null || next.containsValue(color)) {
                color = nextColor(next.values());
            }
            next.put(item, color);
            nextLabels.put(color, name(item));
        }
        this.active = true;
        if (!next.equals(this.selected)) {
            this.selected.clear();
            this.selected.putAll(next);
            this.labels.clear();
            this.labels.putAll(nextLabels);
            this.refreshRequested = true;
        }
    }

    public int toggle(List<MaterialList.Entry> page) {
        if (MaterialHudConfig.current().searchAllChests) {
            return this.selected.size();
        }
        if (this.active && searchable(page).equals(new ArrayList<>(this.selected.keySet()))) {
            clear();
            return 0;
        }
        this.active = true;
        return sync(page);
    }

    public int follow(List<MaterialList.Entry> page) {
        if (MaterialHudConfig.current().searchAllChests) {
            return this.selected.size();
        }
        return this.active ? sync(page) : 0;
    }

    private static List<Item> searchable(List<MaterialList.Entry> page) {
        boolean skipOwned = MaterialHudConfig.current().skipOwned;
        List<Item> items = new ArrayList<>();
        for (MaterialList.Entry entry : page) {
            if (skipOwned && entry.available() >= entry.missing() || items.contains(entry.item())) {
                continue;
            }
            items.add(entry.item());
        }
        return items;
    }

    private int sync(List<MaterialList.Entry> page) {
        LinkedHashMap<Item, Integer> next = new LinkedHashMap<>();
        HashMap<Integer, String> nextLabels = new HashMap<>();
        for (Item item : searchable(page)) {
            Integer color = this.selected.get(item);
            if (color == null || next.containsValue(color)) {
                color = nextColor(next.values());
            }
            next.put(item, color);
            nextLabels.put(color, name(item));
        }
        boolean changed = !next.equals(this.selected);
        this.selected.clear();
        this.selected.putAll(next);
        this.labels.clear();
        this.labels.putAll(nextLabels);
        if (changed) {
            this.refreshRequested = true;
        }
        return this.selected.size();
    }

    private static String name(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    static int paletteColor(int index) {
        return PALETTE[Math.floorMod(index, PALETTE.length)];
    }

    private static int nextColor(Collection<Integer> used) {
        for (int color : PALETTE) {
            if (!used.contains(color)) {
                return color;
            }
        }
        float hue = used.size() * 0.618034F % 1.0F;
        int color = 0xFF000000 | (Color.HSBtoRGB(hue, 0.7F, 1.0F) & 0xFFFFFF);
        while (used.contains(color)) {
            color = 0xFF000000 | ((color + 1) & 0xFFFFFF);
        }
        return color;
    }

    public void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            this.matches = List.of();
            return;
        }
        long revision = StorageIndex.get().revision();
        if (++this.ticks % REFRESH_TICKS == 0 || this.refreshRequested || revision != this.seenRevision) {
            this.refreshRequested = false;
            this.seenRevision = revision;
            refresh(minecraft);
        }
    }

    private void refresh(Minecraft minecraft) {
        if (this.selected.isEmpty()) {
            this.matches = List.of();
            return;
        }
        MaterialHudConfig config = MaterialHudConfig.current();
        double px = minecraft.player.getX();
        double pz = minecraft.player.getZ();
        double radius = config.searchRadius;
        double radiusSq = radius * radius;
        String scope = StorageIndex.scope(minecraft);
        Map<String, Integer> byId = new HashMap<>();
        this.selected.forEach((item, color) -> byId.put(StorageIndex.itemId(item), color));
        LinkedHashMap<BlockPos, Match> found = new LinkedHashMap<>();
        for (StorageIndex.Record record : StorageIndex.get().inScope(scope)) {
            BlockPos pos = record.pos();
            if (!near(px, pz, pos, radiusSq) || !minecraft.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    || !(minecraft.level.getBlockEntity(pos) instanceof Container)) {
                continue;
            }
            for (String id : record.counts.keySet()) {
                Integer color = byId.get(id);
                if (color != null) {
                    add(found, pos, record.pair(), color);
                }
            }
        }
        if (config.searchItemFrames) {
            AABB area = new AABB(px - radius, minecraft.level.getMinY(), pz - radius, px + radius,
                    minecraft.level.getMaxY(), pz + radius);
            for (ItemFrame frame : minecraft.level.getEntitiesOfClass(ItemFrame.class, area)) {
                if (frame.getItem().isEmpty()) {
                    continue;
                }
                Integer color = this.selected.get(frame.getItem().getItem());
                if (color == null) {
                    continue;
                }
                BlockPos support = frame.getPos().relative(frame.getDirection().getOpposite());
                if (!near(px, pz, support, radiusSq) || !(minecraft.level.getBlockEntity(support) instanceof Container)) {
                    continue;
                }
                StorageIndex.Record record = canonical(scope, support);
                String id = StorageIndex.itemId(frame.getItem().getItem());
                if (record == null || record.counts.containsKey(id)) {
                    add(found, record == null ? support : record.pos(), record == null ? null : record.pair(), color);
                }
            }
        }
        this.matches = List.copyOf(found.values());
    }

    private static StorageIndex.Record canonical(String scope, BlockPos pos) {
        StorageIndex.Record record = StorageIndex.get().at(scope, pos);
        if (record != null) {
            return record;
        }
        for (StorageIndex.Record candidate : StorageIndex.get().inScope(scope)) {
            if (pos.equals(candidate.pair())) {
                return candidate;
            }
        }
        return null;
    }

    private static void add(Map<BlockPos, Match> found, BlockPos pos, BlockPos pair, int color) {
        Match match = found.get(pos);
        if (match == null) {
            match = new Match(pos, pair, new ArrayList<>());
            found.put(pos, match);
        }
        if (!match.colors().contains(color)) {
            match.colors().add(color);
        }
    }

    private static boolean near(double px, double pz, BlockPos pos, double radiusSq) {
        double dx = pos.getX() + 0.5 - px;
        double dz = pos.getZ() + 0.5 - pz;
        return dx * dx + dz * dz <= radiusSq;
    }
}
