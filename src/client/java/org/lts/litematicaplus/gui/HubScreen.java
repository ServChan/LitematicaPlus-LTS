package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.Session;
import org.lts.litematicaplus.SessionStore;

public final class HubScreen extends Screen {
    public enum Tab {
        OVERVIEW("overview"),
        SCHEMATICS("schematics"),
        PLACEMENTS("placements"),
        LAYERS("layers"),
        AREA("area"),
        MATERIALS("materials"),
        VERIFIER("verifier"),
        SETTINGS("settings");

        private final String key;

        Tab(String key) {
            this.key = key;
        }

        Component title() {
            return Component.translatable("litematicaplus.tab." + this.key);
        }
    }

    private static Tab lastTab = Tab.OVERVIEW;

    private final Screen parent;
    private Tab tab;
    private TabView view;
    private int sidebarWidth = Theme.SIDEBAR_WIDTH;
    private List<GuiEventListener> previousChildren = List.of();
    private boolean refreshPending;

    public HubScreen(Screen parent) {
        this(parent, lastTab);
    }

    public HubScreen(Screen parent, Tab tab) {
        super(Component.translatable("litematicaplus.screen.title"));
        this.parent = parent;
        this.tab = tab;
    }

    public static HubScreen settings(Screen parent) {
        return new HubScreen(parent, Tab.SETTINGS);
    }

    public static HubScreen layers(Screen parent) {
        return new HubScreen(parent, Tab.LAYERS);
    }

    public Tab tab() {
        return this.tab;
    }

    public void switchTab(Tab next) {
        setFocused(null);
        if (this.view != null) {
            this.view.closed();
        }
        this.tab = next;
        lastTab = next;
        this.view = null;
        rebuildWidgets();
    }

    static boolean typing(GuiEventListener focused) {
        return focused instanceof Stepper stepper && stepper.editing()
                || focused instanceof net.minecraft.client.gui.components.EditBox box && box.isFocused();
    }

    public void refresh() {
        if (typing(getFocused())) {
            this.refreshPending = true;
            return;
        }
        this.refreshPending = false;
        this.previousChildren = new ArrayList<>(children());
        GuiEventListener focused = getFocused();
        int index = focused == null ? -1 : this.previousChildren.indexOf(focused);
        rebuildWidgets();
        refocus(focused, index);
    }

