package org.lts.litematicaplus.mhud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lts.litematicaplus.Hotkeys;
import org.lts.litematicaplus.gui.Theme;
import org.lts.litematicaplus.hud.InfoHud;
import org.lts.litematicaplus.materials.MaterialList;

final class HudPanel {
    private static final int PADDING = 4;
    private static final int LINE = 16;
    private static final int TITLE = 12;
    private static final int BAR = 4;
    private static final int BAR_GAP = 2;
    private static final int TEXT = 9;
    private static final int HEADER_GAP = 3;
    private static final int DONE = 12;
    private static final int FOOTER = 11;
    private static final int MIN_WIDTH = 130;
    private static final int TITLE_GAP = 8;
    private static final int BAR_BACKGROUND = 0xFF2A2A2A;
    private static final int BAR_FILL = 0xFF5B9BFF;

    private record Row(MaterialList.Entry entry, String name, String count, int color, String shortfall) {
    }

    private record Layout(int width, int height, String title, String label, String progress, String done,
            boolean doneOk, List<Row> rows, List<String> footer) {
    }

    private HudPanel() {
    }

    static int width(Font font, MaterialHud.State state) {
        return layout(font, state).width();
    }

    static int height(Font font, MaterialHud.State state) {
        return layout(font, state).height();
    }

    private static Layout layout(Font font, MaterialHud.State state) {
        MaterialHudConfig config = MaterialHudConfig.current();
        String title = Component.translatable("litematicaplus.mhud.title").getString();
        String label = state.title().isEmpty() ? Component.translatable("litematicaplus.mhud.all_layers").getString()
                : state.title();
        long total = state.total();
        long placed = Math.min(total, state.placed());
        int percent = total > 0L ? (int) (placed * 100L / total) : 100;
        String progress = config.showProgress
                ? Component.translatable("litematicaplus.mhud.progress", placed, total, percent).getString() : null;
        List<Row> rows = new ArrayList<>();
        for (MaterialList.Entry entry : MaterialHud.pageEntries()) {
            ItemStack stack = InfoHud.stackOf(entry.item());
            long have = entry.available() + Math.max(0L, entry.collected());
            int color = have >= entry.missing() ? Theme.SUCCESS : have > 0L ? Theme.WARNING : Theme.DANGER;
            Long lacking = state.shortfall().get(entry.item());
            String shortfall = config.showShortfall && lacking != null && lacking > 0L
                    ? Component.translatable("litematicaplus.mhud.shortfall", lacking).getString() : null;
            rows.add(new Row(entry, stack.getHoverName().getString(), count(entry.missing(), stack.getMaxStackSize(),
                    config.countInBoxes), color, shortfall));
        }
        String done = null;
        boolean doneOk = false;
        if (rows.isEmpty()) {
            doneOk = total > 0L && placed >= total;
            done = Component.translatable(doneOk ? "litematicaplus.mhud.done" : "litematicaplus.mhud.empty").getString();
        }
        List<String> footer = new ArrayList<>();
        int pages = MaterialHud.pages();
        if (pages > 1) {
            String keys = keys();
            footer.add(keys.isEmpty()
                    ? Component.translatable("litematicaplus.mhud.page_no_keys", MaterialHud.page() + 1, pages).getString()
                    : Component.translatable("litematicaplus.mhud.page", MaterialHud.page() + 1, pages, keys).getString());
        }
        if (state.pinned()) {
            footer.add(Component.translatable("litematicaplus.mhud.pinned", state.pinnedFrom(), state.placement()).getString());
        }
        int width = font.width(title) + Math.min(font.width(label), 100) + TITLE_GAP * 2;
        if (progress != null) {
            width = Math.max(width, font.width(progress) + 8);
        }
        if (done != null) {
            width = Math.max(width, font.width(done) + 8);
        }
        for (Row row : rows) {
            width = Math.max(width, 20 + font.width(row.name()) + 10 + font.width(row.count())
                    + (row.shortfall() != null ? font.width(row.shortfall()) + 8 : 0));
        }
        width = Math.max(width, MIN_WIDTH) + PADDING * 2;
        int height = header(progress != null) + (done != null ? DONE : rows.size() * LINE) + footer.size() * FOOTER + PADDING;
        return new Layout(width, height, title, label, progress, done, doneOk, rows, footer);
    }

    private static String keys() {
        String up = key("mh_page_up");
        String down = key("mh_page_down");
        return up.isEmpty() ? down : down.isEmpty() ? up : up.equals(down) ? up : up + "/" + down;
    }

