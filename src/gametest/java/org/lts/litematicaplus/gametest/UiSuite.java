package org.lts.litematicaplus.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.BlockPos;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.SchematicIo;

final class UiSuite {
    private static final BlockPos ORIGIN = new BlockPos(4, -60, 4);
    private static final String[] CATEGORIES = {"rendering", "verify", "hud", "tools", "easy_place", "breaking",
            "materials", "hotkeys", "performance"};
    private static final int[][] SIZES = {{854, 480}, {1280, 720}, {1920, 1080}};

    private UiSuite() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("tp @p -2.5 -59 -2.5 -45 12");
        context.waitTicks(10);
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/house.litematic");
            try {
                SchematicIo.write(target, LitematicaPlusGameTest.house());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, ORIGIN));
        LitematicaPlusGameTest.settle(context, placement);
        screens(context);
        context.runOnClient(client -> PlacementManager.get().remove(placement));
    }

    static void screens(ClientGameTestContext context) {
        List<String> problems = new ArrayList<>();
        for (int[] size : SIZES) {
            context.getInput().resizeWindow(size[0], size[1]);
            context.waitTicks(10);
            String prefix = "ui_" + size[0] + "_";
            for (HubScreen.Tab tab : HubScreen.Tab.values()) {
                context.setScreen(() -> new HubScreen(null, tab));
                context.waitTicks(tab == HubScreen.Tab.SCHEMATICS ? 25 : 8);
                String name = prefix + tab.name().toLowerCase(Locale.ROOT);
                audit(context, name, problems);
                context.takeScreenshot(name);
                if (tab == HubScreen.Tab.SETTINGS) {
                    settings(context, prefix, problems);
                }
            }
        }
        context.setScreen(() -> null);
        context.getInput().resizeWindow(854, 480);
        context.waitTicks(5);
        for (String problem : problems) {
            System.out.println("UI-AUDIT " + problem);
        }
        System.out.println("UI-AUDIT done, problems: " + problems.size());
    }

    private static void settings(ClientGameTestContext context, String prefix, List<String> problems) {
        for (String category : CATEGORIES) {
            TestUi.clickLastLabel(context, TestUi.text("litematicaplus.settings.category." + category));
            context.waitTicks(3);
            for (int page = 1; page <= 6; page++) {
                String name = prefix + "settings_" + category + "_" + page;
                audit(context, name, problems);
                context.takeScreenshot(name);
                if (!nextPage(context)) {
                    break;
                }
            }
        }
        TestUi.clickLabel(context, TestUi.text("litematicaplus.settings.category.easy_place"));
        context.waitTicks(3);
        hover(context, TestUi.text("litematicaplus.option.easy_place"));
        context.takeScreenshot(prefix + "settings_hint");
        hover(context, TestUi.text("litematicaplus.tab.layers"));
        context.takeScreenshot(prefix + "sidebar_hint");
        TestUi.clickLabel(context, TestUi.text("litematicaplus.settings.category.rendering"));
        context.waitTicks(3);
        search(context, prefix + "settings_search", "ghost", problems);
        search(context, prefix + "settings_search_empty", "zzqx", problems);
        search(context, prefix + "settings_search_cleared", "", problems);
    }

    private static void search(ClientGameTestContext context, String name, String text, List<String> problems) {
        context.runOnClient(client -> {
            for (GuiEventListener child : List.copyOf(client.gui.screen().children())) {
                if (child instanceof net.minecraft.client.gui.components.EditBox box) {
                    client.gui.screen().setFocused(box);
                    box.setValue(text);
                    return;
                }
            }
            LitematicaPlusGameTest.require(false, "settings search field is missing");
        });
        context.waitTicks(3);
        audit(context, name, problems);
        context.takeScreenshot(name);
    }

    private static void hover(ClientGameTestContext context, String label) {
        int[] center = context.computeOnClient(client -> {
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().equals(label)) {
                    return new int[] {widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2};
                }
            }
            return null;
        });
        if (center == null) {
            return;
        }
        int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
        context.getInput().setCursorPos(center[0] * scale, center[1] * scale);
        context.waitTicks(20);
    }

    private static boolean nextPage(ClientGameTestContext context) {
        Boolean active = context.computeOnClient(client -> {
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().equals(">")) {
                    return widget.active;
                }
            }
            return false;
        });
        if (!active) {
            return false;
        }
        TestUi.clickLabel(context, ">");
        context.waitTicks(3);
        return true;
    }

    private static void audit(ClientGameTestContext context, String name, List<String> problems) {
        problems.addAll(context.computeOnClient(client -> {
            List<String> found = new ArrayList<>();
            int width = client.gui.screen().width;
            int height = client.gui.screen().height;
            List<AbstractWidget> widgets = new ArrayList<>();
            for (GuiEventListener child : client.gui.screen().children()) {
                if (child instanceof AbstractWidget widget && widget.visible) {
                    widgets.add(widget);
                }
            }
            for (int i = 0; i < widgets.size(); i++) {
                AbstractWidget a = widgets.get(i);
                ScreenRectangle ra = a.getRectangle();
                if (ra.left() < 0 || ra.top() < 0 || ra.right() > width || ra.bottom() > height || ra.width() <= 0) {
                    found.add(name + ": out of screen " + describe(a) + " in " + width + "x" + height);
                }
                for (int j = i + 1; j < widgets.size(); j++) {
                    AbstractWidget b = widgets.get(j);
                    ScreenRectangle rb = b.getRectangle();
                    if (ra.left() < rb.right() && rb.left() < ra.right() && ra.top() < rb.bottom() && rb.top() < ra.bottom()) {
                        found.add(name + ": overlap " + describe(a) + " / " + describe(b));
                    }
                }
            }
            return found;
        }));
    }

    private static String describe(AbstractWidget widget) {
        ScreenRectangle r = widget.getRectangle();
        return "'" + widget.getMessage().getString() + "' " + widget.getClass().getSimpleName() + "@" + r.left() + ","
                + r.top() + " " + r.width() + "x" + r.height();
    }
}
