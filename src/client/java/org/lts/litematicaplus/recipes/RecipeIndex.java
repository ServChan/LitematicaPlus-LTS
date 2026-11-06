package org.lts.litematicaplus.recipes;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.level.block.Blocks;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.util.Workers;

public final class RecipeIndex {
    public enum State {
        EMPTY,
        LOADING,
        READY,
        FAILED
    }

    private static RecipeGraph<Item> graph = RecipeGraph.empty();
    private static State state = State.EMPTY;
    private static boolean full;
    private static int generation;
    private static Object builtFor;
    private static int builtBook = -1;
    private static Object pendingFor;
    private static int pendingBook = -1;
    private static CompletableFuture<RecipeGraph<Item>> pending;
    private static Object localFor;
    private static List<RecipeDisplay> local = List.of();

    private RecipeIndex() {
    }

    public static RecipeGraph<Item> graph() {
        refresh();
        return graph;
    }

    public static State state() {
        return state;
    }

    public static boolean full() {
        return full;
    }

    public static int generation() {
        return generation;
    }

    public static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        int book = server != null ? 0 : bookSize(minecraft);
        if (level == builtFor && book == builtBook) {
            return;
        }
        if (pending != null && level == pendingFor && book == pendingBook) {
            return;
        }
        List<RecipeDisplay> displays = new ArrayList<>();
        try {
            if (server != null) {
                for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                    displays.addAll(holder.value().display());
                }
            } else {
                knownDisplays(minecraft, displays);
            }
            stonecutterDisplays(minecraft, displays);
        } catch (RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not read the recipes the game knows", exception);
        }
        ContextMap context = SlotDisplayContext.fromLevel(level);
        HolderLookup.Provider registries = level.registryAccess();
        boolean local = server == null;
        Object key = level;
        pendingFor = key;
        pendingBook = book;
        if (state != State.READY) {
            state = State.LOADING;
        }
        CompletableFuture<RecipeGraph<Item>> future;
        try {
            future = CompletableFuture.supplyAsync(() -> {
                List<RecipeDisplay> all = new ArrayList<>(displays);
                if (local) {
                    all.addAll(localDisplays(registries));
                }
                return build(all, context);
            }, Workers.io());
        } catch (RejectedExecutionException exception) {
            state = State.FAILED;
            pending = null;
            return;
        }
        pending = future;
        future.whenComplete((result, failure) -> minecraft.execute(() -> {
            if (pending != future) {
                return;
            }
            pending = null;
            if (failure != null || result == null) {
                LitematicaPlusClient.LOGGER.warn("Could not index recipes for the raw material count", failure);
                state = graph.size() > 0 ? State.READY : State.FAILED;
                builtFor = key;
                builtBook = book;
                return;
            }
            graph = result;
            full = !local;
            builtFor = key;
            builtBook = book;
            state = State.READY;
            generation++;
            LitematicaPlusClient.LOGGER.info("Indexed {} recipes for the raw material count", result.size());
        }));
    }

    public static void clear() {
        graph = RecipeGraph.empty();
        state = State.EMPTY;
        builtFor = null;
        builtBook = -1;
        pending = null;
        pendingFor = null;
        generation++;
    }

    private static int bookSize(Minecraft minecraft) {
        if (minecraft.player == null) {
            return 0;
        }
        ClientRecipeBook book = minecraft.player.getRecipeBook();
        if (book == null) {
            return 0;
        }
        int size = 0;
        for (RecipeCollection collection : book.getCollections()) {
            size += collection.getRecipes().size();
        }
        if (minecraft.getConnection() != null) {
            size += minecraft.getConnection().recipes().stonecutterRecipes().entries().size() * 31;
        }
        return size;
    }

    private static void knownDisplays(Minecraft minecraft, List<RecipeDisplay> displays) {
        if (minecraft.player == null || minecraft.player.getRecipeBook() == null) {
            return;
        }
        for (RecipeCollection collection : minecraft.player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (entry != null && entry.display() != null) {
                    displays.add(entry.display());
                }
            }
        }
    }

    private static void stonecutterDisplays(Minecraft minecraft, List<RecipeDisplay> displays) {
        if (minecraft.getConnection() == null) {
            return;
        }
        for (SelectableRecipe.SingleInputEntry<?> entry : minecraft.getConnection().recipes().stonecutterRecipes().entries()) {
            displays.add(new StonecutterRecipeDisplay(entry.input().display(), entry.recipe().optionDisplay(),
                    new SlotDisplay.ItemSlotDisplay(Blocks.STONECUTTER.asItem())));
        }
    }

    private static synchronized List<RecipeDisplay> localDisplays(HolderLookup.Provider registries) {
        if (localFor == registries) {
            return local;
        }
        List<RecipeDisplay> displays = new ArrayList<>();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        PackLocationInfo location = new PackLocationInfo("litematicaplus-vanilla", Component.literal("Minecraft"),
                PackSource.BUILT_IN, Optional.empty());
        try (PackResources resources = new VanillaPackResourcesBuilder().pushJarResources().exposeNamespace("minecraft")
                .build(location).fullResources()) {
            resources.listResources(PackType.SERVER_DATA, "minecraft", "recipe", (id, supplier) -> {
                if (!id.getPath().endsWith(".json")) {
                    return;
                }
                try (InputStreamReader reader = new InputStreamReader(supplier.get(), StandardCharsets.UTF_8)) {
                    parse(ops, GsonHelper.parse(reader), displays);
                } catch (Exception exception) {
                    LitematicaPlusClient.LOGGER.debug("Skipping bundled recipe {}", id, exception);
                }
            });
        } catch (Exception exception) {
            LitematicaPlusClient.LOGGER.warn("Could not read the recipes bundled with Minecraft", exception);
        }
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();
            if (modId.equals("minecraft") || modId.equals("java")) {
                continue;
            }
            for (Path root : mod.getRootPaths()) {
                Path data = root.resolve("data");
                if (!Files.isDirectory(data)) {
                    continue;
                }
                try (Stream<Path> paths = Files.walk(data)) {
                    paths.filter(Files::isRegularFile).filter(RecipeIndex::recipeFile).forEach(path -> {
                        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path),
                                StandardCharsets.UTF_8)) {
                            parse(ops, GsonHelper.parse(reader), displays);
                        } catch (Exception exception) {
                            LitematicaPlusClient.LOGGER.debug("Skipping recipe {} of {}", path, modId, exception);
                        }
                    });
                } catch (Exception exception) {
                    LitematicaPlusClient.LOGGER.debug("Could not scan the recipes of {}", modId, exception);
                }
            }
        }
        local = List.copyOf(displays);
        localFor = registries;
        return local;
    }

    private static boolean recipeFile(Path path) {
        Path name = path.getFileName();
        if (name == null || !name.toString().endsWith(".json")) {
            return false;
        }
        for (Path part : path) {
            if (part.toString().equals("recipe")) {
                return true;
            }
        }
        return false;
    }

    private static void parse(RegistryOps<JsonElement> ops, JsonElement json, List<RecipeDisplay> displays) {
        Recipe.DIRECT_CODEC.parse(ops, json).result().ifPresent(recipe -> displays.addAll(recipe.display()));
    }

    static RecipeGraph<Item> build(List<RecipeDisplay> displays, ContextMap context) {
        RecipeGraph.Builder<Item> builder = RecipeGraph.builder();
        Map<SlotDisplay, List<Item>> resolved = new LinkedHashMap<>();
        for (RecipeDisplay display : displays) {
            try {
                add(builder, display, context, resolved);
            } catch (RuntimeException exception) {
                LitematicaPlusClient.LOGGER.debug("Skipping a recipe that could not be resolved", exception);
            }
        }
        return builder.build();
    }

    private static void add(RecipeGraph.Builder<Item> builder, RecipeDisplay display, ContextMap context,
            Map<SlotDisplay, List<Item>> resolved) {
        if (display == null || display.result() == null) {
            return;
        }
        List<SlotDisplay> slots;
        RecipeGraph.Kind kind;
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            slots = shaped.ingredients();
            kind = RecipeGraph.Kind.CRAFTING;
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            slots = shapeless.ingredients();
            kind = RecipeGraph.Kind.CRAFTING;
        } else if (display instanceof StonecutterRecipeDisplay stonecutter) {
            slots = List.of(stonecutter.input());
            kind = RecipeGraph.Kind.STONECUTTER;
        } else if (display instanceof FurnaceRecipeDisplay furnace) {
            slots = List.of(furnace.ingredient());
            kind = RecipeGraph.Kind.SMELTING;
        } else {
            return;
        }
        ItemStack result = ItemStack.EMPTY;
        for (ItemStack stack : display.result().resolveForStacks(context)) {
            if (!stack.isEmpty()) {
                result = stack;
                break;
            }
        }
        if (result.isEmpty()) {
            return;
        }
        Map<List<Item>, Integer> counts = new LinkedHashMap<>();
        for (SlotDisplay slot : slots) {
            List<Item> items = resolved.computeIfAbsent(slot, key -> items(key, context));
            if (kind == RecipeGraph.Kind.SMELTING) {
                items = items.stream().filter(item -> new ItemStack(item).getMaxStackSize() > 1).toList();
            }
            if (!items.isEmpty()) {
                counts.merge(items, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return;
        }
        List<RecipeGraph.Input<Item>> inputs = new ArrayList<>();
        counts.forEach((items, count) -> inputs.add(new RecipeGraph.Input<>(items, count)));
        builder.add(new RecipeGraph.Option<>(result.getItem(), result.getCount(), kind, inputs));
    }

    private static List<Item> items(SlotDisplay slot, ContextMap context) {
        List<Item> items = new ArrayList<>();
        for (ItemStack stack : slot.resolveForStacks(context)) {
            if (!stack.isEmpty() && stack.getItem() != Items.AIR && !items.contains(stack.getItem())) {
                items.add(stack.getItem());
            }
        }
        return List.copyOf(items);
    }
}
