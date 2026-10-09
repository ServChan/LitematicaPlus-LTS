package org.lts.litematicaplus.gui;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.share.SharedPlacement;
import org.lts.litematicaplus.share.SyncSession;
import org.lts.litematicaplus.share.SyncmaticaClient;

final class ServerView extends TabView {
    private static final int ARM_TICKS = 80;
    private static final int LINE_HEIGHT = 12;

    private static UUID selectedId;

    private ListPanel<SharedPlacement> list;
    private int knownRevision = -1;
    private int removeArmed;
    private int listScroll;
    private int detailX;
    private int detailWidth;
    private final Map<SharedPlacement, TextMemo> statusText = new IdentityHashMap<>();

    ServerView(HubScreen screen) {
        super(screen);
    }

    private static SyncmaticaClient client() {
        return SyncmaticaClient.get();
    }

    @Override
    Component summary() {
        return client().available() ? tr("litematicaplus.server.summary", client().placements().size())
                : Component.empty();
    }

    private SharedPlacement selected(List<SharedPlacement> placements) {
        for (SharedPlacement placement : placements) {
            if (placement.id().equals(selectedId)) {
                return placement;
            }
        }
        if (placements.isEmpty()) {
            return null;
        }
        selectedId = placements.get(0).id();
        return placements.get(0);
    }

    @Override
    void init() {
        SyncmaticaClient client = client();
        this.knownRevision = client.revision();
        this.list = null;
        if (!client.available()) {
            return;
        }
        List<SharedPlacement> placements = client.placements();
        this.statusText.keySet().retainAll(placements);
        int listWidth = Math.max(110, this.width * 40 / 100);
        this.detailX = this.left + listWidth + Theme.GAP;
        this.detailWidth = this.width - listWidth - Theme.GAP;
        int listHeight = this.height - Theme.CONTROL_HEIGHT - Theme.GAP;
        this.list = add(new ListPanel<SharedPlacement>(this.left, this.top, listWidth, listHeight, Theme.ROW_HEIGHT,
                tr("litematicaplus.tab.server"), this::renderRow)
                .describe(SharedPlacement::title)
                .empty(tr("litematicaplus.server.empty_title"), tr("litematicaplus.server.empty_hint"))
                .onSelect(placement -> {
                    selectedId = placement.id();
                    this.removeArmed = 0;
                    this.screen.refresh();
                })
                .onActivate(client::load));
        this.list.setItems(placements);
        SharedPlacement selected = selected(placements);
        this.list.selectItem(selected);
        this.list.setScroll(this.listScroll);
        addShareButton(client, listWidth);
        if (selected == null) {
            return;
        }
        int bottomY = this.top + this.height - Theme.CONTROL_HEIGHT;
        int half = (this.detailWidth - Theme.GAP) / 2;
        SyncmaticaClient.Status status = client.status(selected);
        String loadKey = switch (status) {
            case LOADED -> "litematicaplus.server.unload";
            case DOWNLOADING -> "litematicaplus.server.downloading";
            default -> "litematicaplus.server.load";
        };
        FlatButton load = add(FlatButton.primary(this.detailX, bottomY, half, tr(loadKey), button -> {
            if (client.status(selected) == SyncmaticaClient.Status.LOADED) {
                client.unload(selected);
            } else {
                client.load(selected);
            }
            this.screen.refresh();
        }));
        load.active = status != SyncmaticaClient.Status.DOWNLOADING && status != SyncmaticaClient.Status.OTHER_DIMENSION;
        add(FlatButton.danger(this.detailX + half + Theme.GAP, bottomY, this.detailWidth - half - Theme.GAP,
                tr(this.removeArmed > 0 ? "litematicaplus.server.remove_confirm" : "litematicaplus.server.remove"),
                button -> {
                    if (this.removeArmed > 0) {
                        this.removeArmed = 0;
                        client.removeFromServer(selected);
                    } else {
                        this.removeArmed = ARM_TICKS;
                    }
                    this.screen.refresh();
                }));
    }

    private void addShareButton(SyncmaticaClient client, int listWidth) {
        SchematicPlacement local = PlacementManager.get().selected();
        Component label;
        if (local == null) {
            label = tr("litematicaplus.server.share_none");
        } else if (local.serverId() != null) {
            label = tr("litematicaplus.server.share_already", local.name());
        } else if (client.isSharing(local)) {
            label = tr("litematicaplus.server.share_progress", local.name());
        } else {
            label = tr("litematicaplus.server.share", local.name());
        }
        FlatButton share = add(FlatButton.secondary(this.left, this.top + this.height - Theme.CONTROL_HEIGHT, listWidth,
                label, button -> {
                    client.share(PlacementManager.get().selected());
                    this.screen.refresh();
                }));
        share.active = client.canShare(local);
    }

