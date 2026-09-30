package org.lts.litematicaplus.materials;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.world.PlacementVolume;

public final class MaterialList {
    public record Entry(Item item, long total, long missing, long available) {
        public long shortfall() {
            return Math.max(0L, this.missing - this.available);
        }
    }

    public enum Sort {
        MISSING,
        TOTAL,
        NAME,
        SHORTFALL;

        public Sort next() {
            Sort[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private static final IntegerProperty[] COUNT_PROPERTIES = {
        BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.EGGS,
        BlockStateProperties.LAYERS, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT
    };

    private MaterialList() {
    }

    public static Item itemOf(BlockState state) {
        if (state.isAir()) {
            return Items.AIR;
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return Items.AIR;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return Items.AIR;
        }
        Item item = state.getBlock().asItem();
        if (item != Items.AIR) {
            return item;
        }
        FluidState fluid = state.getFluidState();
        if (fluid.isSource()) {
            if (fluid.getType() == Fluids.WATER) {
                return Items.WATER_BUCKET;
            }
            if (fluid.getType() == Fluids.LAVA) {
                return Items.LAVA_BUCKET;
            }
        }
        return Items.AIR;
    }

    public static int countOf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)
                && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
            return 2;
        }
        for (IntegerProperty property : COUNT_PROPERTIES) {
            if (state.hasProperty(property)) {
                return state.getValue(property);
            }
        }
        return 1;
    }

    public static List<Entry> compute(PlacementRuntime runtime, Inventory inventory, Sort sort) {
        PlacementVolume volume = runtime == null ? null : runtime.volume();
        if (volume == null) {
            return List.of();
        }
        BlockState[] palette = volume.palette();
        long[] totals = volume.totalByPalette();
        long[] correct = runtime.correctByPalette();
        Reference2LongOpenHashMap<Item> totalByItem = new Reference2LongOpenHashMap<>();
        Reference2LongOpenHashMap<Item> missingByItem = new Reference2LongOpenHashMap<>();
        for (int i = 1; i < palette.length; i++) {
            long total = totals[i];
            if (total <= 0L) {
                continue;
            }
            Item item = itemOf(palette[i]);
            if (item == Items.AIR) {
                continue;
            }
            int multiplier = countOf(palette[i]);
            long done = i < correct.length ? Math.max(0L, Math.min(total, correct[i])) : 0L;
            totalByItem.addTo(item, total * multiplier);
            missingByItem.addTo(item, (total - done) * multiplier);
        }
        Reference2LongMap<Item> available = inventory == null ? new Reference2LongOpenHashMap<>() : countInventory(inventory);
        List<Entry> entries = new ArrayList<>(totalByItem.size());
        for (Reference2LongMap.Entry<Item> entry : totalByItem.reference2LongEntrySet()) {
            Item item = entry.getKey();
            entries.add(new Entry(item, entry.getLongValue(), missingByItem.getLong(item), available.getLong(item)));
        }
        entries.sort(comparator(sort));
        return entries;
    }

    private static Comparator<Entry> comparator(Sort sort) {
        Comparator<Entry> byName = Comparator.comparing(entry -> new ItemStack(entry.item()).getHoverName().getString());
        return switch (sort) {
            case TOTAL -> Comparator.comparingLong(Entry::total).reversed().thenComparing(byName);
            case NAME -> byName;
            case SHORTFALL -> Comparator.comparingLong(Entry::shortfall).reversed().thenComparing(byName);
            case MISSING -> Comparator.comparingLong(Entry::missing).reversed().thenComparing(byName);
        };
    }

    public static Reference2LongMap<Item> countInventory(Inventory inventory) {
        Reference2LongOpenHashMap<Item> counts = new Reference2LongOpenHashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            add(counts, inventory.getItem(slot), 1);
        }
        return counts;
    }

    private static void add(Reference2LongOpenHashMap<Item> counts, ItemStack stack, int depth) {
        if (stack.isEmpty()) {
            return;
        }
        counts.addTo(stack.getItem(), stack.getCount());
        if (depth > 2) {
            return;
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            long outer = stack.getCount();
            contents.nonEmptyItemCopyStream().forEach(inner -> {
                int count = inner.getCount();
                inner.setCount((int) Math.min(Integer.MAX_VALUE, count * outer));
                add(counts, inner, depth + 1);
            });
        }
    }

    public static String formatCount(long count, int stackSize) {
        if (stackSize <= 1 || count < stackSize) {
            return Long.toString(count);
        }
        long stacks = count / stackSize;
        long rest = count % stackSize;
        if (stacks >= 54) {
            long boxes = stacks / 27;
            long leftover = stacks % 27;
            return count + " (" + boxes + "sb" + (leftover > 0 ? " + " + leftover + "st" : "")
                    + (rest > 0 ? " + " + rest : "") + ")";
        }
        return count + " (" + stacks + "st" + (rest > 0 ? " + " + rest : "") + ")";
    }
}
