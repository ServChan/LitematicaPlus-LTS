package org.lts.litematicaplus.hud;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.gui.Blueprint;
import org.lts.litematicaplus.gui.TextMemo;
import org.lts.litematicaplus.gui.Theme;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.render.GhostRenderer;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.selection.AreaBox;
import org.lts.litematicaplus.selection.SelectionManager;
import org.lts.litematicaplus.tool.EasyPlace;
import org.lts.litematicaplus.tool.EntityAssist;
import org.lts.litematicaplus.tool.GhostRaycast;
import org.lts.litematicaplus.tool.NoteTuner;
import org.lts.litematicaplus.tool.PasteController;
import org.lts.litematicaplus.tool.ToolController;
import org.lts.litematicaplus.tool.ToolMode;
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class InfoHud {
    public static final int WIDTH = 176;
    private static final int MARGIN = 6;
    private static final int PADDING = 6;
    private static final int LINE = 10;
    private static final int TEXT_WIDTH = WIDTH - PADDING * 2 - 4;
    private static final Reference2ObjectOpenHashMap<Item, ItemStack> STACKS = new Reference2ObjectOpenHashMap<>();
    private static final TextMemo TITLE = new TextMemo();
    private static final TextMemo MODE = new TextMemo();
    private static final TextMemo LAYER = new TextMemo();
    private static final TextMemo SELECTION = new TextMemo();
    private static final TextMemo PASTE = new TextMemo();
    private static final TextMemo TUNING = new TextMemo();
    private static final TextMemo RENDER_FAILED = new TextMemo();
    private static final TextMemo DIAG_SECTIONS = new TextMemo();
    private static final TextMemo DIAG_WORK = new TextMemo();
    private static final TextMemo DIAG_DEPTH = new TextMemo();
    private static final TextMemo DIAG_CHANGES = new TextMemo();
    private static final TextMemo NAME = new TextMemo();
    private static final TextMemo STATE = new TextMemo();
    private static final TextMemo PROGRESS = new TextMemo();
    private static final TextMemo SCOPED = new TextMemo();
    private static final TextMemo MISSING = new TextMemo();
    private static final TextMemo WRONG = new TextMemo();
    private static final TextMemo EXTRA = new TextMemo();

    private record MaterialRow(ItemStack stack, String name, String count, boolean enough) {
    }

    private static List<MaterialRow> materials = List.of();
    private static Component target;
    private static Component targetSource;
    private static FormattedCharSequence targetLine = FormattedCharSequence.EMPTY;
    private static NoteTuner.Progress tuning;
    private static int ticks;
    private static long lastJobs;
    private static long jobsPerSecond;
    private static boolean wasVisible;

    private InfoHud() {
    }

    public static ItemStack stackOf(Item item) {
        ItemStack stack = STACKS.get(item);
        if (stack == null) {
            stack = new ItemStack(item);
            STACKS.put(item, stack);
        }
        return stack;
    }

    private static boolean visible(Minecraft minecraft, LitematicaPlusConfig config) {
        return config.hudEnabled && minecraft.player != null && minecraft.level != null && minecraft.gui.screen() == null
                && !minecraft.gui.hud.isHidden() && !minecraft.gui.hud.getDebugOverlay().showDebugScreen();
    }

    public static void tick(Minecraft minecraft) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (++ticks % 2 == 0) {
            target = null;
            if (config.hudEnabled && config.renderEnabled && minecraft.gui.screen() == null) {
                GhostRaycast.Hit hit = GhostRaycast.trace(minecraft,
                        candidate -> EasyPlace.isPlaceable(minecraft, candidate));
                if (hit != null) {
                    target = Component.translatable("litematicaplus.hud.target", hit.state().getBlock().getName());
                }
                if (config.entityPlacement) {
                    Component entity = EntityAssist.describe(minecraft);
                    if (entity != null) {
                        target = entity;
                    }
                }
            }
        }
        if (ticks % 20 == 0) {
            long jobs = GhostEngine.get().jobsStarted();
            jobsPerSecond = jobs - lastJobs;
            lastJobs = jobs;
        }
        boolean visible = visible(minecraft, config);
        tuning = visible && config.noteTuningHud ? NoteTuner.progress(minecraft) : null;
        boolean appeared = visible && !wasVisible;
        wasVisible = visible;
        if (!visible || ticks % 10 != 0 && !appeared) {
            return;
        }
        SchematicPlacement placement = PlacementManager.get().visibleSelection();
        if (config.hudMaterialLines <= 0 || placement == null) {
            materials = List.of();
            return;
        }
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        List<MaterialList.Entry> all = MaterialList.compute(runtime, minecraft.player.getInventory(), MaterialList.Sort.MISSING);
        List<MaterialRow> top = new ArrayList<>();
        Font font = minecraft.font;
        for (MaterialList.Entry entry : all) {
            if (entry.missing() <= 0L) {
                break;
            }
            ItemStack stack = stackOf(entry.item());
            String count = entry.missing() + " / " + entry.haveText();
            int nameWidth = WIDTH - PADDING * 2 - 14 - font.width(count) - 6;
            top.add(new MaterialRow(stack, font.plainSubstrByWidth(stack.getHoverName().getString(), nameWidth), count,
                    entry.available() >= entry.missing()));
            if (top.size() >= config.hudMaterialLines) {
                break;
            }
        }
        materials = top;
    }

    private static String text(TextMemo memo, Font font, Object ref, long a, long b, long c, Supplier<Component> make) {
        return memo.stale(ref, a, b, c) ? memo.set(font.plainSubstrByWidth(make.get().getString(), TEXT_WIDTH)) : memo.text();
    }

    private static String text(TextMemo memo, Font font, String key) {
        return text(memo, font, key, 0L, 0L, 0L, () -> Component.translatable(key));
    }

    public static int left(int screenWidth, float fraction) {
        return MARGIN + Math.round(fraction * Math.max(0, screenWidth - WIDTH - MARGIN * 2));
    }

    public static int top(int screenHeight, int height, float fraction) {
        return MARGIN + Math.round(fraction * Math.max(0, screenHeight - height - MARGIN * 2));
    }

    public static void render(Minecraft minecraft, GuiGraphicsExtractor graphics) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (visible(minecraft, config)) {
            renderAt(minecraft, graphics, config.hudX, config.hudY);
        }
    }

    public static int renderAt(Minecraft minecraft, GuiGraphicsExtractor graphics, float fractionX, float fractionY) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        boolean tool = ToolController.get().holdingTool(minecraft);
        SchematicPlacement placement = PlacementManager.get().visibleSelection();
        NoteTuner.Progress tuned = config.noteTuningHud ? tuning : null;
        boolean tuningActive = tuned != null && tuned.blocks() > 0;
        if (placement == null && !tool && !PasteController.active() && !GhostRenderer.failed() && !tuningActive) {
            return 0;
        }
        Font font = minecraft.font;
        PlacementRuntime runtime = placement == null ? null : GhostEngine.get().runtime(placement.id());
        LayerRange layer = GhostEngine.get().layer();
        AreaBox box = tool ? SelectionManager.get().selectedBox() : null;
        List<MaterialRow> rows = materials;
        int lines = 1;
        if (placement != null) {
            lines += 3;
        }
        if (!layer.unrestricted()) {
            lines++;
        }
        if (box != null && !box.isEmpty()) {
            lines++;
        }
        if (PasteController.active()) {
            lines++;
        }
        if (tuningActive) {
            lines++;
        }
        if (GhostRenderer.failed()) {
            lines++;
        }
        if (config.hudDiagnostics) {
            lines += 4;
        }
        Component targeted = target;
        if (targeted != null) {
            lines++;
        }
        int height = PADDING * 2 + lines * LINE + 1 + (placement != null ? 7 : 0) + rows.size() * LINE
                + (rows.isEmpty() ? 0 : 4);
        int x = left(graphics.guiWidth(), fractionX);
        int y = top(graphics.guiHeight(), height, fractionY);
        graphics.fill(x, y, x + WIDTH, y + height, Theme.HUD_BACKGROUND);
        graphics.outline(x, y, WIDTH, height, Theme.BORDER);
        int textX = x + PADDING + 2;
        int cursor = y + PADDING;
        Blueprint.marker(graphics, textX, cursor + 2, Theme.AMBER);
        graphics.text(font, text(TITLE, font, "litematicaplus.hud.title"), textX + 6, cursor, Theme.TEXT_DIM, false);
        if (tool) {
            ToolMode current = ToolController.get().mode();
            String mode = MODE.stale(current) ? MODE.set(Component.translatable(current.translationKey()).getString())
                    : MODE.text();
            int modeWidth = font.width(mode) + 8;
            int modeX = x + WIDTH - PADDING - modeWidth;
            graphics.fill(modeX, cursor - 2, modeX + modeWidth, cursor + 9, Theme.CHIP);
            graphics.text(font, mode, modeX + 4, cursor, Theme.TEXT_BRIGHT, false);
        }
        cursor += LINE + 1;
        Blueprint.dashed(graphics, textX, cursor - 2, WIDTH - PADDING * 2 - 2, Theme.DIVIDER);
        cursor += 1;
        if (placement != null) {
            cursor = renderPlacement(graphics, font, placement, runtime, textX, cursor);
        }
        if (!layer.unrestricted()) {
            graphics.text(font, text(LAYER, font, layer, 0L, 0L, 0L, () -> layerText(layer)), textX, cursor,
                    Theme.TEXT_DIM, false);
            cursor += LINE;
        }
        if (box != null && !box.isEmpty()) {
            long size = (long) box.sizeX() << 42 ^ (long) box.sizeY() << 21 ^ box.sizeZ();
            graphics.text(font, text(SELECTION, font, box, size, box.name().hashCode(), 0L,
                    () -> Component.translatable("litematicaplus.hud.selection", box.name(), box.sizeX(), box.sizeY(),
                            box.sizeZ())), textX, cursor, Theme.TEXT_DIM, false);
            cursor += LINE;
        }
        if (PasteController.active()) {
            double progress = PasteController.progress();
            graphics.text(font, text(PASTE, font, null, Double.doubleToLongBits(progress), 0L, 0L,
                    () -> Component.translatable("litematicaplus.hud.paste",
                            String.format(Locale.ROOT, "%.0f", progress * 100.0))), textX, cursor, Theme.WARNING, false);
            cursor += LINE;
        }
        if (tuningActive) {
            graphics.text(font, text(TUNING, font, null, tuned.blocks(), tuned.clicks(), 0L,
                    () -> Component.translatable("litematicaplus.hud.tuning", tuned.blocks(), tuned.clicks())),
                    textX, cursor, Theme.WARNING, false);
            cursor += LINE;
        }
        if (targeted != null) {
            if (targeted != targetSource) {
                targetSource = targeted;
                targetLine = Language.getInstance().getVisualOrder(font.substrByWidth(targeted, TEXT_WIDTH));
            }
            graphics.text(font, targetLine, textX, cursor, Theme.TEXT_BRIGHT, false);
            cursor += LINE;
        }
        if (GhostRenderer.failed()) {
            graphics.text(font, text(RENDER_FAILED, font, "litematicaplus.hud.render_failed"), textX, cursor,
                    Theme.DANGER, false);
            cursor += LINE;
        }
        if (config.hudDiagnostics) {
            GhostEngine engine = GhostEngine.get();
            int total = 0;
            int ready = 0;
            for (PlacementRuntime each : engine.runtimes()) {
                total += each.slotCount();
                ready += each.drawable().size();
            }
            int drawn = GhostRenderer.lastDrawn();
            int readyCount = ready;
            int totalCount = total;
            graphics.text(font, text(DIAG_SECTIONS, font, null, drawn, readyCount, totalCount,
                    () -> Component.translatable("litematicaplus.hud.diag_sections", drawn, readyCount, totalCount)),
                    textX, cursor, Theme.TEXT_DIM, false);
            cursor += LINE;
            int pending = engine.pending();
            int inFlight = engine.inFlight();
            long perSecond = jobsPerSecond;
            graphics.text(font, text(DIAG_WORK, font, null, pending, inFlight, perSecond,
                    () -> Component.translatable("litematicaplus.hud.diag_work", pending, inFlight, perSecond)),
                    textX, cursor, Theme.TEXT_DIM, false);
            cursor += LINE;
            int depthMode = GhostRenderer.lastDepth();
            long hidden = GhostRenderer.lastHidden();
            graphics.text(font, text(DIAG_DEPTH, font, null, depthMode, hidden, 0L, () -> {
                Component depth = switch (depthMode) {
                    case 1 -> Component.translatable("litematicaplus.hud.diag_depth.voxy");
                    case 2 -> Component.translatable("litematicaplus.hud.diag_depth.hidden", GhostRenderer.lastHidden());
                    default -> Component.translatable("litematicaplus.hud.diag_depth.game");
                };
                return Component.translatable("litematicaplus.hud.diag_depth", depth);
            }), textX, cursor, depthMode == 2 ? Theme.WARNING : Theme.TEXT_DIM, false);
            cursor += LINE;
            long changes = engine.changesLastMinute();
            graphics.text(font, text(DIAG_CHANGES, font, null, changes, 0L, 0L,
                    () -> Component.translatable("litematicaplus.hud.diag_changes", changes)),
                    textX, cursor, Theme.TEXT_DIM, false);
            cursor += LINE;
        }
        if (!rows.isEmpty()) {
            cursor += 2;
            Blueprint.dashed(graphics, textX, cursor - 1, WIDTH - PADDING * 2 - 2, Theme.DIVIDER);
            cursor += 2;
            for (MaterialRow row : rows) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(textX, cursor - 1);
                graphics.pose().scale(0.5F, 0.5F);
                graphics.fakeItem(row.stack(), 0, 0);
                graphics.pose().popMatrix();
                int countWidth = font.width(row.count());
                graphics.text(font, row.name(), textX + 11, cursor, Theme.TEXT, false);
                graphics.text(font, row.count(), x + WIDTH - PADDING - countWidth, cursor,
                        row.enough() ? Theme.SUCCESS : Theme.WARNING, false);
                cursor += LINE;
            }
        }
        return height;
    }

    private static int renderPlacement(GuiGraphicsExtractor graphics, Font font, SchematicPlacement placement,
            PlacementRuntime runtime, int textX, int cursor) {
        String placementName = placement.name();
        String name = NAME.stale(placementName) ? NAME.set(font.plainSubstrByWidth(placementName, TEXT_WIDTH)) : NAME.text();
        graphics.text(font, name, textX, cursor, Theme.TEXT_BRIGHT, false);
        cursor += LINE;
        PlacementVolume volume = runtime == null ? null : runtime.volume();
        if (placement.loadState() == SchematicPlacement.LoadState.FAILED) {
            graphics.text(font, text(STATE, font, "litematicaplus.hud.failed"), textX, cursor, Theme.DANGER, false);
            return cursor + LINE * 2 + 6;
        }
        if (volume == null) {
            graphics.text(font, text(STATE, font, "litematicaplus.hud.loading"), textX, cursor, Theme.TEXT_DIM, false);
            return cursor + LINE * 2 + 6;
        }
        double progress = runtime.progress();
        int barX = textX;
        int barWidth = TEXT_WIDTH;
        Blueprint.progress(graphics, barX, cursor, barWidth, 4, progress, progress >= 0.9999 ? Theme.SUCCESS : Theme.ACCENT);
        cursor += 7;
        long correct = runtime.count(MismatchType.CORRECT);
        long total = runtime.totalBlocks();
        String progressText = text(PROGRESS, font, null, Double.doubleToLongBits(progress), correct, total,
                () -> Component.translatable("litematicaplus.hud.progress",
                        String.format(Locale.ROOT, "%.1f", progress * 100.0), correct, total));
        graphics.text(font, progressText, textX, cursor, Theme.TEXT, false);
        if (runtime.scoped()) {
            int scopedX = textX + font.width(progressText) + 6;
            int room = textX + TEXT_WIDTH - scopedX;
            if (room > 0) {
                String scoped = text(SCOPED, font, "litematicaplus.hud.scoped");
                graphics.text(font, font.width(scoped) > room ? font.plainSubstrByWidth(scoped, room) : scoped, scopedX,
                        cursor, Theme.WARNING, false);
            }
        }
        cursor += LINE;
        long wrong = runtime.count(MismatchType.WRONG_BLOCK) + runtime.count(MismatchType.WRONG_STATE);
        int limit = textX + TEXT_WIDTH;
        int cx = textX;
        cx = stat(graphics, font, MISSING, "litematicaplus.hud.missing", runtime.count(MismatchType.MISSING),
                MismatchType.MISSING.textColor(), cx, cursor, limit);
        cx = stat(graphics, font, WRONG, "litematicaplus.hud.wrong", wrong, MismatchType.WRONG_BLOCK.textColor(), cx,
                cursor, limit);
        stat(graphics, font, EXTRA, "litematicaplus.hud.extra", runtime.count(MismatchType.EXTRA),
                MismatchType.EXTRA.textColor(), cx, cursor, limit);
        return cursor + LINE;
    }

    private static int stat(GuiGraphicsExtractor graphics, Font font, TextMemo memo, String key, long value, int color,
            int x, int y, int limit) {
        String text = text(memo, font, key, value, 0L, 0L, () -> Component.translatable(key, value));
        int room = limit - x;
        if (room <= 0) {
            return x;
        }
        String shown = font.width(text) > room ? font.plainSubstrByWidth(text, room) : text;
        graphics.text(font, shown, x, y, value > 0L ? color : Theme.TEXT_DIM, false);
        return x + font.width(shown) + 8;
    }

    public static Component layerText(LayerRange layer) {
        Component layerPart = null;
        if (!layer.isAll()) {
            String axis = layer.axis().getName().toUpperCase(Locale.ROOT);
            layerPart = layer.mode() == LayerRange.Mode.RANGE
                    ? Component.translatable("litematicaplus.hud.layer_range", axis, layer.min(), layer.max())
                    : Component.translatable("litematicaplus.hud.layer_single",
                            Component.translatable("litematicaplus.layer.mode." + layer.mode().name().toLowerCase(Locale.ROOT)), axis,
                            layer.single());
        }
        Component clipPart = null;
        LayerRange.Clip clip = layer.clip();
        if (clip.active()) {
            StringBuilder bounds = new StringBuilder();
            if (clip.xEnabled()) {
                bounds.append("X ").append(clip.minX()).append("..").append(clip.maxX());
            }
            if (clip.zEnabled()) {
                if (!bounds.isEmpty()) {
                    bounds.append(", ");
                }
                bounds.append("Z ").append(clip.minZ()).append("..").append(clip.maxZ());
            }
            clipPart = Component.translatable("litematicaplus.hud.clip", bounds.toString());
        }
        if (layerPart != null && clipPart != null) {
            return Component.empty().append(layerPart).append("; ").append(clipPart);
        }
        return layerPart != null ? layerPart : clipPart != null ? clipPart : Component.empty();
    }
}
