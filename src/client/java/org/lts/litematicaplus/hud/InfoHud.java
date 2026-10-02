package org.lts.litematicaplus.hud;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.gui.Blueprint;
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
import org.lts.litematicaplus.verify.MismatchType;
import org.lts.litematicaplus.world.LayerRange;
import org.lts.litematicaplus.world.PlacementVolume;

public final class InfoHud {
    private static final int WIDTH = 176;
    private static final int PADDING = 6;
    private static final int LINE = 10;
    private static final Reference2ObjectOpenHashMap<Item, ItemStack> STACKS = new Reference2ObjectOpenHashMap<>();
    private static List<MaterialList.Entry> materials = List.of();
    private static Component target;
    private static int ticks;
    private static long lastJobs;
    private static long jobsPerSecond;

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
        if (ticks % 10 != 0) {
            return;
        }
        SchematicPlacement placement = PlacementManager.get().selected();
        if (!config.hudEnabled || config.hudMaterialLines <= 0 || placement == null || minecraft.player == null) {
            materials = List.of();
            return;
        }
        PlacementRuntime runtime = GhostEngine.get().runtime(placement.id());
        List<MaterialList.Entry> all = MaterialList.compute(runtime, minecraft.player.getInventory(), MaterialList.Sort.MISSING);
        List<MaterialList.Entry> top = new ArrayList<>();
        for (MaterialList.Entry entry : all) {
            if (entry.missing() <= 0L) {
                break;
            }
            top.add(entry);
            if (top.size() >= config.hudMaterialLines) {
                break;
            }
        }
        materials = top;
    }

    public static void render(Minecraft minecraft, GuiGraphicsExtractor graphics) {
        if (minecraft.player == null || minecraft.level == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        if (minecraft.gui.screen() != null || minecraft.gui.hud.getDebugOverlay().showDebugScreen()) {
            return;
        }
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!config.hudEnabled) {
            return;
        }
        boolean tool = ToolController.get().holdingTool(minecraft);
        SchematicPlacement placement = PlacementManager.get().selected();
        NoteTuner.Progress tuning = config.noteTuningHud ? NoteTuner.progress(minecraft) : null;
        boolean tuningActive = tuning != null && tuning.blocks() > 0;
        if (placement == null && !tool && !PasteController.active() && !GhostRenderer.failed() && !tuningActive) {
            return;
        }
        Font font = minecraft.font;
        PlacementRuntime runtime = placement == null ? null : GhostEngine.get().runtime(placement.id());
        LayerRange layer = GhostEngine.get().layer();
        AreaBox box = tool ? SelectionManager.get().selectedBox() : null;
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
        int height = PADDING * 2 + lines * LINE + 1 + (placement != null ? 7 : 0) + materials.size() * LINE
                + (materials.isEmpty() ? 0 : 4);
        int screenWidth = graphics.guiWidth();
        int x = config.hudRightSide ? screenWidth - WIDTH - 6 : 6;
        int y = 6;
        graphics.fill(x, y, x + WIDTH, y + height, Theme.HUD_BACKGROUND);
        graphics.outline(x, y, WIDTH, height, Theme.BORDER);
        int textX = x + PADDING + 2;
        int cursor = y + PADDING;
        Blueprint.marker(graphics, textX, cursor + 2, Theme.AMBER);
        graphics.text(font, Component.translatable("litematicaplus.hud.title"), textX + 6, cursor, Theme.TEXT_DIM, false);
        if (tool) {
            Component mode = Component.translatable(ToolController.get().mode().translationKey());
            int modeWidth = font.width(mode) + 8;
            int modeX = x + WIDTH - PADDING - modeWidth;
            graphics.fill(modeX, cursor - 2, modeX + modeWidth, cursor + 9, Theme.CHIP);
            graphics.text(font, mode, modeX + 4, cursor, Theme.TEXT_BRIGHT, false);
        }
        cursor += LINE + 1;
        Blueprint.dashed(graphics, textX, cursor - 2, WIDTH - PADDING * 2 - 2, Theme.DIVIDER);
        cursor += 1;
        if (placement != null) {
            cursor = renderPlacement(graphics, font, placement, runtime, x, textX, cursor);
        }
        if (!layer.unrestricted()) {
            graphics.text(font, font.plainSubstrByWidth(layerText(layer).getString(), WIDTH - PADDING * 2 - 4), textX,
                    cursor, Theme.TEXT_DIM, false);
            cursor += LINE;
        }
        if (box != null && !box.isEmpty()) {
            graphics.text(font, font.plainSubstrByWidth(Component.translatable("litematicaplus.hud.selection",
                    box.name(), box.sizeX(), box.sizeY(), box.sizeZ()).getString(), WIDTH - PADDING * 2 - 4),
                    textX, cursor, Theme.TEXT_DIM, false);
            cursor += LINE;
        }
        if (PasteController.active()) {
            graphics.text(font, Component.translatable("litematicaplus.hud.paste",
                    String.format(Locale.ROOT, "%.0f", PasteController.progress() * 100.0)), textX, cursor, Theme.WARNING, false);
            cursor += LINE;
        }
        if (tuningActive) {
            graphics.text(font, Component.translatable("litematicaplus.hud.tuning", tuning.blocks(), tuning.clicks()),
                    textX, cursor, Theme.WARNING, false);
            cursor += LINE;
        }
        if (targeted != null) {
            graphics.text(font, targeted, textX, cursor, Theme.TEXT_BRIGHT, false);
            cursor += LINE;
        }
        if (GhostRenderer.failed()) {
            graphics.text(font, Component.translatable("litematicaplus.hud.render_failed"), textX, cursor, Theme.DANGER, false);
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
            graphics.text(font, Component.translatable("litematicaplus.hud.diag_sections", GhostRenderer.lastDrawn(), ready,
                    total), textX, cursor, Theme.TEXT_DIM, false);
            cursor += LINE;
            graphics.text(font, Component.translatable("litematicaplus.hud.diag_work", engine.pending(), engine.inFlight(),
                    jobsPerSecond), textX, cursor, Theme.TEXT_DIM, false);
            cursor += LINE;
            Component depth = switch (GhostRenderer.lastDepth()) {
                case 1 -> Component.translatable("litematicaplus.hud.diag_depth.voxy");
                case 2 -> Component.translatable("litematicaplus.hud.diag_depth.hidden", GhostRenderer.lastHidden());
                default -> Component.translatable("litematicaplus.hud.diag_depth.game");
            };
            graphics.text(font, Component.translatable("litematicaplus.hud.diag_depth", depth), textX, cursor,
                    GhostRenderer.lastDepth() == 2 ? Theme.WARNING : Theme.TEXT_DIM, false);
            cursor += LINE;
            graphics.text(font, Component.translatable("litematicaplus.hud.diag_changes", engine.changesLastMinute()),
                    textX, cursor, Theme.TEXT_DIM, false);
            cursor += LINE;
        }
        if (!materials.isEmpty()) {
            cursor += 2;
            Blueprint.dashed(graphics, textX, cursor - 1, WIDTH - PADDING * 2 - 2, Theme.DIVIDER);
            cursor += 2;
            for (MaterialList.Entry entry : materials) {
                ItemStack stack = stackOf(entry.item());
                graphics.pose().pushMatrix();
                graphics.pose().translate(textX, cursor - 1);
                graphics.pose().scale(0.5F, 0.5F);
                graphics.fakeItem(stack, 0, 0);
                graphics.pose().popMatrix();
                String count = entry.missing() + " / " + entry.available();
                int countWidth = font.width(count);
                int nameWidth = WIDTH - PADDING * 2 - 14 - countWidth - 6;
                String name = font.plainSubstrByWidth(stack.getHoverName().getString(), nameWidth);
                graphics.text(font, name, textX + 11, cursor, Theme.TEXT, false);
                graphics.text(font, count, x + WIDTH - PADDING - countWidth, cursor,
                        entry.available() >= entry.missing() ? Theme.SUCCESS : Theme.WARNING, false);
                cursor += LINE;
            }
        }
    }

    private static int renderPlacement(GuiGraphicsExtractor graphics, Font font, SchematicPlacement placement,
            PlacementRuntime runtime, int x, int textX, int cursor) {
        String name = font.plainSubstrByWidth(placement.name(), WIDTH - PADDING * 2 - 4);
        graphics.text(font, name, textX, cursor, Theme.TEXT_BRIGHT, false);
        cursor += LINE;
        PlacementVolume volume = runtime == null ? null : runtime.volume();
        if (placement.loadState() == SchematicPlacement.LoadState.FAILED) {
            graphics.text(font, Component.translatable("litematicaplus.hud.failed"), textX, cursor, Theme.DANGER, false);
            return cursor + LINE * 2 + 6;
        }
        if (volume == null) {
            graphics.text(font, Component.translatable(placement.enabled()
                    ? "litematicaplus.hud.loading" : "litematicaplus.hud.disabled"), textX, cursor, Theme.TEXT_DIM, false);
            return cursor + LINE * 2 + 6;
        }
        double progress = runtime.progress();
        int barX = textX;
        int barWidth = WIDTH - PADDING * 2 - 4;
        Blueprint.progress(graphics, barX, cursor, barWidth, 4, progress, progress >= 0.9999 ? Theme.SUCCESS : Theme.ACCENT);
        cursor += 7;
        Component progressText = Component.translatable("litematicaplus.hud.progress",
                String.format(Locale.ROOT, "%.1f", progress * 100.0), runtime.count(MismatchType.CORRECT),
                runtime.totalBlocks());
        graphics.text(font, progressText, textX, cursor, Theme.TEXT, false);
        if (runtime.scoped()) {
            graphics.text(font, Component.translatable("litematicaplus.hud.scoped"), textX + font.width(progressText) + 6,
                    cursor, Theme.WARNING, false);
        }
        cursor += LINE;
        long wrong = runtime.count(MismatchType.WRONG_BLOCK) + runtime.count(MismatchType.WRONG_STATE);
        int cx = textX;
        cx = stat(graphics, font, "litematicaplus.hud.missing", runtime.count(MismatchType.MISSING),
                MismatchType.MISSING.textColor(), cx, cursor);
        cx = stat(graphics, font, "litematicaplus.hud.wrong", wrong, MismatchType.WRONG_BLOCK.textColor(), cx, cursor);
        stat(graphics, font, "litematicaplus.hud.extra", runtime.count(MismatchType.EXTRA),
                MismatchType.EXTRA.textColor(), cx, cursor);
        return cursor + LINE;
    }

    private static int stat(GuiGraphicsExtractor graphics, Font font, String key, long value, int color, int x, int y) {
        Component text = Component.translatable(key, value);
        graphics.text(font, text, x, y, value > 0L ? color : Theme.TEXT_DIM, false);
        return x + font.width(text) + 8;
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
