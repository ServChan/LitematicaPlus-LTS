package org.lts.litematicaplus.materials;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.compat.StackCalculatorBridge;
import org.lts.litematicaplus.gui.FlatButton;
import org.lts.litematicaplus.gui.Blueprint;
import org.lts.litematicaplus.gui.Theme;
import org.lts.litematicaplus.util.Notify;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import com.mojang.blaze3d.platform.InputConstants;

final class MaterialPanel {
    static final int WIDTH = 172;
    static final int MIN_WIDTH = 100;
    private static final int COMPACT_WIDTH = 140;
    private static final int GAP = 4;
    private static final int ROW = 18;
    private static final int TITLE = 16;
    private static final int COLUMNS = 12;
    private static final int FOOTER = 13;
    private static final int NUMBER_COLUMN = 38;
    private static final int TOGGLE = 16;

    record Row(Item item, ItemStack icon, String name, long shortfall, long missing, long available, long here,
            long boxed) {
    }

    interface Actions {
        void takeAll();

        void deposit();

        void take(Item item, boolean everything);
    }

    private final Font font;
    private final Actions actions;
    private int x;
    private int y;
    private int width;
    private int height;
    private int scroll;
    private List<Row> rows = List.of();
    private Component title = Component.empty();
    private Component status = Component.empty();
    private int statusColor = Theme.TEXT_DIM;
    private Component empty = Component.empty();
    private Item hovered;
    private boolean visible;
    private boolean collapsed = LitematicaPlusConfig.current().materialPanelCollapsed;
    private FlatButton takeButton;
    private FlatButton depositButton;

    MaterialPanel(Font font, Actions actions) {
        this.font = font;
        this.actions = actions;
    }

    boolean visible() {
        return this.visible;
    }

    boolean expanded() {
        return this.visible && !this.collapsed;
    }

    private void setCollapsed(boolean value) {
        this.collapsed = value;
        this.scroll = 0;
        this.hovered = null;
        if (this.takeButton != null) {
            this.takeButton.visible = !value;
            this.depositButton.visible = !value;
        }
        LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
        config.materialPanelCollapsed = value;
        LitematicaPlusConfig.save(config);
    }

    private int shownHeight() {
        return this.collapsed ? TITLE : this.height;
    }

    private boolean overToggle(double mouseX, double mouseY) {
        return this.visible && mouseX >= this.x + this.width - TOGGLE && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + TITLE;
    }

    Item hovered() {
        return this.hovered;
    }

    boolean layout(Screen screen, int left, int top, int imageWidth, int imageHeight) {
        int height = Math.min(Math.max(imageHeight, 150), screen.height - 8);
        int panelY = Math.max(4, Math.min(top, screen.height - height - 4));
        int leftSpace = left - GAP - 4;
        int rightSpace = screen.width - (left + imageWidth) - GAP - 4;
        List<int[]> candidates = new ArrayList<>();
        if (leftSpace >= MIN_WIDTH) {
            int w = Math.min(WIDTH, leftSpace);
            candidates.add(new int[] {left - GAP - w, panelY, w, height});
        }
        if (rightSpace >= MIN_WIDTH) {
            int w = Math.min(WIDTH, rightSpace);
            candidates.add(new int[] {left + imageWidth + GAP, panelY, w, height});
        }
        List<AbstractWidget> others = new ArrayList<>(Screens.getWidgets(screen).stream()
                .filter(widget -> widget instanceof AbstractWidget && widget.visible)
                .map(widget -> (AbstractWidget) widget).toList());
        int[] chosen = null;
        for (int[] candidate : candidates) {
            if (others.stream().noneMatch(widget -> overlaps(candidate, widget))) {
                chosen = candidate;
                break;
            }
        }
        if (chosen == null && !candidates.isEmpty()) {
            chosen = candidates.get(0);
        }
        this.visible = chosen != null;
        if (!this.visible) {
            return false;
        }
        this.x = chosen[0];
        this.y = chosen[1];
        this.width = chosen[2];
        this.height = chosen[3];
        boolean stacked = this.width < COMPACT_WIDTH;
        int buttonWidth = stacked ? this.width - 8 : (this.width - 12) / 2;
        int buttonY = this.y + TITLE + 2;
        this.takeButton = FlatButton.primary(this.x + 4, buttonY, buttonWidth,
                Component.translatable("litematicaplus.panel.take_all"), pressed -> this.actions.takeAll());
        this.depositButton = FlatButton.secondary(stacked ? this.x + 4 : this.x + 8 + buttonWidth,
                stacked ? buttonY + Theme.CONTROL_HEIGHT + 2 : buttonY, buttonWidth,
                Component.translatable("litematicaplus.panel.deposit"), pressed -> this.actions.deposit());
        this.takeButton.visible = !this.collapsed;
        this.depositButton.visible = !this.collapsed;
        Screens.getWidgets(screen).add(this.takeButton);
        Screens.getWidgets(screen).add(this.depositButton);
        return true;
    }

