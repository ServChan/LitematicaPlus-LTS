package org.lts.litematicaplus.materials;

import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.lts.litematicaplus.Hotkeys;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.gui.FlatButton;
import org.lts.litematicaplus.gui.Theme;
import org.lts.litematicaplus.mixin.AbstractContainerScreenAccessor;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.tool.TickQueue;
import org.lts.litematicaplus.util.Notify;

public final class ContainerHelper {
    private static final int REFRESH_TICKS = 10;
    private static final int BUTTON_WIDTH = 124;
    private static final int DIRECT_FILL = 0x5055FF55;
    private static final int BOX_FILL = 0x50FFC857;

    private static AbstractContainerScreen<?> open;
    private static Plan plan = Plan.EMPTY;
    private static FlatButton button;
    private static int refreshIn;
    private static boolean running;

    private record Move(int slot, ItemStack expected, boolean box) {
    }

    private record Plan(List<Move> moves, Int2BooleanMap highlighted, boolean listed) {
        private static final Plan EMPTY = new Plan(List.of(), new Int2BooleanOpenHashMap(), false);

        private int boxes() {
            int count = 0;
            for (Move move : this.moves) {
                if (move.box()) {
                    count++;
                }
            }
            return count;
        }
    }

    private ContainerHelper() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !isStorage(container)
                    || !LitematicaPlusConfig.current().materialHelper || PlacementManager.get().selected() == null) {
                return;
            }
            open = container;
            running = false;
            refresh(container);
            addButton(container);
            ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> highlight(container, graphics));
            ScreenEvents.afterTick(screen).register(current -> {
                if (--refreshIn <= 0 && !running) {
                    refresh(container);
                }
            });
            ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) -> {
                if (Hotkeys.takeMaterials() != null && Hotkeys.takeMaterials().matches(event)) {
                    take(container);
                    return false;
                }
                return true;
            });
            ScreenEvents.remove(screen).register(current -> {
                if (open == current) {
                    open = null;
                    plan = Plan.EMPTY;
                    button = null;
                    running = false;
                }
            });
        });
    }

    public static boolean isStorage(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return false;
        }
        AbstractContainerMenu menu = container.getMenu();
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof DispenserMenu
                || menu instanceof HopperMenu;
    }

    private static void refresh(AbstractContainerScreen<?> screen) {
        refreshIn = REFRESH_TICKS;
        plan = build(screen);
        updateButton();
    }

    private static Plan build(AbstractContainerScreen<?> screen) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        SchematicPlacement placement = PlacementManager.get().selected();
        if (player == null || placement == null) {
            return Plan.EMPTY;
        }
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        if (runtime == null || runtime.volume() == null) {
            return Plan.EMPTY;
        }
        Reference2LongOpenHashMap<Item> remaining = new Reference2LongOpenHashMap<>();
        for (MaterialList.Entry entry : MaterialList.compute(runtime, player.getInventory(), MaterialList.Sort.MISSING)) {
            if (entry.shortfall() > 0L) {
                remaining.put(entry.item(), entry.shortfall());
            }
        }
        return plan(screen.getMenu().slots, player, remaining);
    }

    private static Plan plan(List<Slot> slots, LocalPlayer player, Reference2LongOpenHashMap<Item> remaining) {
        if (remaining.isEmpty()) {
            return new Plan(List.of(), new Int2BooleanOpenHashMap(), true);
        }
        List<Move> moves = new ArrayList<>();
        Int2BooleanOpenHashMap highlighted = new Int2BooleanOpenHashMap();
        for (Slot slot : slots) {
            if (slot.container == player.getInventory() || !slot.hasItem()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            long wanted = remaining.getLong(stack.getItem());
            if (wanted > 0L && stack.get(DataComponents.CONTAINER) == null) {
                remaining.put(stack.getItem(), wanted - stack.getCount());
                moves.add(new Move(slot.index, stack.copy(), false));
                highlighted.put(slot.index, false);
                continue;
            }
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents == null) {
                continue;
            }
            boolean useful = false;
            for (ItemStack inner : contents.nonEmptyItemCopyStream().toList()) {
                long needed = remaining.getLong(inner.getItem());
                if (needed > 0L) {
                    remaining.put(inner.getItem(), needed - (long) inner.getCount() * stack.getCount());
                    useful = true;
                }
            }
            if (useful) {
                moves.add(new Move(slot.index, stack.copy(), true));
                highlighted.put(slot.index, true);
            }
        }
        return new Plan(List.copyOf(moves), highlighted, true);
    }

    private static void addButton(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor) screen;
        int x = bounds.litematicaplus$leftPos() + bounds.litematicaplus$imageWidth() - BUTTON_WIDTH;
        int y = bounds.litematicaplus$topPos() - Theme.CONTROL_HEIGHT - 3;
        if (y < 2) {
            x = Math.min(screen.width - BUTTON_WIDTH - 2, bounds.litematicaplus$leftPos() + bounds.litematicaplus$imageWidth() + 4);
            y = bounds.litematicaplus$topPos();
        }
        button = FlatButton.primary(x, y, BUTTON_WIDTH, Component.translatable("litematicaplus.materials.take", 0),
                pressed -> take(screen));
        Screens.getWidgets(screen).add(button);
        updateButton();
    }

    private static void updateButton() {
        if (button == null) {
            return;
        }
        int count = plan.moves().size();
        button.setMessage(Component.translatable(count > 0 ? "litematicaplus.materials.take"
                : "litematicaplus.materials.take_none", count));
        button.active = count > 0 && !running;
        button.setTooltip(Tooltip.create(Component.translatable(count > 0 ? "litematicaplus.materials.take.tip"
                : "litematicaplus.materials.take_none.tip", count - plan.boxes(), plan.boxes())));
    }

    public static void take(AbstractContainerScreen<?> screen) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || running || open != screen) {
            return;
        }
        refresh(screen);
        List<Move> moves = plan.moves();
        if (moves.isEmpty()) {
            Notify.actionBar("litematicaplus.materials.take_none.tip");
            return;
        }
        int free = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                free++;
            }
        }
        int count = Math.min(moves.size(), free);
        if (count == 0) {
            Notify.actionBar("litematicaplus.materials.take_full");
            return;
        }
        int containerId = screen.getMenu().containerId;
        running = true;
        updateButton();
        for (int i = 0; i < count; i++) {
            Move move = moves.get(i);
            boolean last = i == count - 1;
            boolean queued = TickQueue.after(i + 1, () -> {
                try {
                    quickMove(screen, containerId, move);
                } finally {
                    if (last) {
                        finish(screen);
                    }
                }
            });
            if (!queued) {
                finish(screen);
                break;
            }
        }
        Notify.actionBar(count < moves.size() ? "litematicaplus.materials.take_partial" : "litematicaplus.materials.take_started",
                count, moves.size() - count);
    }

    private static void finish(AbstractContainerScreen<?> screen) {
        running = false;
        if (open == screen) {
            refresh(screen);
        }
    }

    private static void quickMove(AbstractContainerScreen<?> screen, int containerId, Move move) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null || open != screen) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (menu.containerId != containerId || move.slot() >= menu.slots.size()) {
            return;
        }
        ItemStack current = menu.slots.get(move.slot()).getItem();
        if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, move.expected())) {
            return;
        }
        minecraft.gameMode.handleContainerInput(containerId, move.slot(), 0, ContainerInput.QUICK_MOVE, minecraft.player);
    }

    private static void highlight(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        if (plan.highlighted().isEmpty()) {
            return;
        }
        AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor) screen;
        int left = bounds.litematicaplus$leftPos();
        int top = bounds.litematicaplus$topPos();
        List<Slot> slots = screen.getMenu().slots;
        for (Int2BooleanMap.Entry entry : plan.highlighted().int2BooleanEntrySet()) {
            if (entry.getIntKey() >= slots.size()) {
                continue;
            }
            Slot slot = slots.get(entry.getIntKey());
            int x = left + slot.x;
            int y = top + slot.y;
            boolean box = entry.getBooleanValue();
            graphics.fill(x, y, x + 16, y + 16, box ? BOX_FILL : DIRECT_FILL);
            graphics.outline(x - 1, y - 1, 18, 18, box ? Theme.WARNING : Theme.SUCCESS);
        }
    }
}
