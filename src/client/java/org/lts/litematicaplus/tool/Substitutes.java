package org.lts.litematicaplus.tool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseCoralFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.util.AtomicFilePersistence;
import org.lts.litematicaplus.util.Workers;

public final class Substitutes {
    private static final int MAX_ITEMS = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<Item> ITEMS = new LinkedHashSet<>();
    private static boolean loaded;
    private static volatile Set<Item> snapshot;

    private static final class FileData {
        int schemaVersion = 1;
        List<String> items = new ArrayList<>();
    }

    private Substitutes() {
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("litematica-plus-substitutes.json");
    }

    public static synchronized void load() {
        loaded = true;
        ITEMS.clear();
        snapshot = Set.of();
        Path file = path();
        FileData data = read(file);
        if (data == null) {
            data = read(file.resolveSibling(file.getFileName() + ".bak"));
        }
        if (data == null || data.items == null) {
            return;
        }
        for (String id : data.items) {
            Identifier identifier = id == null ? null : Identifier.tryParse(id);
            Item item = identifier == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(identifier).orElse(Items.AIR);
            if (item != Items.AIR && ITEMS.size() < MAX_ITEMS) {
                ITEMS.add(item);
            }
        }
        snapshot = Set.copyOf(ITEMS);
    }

    private static FileData read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, FileData.class);
        } catch (IOException | RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not read substitute list {}", file, exception);
            return null;
        }
    }

    public static synchronized int size() {
        ensureLoaded();
        return ITEMS.size();
    }

    public static synchronized List<Item> items() {
        ensureLoaded();
        return new ArrayList<>(ITEMS);
    }

    public static synchronized boolean add(Item item) {
        ensureLoaded();
        if (item == Items.AIR || !(item instanceof BlockItem) || ITEMS.size() >= MAX_ITEMS || !ITEMS.add(item)) {
            return false;
        }
        save();
        return true;
    }

    public static synchronized boolean remove(Item item) {
        ensureLoaded();
        if (!ITEMS.remove(item)) {
            return false;
        }
        save();
        return true;
    }

    public static synchronized void clear() {
        ensureLoaded();
        ITEMS.clear();
        save();
    }

    private static Set<Item> snapshot() {
        Set<Item> current = snapshot;
        if (current != null) {
            return current;
        }
        synchronized (Substitutes.class) {
            ensureLoaded();
            return snapshot;
        }
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private static void save() {
        snapshot = Set.copyOf(ITEMS);
        FileData data = new FileData();
        for (Item item : ITEMS) {
            data.items.add(BuiltInRegistries.ITEM.getKey(item).toString());
        }
        String json = GSON.toJson(data);
        Path file = path();
        Runnable write = () -> {
            try {
                AtomicFilePersistence.write(file, file.resolveSibling(file.getFileName() + ".bak"), json);
            } catch (IOException exception) {
                LitematicaPlusClient.LOGGER.error("Could not save substitute list {}", file, exception);
            }
        };
        try {
            Workers.writer().execute(write);
        } catch (RejectedExecutionException exception) {
            write.run();
        }
    }

    public static boolean accepts(BlockState target, BlockState world) {
        Block wanted = target.getBlock();
        Block present = world.getBlock();
        if (present == wanted) {
            return false;
        }
        if (wanted instanceof WallBlock && present instanceof WallBlock
                || wanted instanceof FenceGateBlock && present instanceof FenceGateBlock
                || wanted instanceof TrapDoorBlock && present instanceof TrapDoorBlock
                || wanted instanceof BaseCoralFanBlock && present instanceof BaseCoralFanBlock) {
            return true;
        }
        Item item = present.asItem();
        return item != Items.AIR && snapshot().contains(item);
    }

    public static Item find(Inventory inventory, BlockState target, Predicate<Item> usable) {
        Block wanted = target.getBlock();
        Predicate<Block> family = null;
        if (wanted instanceof WallBlock) {
            family = block -> block instanceof WallBlock;
        } else if (wanted instanceof FenceGateBlock) {
            family = block -> block instanceof FenceGateBlock;
        } else if (wanted instanceof TrapDoorBlock) {
            family = block -> block instanceof TrapDoorBlock;
        } else if (wanted instanceof BaseCoralFanBlock) {
            family = block -> block instanceof BaseCoralFanBlock;
        }
        if (family != null) {
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.getItem() instanceof BlockItem blockItem && family.test(blockItem.getBlock())
                        && usable.test(blockItem)) {
                    return stack.getItem();
                }
            }
        }
        Set<Item> list = snapshot();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && list.contains(stack.getItem()) && usable.test(stack.getItem())) {
                return stack.getItem();
            }
        }
        return Items.AIR;
    }
}
