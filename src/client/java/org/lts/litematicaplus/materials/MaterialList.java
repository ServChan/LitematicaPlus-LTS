package org.lts.litematicaplus.materials;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LiquidBlock;
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
    private static final Reference2ObjectOpenHashMap<Item, String> NAMES = new Reference2ObjectOpenHashMap<>();
    private static Language namesLanguage;

    public record Entry(Item item, long total, long missing, long available, long ready) {
        public Entry(Item item, long total, long missing, long available) {
            this(item, total, missing, available, available);
        }

        public long shortfall() {
            return Math.max(0L, this.missing - this.available);
        }

        public long packed() {
            return Math.max(0L, this.available - this.ready);
        }

        public String haveText() {
            long packed = packed();
            return packed > 0L ? Math.min(this.ready, this.available) + "+" + packed : Long.toString(this.available);
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

    @FunctionalInterface
    public interface Sink {
        void add(Item item, int count);
    }

    private static volatile Map<Block, Item> overrides;

    private MaterialList() {
    }

    private static Map<Block, Item> overrides() {
        Map<Block, Item> current = overrides;
        if (current == null) {
            current = new IdentityHashMap<>();
            current.put(Blocks.KELP_PLANT, Items.KELP);
            current.put(Blocks.TALL_SEAGRASS, Items.SEAGRASS);
            current.put(Blocks.BAMBOO_SAPLING, Items.BAMBOO);
            current.put(Blocks.CAVE_VINES_PLANT, Items.GLOW_BERRIES);
            current.put(Blocks.WEEPING_VINES_PLANT, Items.WEEPING_VINES);
            current.put(Blocks.TWISTING_VINES_PLANT, Items.TWISTING_VINES);
            current.put(Blocks.ATTACHED_MELON_STEM, Items.MELON_SEEDS);
            current.put(Blocks.ATTACHED_PUMPKIN_STEM, Items.PUMPKIN_SEEDS);
            current.put(Blocks.BIG_DRIPLEAF_STEM, Items.BIG_DRIPLEAF);
            current.put(Blocks.WATER_CAULDRON, Items.CAULDRON);
            current.put(Blocks.LAVA_CAULDRON, Items.CAULDRON);
            current.put(Blocks.POWDER_SNOW_CAULDRON, Items.CAULDRON);
            overrides = current;
        }
        return current;
    }

    public static Item candleOf(Block candleCake) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(candleCake);
        String path = id.getPath();
        if (!path.endsWith("_cake")) {
            return Items.AIR;
        }
        return BuiltInRegistries.ITEM.getOptional(
                Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(0, path.length() - 5))).orElse(Items.AIR);
    }

    public static void requirements(BlockState state, Sink sink) {
        Block block = state.getBlock();
        if (block instanceof FlowerPotBlock pot) {
            sink.add(Items.FLOWER_POT, 1);
            Item content = pot.getPotted().asItem();
            if (content != Items.AIR) {
                sink.add(content, 1);
            }
            return;
        }
        if (block instanceof CandleCakeBlock) {
            sink.add(Items.CAKE, 1);
            Item candle = candleOf(block);
            if (candle != Items.AIR) {
                sink.add(candle, 1);
            }
            return;
        }
        Item item = itemOf(state);
        if (item != Items.AIR) {
            sink.add(item, block == Blocks.TALL_SEAGRASS ? 2 : countOf(state));
        }
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
        Item override = overrides().get(state.getBlock());
        if (override != null) {
            return override;
        }
        FluidState fluid = state.getFluidState();
        if (state.getBlock() instanceof LiquidBlock && fluid.isSource()) {
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
        return compute(runtime, inventory, sort, true);
    }

    public static List<Entry> compute(PlacementRuntime runtime, Inventory inventory, Sort sort, boolean withEnderChest) {
        PlacementVolume volume = runtime == null ? null : runtime.volume();
        if (volume == null) {
            return List.of();
        }
        BlockState[] palette = volume.palette();
        long[] totals = runtime.totalByPalette();
        long[] correct = runtime.correctByPalette();
        Reference2LongOpenHashMap<Item> totalByItem = new Reference2LongOpenHashMap<>();
        Reference2LongOpenHashMap<Item> missingByItem = new Reference2LongOpenHashMap<>();
        int paletteSize = Math.min(palette.length, totals.length);
        for (int i = 1; i < paletteSize; i++) {
            long total = totals[i];
            if (total <= 0L) {
                continue;
            }
            long done = i < correct.length ? Math.max(0L, Math.min(total, correct[i])) : 0L;
            requirements(palette[i], (item, multiplier) -> {
                totalByItem.addTo(item, total * multiplier);
                missingByItem.addTo(item, (total - done) * multiplier);
            });
        }
        Reference2LongMap<Item> available = inventory == null ? new Reference2LongOpenHashMap<>() : countInventory(inventory);
        Reference2LongMap<Item> ready = inventory == null ? new Reference2LongOpenHashMap<>() : countTopLevel(inventory);
        if (withEnderChest && inventory != null && EnderChestMemory.counted()) {
            for (Reference2LongMap.Entry<Item> stored : EnderChestMemory.counts().reference2LongEntrySet()) {
                available.put(stored.getKey(), available.getLong(stored.getKey()) + stored.getLongValue());
            }
        }
        List<Entry> entries = new ArrayList<>(totalByItem.size());
        for (Reference2LongMap.Entry<Item> entry : totalByItem.reference2LongEntrySet()) {
            Item item = entry.getKey();
            entries.add(new Entry(item, entry.getLongValue(), missingByItem.getLong(item), available.getLong(item),
                    ready.getLong(item)));
        }
        entries.sort(comparator(sort));
        return entries;
    }

    private static Reference2ObjectOpenHashMap<Item, String> names() {
        Language language = Language.getInstance();
        if (language != namesLanguage) {
            NAMES.clear();
            namesLanguage = language;
        }
        return NAMES;
    }

    static String name(Item item) {
        return names().computeIfAbsent(item, (Item key) -> new ItemStack(key).getHoverName().getString());
    }

    private static Comparator<Entry> comparator(Sort sort) {
        Comparator<Entry> byName = Comparator.comparing((Entry entry) -> name(entry.item()));
        return switch (sort) {
            case TOTAL -> Comparator.comparingLong(Entry::total).reversed().thenComparing(byName);
            case NAME -> byName;
            case SHORTFALL -> Comparator.comparingLong(Entry::shortfall).reversed().thenComparing(byName);
            case MISSING -> Comparator.comparingLong(Entry::missing).reversed().thenComparing(byName);
        };
    }

    public static Reference2LongMap<Item> countTopLevel(Inventory inventory) {
        Reference2LongOpenHashMap<Item> counts = new Reference2LongOpenHashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                counts.addTo(stack.getItem(), stack.getCount());
            }
        }
        return counts;
    }

    public static Reference2LongMap<Item> countStacks(Iterable<ItemStack> stacks) {
        Reference2LongOpenHashMap<Item> counts = new Reference2LongOpenHashMap<>();
        for (ItemStack stack : stacks) {
            add(counts, stack, 1);
        }
        return counts;
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
    }}