    private static String key(String name) {
        KeyMapping mapping = Hotkeys.mapping(name);
        if (mapping == null || mapping.isUnbound()) {
            return "";
        }
        return Hotkeys.label(Hotkeys.modifiers(name), mapping.getTranslatedKeyMessage()).getString();
    }

    private static int header(boolean progress) {
        return PADDING + TITLE + (progress ? BAR + BAR_GAP + TEXT : 0) + HEADER_GAP;
    }

    static String count(long count, int stackSize, boolean boxes) {
        if (count > stackSize && stackSize > 1) {
            double box = count / (27.0 * stackSize);
            if (boxes && box >= 1.0) {
                return String.format(Locale.ROOT, "%d (%.1f SB)", count, box);
            }
            long stacks = count / stackSize;
            long rest = count % stackSize;
            return rest > 0L ? String.format(Locale.ROOT, "%d (%dx%d+%d)", count, stacks, stackSize, rest)
                    : String.format(Locale.ROOT, "%d (%dx%d)", count, stacks, stackSize);
        }
        return Long.toString(count);
    }

    static void draw(GuiGraphicsExtractor graphics, Font font, MaterialHud.State state, int x, int y, float scale) {
        Layout layout = layout(font, state);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        if (scale != 1.0F) {
            graphics.pose().scale(scale, scale);
        }
        contents(graphics, font, layout);
        graphics.pose().popMatrix();
    }

    private static void contents(GuiGraphicsExtractor graphics, Font font, Layout layout) {
        int width = layout.width();
        graphics.fill(0, 0, width, layout.height(), Theme.HUD_BACKGROUND);
        graphics.outline(0, 0, width, layout.height(), Theme.BORDER);
        int x = PADDING;
        int y = PADDING;
        graphics.text(font, layout.title(), x, y, Theme.TEXT_BRIGHT, false);
        int room = width - PADDING * 2 - font.width(layout.title()) - TITLE_GAP;
        String label = font.width(layout.label()) > room ? font.plainSubstrByWidth(layout.label(), Math.max(0, room - 6)) + ".."
                : layout.label();
        graphics.text(font, label, width - PADDING - font.width(label), y, Theme.ACCENT_HOVER, false);
        y += TITLE;
        if (layout.progress() != null) {
            int barWidth = width - PADDING * 2;
            MaterialHud.State state = MaterialHud.view();
            long total = state.total();
            int fill = total > 0L ? (int) (barWidth * Math.min(total, state.placed()) / total) : barWidth;
            graphics.fill(x, y, x + barWidth, y + BAR, BAR_BACKGROUND);
            if (fill > 0) {
                graphics.fill(x, y, x + Math.max(fill, BAR), y + BAR, fill >= barWidth ? Theme.SUCCESS : BAR_FILL);
            }
            graphics.text(font, layout.progress(), x, y + BAR + BAR_GAP, Theme.TEXT_DIM, false);
        }
        y = header(layout.progress() != null);
        if (layout.done() != null) {
            graphics.text(font, layout.done(), x, y, layout.doneOk() ? Theme.SUCCESS : Theme.TEXT_DIM, false);
            y += DONE;
        } else {
            boolean markers = MaterialHudConfig.current().searchMarkers;
            for (Row row : layout.rows()) {
                if (markers) {
                    Integer color = StorageSearch.get().colorFor(row.entry().item());
                    if (color != null) {
                        graphics.fill(2, y + 1, 5, y + LINE - 2, color);
                    }
                }
                graphics.fakeItem(InfoHud.stackOf(row.entry().item()), x, y);
                graphics.text(font, row.name(), x + 20, y + 4, Theme.TEXT, false);
                int countX = width - PADDING - font.width(row.count());
                graphics.text(font, row.count(), countX, y + 4, row.color(), false);
                if (row.shortfall() != null) {
                    graphics.text(font, row.shortfall(), countX - 8 - font.width(row.shortfall()), y + 4, Theme.DANGER, false);
                }
                y += LINE;
            }
        }
        for (String line : layout.footer()) {
            String shown = font.width(line) > width - PADDING * 2
                    ? font.plainSubstrByWidth(line, Math.max(0, width - PADDING * 2 - 6)) + ".." : line;
            graphics.text(font, shown, x, y + 1, Theme.TEXT_DIM, false);
            y += FOOTER;
        }
    }
}
