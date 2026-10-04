package org.lts.litematicaplus.materials;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lts.litematicaplus.LitematicaPlusConfig;

public final class EnderChestMemory {
    private static final String TITLE_KEY = "container.enderchest";

    private static ClientPacketListener owner;
    private static List<ItemStack> contents = List.of();
    private static Reference2LongMap<Item> counts;

    private EnderChestMemory() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!isEnderChest(screen)) {
                return;
            }
            ChestMenu menu = (ChestMenu) ((AbstractContainerScreen<?>) screen).getMenu();
            ScreenEvents.afterTick(screen).register(current -> capture(client, menu.getContainer()));
            ScreenEvents.remove(screen).register(current -> capture(client, menu.getContainer()));
        });
    }

    static boolean isEnderChest(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> container && container.getMenu() instanceof ChestMenu
                && screen.getTitle().getContents() instanceof TranslatableContents translatable
                && TITLE_KEY.equals(translatable.getKey());
    }

    private static void capture(Minecraft minecraft, Container container) {
        if (minecraft.getConnection() == null) {
            return;
        }
        List<ItemStack> copy = new ArrayList<>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            copy.add(container.getItem(slot).copy());
        }
        if (owner == minecraft.getConnection() && same(copy, contents)) {
            return;
        }
        owner = minecraft.getConnection();
        contents = copy;
        counts = null;
        MaterialCache.invalidate();
    }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean known() {
        return owner != null && owner == Minecraft.getInstance().getConnection();
    }

    public static Reference2LongMap<Item> counts() {
        if (!known()) {
            return new Reference2LongOpenHashMap<>();
        }
        Reference2LongMap<Item> current = counts;
        if (current == null) {
            current = MaterialList.countStacks(contents);
            counts = current;
        }
        return current;
    }

    public static boolean counted() {
        return LitematicaPlusConfig.current().enderChestMaterials;
    }
}
