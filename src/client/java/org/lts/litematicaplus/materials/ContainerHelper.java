package org.lts.litematicaplus.materials;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.lts.litematicaplus.Hotkeys;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.gui.Theme;
import org.lts.litematicaplus.mixin.AbstractContainerScreenAccessor;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.util.Notify;

public final class ContainerHelper {
    private static final int REFRESH_TICKS = 10;
    private static final int DIRECT_FILL = 0x5055FF55;
    private static final int BOX_FILL = 0x50FFC857;
    private static final int FOCUS_FILL = 0x6000E5FF;

    private static AbstractContainerScreen<?> open;
    private static MaterialPanel panel;
    private static TransferJob job;
    private static int refreshIn;
    private static int seenState;
    private static int statusHold;
    private static Int2IntOpenHashMap highlighted = new Int2IntOpenHashMap();
    private static Item focusItem;
    private static IntOpenHashSet focusSlots = new IntOpenHashSet();
    private static long focusStamp;

    private ContainerHelper() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !isStorage(container)
                    || !LitematicaPlusConfig.current().materialHelper || !schematicShown()) {
                return;
            }
            if (open != container) {
                job = null;
            }
            open = container;
            AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor) container;
            panel = new MaterialPanel(client.font, new PanelActions(container));
            if (LitematicaPlusConfig.current().materialPanel) {
                panel.layout(container, bounds.litematicaplus$leftPos(), bounds.litematicaplus$topPos(),
                        bounds.litematicaplus$imageWidth(), bounds.litematicaplus$imageHeight());
            }
            refresh(container);
            ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> {
                highlight(container, graphics);
                if (panel != null) {
                    panel.render(graphics, mouseX, mouseY, delta);
                }
            });
            ScreenEvents.afterTick(screen).register(current -> tick(container));
            ScreenMouseEvents.allowMouseClick(screen).register((current, event) ->
                    panel == null || !panel.contains(event.x(), event.y()) || panel.overButton(event.x(), event.y())
                            || !panel.click(event));
            ScreenMouseEvents.allowMouseScroll(screen).register((current, mouseX, mouseY, horizontal, vertical) ->
                    panel == null || !panel.contains(mouseX, mouseY) || !panel.scroll(vertical));
            ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) -> {
                if (current.getFocused() instanceof EditBox) {
                    return true;
                }
                if (Hotkeys.takeMaterials() != null && Hotkeys.matches("take_materials", event)) {
                    start(container, TransferJob.Kind.TAKE, null, false);
                    return false;
                }
                return true;
            });
            ScreenEvents.remove(screen).register(current -> {
                if (open == current) {
                    open = null;
                    panel = null;
                    job = null;
                    highlighted = new Int2IntOpenHashMap();
                    focusItem = null;
                    focusSlots = new IntOpenHashSet();
                }
            });
        });
    }

    public static boolean schematicShown() {
        return PlacementManager.get().announcedSelection() != null;
    }

    public static boolean isStorage(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return false;
        }
        AbstractContainerMenu menu = container.getMenu();
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof DispenserMenu
                || menu instanceof HopperMenu || menu instanceof AbstractMountInventoryMenu;
    }

    public static void take(AbstractContainerScreen<?> screen) {
        start(screen, TransferJob.Kind.TAKE, null, false);
    }

    private record PanelActions(AbstractContainerScreen<?> screen) implements MaterialPanel.Actions {
        @Override
        public void takeAll() {
            start(this.screen, TransferJob.Kind.TAKE, null, false);
        }

        @Override
        public void deposit() {
            start(this.screen, TransferJob.Kind.DEPOSIT, null, false);
        }

        @Override
        public void take(Item item, boolean everything) {
            start(this.screen, TransferJob.Kind.TAKE, Set.of(item), everything);
        }
    }

    private static void start(AbstractContainerScreen<?> screen, TransferJob.Kind kind, Set<Item> only, boolean everything) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || open != screen) {
            return;
        }
        if (job != null) {
            Notify.actionBar("litematicaplus.panel.busy");
            return;
        }
        if (!screen.getMenu().getCarried().isEmpty()) {
            Notify.actionBar("litematicaplus.panel.cursor_busy");
            return;
        }
        job = new TransferJob(kind, only, everything, screen.getMenu(), player.getInventory());
        if (panel != null) {
            panel.status(Component.translatable(kind == TransferJob.Kind.TAKE ? "litematicaplus.panel.taking"
                    : "litematicaplus.panel.depositing"), Theme.ACCENT);
        }
        refresh(screen);
    }

    private static void tick(AbstractContainerScreen<?> screen) {
        if (open != screen) {
            return;
        }
        if (job != null) {
            Minecraft minecraft = Minecraft.getInstance();
            TransferJob.Outcome outcome = job.step(minecraft, screen.getMenu());
            if (outcome != TransferJob.Outcome.RUNNING) {
                finish(minecraft, outcome);
            }
            refresh(screen);
            return;
        }
        if (statusHold > 0) {
            statusHold--;
        }
        if (--refreshIn <= 0 || screen.getMenu().getStateId() != seenState) {
            refresh(screen);
        }
    }

    private static void finish(Minecraft minecraft, TransferJob.Outcome outcome) {
        TransferJob done = job;
        job = null;
        if (done == null || minecraft.player == null) {
            return;
        }
        long moved = done.moved(minecraft.player.getInventory());
        boolean take = done.kind() == TransferJob.Kind.TAKE;
        String key = switch (outcome) {
            case INVENTORY_FULL -> "litematicaplus.panel.result.inventory_full";
            case CONTAINER_FULL -> "litematicaplus.panel.result.container_full";
            case INTERRUPTED -> "litematicaplus.panel.result.interrupted";
            default -> moved == 0L ? (take ? "litematicaplus.panel.result.nothing_taken"
                    : "litematicaplus.panel.result.nothing_deposited")
                    : take ? "litematicaplus.panel.result.taken" : "litematicaplus.panel.result.deposited";
        };
        int color = outcome == TransferJob.Outcome.DONE && moved > 0L ? Theme.SUCCESS
                : outcome == TransferJob.Outcome.DONE ? Theme.TEXT_DIM : Theme.WARNING;
        Component message = Component.translatable(key, moved);
        if (panel != null && panel.expanded()) {
            panel.status(message, color);
            statusHold = 100;
        } else {
            Notify.actionBar(key, moved);
        }
    }

    private static void refresh(AbstractContainerScreen<?> screen) {
        refreshIn = REFRESH_TICKS;
        seenState = screen.getMenu().getStateId();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        MaterialCache.Snapshot snapshot = MaterialCache.get();
        Int2IntOpenHashMap marks = new Int2IntOpenHashMap();
        focusStamp++;
        if (player == null || snapshot.placement() == null) {
            highlighted = marks;
            panel = null;
            return;
        }
        Reference2LongOpenHashMap<Item> here = new Reference2LongOpenHashMap<>();
        Reference2LongOpenHashMap<Item> boxed = new Reference2LongOpenHashMap<>();
        for (Slot slot : screen.getMenu().slots) {
            if (!TransferJob.storageSlot(slot, screen.getMenu(), player.getInventory()) || !slot.hasItem()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (TransferJob.loose(stack)) {
                here.addTo(stack.getItem(), stack.getCount());
                MaterialList.Entry entry = snapshot.entry(stack.getItem());
                if (entry != null && entry.shortfall() > 0L) {
                    marks.put(slot.index, 1);
                }
                continue;
            }
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            boolean useful = false;
            for (ItemStack inner : contents.nonEmptyItemCopyStream().toList()) {
                long count = (long) inner.getCount() * stack.getCount();
                boxed.addTo(inner.getItem(), count);
                MaterialList.Entry entry = snapshot.entry(inner.getItem());
                useful |= entry != null && entry.shortfall() > 0L;
            }
            if (useful) {
                marks.put(slot.index, 2);
            }
        }
        highlighted = marks;
        if (panel == null || !panel.visible()) {
            return;
        }
        List<MaterialPanel.Row> rows = new ArrayList<>();
        boolean canDeposit = false;
        for (MaterialList.Entry entry : snapshot.entries()) {
            canDeposit |= entry.available() > entry.missing();
            if (entry.missing() <= 0L || entry.shortfall() <= 0L) {
                continue;
            }
            ItemStack icon = new ItemStack(entry.item());
            rows.add(new MaterialPanel.Row(entry.item(), icon, MaterialList.name(entry.item()), entry.shortfall(),
                    entry.missing(), entry.available(), here.getLong(entry.item()), boxed.getLong(entry.item())));
        }
        rows.sort(Comparator.<MaterialPanel.Row>comparingInt(row -> row.here() > 0L ? 0 : row.boxed() > 0L ? 1 : 2)
                .thenComparing(Comparator.comparingLong(MaterialPanel.Row::shortfall).reversed()));
        boolean canTake = rows.stream().anyMatch(row -> row.here() > 0L || row.boxed() > 0L);
        boolean anyMissing = snapshot.entries().stream().anyMatch(entry -> entry.missing() > 0L);
        Component empty = Component.translatable(anyMissing ? "litematicaplus.panel.empty.have_all"
                : "litematicaplus.panel.empty.complete");
        panel.update(Component.translatable("litematicaplus.panel.title", snapshot.placement().name()), rows, empty,
                canTake, canDeposit, job != null);
        if (job == null && statusHold <= 0) {
            long useful = rows.stream().filter(row -> row.here() > 0L).count();
            panel.status(Component.translatable("litematicaplus.panel.summary", rows.size(), useful), Theme.TEXT_DIM);
        }
    }

    private static void highlight(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
        AbstractContainerScreenAccessor bounds = (AbstractContainerScreenAccessor) screen;
        int left = bounds.litematicaplus$leftPos();
        int top = bounds.litematicaplus$topPos();
        List<Slot> slots = screen.getMenu().slots;
        Item focus = panel == null ? null : panel.hovered();
        IntOpenHashSet focused = focusSlots(slots, focus);
        for (Slot slot : slots) {
            int mark = highlighted.getOrDefault(slot.index, 0);
            boolean focusedSlot = focused.contains(slot.index);
            if (mark == 0 && !focusedSlot) {
                continue;
            }
            int x = left + slot.x;
            int y = top + slot.y;
            graphics.fill(x, y, x + 16, y + 16, focusedSlot ? FOCUS_FILL : mark == 2 ? BOX_FILL : DIRECT_FILL);
            graphics.outline(x - 1, y - 1, 18, 18, focusedSlot ? Theme.ACCENT : mark == 2 ? Theme.WARNING : Theme.SUCCESS);
        }
    }

    private static long focusComputedAt = -1L;

    private static IntOpenHashSet focusSlots(List<Slot> slots, Item focus) {
        if (focus == null) {
            focusItem = null;
            focusSlots.clear();
            return focusSlots;
        }
        if (focus == focusItem && focusComputedAt == focusStamp) {
            return focusSlots;
        }
        focusItem = focus;
        focusComputedAt = focusStamp;
        focusSlots.clear();
        for (Slot slot : slots) {
            if (slot.hasItem() && contains(slot.getItem(), focus)) {
                focusSlots.add(slot.index);
            }
        }
        return focusSlots;
    }

    private static boolean contains(ItemStack stack, Item item) {
        if (stack.is(item)) {
            return true;
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        return contents != null && contents.nonEmptyItemCopyStream().anyMatch(inner -> inner.is(item));
    }
}
