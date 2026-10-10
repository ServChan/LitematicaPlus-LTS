package org.lts.litematicaplus.gui;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.zones.ZoneChat;
import org.lts.litematicaplus.zones.ZoneManager;
import org.lts.litematicaplus.zones.ZonePlan;
import org.lts.litematicaplus.zones.ZoneProtocol;

final class ZonesView extends TabView {
    private static int selectedZone = 1;

    private ListPanel<ZonePlan.Zone> list;
    private int detailX;
    private int detailWidth;
    private int infoY;
    private int teamY;
    private int hintY;
    private List<ZonePlan.Zone> shown = List.of();
    private Object knownVolume;

    ZonesView(HubScreen screen) {
        super(screen);
    }

    @Override
    Component summary() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null || !placement.zonePlan().active()) {
            return tr("litematicaplus.zones.summary_none");
        }
        return tr("litematicaplus.zones.summary", placement.zonePlan().count(), placement.zoneClaims().size());
    }

    @Override
    void init() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null || placement.schematic() == null) {
            return;
        }
        ZoneManager zones = ZoneManager.get();
        PlacementManager manager = PlacementManager.get();
        int listWidth = Math.max(110, this.width * 38 / 100);
        this.detailX = this.left + listWidth + Theme.GAP;
        this.detailWidth = this.width - listWidth - Theme.GAP;
        this.shown = zones.zones(placement);
        this.knownVolume = this.shown;
        this.list = add(new ListPanel<ZonePlan.Zone>(this.left, this.top, listWidth, this.height, Theme.ROW_HEIGHT,
                tr("litematicaplus.tab.zones"), (graphics, rowFont, zone, x, y, w, h, isSelected, hovered) ->
                renderRow(graphics, rowFont, placement, zone, x, y, w, h))
                .describe(zone -> tr("litematicaplus.zones.zone", zone.index()).getString())
                .onSelect(zone -> {
                    selectedZone = zone.index();
                    this.screen.refresh();
                })
                .empty(tr("litematicaplus.zones.empty_title"), tr("litematicaplus.zones.empty_hint")));
        this.list.setItems(this.shown);
        ZonePlan.Zone current = zones.zone(placement, selectedZone);
        if (current == null && !this.shown.isEmpty()) {
            selectedZone = 1;
            current = this.shown.get(0);
        }
        if (current != null) {
            this.list.selectItem(current);
        }
        int x = this.detailX;
        int y = this.top + 11;
        int third = (this.detailWidth - Theme.GAP * 2) / 3;
        ZonePlan plan = placement.zonePlan();
        add(new Stepper(x, y, third, tr("litematicaplus.zones.columns"), () -> placement.zonePlan().columns(),
                value -> {
                    manager.setZonePlan(placement, placement.zonePlan().withColumns(value));
                    this.screen.refresh();
                }, 1, ZonePlan.MAX_SPLIT, 1));
        add(new Stepper(x + third + Theme.GAP, y, third, tr("litematicaplus.zones.rows"),
                () -> placement.zonePlan().rows(), value -> {
                    manager.setZonePlan(placement, placement.zonePlan().withRows(value));
                    this.screen.refresh();
                }, 1, ZonePlan.MAX_SPLIT, 1));
        add(new Stepper(x + (third + Theme.GAP) * 2, y, this.detailWidth - (third + Theme.GAP) * 2,
                tr("litematicaplus.zones.floors"), () -> placement.zonePlan().floors(), value -> {
                    manager.setZonePlan(placement, placement.zonePlan().withFloors(value));
                    this.screen.refresh();
                }, 1, ZonePlan.MAX_SPLIT, 1));
        y += Theme.CONTROL_HEIGHT + 15;
        this.infoY = y;
        y += 22;
        int half = (this.detailWidth - Theme.GAP) / 2;
        ZonePlan.Zone zone = current;
        String owner = zone == null ? null : placement.zoneClaims().get(zone.index());
        boolean mine = owner != null && owner.equalsIgnoreCase(ZoneChat.self(this.minecraft));
        FlatButton claim = add(FlatButton.primary(x, y, half,
                tr(mine ? "litematicaplus.zones.release" : "litematicaplus.zones.claim"), button -> {
                    if (mine) {
                        zones.release(this.minecraft, placement, zone.index());
                    } else {
                        zones.claim(this.minecraft, placement, zone.index());
                    }
                    this.screen.refresh();
                }));
        claim.active = zone != null && plan.active();
        boolean focused = zone != null && zones.focused(zone);
        FlatButton focus = add(FlatButton.secondary(x + half + Theme.GAP, y, this.detailWidth - half - Theme.GAP,
                tr(focused ? "litematicaplus.zones.show_all" : "litematicaplus.zones.focus"), button -> {
                    if (focused) {
                        zones.unfocus();
                    } else {
                        zones.focus(zone);
                    }
                    this.screen.refresh();
                }));
        focus.active = zone != null && plan.active();
        y += Theme.CONTROL_HEIGHT + 15;
        this.teamY = y;
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        EditBox team = field(x, y, this.detailWidth, config.zoneTeam, tr("litematicaplus.zones.team_hint"), 512);
        team.setResponder(value -> update(copy -> copy.zoneTeam = value));
        y += Theme.CONTROL_HEIGHT + 4;
        ZoneProtocol.Transport transport = ZoneChat.transport(config);
        add(FlatButton.secondary(x, y, this.detailWidth,
                tr("litematicaplus.zones.transport." + transport.name().toLowerCase(Locale.ROOT)), button -> {
                    update(copy -> copy.zoneTransport = transport.next().name());
                    this.screen.refresh();
                }));
        y += Theme.CONTROL_HEIGHT + 4;
        if (transport == ZoneProtocol.Transport.CUSTOM) {
            EditBox command = field(x, y, this.detailWidth, config.zoneCommand, tr("litematicaplus.zones.command_hint"),
                    128);
            command.setResponder(value -> update(copy -> copy.zoneCommand = value));
            y += Theme.CONTROL_HEIGHT + 4;
        }
        FlatButton share = add(FlatButton.secondary(x, y, half, tr("litematicaplus.zones.share"),
                button -> zones.sharePlan(this.minecraft, placement)));
        share.active = plan.active();
        FlatButton ask = add(FlatButton.secondary(x + half + Theme.GAP, y, this.detailWidth - half - Theme.GAP,
                tr("litematicaplus.zones.ask"), button -> zones.askTeam(this.minecraft, placement)));
        ask.active = plan.active();
        y += Theme.CONTROL_HEIGHT + 4;
        this.hintY = y;
    }

    private static void update(java.util.function.Consumer<LitematicaPlusConfig> change) {
        LitematicaPlusConfig copy = LitematicaPlusConfig.currentCopy();
        change.accept(copy);
        LitematicaPlusConfig.save(copy);
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font rowFont, SchematicPlacement placement, ZonePlan.Zone zone,
            int x, int y, int rowWidth, int rowHeight) {
        int textY = y + (rowHeight - 8) / 2;
        String owner = placement.zoneClaims().get(zone.index());
        boolean mine = owner != null && owner.equalsIgnoreCase(ZoneChat.self(this.minecraft));
        double progress = ZoneManager.get().progress(placement, zone.index());
        boolean done = progress >= 0.9999;
        String percent = progress < 0.0 ? "…" : String.format(Locale.ROOT, "%.0f%%", progress * 100.0);
        String status = owner == null ? percent : percent + " · " + owner;
        int color = done ? Theme.SUCCESS : mine ? Theme.ACCENT : owner != null ? Theme.TEXT_DIM : Theme.TEXT;
        int statusWidth = rowFont.width(status);
        Ui.clipped(graphics, rowFont, tr("litematicaplus.zones.zone", zone.index()).getString() + (mine ? " *" : ""),
                x + 8, textY, rowWidth - statusWidth - 22, color);
        Ui.right(graphics, rowFont, status, x + rowWidth - 6, textY, color);
    }

    @Override
    void tick() {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement != null && placement.schematic() != null
                && ZoneManager.get().zones(placement) != this.knownVolume) {
            this.screen.refresh();
        }
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        SchematicPlacement placement = PlacementManager.get().selected();
        if (placement == null || placement.schematic() == null) {
            Ui.emptyState(graphics, this.font, this.left, this.top, this.width, this.height,
                    tr("litematicaplus.placements.empty_title"), tr("litematicaplus.zones.no_placement_hint"));
            return;
        }
        Ui.label(graphics, this.font, tr("litematicaplus.zones.split"), this.detailX, this.top);
        ZonePlan.Zone zone = ZoneManager.get().zone(placement, selectedZone);
        if (zone != null) {
            Ui.label(graphics, this.font, tr("litematicaplus.zones.zone_title", zone.index()), this.detailX,
                    this.infoY - 11);
            Ui.clipped(graphics, this.font, tr("litematicaplus.zones.bounds", zone.minX(), zone.maxX(), zone.minZ(),
                    zone.maxZ(), zone.minY(), zone.maxY()).getString(), this.detailX, this.infoY, this.detailWidth,
                    Theme.TEXT_DIM);
            long[] counts = ZoneManager.get().counts(placement, zone.index());
            String owner = placement.zoneClaims().get(zone.index());
            Component line = counts == null ? tr("litematicaplus.zones.counting")
                    : tr("litematicaplus.zones.done", counts[0], counts[1]);
            if (owner != null) {
                line = line.copy().append(" · ").append(tr("litematicaplus.zones.owner", owner));
            }
            Ui.clipped(graphics, this.font, line.getString(), this.detailX, this.infoY + 11, this.detailWidth,
                    Theme.TEXT);
        } else {
            Ui.wrapped(graphics, this.font, tr("litematicaplus.zones.set_split"), this.detailX, this.infoY - 11,
                    this.detailWidth, 3, Theme.TEXT_DIM, mouseX, mouseY);
        }
        Ui.label(graphics, this.font, tr("litematicaplus.zones.team"), this.detailX, this.teamY - 11);
        Component problem = ZoneChat.problem(this.minecraft);
        String template = ZoneChat.template(LitematicaPlusConfig.current());
        Component hint = problem != null ? problem : tr("litematicaplus.zones.private_hint", "/" + template
                .replace("{player}", tr("litematicaplus.zones.player").getString()).replace("{text}", "…"));
        int lines = Math.max(0, (this.top + this.height - this.hintY) / 10);
        Ui.wrapped(graphics, this.font, hint, this.detailX, this.hintY, this.detailWidth, lines,
                problem != null ? Theme.WARNING : Theme.TEXT_DIM, mouseX, mouseY);
    }
}
