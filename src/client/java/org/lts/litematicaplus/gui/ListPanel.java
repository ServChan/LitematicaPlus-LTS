package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ListPanel<T> extends AbstractWidget {
    @FunctionalInterface
    public interface RowRenderer<T> {
        void render(GuiGraphicsExtractor graphics, Font font, T item, int x, int y, int width, int height,
                boolean selected, boolean hovered);
    }

    private final int rowHeight;
    private final RowRenderer<T> renderer;
    private List<T> items = List.of();
    private int selected = -1;
    private int scroll;
    private Consumer<T> onSelect = item -> { };
    private Consumer<T> onActivate = item -> { };
    private Consumer<T> onPress;
    private Function<T, String> describer;
    private Component emptyTitle = Component.empty();
    private Component emptyHint = Component.empty();

    public ListPanel(int x, int y, int width, int height, int rowHeight, Component narration, RowRenderer<T> renderer) {
        super(x, y, width, height, narration);
        this.rowHeight = rowHeight;
        this.renderer = renderer;
    }

    public ListPanel<T> onSelect(Consumer<T> listener) {
        this.onSelect = listener;
        return this;
    }

    public ListPanel<T> onActivate(Consumer<T> listener) {
        this.onActivate = listener;
        return this;
    }

    public ListPanel<T> onPress(Consumer<T> listener) {
        this.onPress = listener;
        return this;
    }

    public ListPanel<T> describe(Function<T, String> function) {
        this.describer = function;
        return this;
    }

    public ListPanel<T> empty(Component title, Component hint) {
        this.emptyTitle = title;
        this.emptyHint = hint;
        return this;
    }

    public void setItems(List<T> next) {
        this.items = next == null ? List.of() : next;
        if (this.selected >= this.items.size()) {
            this.selected = this.items.size() - 1;
        }
        clampScroll();
    }

    public List<T> items() {
        return this.items;
    }

    public T selectedItem() {
        return this.selected >= 0 && this.selected < this.items.size() ? this.items.get(this.selected) : null;
    }

    public int selectedIndex() {
        return this.selected;
    }

    public void select(int index) {
        if (index >= -1 && index < this.items.size()) {
            this.selected = index;
            ensureVisible();
        }
    }

    public void selectItem(T item) {
        select(item == null ? -1 : this.items.indexOf(item));
    }

    public int scroll() {
        return this.scroll;
    }

    public void setScroll(int value) {
        this.scroll = value;
        clampScroll();
    }

    private int maxScroll() {
        return Math.max(0, this.items.size() * this.rowHeight - (getHeight() - 2));
    }

    private void clampScroll() {
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
    }

    private void ensureVisible() {
        if (this.selected < 0) {
            return;
        }
        int top = this.selected * this.rowHeight;
        int bottom = top + this.rowHeight;
        int view = getHeight() - 2;
        if (top < this.scroll) {
            this.scroll = top;
        } else if (bottom > this.scroll + view) {
            this.scroll = bottom - view;
        }
        clampScroll();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        graphics.fill(x, y, x + width, y + height, Theme.SURFACE);
        graphics.outline(x, y, width, height, isFocused() ? Theme.FOCUS : Theme.BORDER);
        if (this.items.isEmpty()) {
            int centerY = y + height / 2;
            graphics.centeredText(font, this.emptyTitle, x + width / 2, centerY - 10, Theme.TEXT);
            graphics.textWithWordWrap(font, this.emptyHint, x + 16, centerY + 4, width - 32, Theme.TEXT_DIM, false);
            return;
        }
        clampScroll();
        int innerX = x + 1;
        int innerY = y + 1;
        int innerWidth = width - 2 - (maxScroll() > 0 ? 5 : 0);
        int innerHeight = height - 2;
        graphics.enableScissor(innerX, innerY, x + width - 1, innerY + innerHeight);
        int first = Math.max(0, this.scroll / this.rowHeight);
        int last = Math.min(this.items.size(), (this.scroll + innerHeight) / this.rowHeight + 1);
        for (int index = first; index < last; index++) {
            int rowY = innerY + index * this.rowHeight - this.scroll;
            boolean hovered = mouseX >= innerX && mouseX < innerX + innerWidth && mouseY >= Math.max(rowY, innerY)
                    && mouseY < Math.min(rowY + this.rowHeight, innerY + innerHeight);
            boolean isSelected = index == this.selected;
            if (isSelected) {
                graphics.fill(innerX, rowY, innerX + innerWidth, rowY + this.rowHeight, Theme.SURFACE_SELECTED);
                graphics.fill(innerX, rowY, innerX + 2, rowY + this.rowHeight, Theme.ACCENT_DARK);
            } else if (hovered) {
                graphics.fill(innerX, rowY, innerX + innerWidth, rowY + this.rowHeight, Theme.SURFACE_HOVER);
            }
            this.renderer.render(graphics, font, this.items.get(index), innerX, rowY, innerWidth, this.rowHeight,
                    isSelected, hovered);
        }
        graphics.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int trackX = x + width - 5;
            graphics.fill(trackX + 1, innerY, trackX + 2, innerY + innerHeight, Theme.DIVIDER);
            int thumb = Math.max(16, (int) ((long) innerHeight * innerHeight / (innerHeight + max)));
            int thumbY = innerY + Math.round((float) this.scroll / max * (innerHeight - thumb));
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumb, Theme.BORDER_STRONG);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int index = (int) ((event.y() - getY() - 1 + this.scroll) / this.rowHeight);
        if (event.y() < getY() + 1 || index < 0 || index >= this.items.size()) {
            return;
        }
        if (maxScroll() > 0 && event.x() >= getX() + getWidth() - 6) {
            return;
        }
        boolean same = index == this.selected;
        this.selected = index;
        T item = this.items.get(index);
        this.onSelect.accept(item);
        if (this.onPress != null) {
            this.onPress.accept(item);
        } else if (doubleClick && same) {
            this.onActivate.accept(item);
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        int max = maxScroll();
        if (max > 0 && event.x() >= getX() + getWidth() - 8) {
            int innerHeight = getHeight() - 2;
            double ratio = (event.y() - getY() - 1) / Math.max(1.0, innerHeight);
            this.scroll = (int) Math.round(ratio * max);
            clampScroll();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.scroll -= (int) Math.round(scrollY * this.rowHeight * 2);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.visible || !isFocused() || this.items.isEmpty()) {
            return false;
        }
        int page = Math.max(1, (getHeight() - 2) / this.rowHeight - 1);
        int key = event.key();
        int next = this.selected;
        if (key == InputConstants.KEY_UP) {
            next = Math.max(0, this.selected - 1);
        } else if (key == InputConstants.KEY_DOWN) {
            next = Math.min(this.items.size() - 1, this.selected + 1);
        } else if (key == InputConstants.KEY_PAGEUP) {
            next = Math.max(0, this.selected - page);
        } else if (key == InputConstants.KEY_PAGEDOWN) {
            next = Math.min(this.items.size() - 1, Math.max(0, this.selected) + page);
        } else if (key == InputConstants.KEY_HOME) {
            next = 0;
        } else if (key == InputConstants.KEY_END) {
            next = this.items.size() - 1;
        } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
                || key == InputConstants.KEY_SPACE && this.onPress != null) {
            T item = selectedItem();
            if (item != null) {
                if (this.onPress != null) {
                    this.onPress.accept(item);
                } else {
                    this.onActivate.accept(item);
                }
                return true;
            }
            return false;
        } else {
            return false;
        }
        if (next != this.selected) {
            this.selected = next;
            ensureVisible();
            this.onSelect.accept(this.items.get(next));
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        T item = selectedItem();
        if (item != null) {
            output.add(NarratedElementType.POSITION, Component.translatable("narrator.position.list",
                    this.selected + 1, this.items.size()));
            if (this.describer != null) {
                output.add(NarratedElementType.HINT, Component.literal(this.describer.apply(item)));
            }
        }
    }
}
