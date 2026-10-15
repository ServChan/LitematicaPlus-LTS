package org.lts.litematicaplus.gui;

import com.mojang.blaze3d.Blaze3D;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.compat.Addon;
import org.lts.litematicaplus.compat.Addons;
import org.lts.litematicaplus.share.SyncmaticaClient;

final class AddonsView extends TabView {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final int MAX_STATUS_LINES = 3;
    private static final int MAX_ABOUT_LINES = 4;

    private static Addon selected = Addon.SYNCMATICA;

    private ListPanel<Addon> list;
    private String knownSignature = "";
    private int detailX;
    private int detailWidth;
    private int metaY;
    private int chipY;
    private int statusY;
    private int statusLines;
    private int aboutY;
    private int aboutLines;
    private int settingsY = -1;
    private int problemsY;
    private int bottomY;
    private Component copied = Component.empty();
    private int copiedTicks;

    AddonsView(HubScreen screen) {
        super(screen);
    }

    private static List<Addon> listed() {
        List<Addon> addons = new ArrayList<>();
        for (Addon addon : Addon.values()) {
            if (addon.listed()) {
                addons.add(addon);
            }
        }
        addons.sort(Comparator.comparingInt((Addon addon) -> addon.state().ordinal()).thenComparingInt(Addon::ordinal));
        return addons;
    }

    private static String signature() {
        StringBuilder builder = new StringBuilder();
        for (Addon addon : Addon.values()) {
            builder.append(addon.state().ordinal());
        }
        return builder.append('|').append(Addons.revision()).append('|').append(SyncmaticaClient.get().revision())
                .toString();
    }

    @Override
    Component summary() {
        int active = 0;
        int failing = 0;
        for (Addon addon : Addon.values()) {
            Addon.State state = addon.state();
            if (state == Addon.State.ACTIVE) {
                active++;
            }
            if (state == Addon.State.FAILED || state == Addon.State.CONFLICT || Addons.count(addon) > 0) {
                failing++;
            }
        }
        return tr("litematicaplus.addons.summary", active, failing);
    }

    @Override
    void init() {
        this.knownSignature = signature();
        List<Addon> addons = listed();
        if (!addons.contains(selected)) {
            selected = addons.get(0);
        }
        int listWidth = Math.max(110, this.width * 36 / 100);
        this.detailX = this.left + listWidth + Theme.GAP;
        this.detailWidth = this.width - listWidth - Theme.GAP;
        this.bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        this.list = add(new ListPanel<Addon>(this.left, this.top, listWidth, this.height, Theme.ROW_HEIGHT,
                tr("litematicaplus.tab.addons"), this::renderRow)
                .describe(addon -> addon.title().getString())
                .onSelect(addon -> {
                    selected = addon;
                    this.screen.refresh();
                }));
        this.list.setItems(addons);
        this.list.selectItem(selected);
        Addon addon = selected;
        int y = this.top + 16;
        this.metaY = y;
        y += 12;
        this.chipY = y;
        y += 14;
        this.statusY = y;
        boolean tight = this.height < 200;
        this.statusLines = Math.min(tight ? 2 : MAX_STATUS_LINES, this.font.split(addon.status(), this.detailWidth).size());
        y += this.statusLines * 10 + 4;
        this.aboutY = y;
        this.aboutLines = Math.min(tight ? 1 : MAX_ABOUT_LINES, this.font.split(addon.about(), this.detailWidth).size());
        y += this.aboutLines * 10 + 8;
        this.settingsY = -1;
        if (!addon.settings().isEmpty()) {
            this.settingsY = y;
            y += 12;
            for (Addon.Setting setting : addon.settings()) {
                if (y + Theme.CONTROL_HEIGHT > this.bottomY - 16) {
                    break;
                }
                FlatButton toggle = add(FlatButton.toggle(this.detailX, y, this.detailWidth,
                        tr("litematicaplus.option." + setting.option()), setting::value, button -> {
                            setting.set(!setting.value());
                            this.screen.refresh();
                        }));
                toggle.setTooltip(Tooltip.create(tr("litematicaplus.option." + setting.option() + ".tip")));
                y += Theme.CONTROL_HEIGHT + 4;
            }
        }
        this.problemsY = y + 4;
        int buttons = addon == Addon.LITEMATICA ? 3 : 2;
        int buttonWidth = (this.detailWidth - Theme.GAP * (buttons - 1)) / buttons;
        add(FlatButton.secondary(this.detailX, this.bottomY, buttonWidth, tr("litematicaplus.addons.copy_report"),
                button -> copyReport()));
        FlatButton clear = add(FlatButton.secondary(this.detailX + buttonWidth + Theme.GAP, this.bottomY,
                buttons == 2 ? this.detailWidth - buttonWidth - Theme.GAP : buttonWidth,
                tr("litematicaplus.addons.clear"), button -> {
                    Addons.clear(selected);
                    this.screen.refresh();
                }));
        clear.active = Addons.count(addon) > 0;
        if (addon == Addon.LITEMATICA) {
            int x = this.detailX + (buttonWidth + Theme.GAP) * 2;
            add(FlatButton.primary(x, this.bottomY, this.detailX + this.detailWidth - x,
                    tr("litematicaplus.conflict.open_mods"), button -> openMods()));
        }
    }