    private static boolean overlaps(int[] rect, AbstractWidget widget) {
        return widget.getX() < rect[0] + rect[2] && widget.getX() + widget.getWidth() > rect[0]
                && widget.getY() < rect[1] + rect[3] && widget.getY() + widget.getHeight() > rect[1];
    }

    void update(Component title, List<Row> rows, Component empty, boolean canTake, boolean canDeposit, boolean busy) {
        this.title = title;
        this.rows = rows;
        this.empty = empty;
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, rows.size() - visibleRows())));
        if (this.takeButton != null) {
            this.takeButton.active = canTake && !busy;
            this.depositButton.active = canDeposit && !busy;
        }
    }

    void status(Component text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    boolean overButton(double mouseX, double mouseY) {
        return this.takeButton != null && !this.collapsed && (this.takeButton.isMouseOver(mouseX, mouseY)
                || this.depositButton.isMouseOver(mouseX, mouseY));
    }

    private int buttonsHeight() {
        return this.width < COMPACT_WIDTH ? Theme.CONTROL_HEIGHT * 2 + 2 : Theme.CONTROL_HEIGHT;
    }

    boolean contains(double mouseX, double mouseY) {
        return this.visible && mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y
                && mouseY < this.y + shownHeight();
    }

    private int listTop() {
        return this.y + TITLE + buttonsHeight() + 6 + COLUMNS;
    }

    private int listBottom() {
        return this.y + this.height - FOOTER;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW);
    }

    boolean scroll(double amount) {
        if (this.collapsed) {
            return true;
        }
        int max = Math.max(0, this.rows.size() - visibleRows());
        this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(amount)));
        return true;
    }

    boolean click(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && (overToggle(event.x(), event.y())
                || this.collapsed && contains(event.x(), event.y()))) {
            setCollapsed(!this.collapsed);
            return true;
        }
        Row row = rowAt(event.x(), event.y());
        if (row == null) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (!StackCalculatorBridge.show(row.item(), row.shortfall() > 0L ? row.shortfall() : row.missing())) {
                Notify.actionBar("litematicaplus.panel.no_calculator");
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.actions.take(row.item(), event.hasShiftDown());
        }
        return true;
    }

    private Row rowAt(double mouseX, double mouseY) {
        if (this.collapsed || !contains(mouseX, mouseY) || mouseY < listTop() || mouseY >= listBottom()) {
            return null;
        }
        int index = this.scroll + (int) ((mouseY - listTop()) / ROW);
        return index >= 0 && index < this.rows.size() && index - this.scroll < visibleRows() ? this.rows.get(index) : null;
    }

    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }
        int right = this.x + this.width;
        graphics.fill(this.x, this.y, right, this.y + shownHeight(), Theme.BACKGROUND);
        graphics.fill(this.x + 1, this.y + 1, right - 1, this.y + TITLE, Theme.HEADER);
        if (!this.collapsed) {
            graphics.fill(this.x + 1, this.y + TITLE - 1, right - 1, this.y + TITLE, Theme.DIVIDER);
        }
        graphics.outline(this.x, this.y, this.width, shownHeight(), Theme.BORDER);
        Blueprint.marker(graphics, this.x + 5, this.y + 6, Theme.AMBER);
        graphics.text(this.font, this.font.plainSubstrByWidth(this.title.getString(), this.width - 16 - TOGGLE),
                this.x + 11, this.y + 4, Theme.TEXT_BRIGHT, false);
        boolean toggleHovered = overToggle(mouseX, mouseY) || this.collapsed && contains(mouseX, mouseY);
        drawToggle(graphics, toggleHovered);
        if (this.collapsed) {
            this.hovered = null;
            if (toggleHovered) {
                graphics.setTooltipForNextFrame(this.font.split(
                        Component.translatable("litematicaplus.panel.expand.tip"), 200), mouseX, mouseY);
            }
            return;
        }
        if (overToggle(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font.split(
                    Component.translatable("litematicaplus.panel.collapse.tip"), 200), mouseX, mouseY);
        }
        int columnsY = this.y + TITLE + buttonsHeight() + 5;
        graphics.text(this.font, Component.translatable("litematicaplus.panel.column.item"), this.x + 5, columnsY,
                Theme.TEXT_DIM, false);
        boolean compact = this.width < COMPACT_WIDTH;
        if (!compact) {
            drawRight(graphics, Component.translatable("litematicaplus.panel.column.here").getString(),
                    right - 6 - NUMBER_COLUMN, columnsY, Theme.TEXT_DIM);
        }
        drawRight(graphics, Component.translatable("litematicaplus.panel.column.need").getString(), right - 6, columnsY,
                Theme.TEXT_DIM);
        graphics.fill(this.x + 4, listTop() - 2, this.x + this.width - 4, listTop() - 1, Theme.DIVIDER);
        Row hover = rowAt(mouseX, mouseY);
        this.hovered = hover == null ? null : hover.item();
        if (this.rows.isEmpty()) {
            graphics.textWithWordWrap(this.font, this.empty, this.x + 6, listTop() + 6, this.width - 12, Theme.TEXT_DIM);
        }
        int shown = Math.min(visibleRows(), this.rows.size() - this.scroll);
        for (int i = 0; i < shown; i++) {
            Row row = this.rows.get(this.scroll + i);
            int rowY = listTop() + i * ROW;
            if (row == hover) {
                graphics.fill(this.x + 2, rowY, right - 2, rowY + ROW, Theme.SURFACE_HOVER);
                graphics.fill(this.x + 2, rowY, this.x + 4, rowY + ROW, Theme.ACCENT_DARK);
            }
            graphics.fakeItem(row.icon(), this.x + 4, rowY + 1);
            int nameRight = right - 8 - NUMBER_COLUMN * (compact ? 1 : 2);
            int nameColor = compact && row.here() > 0L ? Theme.SUCCESS
                    : compact && row.boxed() > 0L ? Theme.WARNING : row.shortfall() > 0L ? Theme.TEXT : Theme.TEXT_DIM;
            graphics.text(this.font, this.font.plainSubstrByWidth(row.name(), Math.max(10, nameRight - this.x - 24)),
                    this.x + 23, rowY + 5, nameColor, false);
            if (compact) {
                drawRight(graphics, row.shortfall() > 0L ? compact(row.shortfall()) : "0", right - 6, rowY + 5,
                        row.shortfall() > 0L ? Theme.TEXT_BRIGHT : Theme.SUCCESS);
                continue;
            }
            String here = row.here() > 0L ? compact(row.here()) : row.boxed() > 0L ? "" : "-";
            int hereColor = row.here() > 0L ? Theme.SUCCESS : Theme.TEXT_DISABLED;
            int hereRight = right - 6 - NUMBER_COLUMN;
            if (row.boxed() > 0L) {
                drawRight(graphics, "+", hereRight, rowY + 5, Theme.WARNING);
                hereRight -= this.font.width("+");
            }
            drawRight(graphics, here, hereRight, rowY + 5, hereColor);
            drawRight(graphics, row.shortfall() > 0L ? compact(row.shortfall()) : "0", right - 6, rowY + 5,
                    row.shortfall() > 0L ? Theme.TEXT_BRIGHT : Theme.SUCCESS);
        }
        if (this.rows.size() > visibleRows()) {
            int trackTop = listTop();
            int trackHeight = listBottom() - trackTop;
            int thumb = Math.max(8, trackHeight * visibleRows() / this.rows.size());
            int max = this.rows.size() - visibleRows();
            int thumbY = trackTop + (trackHeight - thumb) * this.scroll / Math.max(1, max);
            graphics.fill(right - 2, trackTop, right - 1, trackTop + trackHeight, Theme.DIVIDER);
            graphics.fill(right - 3, thumbY, right - 1, thumbY + thumb, Theme.BORDER_STRONG);
        }
        if (this.takeButton != null) {
            this.takeButton.extractRenderState(graphics, mouseX, mouseY, delta);
            this.depositButton.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        graphics.fill(this.x + 4, listBottom(), this.x + this.width - 4, listBottom() + 1, Theme.DIVIDER);
        graphics.text(this.font, this.font.plainSubstrByWidth(this.status.getString(), this.width - 10), this.x + 5,
                listBottom() + 3, this.statusColor, false);
        if (hover != null) {
            graphics.setComponentTooltipForNextFrame(this.font, tooltip(hover), mouseX, mouseY);
        } else if (this.takeButton != null && this.takeButton.isMouseOver(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font.split(Component.translatable("litematicaplus.panel.take_all.tip"), 200),
                    mouseX, mouseY);
        } else if (this.depositButton != null && this.depositButton.isMouseOver(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font.split(Component.translatable("litematicaplus.panel.deposit.tip"), 200),
                    mouseX, mouseY);
        }
    }

    private void drawToggle(GuiGraphicsExtractor graphics, boolean hovered) {
        int left = this.x + this.width - TOGGLE;
        int top = this.y + 1;
        if (hovered) {
            graphics.fill(left, top, left + TOGGLE - 1, top + TITLE - 2, Theme.SURFACE_HOVER);
        }
        int color = hovered ? Theme.ACCENT : Theme.TEXT_DIM;
        int centerX = left + TOGGLE / 2 - 1;
        int centerY = top + (TITLE - 2) / 2;
        graphics.fill(centerX - 3, centerY, centerX + 4, centerY + 1, color);
        if (this.collapsed) {
            graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 4, color);
        }
    }

    private List<Component> tooltip(Row row) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(row.name()).withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("litematicaplus.panel.tip.need", amount(row.shortfall(), row.icon()))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("litematicaplus.panel.tip.missing", amount(row.missing(), row.icon()))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("litematicaplus.panel.tip.inventory", amount(row.available(), row.icon()))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("litematicaplus.panel.tip.here", amount(row.here(), row.icon()))
                .withStyle(row.here() > 0L ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (row.boxed() > 0L) {
            lines.add(Component.translatable("litematicaplus.panel.tip.boxed", amount(row.boxed(), row.icon()))
                    .withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("litematicaplus.panel.tip.click").withStyle(ChatFormatting.DARK_AQUA));
        if (StackCalculatorBridge.available()) {
            lines.add(Component.translatable("litematicaplus.panel.tip.calculator").withStyle(ChatFormatting.DARK_AQUA));
        }
        return lines;
    }

    static String amount(long count, ItemStack icon) {
        int size = Math.max(1, icon.getMaxStackSize());
        long stacks = count / size;
        long rest = count % size;
        if (stacks == 0L) {
            return Long.toString(count);
        }
        return Component.translatable(rest == 0L ? "litematicaplus.materials.stacks" : "litematicaplus.materials.stacks_rest",
                count, stacks, rest).getString();
    }

    static String compact(long value) {
        if (value < 10_000L) {
            return Long.toString(value);
        }
        if (value < 1_000_000L) {
            return value / 1000L + "k";
        }
        return value / 1_000_000L + "M";
    }

    private void drawRight(GuiGraphicsExtractor graphics, String text, int right, int textY, int color) {
        graphics.text(this.font, text, right - this.font.width(text), textY, color, false);
    }
}