    private void refocus(GuiEventListener old, int index) {
        if (old == null || index < 0) {
            return;
        }
        List<? extends GuiEventListener> current = children();
        if (index < current.size() && current.get(index).getClass() == old.getClass()) {
            setFocused(current.get(index));
        } else {
            setFocused(null);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.view != null && this.view.mouseClicked(event)) {
            return true;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        GuiEventListener focused = getFocused();
        if (focused != null && !children().contains(focused)) {
            refocus(focused, this.previousChildren.indexOf(focused));
        }
        return handled;
    }

    <T extends GuiEventListener & Renderable & NarratableEntry> T addWidgetPublic(T widget) {
        return addRenderableWidget(widget);
    }

    @Override
    protected void init() {
        this.sidebarWidth = this.width < 440 ? 86 : Theme.SIDEBAR_WIDTH;
        int y = Theme.HEADER_HEIGHT + 8;
        for (Tab entry : Tab.values()) {
            FlatButton button = new FlatButton(0, y, this.sidebarWidth, 22, entry.title(), FlatButton.Style.TAB,
                    pressed -> switchTab(entry)).withState(() -> this.tab == entry)
                    .withIndex(String.format(java.util.Locale.ROOT, "%02d", entry.ordinal() + 1));
            addRenderableWidget(button);
            y += 23;
        }
        if (this.view == null) {
            this.view = create(this.tab);
        }
        int contentX = this.sidebarWidth + Theme.PADDING;
        int contentY = Theme.HEADER_HEIGHT + 30;
        this.view.bounds(contentX, contentY, this.width - contentX - Theme.PADDING,
                this.height - contentY - Theme.PADDING);
        this.view.init();
    }

    private TabView create(Tab target) {
        return switch (target) {
            case OVERVIEW -> new OverviewView(this);
            case SCHEMATICS -> new SchematicsView(this);
            case PLACEMENTS -> new PlacementsView(this);
            case LAYERS -> new LayersView(this);
            case AREA -> new AreaView(this);
            case MATERIALS -> new MaterialsView(this);
            case VERIFIER -> new VerifierView(this);
            case SETTINGS -> new SettingsView(this);
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (this.view != null) {
            this.view.tick();
        }
        if (this.refreshPending && !typing(getFocused())) {
            refresh();
        }
    }

    @Override
    public void removed() {
        if (this.view != null) {
            this.view.closed();
        }
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, Theme.BACKGROUND);
        Blueprint.grid(graphics, this.sidebarWidth + 1, Theme.HEADER_HEIGHT, this.width - this.sidebarWidth - 1,
                this.height - Theme.HEADER_HEIGHT);
        graphics.fill(0, 0, this.width, Theme.HEADER_HEIGHT, Theme.HEADER);
        graphics.fill(0, Theme.HEADER_HEIGHT - 1, this.width, Theme.HEADER_HEIGHT, Theme.DIVIDER);
        for (int tick = Blueprint.MAJOR; tick < this.width; tick += Blueprint.MAJOR) {
            graphics.fill(tick, Theme.HEADER_HEIGHT - 4, tick + 1, Theme.HEADER_HEIGHT - 1, Theme.DIVIDER);
        }
        graphics.fill(0, Theme.HEADER_HEIGHT, this.sidebarWidth, this.height, Theme.SIDEBAR);
        Blueprint.ruler(graphics, this.sidebarWidth, Theme.HEADER_HEIGHT, this.height - Theme.HEADER_HEIGHT,
                Theme.DIVIDER);
        graphics.outline(10, 9, 11, 11, Theme.ACCENT);
        graphics.fill(14, 13, 17, 16, Theme.AMBER);
        String heading = this.title.getString().toUpperCase(java.util.Locale.ROOT);
        graphics.text(this.font, heading, 27, 11, Theme.TEXT_BRIGHT, false);
        Component sheet = Component.translatable("litematicaplus.hub.sheet",
                String.format(java.util.Locale.ROOT, "%02d", this.tab.ordinal() + 1),
                String.format(java.util.Locale.ROOT, "%02d", Tab.values().length));
        int sheetX = 27 + this.font.width(heading) + 10;
        graphics.fill(sheetX - 5, 9, sheetX - 4, 21, Theme.DIVIDER);
        graphics.text(this.font, sheet, sheetX, 11, Theme.AMBER, false);
        String scope = Session.scope();
        int titleRight = sheetX + this.font.width(sheet) + 14;
        if (SessionStore.get().readOnly()) {
            Ui.wrapped(graphics, this.font, Component.translatable("litematicaplus.error.session_read_only"),
                    titleRight, 11, this.width - titleRight - 12, 1, Theme.WARNING, mouseX, mouseY);
        } else if (!scope.isEmpty()) {
            int scopeWidth = this.width - titleRight - 12;
            String shown = this.font.width(scope) > scopeWidth
                    ? ".." + this.font.plainSubstrByWidth(scope, Math.max(0, scopeWidth - 8), true) : scope;
            Ui.right(graphics, this.font, shown, this.width - 12, 11, Theme.TEXT_DIM);
        }
        int contentX = this.sidebarWidth + Theme.PADDING;
        Ui.heading(graphics, this.font, this.tab.title(), contentX, Theme.HEADER_HEIGHT + 8, 1.5F, Theme.TEXT_BRIGHT);
        int ruleY = Theme.HEADER_HEIGHT + 23;
        Blueprint.dimension(graphics, contentX, ruleY, this.width - Theme.PADDING - contentX, Theme.DIVIDER);
        graphics.fill(contentX, ruleY - 1, contentX + Math.round(this.font.width(this.tab.title()) * 1.5F), ruleY + 1,
                Theme.ACCENT);
        if (this.view != null) {
            Component summary = this.view.summary();
            int titleWidth = Math.round(this.font.width(this.tab.title()) * 1.5F);
            int available = this.width - contentX - Theme.PADDING - titleWidth - 12;
            if (available > 40) {
                String text = summary.getString();
                String shown = this.font.width(text) > available
                        ? this.font.plainSubstrByWidth(text, available - 6) + ".." : text;
                Ui.right(graphics, this.font, shown, this.width - Theme.PADDING, Theme.HEADER_HEIGHT + 13, Theme.TEXT_DIM);
            }
            this.view.extract(graphics, mouseX, mouseY, delta);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.view != null && this.view.keyPressed(event)) {
            return true;
        }
        if (getFocused() instanceof Stepper stepper && stepper.editing() && stepper.keyPressed(event)) {
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Session.flush();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