    private static void openMods() {
        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        try {
            Blaze3D.openPath(mods);
        } catch (RuntimeException exception) {
            LitematicaPlusClient.LOGGER.warn("Could not open the mods folder {}", mods, exception);
        }
    }

    private void copyReport() {
        this.minecraft.keyboardHandler.setClipboard(report());
        this.copied = tr("litematicaplus.addons.copied");
        this.copiedTicks = 60;
    }

    static String report() {
        StringBuilder text = new StringBuilder();
        String own = FabricLoader.getInstance().getModContainer(LitematicaPlusClient.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
        String loader = FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
        text.append("Litematica Plus LTS ").append(own).append(", Minecraft ")
                .append(SharedConstants.getCurrentVersion().id()).append(", Fabric Loader ").append(loader).append('\n');
        for (Addon addon : Addon.values()) {
            text.append("- ").append(addon.title().getString());
            if (!addon.modId().isEmpty()) {
                text.append(" [").append(addon.modId()).append(' ')
                        .append(addon.installed() ? addon.version() : "-").append(']');
            }
            text.append(": ").append(addon.state().name()).append(" - ").append(addon.status().getString()).append('\n');
            for (Addons.Problem problem : Addons.problems(addon)) {
                text.append("    ").append(time(problem.lastTime())).append(' ')
                        .append(Component.translatable(problem.key(), problem.args()).getString());
                if (problem.repeats() > 1) {
                    text.append(" x").append(problem.repeats());
                }
                if (!problem.detail().isEmpty()) {
                    text.append(" | ").append(problem.detail());
                }
                text.append('\n');
            }
        }
        return text.toString();
    }

    private static String time(long millis) {
        return LocalTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(TIME);
    }

    private static int stateColor(Addon.State state) {
        return switch (state) {
            case CONFLICT, FAILED -> Theme.DANGER;
            case ACTIVE -> Theme.SUCCESS;
            case STANDBY -> Theme.ACCENT;
            case OFF -> Theme.TEXT_DIM;
            case MISSING -> Theme.TEXT_DISABLED;
        };
    }

    private static Component stateLabel(Addon.State state) {
        return tr("litematicaplus.addon.state." + state.name().toLowerCase(Locale.ROOT));
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font rowFont, Addon addon, int x, int y, int rowWidth,
            int rowHeight, boolean isSelected, boolean hovered) {
        Addon.State state = addon.state();
        int problems = Addons.count(addon);
        String label = problems > 0 && state == Addon.State.ACTIVE
                ? tr("litematicaplus.addons.with_problems", problems).getString() : stateLabel(state).getString();
        int color = problems > 0 && state == Addon.State.ACTIVE ? Theme.WARNING : stateColor(state);
        int textY = y + (rowHeight - 8) / 2;
        int labelWidth = rowFont.width(label);
        Ui.clipped(graphics, rowFont, addon.title().getString(), x + 8, textY, rowWidth - labelWidth - 22,
                state == Addon.State.MISSING ? Theme.TEXT_DIM : Theme.TEXT_BRIGHT);
        Ui.right(graphics, rowFont, label, x + rowWidth - 6, textY, color);
    }

    @Override
    void tick() {
        if (this.copiedTicks > 0 && --this.copiedTicks == 0) {
            this.copied = Component.empty();
        }
        if (!signature().equals(this.knownSignature)) {
            this.screen.refresh();
        }
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Addon addon = selected;
        Addon.State state = addon.state();
        int x = this.detailX;
        Ui.heading(graphics, this.font, addon.title(), x, this.top, 1.0F, Theme.TEXT_BRIGHT);
        if (this.copiedTicks > 0) {
            Ui.right(graphics, this.font, this.copied.getString(), x + this.detailWidth, this.top, Theme.SUCCESS);
        }
        Component meta = addon.builtIn() ? tr("litematicaplus.addons.built_in")
                : addon.installed() ? tr("litematicaplus.addons.mod_version", addon.modId(), addon.version())
                : tr("litematicaplus.addons.mod_missing", addon.modId());
        Ui.clipped(graphics, this.font, meta.getString(), x, this.metaY, this.detailWidth, Theme.TEXT_DIM);
        Ui.chip(graphics, this.font, x, this.chipY - 3, stateLabel(state), stateColor(state));
        Ui.wrapped(graphics, this.font, addon.status(), x, this.statusY, this.detailWidth, this.statusLines,
                state == Addon.State.FAILED || state == Addon.State.CONFLICT ? Theme.DANGER : Theme.TEXT, mouseX, mouseY);
        Ui.wrapped(graphics, this.font, addon.about(), x, this.aboutY, this.detailWidth, this.aboutLines, Theme.TEXT_DIM,
                mouseX, mouseY);
        if (this.settingsY >= 0) {
            Ui.section(graphics, this.font, tr("litematicaplus.addons.settings"), x, this.settingsY, this.detailWidth);
        }
        List<Addons.Problem> problems = Addons.problems(addon);
        int limit = this.bottomY - 6;
        if (this.problemsY + 12 > limit) {
            return;
        }
        Ui.section(graphics, this.font, tr("litematicaplus.addons.problems", problems.size()), x, this.problemsY,
                this.detailWidth);
        int y = this.problemsY + 14;
        if (problems.isEmpty()) {
            Ui.wrapped(graphics, this.font, tr(addon.settings().isEmpty() && !addon.installed()
                    ? "litematicaplus.addons.no_problems_missing" : "litematicaplus.addons.no_problems"), x, y,
                    this.detailWidth, Math.max(0, (limit - y) / 10), Theme.TEXT_DIM, mouseX, mouseY);
        }
        int shown = 0;
        for (Addons.Problem problem : problems) {
            if (y + 20 > limit) {
                Ui.clipped(graphics, this.font, tr("litematicaplus.addons.more_problems", problems.size() - shown)
                        .getString(), x, y, this.detailWidth, Theme.TEXT_DIM);
                break;
            }
            Component message = Component.translatable(problem.key(), problem.args());
            String head = time(problem.lastTime()) + "  " + message.getString()
                    + (problem.repeats() > 1 ? "  x" + problem.repeats() : "");
            Ui.clipped(graphics, this.font, head, x, y, this.detailWidth, Theme.DANGER);
            if (!problem.detail().isEmpty()) {
                Ui.clipped(graphics, this.font, problem.detail(), x + 8, y + 10, this.detailWidth - 8, Theme.TEXT_DIM);
                if (mouseX >= x && mouseX < x + this.detailWidth && mouseY >= y && mouseY < y + 20) {
                    graphics.setTooltipForNextFrame(this.font.split(Component.literal(head + "\n" + problem.detail()), 300),
                            mouseX, mouseY);
                }
                y += 22;
            } else {
                y += 12;
            }
            shown++;
        }
    }
}