    @Override
    void tick() {
        if (this.list != null) {
            this.listScroll = this.list.scroll();
        }
        boolean expired = this.removeArmed > 0 && --this.removeArmed == 0;
        if (expired || client().revision() != this.knownRevision) {
            this.screen.refresh();
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, Font rowFont, SharedPlacement placement, int x, int y,
            int rowWidth, int rowHeight, boolean isSelected, boolean hovered) {
        SyncmaticaClient client = client();
        SyncmaticaClient.Status status = client.status(placement);
        SchematicPlacement local = client.linked(placement.id());
        boolean editing = client.isModifying(local) || client.isModifyPending(local);
        int bytes = status == SyncmaticaClient.Status.DOWNLOADING ? client.downloadedBytes(placement) : 0;
        TextMemo memo = this.statusText.computeIfAbsent(placement, ignored -> new TextMemo());
        String text = memo.stale(status, bytes / 1024, editing ? 1L : 0L, 0L)
                ? memo.set(editing ? tr("litematicaplus.server.status.editing").getString()
                : status == SyncmaticaClient.Status.DOWNLOADING
                ? tr("litematicaplus.server.status.progress", Math.max(0, bytes) / 1024).getString()
                : tr("litematicaplus.server.status." + status.name().toLowerCase(Locale.ROOT)).getString())
                : memo.text();
        int color = editing ? Theme.WARNING : switch (status) {
            case LOADED -> Theme.SUCCESS;
            case DOWNLOADING -> Theme.WARNING;
            case CACHED -> Theme.ACCENT;
            case REMOTE -> Theme.TEXT_DIM;
            case OTHER_DIMENSION -> Theme.TEXT_DISABLED;
        };
        int textY = y + (rowHeight - 8) / 2;
        int statusWidth = rowFont.width(text);
        Ui.clipped(graphics, rowFont, placement.title(), x + 8, textY, rowWidth - statusWidth - 22,
                status == SyncmaticaClient.Status.OTHER_DIMENSION ? Theme.TEXT_DIM : Theme.TEXT_BRIGHT);
        Ui.right(graphics, rowFont, text, x + rowWidth - 6, textY, color);
    }

    @Override
    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        SyncmaticaClient client = client();
        if (client.conflict()) {
            Ui.emptyState(graphics, this.font, this.left, this.top, this.width, this.height,
                    tr("litematicaplus.server.conflict_title"), tr("litematicaplus.server.conflict_hint"));
            return;
        }
        if (!client.available()) {
            boolean connecting = client.phase() == SyncSession.Phase.FEATURES || client.phase() == SyncSession.Phase.VERSION;
            Ui.emptyState(graphics, this.font, this.left, this.top, this.width, this.height,
                    tr(connecting ? "litematicaplus.server.connecting_title" : "litematicaplus.server.unavailable_title"),
                    tr(connecting ? "litematicaplus.server.connecting_hint" : "litematicaplus.server.unavailable_hint"));
            return;
        }
        SharedPlacement selected = client.placement(selectedId);
        if (selected == null) {
            Ui.emptyState(graphics, this.font, this.detailX, this.top, this.detailWidth,
                    this.height - Theme.CONTROL_HEIGHT - Theme.GAP, tr("litematicaplus.server.none_selected"),
                    tr("litematicaplus.server.none_selected_hint"));
            return;
        }
        int x = this.detailX;
        int y = this.top;
        Ui.heading(graphics, this.font, Component.literal(selected.title()), x, y, 1.0F, Theme.TEXT_BRIGHT);
        y += LINE_HEIGHT + 4;
        y = line(graphics, x, y, "litematicaplus.server.owner", selected.owner().name());
        y = line(graphics, x, y, "litematicaplus.server.modified_by", selected.lastModifiedBy().name());
        y = line(graphics, x, y, "litematicaplus.server.position",
                selected.x() + " " + selected.y() + " " + selected.z());
        y = line(graphics, x, y, "litematicaplus.server.dimension", selected.dimension());
        y = line(graphics, x, y, "litematicaplus.server.orientation",
                tr("litematicaplus.rotation." + Rotation.values()[selected.rotation()].name().toLowerCase(Locale.ROOT))
                        .getString() + ", "
                        + tr("litematicaplus.mirror." + Mirror.values()[selected.mirror()].name().toLowerCase(Locale.ROOT))
                        .getString());
        y = line(graphics, x, y, "litematicaplus.server.file", selected.fileName());
        y += 6;
        SyncmaticaClient.Status status = client.status(selected);
        SchematicPlacement local = client.linked(selected.id());
        String hintKey;
        int hintColor = Theme.TEXT_DIM;
        if (client.isModifying(local)) {
            hintKey = "litematicaplus.server.hint_editing";
            hintColor = Theme.WARNING;
        } else if (status == SyncmaticaClient.Status.LOADED) {
            hintKey = "litematicaplus.server.hint_loaded";
        } else if (status == SyncmaticaClient.Status.OTHER_DIMENSION) {
            hintKey = "litematicaplus.server.hint_other_dimension";
            hintColor = Theme.WARNING;
        } else {
            hintKey = "litematicaplus.server.hint_load";
        }
        int bottom = this.top + this.height - Theme.CONTROL_HEIGHT - 6;
        int lines = Ui.wrapped(graphics, this.font, tr(hintKey), x, y, this.detailWidth, Math.max(0, (bottom - y) / 10),
                hintColor, mouseX, mouseY);
        y += lines * 10 + 4;
    }

    private int line(GuiGraphicsExtractor graphics, int x, int y, String key, String value) {
        String label = tr(key).getString();
        int labelWidth = Math.min(this.detailWidth / 2, Math.max(70, this.font.width(label) + 8));
        Ui.clipped(graphics, this.font, label, x, y, labelWidth - 4, Theme.TEXT_DIM);
        Ui.clipped(graphics, this.font, value, x + labelWidth, y, this.detailWidth - labelWidth, Theme.TEXT);
        return y + LINE_HEIGHT;
    }
}
